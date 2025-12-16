package election;

import com.uppaal.tron.Adapter;
import com.uppaal.tron.Reporter;
import com.uppaal.tron.TronException;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

import java.io.IOException;
import java.io.InputStream;
import java.io.ByteArrayOutputStream;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Arrays;
import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * TRON adapter for the UPPAAL Bully model (Bully.xml) and the Flask
 * implementation of the Bully algorithm.
 *
 * Model side:
 *   broadcast chan request;
 *   int sender, reciever, type;
 *   int CHECK=0, ANSWER=1, SHUT_UP=2, ELECTED=3;
 *
 * SUT side:
 *   Each real Bully process talks to THIS adapter instead of each other:
 *
 *     POST http://<adapterHost>:<adapterPort>/api/message
 *     JSON: { "type": "..", "sender": <int>, "receiver": <int>, "parameters": { ... } }
 *
 *   The adapter:
 *     - Reports all messages to TRON as outputs
 *     - Forwards them to the real receiver using -DbullyRealPeers=id->URL
 */
public class BullyAdapter implements Adapter {

    // ─────────────────────────────────────────────────────────────────────
    // TRON channels
    // ─────────────────────────────────────────────────────────────────────
    private int IN_START_ELECTION;
    private int OUT_REQUEST;

    private Reporter reporter;

    // ─────────────────────────────────────────────────────────────────────
    // HTTP plumbing
    // ─────────────────────────────────────────────────────────────────────

    private final HttpClient http = HttpClient.newBuilder()
        .connectTimeout(Duration.ofSeconds(10))
        .build();

    // Timeout for adapter → SUT calls
    private final Duration reqTimeout = Duration.ofSeconds(5);

    /**
     * Where SUT processes send their messages.
     * All SUT peers must be configured to POST to this address.
     *
     * Example:
     *   -DbullyAdapterPort=7000
     *   each SUT node uses http://<adapterHost>:7000/api/message
     */
    private final int adapterPort = Integer.getInteger("bullyAdapterPort", 7000);

    private final ExecutorService tronOut = Executors.newSingleThreadExecutor();
    private final Object reportLock = new Object();

    /** Embedded HTTP server: SUT → adapter direction */
    private HttpServer ingressServer;

    /**
     * REQUIRED: map of real SUT endpoints.
     *
     * Example:
     *   -DbullyRealPeers="0=http://sut0:5000,1=http://sut1:5001,2=http://sut2:5002,3=http://sut3:5003,4=http://sut4:5004"
     */
    private final String bullyRealPeersProp = System.getProperty("bullyRealPeers");

    /** id → real SUT URI (may contain null gaps if ids aren’t dense) */
    private URI[] realPeers;

    /** All known process ids (sorted) */
    private int[] processIds;

    public BullyAdapter() {
        try {
            buildRealPeers();    // fills realPeers + processIds
            startIngressServer(); // where SUT sends its messages
            // waitForSomePeer();   // sanity: wait until at least one node is up
        } catch (IOException e) {
            throw new RuntimeException("Failed to start adapter HTTP server", e);
        }
    }

    // ─────────────────────────────────────────────────────────────────────
    // Adapter ↔ TRON
    // ─────────────────────────────────────────────────────────────────────

    @Override
    public void configure(Reporter reporter) throws TronException, IOException {
        this.reporter = reporter;

        // Time scale + timeout (same as before)
        reporter.setTimeUnit(50_000);    // 50 ms per model time unit
        reporter.setTimeout(72_000);

        // // ── Inputs (environment → SUT, as seen by TRON) ─────────────
        // start_election is a broadcast chan in the model; we treat each
        // start_election! / ? sync as an input. We also bind the global
        // variable 'starter' so we know which process should start.
        IN_START_ELECTION = reporter.addInput("start_election");
        reporter.addVarToInput(IN_START_ELECTION, "starter");

        // ── Outputs (SUT → environment, as seen by TRON) ────────────
        OUT_REQUEST = reporter.addOutput("observe");
        reporter.addVarToOutput(OUT_REQUEST, "sender");
        reporter.addVarToOutput(OUT_REQUEST, "reciever");
        reporter.addVarToOutput(OUT_REQUEST, "type");
    }


    // Model → SUT
    @Override
    public void perform(int chan, int[] params) {
        if (chan == IN_START_ELECTION) {
            // params[0] is the value of global variable 'starter'
            if (params == null || params.length == 0) {
                System.err.println("[ADAPTER] start_election without starter param");
                return;
            }
            int starterId = params[0];
            System.out.println("[ADAPTER] TRON input start_election for starter=" + starterId);

            // Offload HTTP call to another thread so we don't block TRON's input thread
            new Thread(() -> triggerStartElection(starterId), "start-election-" + starterId).start();
        } else {
            // unknown or unused input; ignore
            System.err.println("[ADAPTER] Unknown input chan=" + chan);
        }
    }

    private void triggerStartElection(int starterId) {
        if (realPeers == null || starterId < 0 || starterId >= realPeers.length) {
            System.err.println("[ADAPTER] No real peer configured for id " + starterId);
            return;
        }
        URI base = realPeers[starterId];
        if (base == null) {
            System.err.println("[ADAPTER] realPeers[" + starterId + "] is null");
            return;
        }

        URI uri = base.resolve("/api/message");
        System.out.println("[ADAPTER] Triggering start_election on " + starterId + " at " + uri);

        try {
            HttpRequest req = HttpRequest.newBuilder()
                .uri(uri)
                .timeout(reqTimeout)
                .POST(HttpRequest.BodyPublishers.ofString("{ \"type\": \"shut_up\", \"sender\": -1, \"receiver\": " + starterId + " }"))
                .build();

            http.send(req, HttpResponse.BodyHandlers.discarding());
        } catch (Exception e) {
            System.err.println("[ADAPTER] Failed to trigger start_election on " + starterId + ": " + e);
        }
    }


    // ─────────────────────────────────────────────────────────────────────
    // SUT → Adapter (HTTP) → TRON
    // ─────────────────────────────────────────────────────────────────────

    private void startIngressServer() throws IOException {
        ingressServer = HttpServer.create(new InetSocketAddress(adapterPort), 0);
        ingressServer.createContext("/api/message", this::handleFromSut);
        ingressServer.setExecutor(Executors.newCachedThreadPool());
        ingressServer.start();

        System.out.println(
            "[ADAPTER] Ingress server listening on http://0.0.0.0:" +
            adapterPort + "/api/message"
        );
    }

    /**
     * Handle messages that the SUT sends to the adapter.
     * We assume JSON: {"type":"..","sender":i,"receiver":j,...}
     */
    private void handleFromSut(HttpExchange exchange) throws IOException {
        String body = readAll(exchange.getRequestBody());

        int senderId   = extractIntField(body, "sender");
        int receiverId = extractIntField(body, "receiver");
        String typeStr = extractStringField(body, "type");
        int typeVal    = nameToType(typeStr);

        // ALWAYS respond to SUT promptly (prevents RemoteDisconnected timeouts)
        sendResponse(exchange, 200, "{\"status\":\"ok\"}");

        // Validate ranges to avoid sending illegal values into the model types
        // pid_t is 0..4 and mtype_t is 0..3 in Bully2.xml :contentReference[oaicite:1]{index=1}
        if (senderId < 0 || senderId > 4 || receiverId < 0 || receiverId > 4 || typeVal < 0 || typeVal > 3) {
            System.err.println("[ADAPTER] Dropping out-of-range event: sender=" + senderId +
                " receiver=" + receiverId + " type=" + typeStr + "(" + typeVal + ")");
            return;
        }

        tronOut.submit(() -> {
            try {
                if (reporter != null) {
                    synchronized (reportLock) {
                        reporter.report(OUT_REQUEST, new int[]{ senderId, receiverId, typeVal });
                    }
                }
            } catch (Exception e) {
                // If TRON disconnects, don't kill HTTP ingress
                System.err.println("[ADAPTER] reporter.report failed: " + e);
            }
            System.out.println("[ADAPTER] recieved from SUT: sender=" + senderId +
                ", receiver=" + receiverId + ", type=" + typeStr + "(" + typeVal + ")");
            // forwardToRealPeer(receiverId, body);
        });
    }

    /**
     * Forward the JSON message to the real SUT peer with the given id,
     * if we have a URL for it in realPeers[].
     */
    private void forwardToRealPeer(int receiverId, String jsonBody) {
        if (receiverId < 0 ||
            realPeers == null ||
            receiverId >= realPeers.length) {
            System.out.println(
                "[ADAPTER] No real peer slot for receiver " + receiverId + "; not forwarding"
            );
            return;
        }

        URI base = realPeers[receiverId];
        if (base == null) {
            System.out.println(
                "[ADAPTER] realPeers[" + receiverId + "] is null; not forwarding"
            );
            return;
        }

        URI target = base.resolve("/api/message");
        HttpRequest req = HttpRequest.newBuilder(target)
            .timeout(reqTimeout)
            .header("Content-Type", "application/json")
            .POST(HttpRequest.BodyPublishers.ofString(jsonBody, StandardCharsets.UTF_8))
            .build();

        try {
            http.send(req, HttpResponse.BodyHandlers.discarding());
        } catch (Exception e) {
            System.out.println(
                "[ADAPTER] Failed to forward message to " +
                receiverId + " at " + target + ": " + e.getMessage()
            );
        }
    }

    // ─────────────────────────────────────────────────────────────────────
    // Real-peer mapping
    // ─────────────────────────────────────────────────────────────────────

    private void buildRealPeers() {
        if (bullyRealPeersProp == null || bullyRealPeersProp.isEmpty()) {
            throw new IllegalStateException(
                "System property -DbullyRealPeers is required and must map id->URL, " +
                "e.g. -DbullyRealPeers=\"0=http://sut0:5000,1=http://sut1:5001\""
            );
        }

        Map<Integer, URI> tmp = new TreeMap<>();
        int maxId = -1;

        for (String entry : bullyRealPeersProp.split(",")) {
            entry = entry.trim();
            if (entry.isEmpty()) continue;

            int eq = entry.indexOf('=');
            if (eq <= 0 || eq == entry.length() - 1) {
                throw new IllegalArgumentException(
                    "Invalid bullyRealPeers entry: " + entry
                );
            }

            String k = entry.substring(0, eq).trim();
            String v = entry.substring(eq + 1).trim();

            int pid = Integer.parseInt(k);
            if (!v.contains("://")) {
                v = "http://" + v;
            }
            URI uri = URI.create(v);

            tmp.put(pid, uri);
            if (pid > maxId) maxId = pid;

            System.out.println("[ADAPTER] Real peer " + pid + " at " + uri);
        }

        realPeers = new URI[maxId + 1];
        for (Map.Entry<Integer, URI> e : tmp.entrySet()) {
            realPeers[e.getKey()] = e.getValue();
        }

        processIds = tmp.keySet().stream().mapToInt(Integer::intValue).toArray();
        System.out.println("[ADAPTER] Process ids = " + Arrays.toString(processIds));
    }

    // private void waitForSomePeer() {
    //     if (processIds == null || processIds.length == 0) {
    //         System.out.println("[ADAPTER] No peers configured; skipping waitForSomePeer");
    //         return;
    //     }

    //     int probeId = processIds[0];
    //     URI base = realPeers[probeId];
    //     if (base == null) {
    //         System.out.println("[ADAPTER] realPeers[" + probeId + "] is null; skipping waitForSomePeer");
    //         return;
    //     }

    //     URI statusUri = base.resolve("/status");
    //     System.out.println("[ADAPTER] Waiting for at least peer " + probeId + " at " + statusUri);

    //     while (true) {
    //         try {
    //             HttpRequest req = HttpRequest.newBuilder(statusUri)
    //                 .timeout(Duration.ofSeconds(2))
    //                 .GET()
    //                 .build();

    //             HttpResponse<String> resp =
    //                 http.send(req, HttpResponse.BodyHandlers.ofString());

    //             if (resp.statusCode() == 200) {
    //                 System.out.println("[ADAPTER] Peer " + probeId + " is up: " + safe(resp.body()));
    //                 return;
    //             } else {
    //                 System.out.println(
    //                     "[ADAPTER] Peer " + probeId + " not ready, status " +
    //                     resp.statusCode()
    //                 );
    //             }
    //         } catch (Exception e) {
    //             System.out.println(
    //                 "[ADAPTER] Peer " + probeId + " not yet reachable: " + e.getMessage()
    //             );
    //         }

    //         try {
    //             Thread.sleep(1000L);
    //         } catch (InterruptedException ie) {
    //             Thread.currentThread().interrupt();
    //             return;
    //         }
    //     }
    // }

    // ─────────────────────────────────────────────────────────────────────
    // Type mapping
    // ─────────────────────────────────────────────────────────────────────

    private String typeToName(int type) {
        switch (type) {
            case 0: return "check";
            case 1: return "answer";
            case 2: return "shut_up";
            case 3: return "elected";
            default: return null;
        }
    }

    private int nameToType(String name) {
        if (name == null) return -1;
        String n = name.trim().toUpperCase();
        switch (n) {
            case "CHECK":   return 0;
            case "ANSWER":  return 1;
            case "SHUT_UP": return 2;
            case "ELECTED": return 3;
            default:        return -1;
        }
    }

    // ─────────────────────────────────────────────────────────────────────
    // Tiny JSON helpers (still hacky on purpose)
    // ─────────────────────────────────────────────────────────────────────

    private int extractIntField(String json, String field) {
        if (json == null) return -1;
        String key = "\"" + field + "\"";
        int idx = json.indexOf(key);
        if (idx < 0) return -1;
        idx = json.indexOf(':', idx);
        if (idx < 0) return -1;
        idx++;
        // skip whitespace
        while (idx < json.length() && Character.isWhitespace(json.charAt(idx))) {
            idx++;
        }
        int start = idx;
        while (idx < json.length() &&
               (Character.isDigit(json.charAt(idx)) || json.charAt(idx) == '-')) {
            idx++;
        }
        if (start == idx) return -1;
        try {
            return Integer.parseInt(json.substring(start, idx));
        } catch (NumberFormatException e) {
            return -1;
        }
    }

    private String extractStringField(String json, String field) {
        if (json == null) return null;
        String key = "\"" + field + "\"";
        int idx = json.indexOf(key);
        if (idx < 0) return null;
        idx = json.indexOf(':', idx);
        if (idx < 0) return null;
        idx++;
        // skip spaces
        while (idx < json.length() && Character.isWhitespace(json.charAt(idx))) {
            idx++;
        }
        if (idx >= json.length() || json.charAt(idx) != '"') {
            return null;
        }
        idx++; // after opening quote
        int start = idx;
        while (idx < json.length() && json.charAt(idx) != '"') {
            idx++;
        }
        if (idx >= json.length()) return null;
        return json.substring(start, idx);
    }

    private String readAll(InputStream input) throws IOException {
        if (input == null) return "";
        try (ByteArrayOutputStream baos = new ByteArrayOutputStream()) {
            byte[] buf = new byte[4096];
            int n;
            while ((n = input.read(buf)) != -1) {
                baos.write(buf, 0, n);
            }
            return baos.toString(StandardCharsets.UTF_8);
        }
    }

    private void sendResponse(HttpExchange ex, int status, String body)
        throws IOException {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        ex.getResponseHeaders().add("Content-Type", "application/json");
        ex.sendResponseHeaders(status, bytes.length);
        ex.getResponseBody().write(bytes);
        ex.close();
    }

    private String safe(String s) {
        return (s == null) ? "" : s.replace("\n", "\\n");
    }

    // ─────────────────────────────────────────────────────────────────────
    // main
    // ─────────────────────────────────────────────────────────────────────

    public static void main(String[] args) throws Exception {
        int tronPort = Integer.getInteger("tronPort", 5000);

        BullyAdapter adapter = new BullyAdapter();
        Reporter reporter = new Reporter(adapter, tronPort);

        Runtime.getRuntime().addShutdownHook(
            new Thread(() -> {
                System.out.println("[ADAPTER] Shutting down");
                if (adapter.ingressServer != null) {
                    adapter.ingressServer.stop(0);
                }
            })
        );

        reporter.join();
    }
}
