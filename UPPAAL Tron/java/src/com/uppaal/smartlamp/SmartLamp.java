package com.uppaal.smartlamp;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.Condition;

import com.uppaal.tron.Reporter;
import com.uppaal.tron.VirtualThread;
import com.uppaal.tron.VirtualLock;
import com.uppaal.tron.VirtualCondition;

public class SmartLamp extends VirtualThread implements LampInterface
{
    static final int epsilon = 200; // 200 ms
    static final int delta   = 500; // 500 ms

    private enum Loc { Idle, Ignoring, Alert, Holding }
    private Loc location;

    Lock lock = null;
    Condition cond = null;
    boolean started = false;

    long graspTime;

    Reporter reporter = null;
    Dimmer dimmer = null;

    public SmartLamp(Dimmer d)
    {
	super("SmartLamp");
	location = Loc.Idle;
	dimmer = d;
    }

    public void setReporter(Reporter r) 
    {
	reporter = r;
    }

    public synchronized void waitForStart() throws InterruptedException
    { 
	while (!started) wait(); 
    }

    public void run() {
	try { execute(); }
	catch (InterruptedException e){}
	dimmer.interrupt();
	reporter.disconnect();
	lock.unlock();
    }

    protected void execute() throws InterruptedException
    {
	// wait for dimmer to initialize:
	dimmer.waitForStart();
	
	lock = new VirtualLock("IFace");
	cond = lock.newCondition();
	lock.lock();
	// notify that lamp is ready:
	synchronized (this) { started = true; notifyAll(); }

	while (started) {
	    switch (location) {
	    case Idle:
		cond.await();
		break;
	    case Ignoring:
		if (!cond.await(epsilon, TimeUnit.MILLISECONDS)) {
		    // timedout, update the state:
		    location = Loc.Alert;
		} // else state is already updated
		break;
	    case Alert:
		if (!cond.await(delta-epsilon, TimeUnit.MILLISECONDS)) {
		    // timedout, update the state:
		    location = Loc.Holding;
		    dimmer.handleStartHold(graspTime+delta);
		} // else state is already updated
		break;
	    case Holding:
		cond.await();
		break;
	    }
	}
    }

    public void handleGrasp() throws InterruptedException
    {
	if (!started) waitForStart();

	lock.lock();
	graspTime = getTimeMillis();
	switch (location) {
	case Idle:
	    location = Loc.Ignoring;
	    cond.signalAll();
	    break;
	}
	lock.unlock();
    }

    public void handleRelease() throws InterruptedException
    {
	if (!started) waitForStart();

	lock.lock();
	switch (location) {
	case Ignoring:
	    location = Loc.Idle;
	    cond.signalAll();
	    break;
	case Alert:
	    location = Loc.Idle;
	    cond.signalAll();
	    dimmer.handleTouch();
	    break;
	case Holding:
	    location = Loc.Idle;
	    cond.signalAll();
	    dimmer.handleEndHold();
	    break;
	}
	lock.unlock();
    }

    public void handleTouch(){ assert(false); /* not applicable here */}
}
