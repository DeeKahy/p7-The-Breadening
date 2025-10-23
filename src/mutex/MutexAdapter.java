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

public class MutexAdapter implements Adapter {

    // TRON channels
    private int IN_REQUEST; // request?
    private int IN_DONE; // done?
    private int OUT_GRANT; // grant!

    private Reporter reporter;

    // HTTP client & config
    private final HttpClient http = HttpClient.newBuilder()
        .connectTimeout(Duration.ofSeconds(20))
        .build();

    /** Base URL for Flask server */
    private final URI base = URI.create(
        System.getProperty("mutexServerBase", "http://localhost:5000")
    );

    private final Duration reqTimeout = Duration.ofSeconds(3);

    @Override
    public void configure(Reporter reporter) throws TronException, IOException {
        this.reporter = reporter;

        reporter.setTimeUnit(50_000);
        reporter.setTimeout(100_000);

        IN_REQUEST = reporter.addInput("request");
        reporter.addVarToInput(IN_REQUEST, "requesting_c_id");

        IN_DONE = reporter.addInput("done");
        reporter.addVarToInput(IN_DONE, "granted_c_id");

        OUT_GRANT = reporter.addOutput("grant");
        reporter.addVarToOutput(OUT_GRANT, "granted_c_id");
    }

    @Override
    public void perform(int chan, int[] params) {
        int cid = params[0];
        try {
            if (chan == IN_REQUEST) {
                requestOnce(cid);
            } else if (chan == IN_DONE) {
                doneOnce(cid);
            }
        } catch (Exception ignored) {
            /* no output on failure */
        }
    }

    private void requestOnce(int cid) throws Exception {
        HttpRequest req = HttpRequest.newBuilder(
            base.resolve("/api/requesting/" + cid)
        )
            .timeout(reqTimeout)
            .GET()
            .build();
        HttpResponse<String> resp = http.send(
            req,
            HttpResponse.BodyHandlers.ofString()
        );
        String body = safe(resp.body()).trim();
        // Expect exactly: grant(cid)
        if (body.startsWith("grant(") && body.endsWith(")")) {
            int id = Integer.parseInt(
                body.substring(6, body.length() - 1).trim()
            );
            if (id == cid) {
                reporter.report(OUT_GRANT, new int[] { id });
            }
        }
        // else: no output
    }

    private void doneOnce(int cid) throws Exception {
        HttpRequest req = HttpRequest.newBuilder(
            base.resolve("/api/returning/" + cid)
        )
            .timeout(reqTimeout)
            .GET()
            .build();
        HttpResponse<String> resp = http.send(
            req,
            HttpResponse.BodyHandlers.ofString()
        );
        String body = safe(resp.body()).trim();
        // If next exists, Flask replies: grant(next)
        if (body.startsWith("grant(") && body.endsWith(")")) {
            int next = Integer.parseInt(
                body.substring(6, body.length() - 1).trim()
            );
            reporter.report(OUT_GRANT, new int[] { next });
        }
        // else: no output
    }

    private static String safe(String s) {
        return (s == null) ? "" : s;
    }

    public static void main(String[] args) throws Exception {
        int port = (args.length > 0) ? Integer.parseInt(args[0]) : 9999;
        MutexAdapter adapter = new MutexAdapter();
        Reporter reporter = new Reporter(adapter, port);
        reporter.join();
    }
}
