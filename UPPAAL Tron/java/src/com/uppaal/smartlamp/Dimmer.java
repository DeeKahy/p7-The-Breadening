package com.uppaal.smartlamp;

import java.util.ArrayList;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.Condition;

import com.uppaal.tron.VirtualThread;

public abstract class Dimmer extends VirtualThread
{
    protected boolean started = false;
    protected enum Loc { UpPassive, UpActive, DnPassive, DnActive }
    protected Loc location;

    protected enum LightState { LightOff, LightOn }
    protected LightState lightState;

    protected int level;
    protected int oldLevel;
    protected int maxLevels;
    protected long nextUpdate;

    protected ArrayList<LevelListener> listeners = 
	new ArrayList<LevelListener>();

    protected Lock lock = null;
    protected Condition cond = null;
   
    protected Dimmer() { super("Dimmer"); }

    public synchronized void waitForStart() throws InterruptedException
    { 
	while (!started) wait(); 
    }

    public static Dimmer create(int mutant, int levelCount) 
    {
	Dimmer d;
	switch (mutant) {
	case 1: d = new DimmerM1(); break;
	case 2: d = new DimmerM2(); break;
	default: d = new DimmerM0(); break;
	}
	d.maxLevels = levelCount;
	return d;
    }

    public void run() {
	try { execute(); }
	catch (InterruptedException e){}
	finally { lock.unlock(); }
    }

    protected abstract void execute() throws InterruptedException;

    // input methods
    public abstract void handleTouch() throws InterruptedException;
    public abstract void handleStartHold(long startHold) throws InterruptedException;
    public abstract void handleEndHold() throws InterruptedException;

    // level display methods:
    public abstract void setLevel(int level);
    public abstract void addLevelListener(LevelListener listener);
}
