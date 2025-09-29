package com.uppaal.autoofflamp;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.Condition;

import com.uppaal.tron.Reporter;
import com.uppaal.tron.VirtualThread;
import com.uppaal.tron.VirtualLock;
import com.uppaal.tron.VirtualCondition;

import com.uppaal.smartlamp.LampInterface;
import com.uppaal.smartlamp.Dimmer;

public class AutoOffLamp extends VirtualThread implements LampInterface
{
    static final int Tsw=2000; //2 seconds
    
    private enum Loc { Off, On };
    private Loc location;

    Lock lock = null;
    Condition cond = null;
    boolean started = false;

    long touchTime;

    Reporter reporter = null;
    Dimmer dimmer = null;

    public AutoOffLamp(Dimmer d)
    {
	super("AutoOffLamp");
	location = Loc.Off;
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

    public void run()
    {
	try { 
	    execute();
	} catch(InterruptedException e){}
	System.out.println("AutoOff interrupted in "+location);
	dimmer.interrupt();
	reporter.disconnect();
	lock.unlock();
    }

    public void execute() throws InterruptedException
    {
	// wait for dimmer to initialize:
	dimmer.waitForStart();

	lock = new VirtualLock("AutoOff");
	cond = lock.newCondition();
	lock.lock();
	// notify that lamp is ready:
	synchronized (this) { 
	    started = true; 
	    notifyAll(); 
	}

	System.out.println("AutoOff Init: "+location);
	while (true) {
	    System.out.println("AutoOff State: "+location);
	    switch (location) {
	    case Off:
		cond.await();
		//do  nothing
		break;
	    case On:
		System.out.println("AutoOff before wait: "+location);
		boolean stillWaiting=true;
		stillWaiting=cond.await(Tsw, TimeUnit.MILLISECONDS);
		System.out.println("AutoOff after cond.wait: "+location);
		if(stillWaiting){
		    //the light was touched before time out
		    //so just wait for a fresh interval
		} else {
		    if (location == Loc.On) {
			dimmer.setLevel(0);
			location=Loc.Off;
		    }
		}
		break;
	    }
	}
    }

    public void handleGrasp(){ assert(false); /* not applicable here */}
    public void handleRelease(){ assert(false); /* not applicable here */}

    public void handleTouch() throws InterruptedException
    {
	if (!started) waitForStart();
	lock.lock();
	touchTime = getTimeMillis();
	switch (location) {
	case Off:
	    location = Loc.On;
	    dimmer.setLevel(10);
	    cond.signalAll();
	    break;
	case On:
	    //location = Loc.On;
	    //dimmer.setLevel(0);
	    cond.signalAll();
	    break;
	}
	lock.unlock();
    }   
}
