package mutex;

import com.uppaal.tron.Adapter;
import com.uppaal.tron.Reporter;
import com.uppaal.tron.TronException;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.concurrent.*;

import com.sun.net.httpserver.HttpServer;
import com.sun.net.httpserver.HttpExchange;
import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;


/**
 * Adapter that maps the three channels of the UPPAAL‑TRON model
 *   request(id)  →  GET /api/requesting/{id}   Output
 *   grant(id)    ←  HTTP 200 "okay go …"       Input
 *   done(id)     →  GET /api/returning/{id}    Output
 *
 * Updated to handle all Flask server status codes:
 *   - 200: proceed / returned
 *   - 202: queued
 *   - 204: queue_empty
 *   - 409: already_in_queue / not_in_queue
 *   - 423: not_your_turn
 *   - 500: queue_empty_error
 */
public class ClientMutexAdapter implements Adapter {

    // ── TRON channel identifiers ────────────────────────────────────────────
    private int OUT_REQUEST; // request?
    private int OUT_DONE; // done?
    private int IN_GRANT; // grant!

    private HttpServer ingressServer;
    private final int adapterPort = Integer.getInteger("Port", 6000);


    private Reporter reporter;

    // ── HTTP client & config ────────────────────────────────────────────────
    private final HttpClient http = HttpClient.newBuilder()
        .connectTimeout(Duration.ofSeconds(20))
        .build();

    /** Base URL for the Flask mutex server (‑DmutexServerBase=…) */
    private final URI base = URI.create(
        System.getProperty("mutexServerBase", "http://localhost:5000")
    );

    private final Duration reqTimeout = Duration.ofSeconds(3);

    private final ScheduledExecutorService sched =
        Executors.newScheduledThreadPool(2);

    // ── Adapter ⇄ TRON interface ────────────────────────────────────────────
    @Override
    public void configure(Reporter reporter) throws TronException, IOException {
        this.reporter = reporter;

        reporter.setTimeUnit(50_000); // 50 ms per model time unit
        reporter.setTimeout(1_000_000); // test budget: 100 s

        // Bind existing *global* int variables – not the template constant `id`!
        OUT_REQUEST = reporter.addOutput("request");
        reporter.addVarToOutput(OUT_REQUEST, "requesting_c_id"); 
        OUT_DONE = reporter.addOutput("done");
        reporter.addVarToOutput(OUT_DONE, "requesting_c_id");
        IN_GRANT = reporter.addInput("grant");
        reporter.addVarToInput(IN_GRANT, "requesting_c_id"); 
    }

    private void startIngressServer() throws IOException {
        ingressServer = HttpServer.create(new InetSocketAddress(adapterPort), 0);

        ingressServer.createContext("/api/request", this::handleRequest);
        ingressServer.createContext("/api/returning", this::handleDone);

        ingressServer.setExecutor(Executors.newCachedThreadPool());
        ingressServer.start();

        System.out.println(
            "[ADAPTER] Ingress server listening on http://0.0.0.0:" +
            adapterPort + "/api/request"
        );
    }

    @Override
    public void perform(int chan, int[] params) {
        int cid = params[0]; // client id

        if (chan == OUT_REQUEST) { // someone in TRON wants to request
            // Send request to Flask server asynchronously
            sched.execute(() -> {
                try {
                    URI requestUri = URI.create(base + "/api/requesting/" + cid);
                    HttpRequest request = HttpRequest.newBuilder()
                            .uri(requestUri)
                            .timeout(reqTimeout)
                            .GET()
                            .build();

                    HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString());
                    int status = response.statusCode();
                    String body = response.body();

                    System.out.println("[ADAPTER] Client " + cid + " requested → " + body + " (" + status + ")");

                    // Grant access if status 200
                    if (status == 200) {
                        reporter.report(IN_GRANT, new int[]{cid});
                        System.out.println("[ADAPTER] Granting client " + cid);
                    } else if (status == 202) {
                        // queued: optionally retry after delay
                        sched.schedule(() -> perform(chan, params), 2, TimeUnit.SECONDS);
                    } else {
                        System.err.println("[ADAPTER] Request failed for client " + cid + ": " + status);
                    }
                } catch (Exception e) {
                    System.err.println("[ADAPTER] Error during request for client " + cid + ": " + e.getMessage());
                }
            });
        } else if (chan == OUT_DONE) { // someone in TRON signals done
            int doneCid = params[0];
            sched.execute(() -> {
                try {
                    URI doneUri = URI.create(base + "/api/returning/" + doneCid);
                    HttpRequest request = HttpRequest.newBuilder()
                            .uri(doneUri)
                            .timeout(reqTimeout)
                            .GET()
                            .build();
                    HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString());
                    System.out.println("[ADAPTER] Client " + doneCid + " returned → " + response.body());
                } catch (Exception e) {
                    System.err.println("[ADAPTER] Error during done for client " + doneCid + ": " + e.getMessage());
                }
            });
        }
    }

    private void handleRequest(HttpExchange exchange) throws IOException {
        String body = readAll(exchange.getRequestBody());

        String path = exchange.getRequestURI().getPath(); // e.g., /api/request/3
        String[] parts = path.split("/");
        int sender = Integer.parseInt(parts[parts.length - 1]); // client requesting

        int receiver = -1; // convention
        int type = 1;      // 1 = request event

        // Report this as an output event: request(sender, receiver, type)
        if (reporter != null) {
            reporter.report(
                OUT_REQUEST,
                new int[] {sender, receiver, type}
            );
        } else {
            System.err.println("[ADAPTER] Reporter is null; cannot report");
        }

        // Respond to the original sender
        sendResponse(exchange, 200, "{\"status\":\"ok\"}");
    }

    private void handleDone(HttpExchange exchange) throws IOException {
        String body = readAll(exchange.getRequestBody());

        // Extract client number from the URL
        String path = exchange.getRequestURI().getPath(); // e.g., /api/returning/3
        String[] parts = path.split("/");
        int sender = Integer.parseInt(parts[parts.length - 1]); // client who is returning

        // For "receiver" and "type" you decide a convention:
        int receiver = -1;  // no specific receiver for done
        int type = 0;       // 0 = done event

        // Report this as an output event: request(sender, receiver, type)
        if (reporter != null) {
            reporter.report(
                OUT_DONE,
                new int[] {sender, receiver, type}
            );
        } else {
            System.err.println("[ADAPTER] Reporter is null; cannot report");
        }

        // Respond to the original sender
        sendResponse(exchange, 200, "{\"status\":\"ok\"}");
    }

    private static String safe(String s) {
        return (s == null) ? "" : s;
    }

    private String readAll(java.io.InputStream is) throws IOException {
        return new String(is.readAllBytes());
    }

    private void sendResponse(HttpExchange exchange, int status, String body) throws IOException {
        byte[] bytes = body.getBytes();
        exchange.sendResponseHeaders(status, bytes.length);
        try (OutputStream os = exchange.getResponseBody()) {
            os.write(bytes);
        }
    }

    // ── standalone entry‑point ───────────────────────────────────────────────
    public static void main(String[] args) throws Exception {
        int port = (args.length > 0) ? Integer.parseInt(args[0]) : 9999;
        ClientMutexAdapter adapter = new ClientMutexAdapter();
        Reporter reporter = new Reporter(adapter, port);
        Runtime.getRuntime().addShutdownHook(
            new Thread(() -> adapter.sched.shutdownNow())
        );
        adapter.startIngressServer();
        reporter.join();
    }
}