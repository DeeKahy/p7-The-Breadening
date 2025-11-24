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
 *   request(id)  →  GET /api/requesting/{id}
 *   grant(id)    ←  HTTP 200 "okay go …"
 *   done(id)     →  GET /api/returning/{id}
 *
 * Updated to handle all Flask server status codes:
 *   - 200: proceed / returned
 *   - 202: queued
 *   - 204: queue_empty
 *   - 409: already_in_queue / not_in_queue
 *   - 423: not_your_turn
 *   - 500: queue_empty_error
 */
public class MutexAdapter implements Adapter {

    // ── TRON channel identifiers ────────────────────────────────────────────
    private int IN_REQUEST; // request?
    private int IN_DONE; // done?
    private int OUT_GRANT; // grant!

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
        IN_REQUEST = reporter.addInput("request");
        reporter.addVarToInput(IN_REQUEST, "requesting_c_id");

        IN_DONE = reporter.addInput("done");
        reporter.addVarToInput(IN_DONE, "granted_c_id");

        OUT_GRANT = reporter.addOutput("grant");
        reporter.addVarToOutput(OUT_GRANT, "granted_c_id");
    }

    @Override
    public void perform(int chan, int[] params) {
        // every bound channel carries ONE integer parameter – the client id
        int cid = params[0];

        if (chan == IN_REQUEST) {
            System.out.println(
                "[ADAPTER] perform: request(" + cid + ") - calling pollRequest"
            );
            pollRequest(cid);
        } else if (chan == IN_DONE) {
            System.out.println(
                "[ADAPTER] perform: done(" + cid + ") - calling sendDone"
            );
            sendDone(cid);
        }
    }

    // ── request → (poll) → grant loop ───────────────────────────────────────
    private void pollRequest(int cid) {
        long startTime = System.nanoTime();
        HttpRequest req = HttpRequest.newBuilder(
            base.resolve("/api/requesting/" + cid)
        )
            .timeout(reqTimeout)
            .GET()
            .build();

        http
            .sendAsync(req, HttpResponse.BodyHandlers.ofString())
            .thenAccept(resp -> {
                long endTime = System.nanoTime();
                long elapsedMs = (endTime - startTime) / 1_000_000;
                int status = resp.statusCode();

                System.out.println(
                    "[ADAPTER] pollRequest(" +
                        cid +
                        ") received status " +
                        status +
                        " after " +
                        elapsedMs +
                        "ms"
                );

                switch (status) {
                    case 200: // proceed - grant access
                        System.out.println(
                            "[ADAPTER] Reporting grant(" + cid + ") to TRON"
                        );
                        reporter.report(OUT_GRANT, new int[] { cid });
                        System.out.println(
                            "[ADAPTER] Grant(" + cid + ") reported successfully"
                        );
                        break;
                    case 202: // queued - keep polling
                    case 409: // already_in_queue - keep polling
                        sched.schedule(
                            () -> pollRequest(cid),
                            50,
                            TimeUnit.MILLISECONDS
                        );
                        break;
                    default:
                        System.err.println(
                            "Unexpected status " +
                                status +
                                " for request(" +
                                cid +
                                "): " +
                                resp.body()
                        );
                        sched.schedule(
                            () -> pollRequest(cid),
                            2,
                            TimeUnit.SECONDS
                        );
                }
            })
            .exceptionally(ex -> {
                System.err.println(
                    "Request failed for client " + cid + ": " + ex.getMessage()
                );
                sched.schedule(() -> pollRequest(cid), 2, TimeUnit.SECONDS);
                return null;
            });
    }

    // ── done → (maybe retry) ────────────────────────────────────────────────
    private void sendDone(int cid) {
        HttpRequest req = HttpRequest.newBuilder(
            base.resolve("/api/returning/" + cid)
        )
            .timeout(reqTimeout)
            .GET()
            .build();

        http
            .sendAsync(req, HttpResponse.BodyHandlers.ofString())
            .thenAccept(resp -> {
                int status = resp.statusCode();
                String body = safe(resp.body());

                switch (status) {
                    case 200: // returned - grant next client if present
                        try {
                            // Parse JSON response to get "next" field
                            if (body.contains("\"next\"")) {
                                String nextStr = body
                                    .split("\"next\"")[1].split(":")[1].split(
                                        "[,}]"
                                    )[0].trim()
                                    .replace("\"", "");
                                int nextId = Integer.parseInt(nextStr);
                                reporter.report(
                                    OUT_GRANT,
                                    new int[] { nextId }
                                );
                            }
                        } catch (Exception e) {
                            System.err.println(
                                "Failed to parse next client from: " + body
                            );
                        }
                        break;
                    case 204: // queue_empty - nothing to do
                        // Successfully returned, queue is now empty
                        break;
                    case 409: // not_in_queue - unexpected, retry
                        System.err.println(
                            "Client " + cid + " not in queue on return"
                        );
                        sched.schedule(
                            () -> sendDone(cid),
                            3,
                            TimeUnit.SECONDS
                        );
                        break;
                    case 423: // not_your_turn - unexpected, retry
                        System.err.println(
                            "Not client " + cid + "'s turn to return"
                        );
                        sched.schedule(
                            () -> sendDone(cid),
                            3,
                            TimeUnit.SECONDS
                        );
                        break;
                    case 500: // queue_empty_error - serious issue, retry
                        System.err.println(
                            "Server error (queue empty) for client " + cid
                        );
                        sched.schedule(
                            () -> sendDone(cid),
                            5,
                            TimeUnit.SECONDS
                        );
                        break;
                    default:
                        System.err.println(
                            "Unexpected status " +
                                status +
                                " for done(" +
                                cid +
                                "): " +
                                body
                        );
                        sched.schedule(
                            () -> sendDone(cid),
                            5,
                            TimeUnit.SECONDS
                        );
                }
            })
            .exceptionally(ex -> {
                System.err.println(
                    "Done failed for client " + cid + ": " + ex.getMessage()
                );
                sched.schedule(() -> sendDone(cid), 5, TimeUnit.SECONDS);
                return null;
            });
    }

    private static String safe(String s) {
        return (s == null) ? "" : s;
    }

    // ── standalone entry‑point ───────────────────────────────────────────────
    public static void main(String[] args) throws Exception {
        int port = (args.length > 0) ? Integer.parseInt(args[0]) : 9999;
        MutexAdapter adapter = new MutexAdapter();
        Reporter reporter = new Reporter(adapter, port);
        Runtime.getRuntime().addShutdownHook(
            new Thread(() -> adapter.sched.shutdownNow())
        );
        reporter.join();
    }
}