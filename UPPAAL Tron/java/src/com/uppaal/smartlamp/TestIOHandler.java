package com.uppaal.smartlamp;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.Random;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.Executors;
import java.time.Duration;
import java.util.LinkedList;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.Condition;

import com.uppaal.tron.Adapter;
import com.uppaal.tron.TronException;
import com.uppaal.tron.Reporter;
import com.uppaal.tron.VirtualThread;
import com.uppaal.tron.VirtualLock;
import com.uppaal.tron.VirtualCondition;

// import com.uppaal.autoofflamp.AutoOffLamp;

/**
 * TestIOHandler is an adapter stub translating abstract input events from 
 * tester into method LampInterface calls and LevelListener method calls into 
 * abstract output events to tester.
 */
public class TestIOHandler extends VirtualThread 
    implements Adapter, LevelListener
{
    /**
     * Controls whether the debug information should be produced into err
     * stream. true enables and false disables debug output.
     * This variable can be set via environment variable DEBUG_LC.
     */
    public static boolean DBG = (System.getenv("DEBUG_LC")!=null);

    Lock lock = null;
    Condition cond = null;
    LinkedList<Integer> inputBuffer = new LinkedList<Integer>();

    int inputGrasp = 0;  // channel identifier for input "grasp"
    int inputRelease = 0;// channel identifier for input "release"
    int inputTouch = 0; /* AutoOffLamp specific */
    int outputSetLevel = 0; // channel identifier for output "level".
    int[] pLevel = new int[1]; // array of one for level value passing.

    Reporter reporter; // tester proxy for adapter.

    LampInterface lamp = null;

	private int CH_LEVEL;
	private final HttpClient http = HttpClient.newHttpClient();
	private final Random rng = new Random();

	// Base URL for your Flask server (override with -DlampServerBase=...)
	private final URI serverBase = URI.create(System.getProperty(
			"lampServerBase", "http://localhost:5000"));

	// Async scheduler for delayed “returning”
	private final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(1);

	// Optional: de-dupe consecutive identical levels
	private Integer lastReportedLevel = null;

    /**
     * @param lamp object to receive inputs as method calls.
     */
    public TestIOHandler(LampInterface lamp)
    {
	super("TestInput");
	this.lamp = lamp;
	start();
	if (DBG) System.err.println("IOHandler: wait for thread to start");
	synchronized (this) {
	    try {  while(lock==null) wait(); }
	    catch(InterruptedException e){}
	}
	if (DBG) System.err.println("IOHandler: thread is started");
    }
    
    /**
     * Adapter method: configures the test interface for incoming connection.
     * The method is normally a called by reporter when connection with tester 
     * is being established.
     * @param reporter provides a configuration and output reporting interface 
     * to the tester.
     */
    public void configure(Reporter reporter)
	throws TronException, IOException
    {
	CH_LEVEL = reporter.addOutput("level");   
	if (lamp instanceof SmartLamp ||
	    lamp instanceof SmartLampM3) {
	    inputGrasp = reporter.addInput("grasp");
	    inputRelease = reporter.addInput("release");
	// } else if (lamp instanceof AutoOffLamp) {
	//     inputTouch = reporter.addInput("touch");
	} else {
	    throw new TronException("Unknown Lamp implementation, "+
				    "see TestIOHandler.configure source");
	}
	outputSetLevel = reporter.addOutput("level");
	reporter.addVarToOutput(outputSetLevel, "envLevel");
	reporter.setTimeUnit(10000);
	reporter.setTimeout(1000000);
	this.reporter = reporter;
	if (DBG) System.err.println("IOHandler: waiting for others");
	// wait until lamp object is initialized:
	try { lamp.waitForStart(); }
	catch (InterruptedException ex) {}
	if (DBG) System.err.println("IOHandler: starting test");
    }

    /**
     * Adapter method: handles abstract inputs encoded as channels with 
     * parameters. Called by Reporter when input is received.
     * @param chan is an input channel identifier.
     * @param params is an array of variable values bound to a channel.
     */
    public void perform(int chan, int[] params)
    {// No virtual wait is allowed in this method
	if (DBG) System.err.println("IOHandler: arrived");
	lock.lock();
	inputBuffer.add(new Integer(chan));
	cond.signalAll();
	lock.unlock();
	if (DBG) System.err.println("IOHandler: left");
    } /* perform() */
    
    /**
     * Adapter method processing the incoming queue of inputs.
     */
    public void run()
    {
	int msg;
	synchronized (this) {
	    lock = new VirtualLock("InputQueue");
	    cond = lock.newCondition();
	    notifyAll();
	}
	try {
	    if (DBG) System.err.println("IOHandler: waiting for inputs");
	    while (true) {
		lock.lock(); // lock operations on input buffer
		while (inputBuffer.isEmpty()) 
		    cond.await();
		msg = inputBuffer.poll().intValue();
		lock.unlock();// allow buffer to be filled again
		if (msg == inputGrasp) {
		    if (DBG) System.err.println("IOHandler: GRASP");
		    lamp.handleGrasp();
		} else if (msg == inputRelease) {
		    if (DBG) System.err.println("IOHandler: RELEASE");
		    lamp.handleRelease();
		} else if (msg == inputTouch) {
		    if (DBG) System.err.println("IOHandler: TOUCH");
		    lamp.handleTouch();
		} else {
		    System.err.println("IOHandler: UNKNOWN");
		}
	    }
	} catch(InterruptedException e) { 
	    System.err.println(e); 
	} finally { lock.unlock(); }
	System.err.println("IOHandler: stopped listening for inputs");
    }

    /**
     * Adapter method: reports level changes encoded into output channel with 
     * parameter.
     * @param level the new light level.
     */
    public void levelChanged(int level)
    {
	if (reporter != null) {
	    pLevel[0] = level;
	    reporter.report(CH_LEVEL, new int[]{ level });

		// Only react to actual changes (optional de-dupe)
		if (lastReportedLevel != null && lastReportedLevel == level) return;
		lastReportedLevel = level;

		// Use the level as the “client id” as you requested.
		// (If collisions worry you, use level+"-"+System.nanoTime().)
		String clientId = String.valueOf(level);

		// Immediately announce “requesting”, then schedule a “returning”
		sendRequesting(clientId);
		
	}
    }

	// private void sendLevelWebhook(int level) {
	// 	// simple JSON payload; adjust as you like
	// 	String json = "{\"level\":" + level + "}";
	// 	HttpRequest req = HttpRequest.newBuilder(lampWebhook)
	// 			.timeout(Duration.ofSeconds(2))
	// 			.header("Content-Type", "application/json")
	// 			.POST(HttpRequest.BodyPublishers.ofString(json))
	// 			.build();

	// 	http.sendAsync(req, HttpResponse.BodyHandlers.discarding())
	// 		.exceptionally(ex -> { 
	// 			System.err.println("Webhook failed: " + ex.getMessage());
	// 			return null; 
	// 		});
	// }

	private void sendRequesting(String clientId) {
		HttpRequest req = HttpRequest.newBuilder(
				serverBase.resolve("/api/requesting/" + clientId))
			.timeout(Duration.ofSeconds(2))
			.GET().build();

		http.sendAsync(req, HttpResponse.BodyHandlers.ofString())
			.thenAccept(resp -> {
				System.out.println("[HTTP] requesting " + clientId + " -> " + resp.statusCode() + " " + resp.body());
				// Schedule a random “returning” after 2–10 seconds
				long delaySec = 2 + rng.nextInt(9); // 2..10
				scheduler.schedule(() -> sendReturningUntilOk(clientId),
								delaySec, TimeUnit.SECONDS);
			})
			.exceptionally(ex -> {
				System.err.println("[HTTP] requesting failed for " + clientId + ": " + ex.getMessage());
				return null;
			});
	}

	private void sendReturningUntilOk(String clientId) {
		HttpRequest req = HttpRequest.newBuilder(
				serverBase.resolve("/api/returning/" + clientId))
			.timeout(Duration.ofSeconds(2))
			.GET().build();

		http.sendAsync(req, HttpResponse.BodyHandlers.ofString())
			.thenAccept(resp -> {
				String body = resp.body();
				System.out.println("[HTTP] returning " + clientId + " -> " + resp.statusCode() + " " + body);
				// Your server replies with “Not your turn yet …” until it’s time. :contentReference[oaicite:2]{index=2}
				if (body != null && body.contains("Not your turn yet")) {
					scheduler.schedule(() -> sendReturningUntilOk(clientId),
									5, TimeUnit.SECONDS); // retry in 5s (matches your Python sim). :contentReference[oaicite:3]{index=3}
				}
			})
			.exceptionally(ex -> {
				System.err.println("[HTTP] returning failed for " + clientId + ": " + ex.getMessage());
				// try again later if you want:
				scheduler.schedule(() -> sendReturningUntilOk(clientId),
								5, TimeUnit.SECONDS);
				return null;
			});
	}

}
