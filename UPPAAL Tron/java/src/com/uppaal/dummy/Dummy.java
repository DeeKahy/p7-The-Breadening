package com.uppaal.dummy;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.Condition;

import com.uppaal.tron.Reporter;
import com.uppaal.tron.VirtualThread;
import com.uppaal.tron.VirtualLock;
import com.uppaal.tron.VirtualCondition;

public class Dummy extends VirtualThread implements DummyInterface
{
    private enum Loc { Idle, Busy }
    private Loc location;

    Lock lock = null;
    Condition cond = null;
    boolean started = false;
    DummyListener listener = null;

    public Dummy(int mutant)
    {
	super("Dummy");
	location = Loc.Idle;
    }

    public void setDummyListener(DummyListener listener) 
    {
	this.listener = listener;
    }

    public synchronized void waitForStart() throws InterruptedException
    { 
	while (!started) wait(); 
    }

    public void run() {
	try { execute(); }
	catch (InterruptedException e){}
	listener.disconnect();
	lock.unlock();
    }

    protected void execute() throws InterruptedException
    {
	lock = new VirtualLock("DummyLock");
	cond = lock.newCondition();
	lock.lock();
	// notify that dummy is ready:
	synchronized (this) { started = true; notifyAll(); }

	while (started) {
	    switch (location) {
	    case Idle:
		cond.await();
		break;
	    case Busy:
		if (!cond.await(1000, TimeUnit.MILLISECONDS)) {
		    listener.reportMyOutput();
		    location = Loc.Idle;
		} // else state is already updated
		break;
	    }
	}
    }

    public void handleMyInput1() throws InterruptedException
    {
	lock.lock();
	switch (location) {
	case Idle:
	    location = Loc.Busy;
	    cond.signalAll();
	    break;
	}
	lock.unlock();
    }

    public void handleMyInput2() throws InterruptedException
    {
	lock.lock();
	switch (location) {
	case Busy:
	    location = Loc.Idle;
	    cond.signalAll();
	    break;
	}
	lock.unlock();
    }
}
