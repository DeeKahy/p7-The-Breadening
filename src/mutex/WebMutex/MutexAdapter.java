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
        .connectTimeout(Duration.ofSeconds(5))
        .build();

    private final URI base = URI.create(
        System.getProperty("mutexServerBase", "http://localhost:5000")
    );

    private final Duration reqTimeout = Duration.ofMillis(500);

    private final ScheduledExecutorService sched =
        Executors.newScheduledThreadPool(3);

    // Track clients that are active
    private final ConcurrentHashMap<Integer, ScheduledFuture<?>> activePolls =
        new ConcurrentHashMap<>();

    // ── Adapter ⇄ TRON interface ────────────────────────────────────────────
    @Override
    public void configure(Reporter reporter) throws TronException, IOException {
        this.reporter = reporter;

        reporter.setTimeUnit(50_000); // 50 ms per model time unit
        reporter.setTimeout(1_000_000); // test budget: 100 s

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
                "Perform: request(" + cid + ") - calling pollRequest"
            );
            startPolling(cid);
        } else if (chan == IN_DONE) {
            System.out.println("Perform: done(" + cid + ") - calling sendDone");
            stopPolling(cid);
            sendDone(cid);
        }
    }

    private void startPolling(int cid) {
        stopPolling(cid);

        ScheduledFuture<?> future = sched.scheduleAtFixedRate(
            () -> pollRequest(cid),
            0,
            10,
            TimeUnit.MILLISECONDS
        );

        activePolls.put(cid, future);
    }

    private void stopPolling(int cid) {
        ScheduledFuture<?> future = activePolls.remove(cid);
        if (future != null) {
            future.cancel(false);
        }
    }

    // ── request → (poll) → grant loop ───────────────────────────────────────
    private void pollRequest(int cid) {
        HttpRequest req = HttpRequest.newBuilder(
            base.resolve("/api/requesting/" + cid) // pottentially grab this from the config file.
        )
            .timeout(reqTimeout)
            .GET()
            .build();

        try {
            long startNs = System.nanoTime();
            HttpResponse<String> resp = http.send(
                req,
                HttpResponse.BodyHandlers.ofString()
            );
            long elapsedMs = (System.nanoTime() - startNs) / 1_000_000;

            int status = resp.statusCode();

            System.out.println(
                "pollRequest(" +
                    cid +
                    ") status: " +
                    status +
                    "after" +
                    " (" +
                    elapsedMs +
                    "ms)"
            );

            if (status == 200) {
                // proceed - grant access
                stopPolling(cid);
                System.out.println("Reporting grant(" + cid + ") to TRON");
                reporter.report(OUT_GRANT, new int[] { cid });
                System.out.println("Grant(" + cid + ") reported successfully");
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            System.err.println("Poll interrupted for client " + cid);
        } catch (Exception ex) {
            if (activePolls.containsKey(cid)) {
                System.err.println(
                    "Poll error for client " + cid + ": " + ex.getMessage()
                );
            }
        }
    }

    // ── done → (maybe retry) ────────────────────────────────────────────────
    private void sendDone(int cid) {
        sched.execute(() -> {
            HttpRequest req = HttpRequest.newBuilder(
                base.resolve("/api/returning/" + cid)
            )
                .timeout(reqTimeout)
                .GET()
                .build();

            try {
                HttpResponse<String> resp = http.send(
                    req,
                    HttpResponse.BodyHandlers.ofString()
                );
                int status = resp.statusCode();

                System.out.println("sendDone(" + cid + ") status: " + status);

                switch (status) {
                    case 200:
                        System.out.println(
                            "Client " + cid + " returned successfully"
                        );
                        break;
                    case 204:
                        System.out.println("Queue empty after client " + cid);
                        break;
                    case 409:
                        System.err.println(
                            "ERROR: Client " + cid + " was not in queue!"
                        );
                        break;
                    case 423:
                        System.err.println(
                            "ERROR: Not client " + cid + "'s turn!"
                        );
                        sched.schedule(
                            () -> sendDone(cid),
                            100,
                            TimeUnit.MILLISECONDS
                        );
                        break;
                    case 500:
                        System.err.println(
                            "ERROR: Server error for client " + cid
                        );
                        sched.schedule(
                            () -> sendDone(cid),
                            500,
                            TimeUnit.MILLISECONDS
                        );
                        break;
                    default:
                        System.err.println(
                            "Unexpected status " +
                                status +
                                " for done(" +
                                cid +
                                ")"
                        );
                }
            } catch (Exception ex) {
                System.err.println(
                    "Done failed for client " + cid + ": " + ex.getMessage()
                );
                sched.schedule(() -> sendDone(cid), 500, TimeUnit.MILLISECONDS);
            }
        });
    }

    public static void main(String[] args) throws Exception {
        int port = (args.length > 0) ? Integer.parseInt(args[0]) : 9999;
        MutexAdapter adapter = new MutexAdapter();
        Reporter reporter = new Reporter(adapter, port);

        Runtime.getRuntime().addShutdownHook(
            new Thread(() -> {
                System.out.println("Shutting down...");
                adapter.sched.shutdownNow();
            })
        );

        System.out.println("Listening on port " + port);
        System.out.println("Flask server: " + adapter.base);
        reporter.join();
    }
}
