package mutex;

import com.uppaal.tron.Adapter;
import com.uppaal.tron.Reporter;
import com.uppaal.tron.TronException;

import java.io.IOException;
import java.net.URI;
import java.net.http.*;
import java.time.Duration;
import java.util.concurrent.*;

public class MutexAdapter implements Adapter {
  // ===== TRON channel/var ids =====
  private int IN_REQUEST, IN_RETURN;
  private int OUT_GRANTED, OUT_QUEUED, OUT_ALREADYQ;
  private int OUT_NOTYET, OUT_RETURNEDNEXT, OUT_EMPTY;
  private int V_GRANTED_ID, V_QUEUED_ID, V_ALREADYQ_ID;
  private int V_NOTYET_ID, V_RETURNEDNEXT_ID, V_RETURNEDNEXT_NEXT;
  private int V_EMPTY_ID;

  // ===== HTTP client & config =====
  private final HttpClient http = HttpClient.newBuilder()
      .connectTimeout(Duration.ofSeconds(2)).build();

  /**
   * Base URL for your Flask server. Override at runtime with:
   *   -DmutexServerBase=http://localhost:5000
   */
  private final URI base = URI.create(System.getProperty("mutexServerBase", "http://localhost:5000"));

  private final Duration reqTimeout = Duration.ofSeconds(3);
  private final ScheduledExecutorService sched = Executors.newScheduledThreadPool(2);

  private Reporter _reporter;

  // channel ids only
  @Override
  public void configure(Reporter reporter) throws TronException, IOException {
    this._reporter = reporter;

    reporter.setTimeUnit(10000);
	reporter.setTimeout(1000000);

    // Inputs
    IN_REQUEST = reporter.addInput("request");
    reporter.addVarToInput(IN_REQUEST, "id");   // returns void in your API

    IN_RETURN = reporter.addInput("return");
    reporter.addVarToInput(IN_RETURN,  "id");

    // Outputs
    OUT_GRANTED = reporter.addOutput("granted");
    reporter.addVarToOutput(OUT_GRANTED, "id");   // order matters!

    OUT_QUEUED = reporter.addOutput("queued");
    reporter.addVarToOutput(OUT_QUEUED, "id");

    OUT_ALREADYQ = reporter.addOutput("alreadyQueued");
    reporter.addVarToOutput(OUT_ALREADYQ, "id");

    OUT_NOTYET = reporter.addOutput("notYourTurn");
    reporter.addVarToOutput(OUT_NOTYET, "id");

    OUT_RETURNEDNEXT = reporter.addOutput("returnedNext");
    reporter.addVarToOutput(OUT_RETURNEDNEXT, "id");
    reporter.addVarToOutput(OUT_RETURNEDNEXT, "next");

    OUT_EMPTY = reporter.addOutput("queueEmpty");
    reporter.addVarToOutput(OUT_EMPTY, "id");

    System.out.println("[MutexAdapter] configured (void var API).");
  }

  @Override
  public void perform(int chan, int[] params) {
    try {
      if (chan == IN_REQUEST) {
        request(params[0]);
      } else if (chan == IN_RETURN) {
        returning(params[0], /*immediate*/true);
      }
    } catch (Exception e) {
      System.err.println("[MutexAdapter] perform error: " + e);
    }
  }

  // ====== HTTP ops ======

  private void request(int id) {
    HttpRequest req = HttpRequest.newBuilder(base.resolve("/api/requesting/" + id))
        .timeout(reqTimeout).GET().build();

    http.sendAsync(req, HttpResponse.BodyHandlers.ofString())
        .thenAccept(resp -> {
          String body = safe(resp.body());
          // Map your Flask texts -> outputs
          if (body.contains("okay go right straight totally ahead")) {
            _reporter.report(OUT_GRANTED, new int[]{ id });
          } else if (body.startsWith("you have now been queued")) {
            _reporter.report(OUT_QUEUED,  new int[]{ id });
          } else if (body.startsWith("you are already in the queue")) {
            _reporter.report(OUT_ALREADYQ,new int[]{ id });
          } else {
            // Fallback: treat unknown as queued
            _reporter.report(OUT_QUEUED,  new int[]{ id });
          }
        })
        .exceptionally(ex -> { System.err.println("[HTTP] request failed: " + ex); return null; });
  }

  private void returning(int id, boolean immediate) {
    Runnable attempt = () -> {
      HttpRequest req = HttpRequest.newBuilder(base.resolve("/api/returning/" + id))
          .timeout(reqTimeout).GET().build();

      http.sendAsync(req, HttpResponse.BodyHandlers.ofString())
          .thenAccept(resp -> {
            String body = safe(resp.body());
            if (body.contains("Not your turn yet")) {
              _reporter.report(OUT_NOTYET, new int[]{ id });
              // retry in 5s (same cadence as your Python sim)
              sched.schedule(() -> returning(id, false), 5, TimeUnit.SECONDS);
            } else if (body.startsWith("Returning:")) {
              int next = parseNext(body); // "Returning: X next is Y"
              _reporter.report(OUT_RETURNEDNEXT, new int[]{ id, next });
            } else if (body.startsWith("Queue is now empty.")) {
              _reporter.report(OUT_EMPTY, new int[]{ id });
            } else {
              // Unknown -> treat as not-yet and retry
              _reporter.report(OUT_NOTYET, new int[]{ id });
              sched.schedule(() -> returning(id, false), 5, TimeUnit.SECONDS);
            }
          })
          .exceptionally(ex -> {
            System.err.println("[HTTP] returning failed: " + ex);
            sched.schedule(() -> returning(id, false), 5, TimeUnit.SECONDS);
            return null;
          });
    };

    if (immediate) attempt.run(); else sched.execute(attempt);
  }

  private static int parseNext(String s) {
    try {
      int idx = s.indexOf("next is ");
      return (idx >= 0) ? Integer.parseInt(s.substring(idx + 8).trim()) : -1;
    } catch (Exception e) { return -1; }
  }

  private static String safe(String b) { return (b == null) ? "" : b; }

  // ---- Entrypoint: accept TRON connection on given port ----
  public static void main(String[] args) throws Exception {
    int port = (args.length > 0) ? Integer.parseInt(args[0]) : 9999;
    MutexAdapter adapter = new MutexAdapter();
    Reporter r = new Reporter(adapter, port); // server mode: accept()
    r.start(); // blocks
  }
}
