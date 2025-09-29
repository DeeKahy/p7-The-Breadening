package com.uppaal.autoofflamp;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.Condition;

import com.uppaal.tron.VirtualThread;
import com.uppaal.tron.VirtualLock;
import com.uppaal.tron.VirtualCondition;
import com.uppaal.tron.Reporter;

import com.uppaal.smartlamp.LampInterface;
import com.uppaal.smartlamp.Dimmer;

public class AutoOffLight extends VirtualThread implements LampInterface
{    
    private enum Loc { Off, On };
    private Loc location;
    
    boolean alive = false;
    
    long aTime; //time of activation

    Dimmer dimmer = null;

    public AutoOffLight(Dimmer d)
    {
	super("AutoOffLight");
    }
 
    public void run()
    {
	try { execute(); } catch (InterruptedException e){}
	System.out.println("IFace interrupted in "+location);
	dimmer.interrupt();
	reporter.disconnect();
	lock.unlock();
    }

    public void execute() throws InterruptedException
    {
	// wait for dimmer to initialize:
	synchronized(dimmer) { while (!dimmer.alive) dimmer.wait(); }

	lock = new VirtualLock("LCLock");
	cond = lock.newCondition();
	lock.lock();
	// notify that LC is ready:
	synchronized (this) { alive = true; notifyAll(); }

	System.out.println("AutoOff Init: "+location);
	aTime = getTimeMillis();
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

    public void handleTouch() throws InterruptedException
    {
	synchronized (this) { while (!alive) wait(); }
	lock.lock();
	switch (location) {
	case Off:
	    location = Loc.On;
	    dimmer.setLevel(levelCount);
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
