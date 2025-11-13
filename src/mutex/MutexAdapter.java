package mutex;

import com.uppaal.tron.Adapter;
import com.uppaal.tron.Reporter;
import com.uppaal.tron.TronException;

import java.io.IOException;
import java.net.URI;
import java.util.concurrent.*;

import org.java_websocket.client.WebSocketClient;
import org.java_websocket.handshake.ServerHandshake;
import org.json.JSONObject;

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
    private int IN_REQUEST; // request?
    private int IN_DONE; // done?
    private int OUT_GRANT; // grant!

    private Reporter reporter;

    // ── WebSocket client ────────────────────────────────────────────────────
    private MutexWebSocketClient wsClient;
    private final URI wsUri;
    private final CountDownLatch connectLatch = new CountDownLatch(1);

    // ── Executor for async operations ───────────────────────────────────────
    private final ScheduledExecutorService sched =
        Executors.newScheduledThreadPool(2);

    // Track "done" clients waiting to retry (423, 500)
    private final ConcurrentHashMap<Integer, Integer> pendingDone = 
        new ConcurrentHashMap<>();

    public MutexAdapter() {
        String wsUrl = System.getProperty("mutexServerBase", "ws://localhost:5000/ws");
        if (wsUrl.startsWith("http://")) {
            wsUrl = wsUrl.replace("http://", "ws://") + "/ws";
        } else if (wsUrl.startsWith("https://")) {
            wsUrl = wsUrl.replace("https://", "wss://") + "/ws";
        } else if (!wsUrl.contains("/ws")) {
            wsUrl = wsUrl + "/ws";
        }
        this.wsUri = URI.create(wsUrl);
    }

    // ── Adapter ⇄ TRON interface ────────────────────────────────────────────
    @Override
    public void configure(Reporter reporter) throws TronException, IOException {
        this.reporter = reporter;

        reporter.setTimeUnit(100_000); // 100 ms per model time unit
        reporter.setTimeout(1_000_000); // test budget: 100 s

        IN_REQUEST = reporter.addInput("request");
        reporter.addVarToInput(IN_REQUEST, "requesting_c_id");

        IN_DONE = reporter.addInput("done");
        reporter.addVarToInput(IN_DONE, "granted_c_id");

        OUT_GRANT = reporter.addOutput("grant");
        reporter.addVarToOutput(OUT_GRANT, "granted_c_id");

        // Connect to WebSocket server
        connectWebSocket();
    }

    private void connectWebSocket() throws IOException {
        wsClient = new MutexWebSocketClient(wsUri);
        System.out.println("Connecting to WebSocket server at " + wsUri);
        
        try {
            wsClient.connectBlocking(5, TimeUnit.SECONDS);
            connectLatch.await(5, TimeUnit.SECONDS);
            System.out.println("WebSocket connection established");
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException("WebSocket connection interrupted", e);
        } catch (Exception e) {
            throw new IOException("Failed to connect to WebSocket server", e);
        }
    }

    @Override
    public void perform(int chan, int[] params) {
        // every bound channel carries ONE integer parameter – the client id
        int cid = params[0];

        if (chan == IN_REQUEST) {
            System.out.println("Perform: request(" + cid + ")");
            sendRequest(cid);
        } else if (chan == IN_DONE) {
            System.out.println("Perform: done(" + cid + ")");
            sendDone(cid);
        }
    }

    // ── WebSocket message handlers ──────────────────────────────────────────
    private void sendRequest(int cid) {
        JSONObject msg = new JSONObject();
        msg.put("type", "request");
        msg.put("client_id", String.valueOf(cid));
        
        if (wsClient != null && wsClient.isOpen()) {
            wsClient.send(msg.toString());
            System.out.println("Sent request for client " + cid);
        } else {
            System.err.println("WebSocket not connected, cannot send request for client " + cid);
        }
    }

    private void sendDone(int cid) {
        JSONObject msg = new JSONObject();
        msg.put("type", "done");
        msg.put("client_id", String.valueOf(cid));
        
        if (wsClient != null && wsClient.isOpen()) {
            wsClient.send(msg.toString());
            System.out.println("Sent done for client " + cid);
            pendingDone.put(cid, 0); // Wait and retry
        } else {
            System.err.println("WebSocket not connected, cannot send done for client " + cid);
        }
    }

    private void handleMessage(JSONObject msg) {
        String type = msg.optString("type", "");
        
        if ("grant".equals(type)) {
            handleGrant(msg);
        } else if ("response".equals(type)) {
            handleResponse(msg);
        } else if ("error".equals(type)) {
            System.err.println("Server error: " + msg.optString("message", "Unknown error"));
        }
    }

    private void handleGrant(JSONObject msg) {
        String clientId = msg.optString("client_id", "");
        int status = msg.optInt("status", 200);
        
        try {
            int cid = Integer.parseInt(clientId);
            System.out.println("Received grant (status " + status + ") for client " + cid);
            reporter.report(OUT_GRANT, new int[] { cid });
            System.out.println("Grant(" + cid + ") reported to TRON");
        } catch (NumberFormatException e) {
            System.err.println("Invalid client ID in grant message: " + clientId);
        } catch (Exception e) {
            System.err.println("Error reporting grant: " + e.getMessage());
            e.printStackTrace();
        }
    }

    private void handleResponse(JSONObject msg) {
        String action = msg.optString("action", "");
        int status = msg.optInt("status", 0);
        String clientIdStr = msg.optString("client_id", "");
        
        try {
            int cid = Integer.parseInt(clientIdStr);
            
            if ("request".equals(action)) {
                handleRequestResponse(cid, status, msg);
            } else if ("done".equals(action)) {
                handleDoneResponse(cid, status, msg);
            }
        } catch (NumberFormatException e) {
            System.err.println("Invalid client ID: " + clientIdStr);
        }
    }

    private void handleRequestResponse(int cid, int status, JSONObject msg) {
        switch (status) {
            case 200: // proceed - grant access
                System.out.println("200: Client " + cid + " granted immediately");
                break;
                
            case 202: // queued
                int position = msg.optInt("position", -1);
                System.out.println("202: Client " + cid + " queued at position " + position);
                break;
                
            case 409: // already_in_queue
                position = msg.optInt("position", -1);
                System.out.println("409: Client " + cid + " already in queue at position " + position);
                break;
                
            default:
                System.err.println("Unexpected request status " + status + " for client " + cid);
        }
    }

    private void handleDoneResponse(int cid, int status, JSONObject msg) {
        switch (status) {
            case 200: // returned
                String next = msg.optString("next", "");
                System.out.println("200: Client " + cid + " returned successfully, next is " + next);
                pendingDone.remove(cid);
                break;
                
            case 204: // queue_empty
                System.out.println("204: Queue empty after client " + cid);
                pendingDone.remove(cid);
                break;
                
            case 409: // not_in_queue
                System.err.println("409: ERROR: Client " + cid + " was not in queue!");
                pendingDone.remove(cid);
                break;
                
            case 423: // not_your_turn
                int position = msg.optInt("position", -1);
                System.err.println("423: ERROR: Not client " + cid + "'s turn! (position " + position + ")");
                
                // Retry after delay
                int retryCount = pendingDone.getOrDefault(cid, 0);
                if (retryCount < 10) { // Max 10 retries
                    pendingDone.put(cid, retryCount + 1);
                    System.out.println("Scheduling retry for client " + cid + " (attempt " + (retryCount + 1) + ")");
                    sched.schedule(() -> sendDone(cid), 100, TimeUnit.MILLISECONDS);
                } else {
                    System.err.println("Max retries reached for client " + cid);
                    pendingDone.remove(cid);
                }
                break;
                
            case 500: // queue_empty_error
                System.err.println("500: ERROR: Server error for client " + cid);
                
                // Retry after delay
                retryCount = pendingDone.getOrDefault(cid, 0);
                if (retryCount < 10) {
                    pendingDone.put(cid, retryCount + 1);
                    System.out.println("Scheduling retry for client " + cid + " (attempt " + (retryCount + 1) + ")");
                    sched.schedule(() -> sendDone(cid), 500, TimeUnit.MILLISECONDS);
                } else {
                    System.err.println("Max retries reached for client " + cid);
                    pendingDone.remove(cid);
                }
                break;
                
            default:
                System.err.println("Unexpected done status " + status + " for client " + cid);
                pendingDone.remove(cid);
        }
    }

    // ── WebSocket Client Implementation ─────────────────────────────────────
    private class MutexWebSocketClient extends WebSocketClient {
        public MutexWebSocketClient(URI serverUri) {
            super(serverUri);
        }

        @Override
        public void onOpen(ServerHandshake handshakedata) {
            System.out.println("WebSocket connection opened (status: " + handshakedata.getHttpStatus() + ")");
            connectLatch.countDown();
        }

        @Override
        public void onMessage(String message) {
            System.out.println("Received message: " + message);
            
            try {
                JSONObject msg = new JSONObject(message);
                handleMessage(msg);
            } catch (Exception e) {
                System.err.println("Error parsing message: " + e.getMessage());
            }
        }

        @Override
        public void onClose(int code, String reason, boolean remote) {
            System.out.println("WebSocket connection closed: " + reason + " (code: " + code + ")");
            if (remote) {
                System.out.println("Connection closed by server, attempting to reconnect...");
                reconnect();
            }
        }

        @Override
        public void onError(Exception ex) {
            System.err.println("WebSocket error: " + ex.getMessage());
            ex.printStackTrace();
        }
    }

    private void reconnect() {
        sched.schedule(() -> {
            try {
                System.out.println("Attempting to reconnect...");
                connectWebSocket();
            } catch (IOException e) {
                System.err.println("Reconnection failed: " + e.getMessage());
                reconnect();
            }
        }, 5, TimeUnit.SECONDS);
    }

    public void shutdown() {
        System.out.println("Shutting down adapter...");
        if (wsClient != null) {
            wsClient.close();
        }
        sched.shutdownNow();
    }

    public static void main(String[] args) throws Exception {
        int port = (args.length > 0) ? Integer.parseInt(args[0]) : 9999;
        MutexAdapter adapter = new MutexAdapter();
        Reporter reporter = new Reporter(adapter, port);
        
        Runtime.getRuntime().addShutdownHook(
            new Thread(() -> {
                System.out.println("Shutting down...");
                adapter.shutdown();
            })
        );
        
        System.out.println("Listening on port " + port);
        System.out.println("WebSocket server: " + adapter.wsUri);
        System.out.println("\nHandling status codes:");
        System.out.println("  200: proceed / returned");
        System.out.println("  202: queued");
        System.out.println("  204: queue_empty");
        System.out.println("  409: already_in_queue / not_in_queue");
        System.out.println("  423: not_your_turn (try again)");
        System.out.println("  500: queue_empty_error (try again)");
        reporter.join();
    }
}