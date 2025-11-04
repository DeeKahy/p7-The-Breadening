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
 * The only change in this revision is **binding the channels to the two global
 * integer variables that already exist in the model** (`requesting_c_id` and
 * `granted_c_id`).  Using those avoids the runtime error:
 *   addVarToInput: Variable name not found, must be declared as integer.
 */
public class MutexAdapter implements Adapter {

    // ── TRON channel identifiers ────────────────────────────────────────────
    private int IN_REQUEST;   // request?
    private int IN_DONE;      // done?
    private int OUT_GRANT;    // grant!

    private Reporter reporter;

    // ── HTTP client & config ────────────────────────────────────────────────
    private final HttpClient http = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(20))
            .build();

    /** Base URL for the Flask mutex server (‑DmutexServerBase=…) */
    private final URI base = URI.create(System.getProperty("mutexServerBase", "http://localhost:5000"));

    private final Duration reqTimeout = Duration.ofSeconds(3);

    private final ScheduledExecutorService sched = Executors.newScheduledThreadPool(2);

    // ── Adapter ⇄ TRON interface ────────────────────────────────────────────
    @Override
    public void configure(Reporter reporter) throws TronException, IOException {
        this.reporter = reporter;

        reporter.setTimeUnit(50_000);   // 50 ms per model time unit
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
            pollRequest(cid);
        } else if (chan == IN_DONE) {
            sendDone(cid);
        }
    }

    // ── request → (poll) → grant loop ───────────────────────────────────────
    private void pollRequest(int cid) {
        HttpRequest req = HttpRequest.newBuilder(base.resolve("/api/requesting/" + cid))
                .timeout(reqTimeout)
                .GET()
                .build();

        http.sendAsync(req, HttpResponse.BodyHandlers.ofString())
                .thenAccept(resp -> {
                    String body = safe(resp.body());
                    if (body.startsWith("okay go")) {
                        reporter.report(OUT_GRANT, new int[]{cid});
                    }
                })
                .exceptionally(ex -> {
                    sched.schedule(() -> pollRequest(cid), 2, TimeUnit.SECONDS);
                    return null;
                });
    }

    // ── done → (maybe retry) ────────────────────────────────────────────────
    private void sendDone(int cid) {
        HttpRequest req = HttpRequest.newBuilder(base.resolve("/api/returning/" + cid))
                .timeout(reqTimeout)
                .GET()
                .build();

        http.sendAsync(req, HttpResponse.BodyHandlers.ofString())
                .thenAccept(resp -> {
                    String body = safe(resp.body());
                    if (body.contains("Returning")) {
                        String needle = "next is";
                        String tail = body.substring(body.toLowerCase().indexOf(needle) + needle.length()).trim();
                        int next_id = Integer.parseInt(tail);
                        reporter.report(OUT_GRANT, new int[]{next_id});
                    }
                })
                .exceptionally(ex -> {
                    sched.schedule(() -> sendDone(cid), 5, TimeUnit.SECONDS);
                    return null;
                });
    }

    private static String safe(String s) { return (s == null) ? "" : s; }

    // ── standalone entry‑point ───────────────────────────────────────────────
    public static void main(String[] args) throws Exception {
        int port = (args.length > 0) ? Integer.parseInt(args[0]) : 9999;
        MutexAdapter adapter = new MutexAdapter();
        Reporter reporter = new Reporter(adapter, port);
        Runtime.getRuntime().addShutdownHook(new Thread(() -> adapter.sched.shutdownNow()));
        reporter.join();
    }
}
