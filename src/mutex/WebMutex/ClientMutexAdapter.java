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

    @override
    public void perform(int chan, int[] params) {
        // every bound channel carries ONE integer parameter – the client id
        int cid = params[0];

        if (chan == IN_GRANT) {
            sendGrant();
        }
    }

    private void handleRequest(HttpExchange exchange) throws IOException {
        String body = readAll(exchange.getRequestBody());

        /**
        Extract values from body here.
        **/

        // Report this as an output event: request(sender, receiver, type)
        if (reporter != null) {
            reporter.report(
                OUT_REQUEST,
                new int[] { /**Input your extracted values here**/ }
            );
        } else {
            System.err.println("[ADAPTER] Reporter is null; cannot report");
        }

        // Respond to the original sender
        sendResponse(exchange, 200, "{\"status\":\"ok\"}");
    }

    private void handleDone(HttpExchange exchange) throws IOException {
        String body = readAll(exchange.getRequestBody());

        /**
        Extract values from body here.
        **/

        // Report this as an output event: request(sender, receiver, type)
        if (reporter != null) {
            reporter.report(
                OUT_REQUEST,
                new int[] { /**Input your extracted values here**/ }
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

    // ── standalone entry‑point ───────────────────────────────────────────────
    public static void main(String[] args) throws Exception {
        int port = (args.length > 0) ? Integer.parseInt(args[0]) : 9999;
        ClientMutexAdapter adapter = new ClientMutexAdapter();
        Reporter reporter = new Reporter(adapter, port);
        Runtime.getRuntime().addShutdownHook(
            new Thread(() -> adapter.sched.shutdownNow())
        );
        reporter.join();
    }
}