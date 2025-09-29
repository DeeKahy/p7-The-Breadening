package com.uppaal.smartlamp;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.Condition;

import com.uppaal.tron.*;

public class DimmerM1 extends Dimmer
{
    static final int delay = 1000; // delay between level inc/decrements

    public DimmerM1()
    {
	started = false;
	location = Loc.UpPassive;
	level = 0;
	oldLevel = 0;
	lightState = LightState.LightOn;
    }

    public void handleTouch() throws InterruptedException
    {
	if (!started) waitForStart();

	System.out.println("Dimmer: handleTouch");
	switch (lightState) {
	case LightOff:
	    setLevel(oldLevel);
	    lightState = LightState.LightOn;
	case LightOn:
	    oldLevel = level;
	    setLevel(0);
	    lightState = LightState.LightOff;
	    break;
	}
    }

    public void handleStartHold(long startHold) throws InterruptedException
    {
	if (!started) waitForStart();

	lock.lock();
	nextUpdate = startHold;
	System.out.println("Dimmer: handleStartHold in "+location);
	switch (location) {
	case UpPassive:
	    setLevel(oldLevel);
	    lightState = LightState.LightOn;
	    location = Loc.UpActive;
	    cond.signalAll();
	    break;
	case DnPassive:
	    oldLevel = level;
	    lightState = LightState.LightOn;
	    location = Loc.DnActive;
	    cond.signalAll();
	    break;
	}
	lock.unlock();
    }

    public void handleEndHold() throws InterruptedException
    {
	if (!started) waitForStart();

	lock.lock();
	switch(location){
	case UpActive:
	    location = Loc.DnPassive;
	    cond.signalAll();
	    break;
	case DnActive:
	    location = Loc.UpPassive;
	    cond.signalAll();
	    break;
	}
	lock.unlock();
    }

    public void execute() throws InterruptedException
    {
	boolean signalled;
	lock = new VirtualLock();
	cond = lock.newCondition();
	lock.lock();
	synchronized (this) { started = true; notifyAll(); }

	boolean doDelay = true;
	while (true) {
	    switch (location) {
	    case UpPassive:
		cond.await();
		break;
		//startHold happened: state has changed to  dimUpActive
	    case UpActive:
		signalled = false;
		if (doDelay) {
		    nextUpdate += delay;
		    signalled = cond.await(delay, TimeUnit.MILLISECONDS);
		}
		if (!signalled) { // timed out=>increaseLevel
		    if (level < maxLevels) {
			setLevel(level + 1);
			doDelay = true;
		    } else if (level == maxLevels) {
			location = Loc.DnActive;
			doDelay = false;
		    }
		}// else endHold has changed our state to DnPassive
		break;
	    case DnPassive:
		cond.await();
		//startHold happened: state has changed to  dimDnActive
		break;
	    case DnActive:
		signalled = false;
		if (doDelay) {
		    nextUpdate += delay;
		    signalled = cond.await(delay, TimeUnit.MILLISECONDS);
		}
		if (!signalled) { // timed out=>decrease Level
		    if(level > 0) {
			setLevel(level - 1);
			doDelay = true;
		    } else if (level == 0) {
			location = Loc.UpActive;
			doDelay = false;
		    }
		} // else endHold has changed our state to dimUpPassive
		break;
	    }
	}
    }

    public void setLevel(int level)
    {
	this.level = level;
	for (LevelListener l: listeners) 
	    l.levelChanged(level);
    }

    public synchronized void addLevelListener(LevelListener listener)
    {
	listeners.add(listener);
    }
}
