package com.uppaal.smartlamp;

import java.awt.Color;
import java.awt.Panel;
import java.awt.Graphics;
import java.awt.Dimension;
import java.awt.Image;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.Condition;

import com.uppaal.tron.VirtualLock;
import com.uppaal.tron.VirtualThread;

public class LevelHistory extends Panel implements LevelListener, Runnable
{
    int max, current, history[]=null, len=0;
    int gridLineCount, gridOffset;
    Image img = null;
    Dimension my = new Dimension(2,2);
    int phase=0;
    int stepSize = 1;
    private boolean alive = false;

    private synchronized void setAlive(boolean a)
    {
	alive = a; notifyAll();
    }

    public synchronized void waitForReady()
    {
	try { while (!alive) wait(); }
	catch (InterruptedException e) {
	    System.err.println(e); System.exit(1);
	}
    }

    Lock lock = null;
    Condition cond = null;

    public void run()
    {
	try {
	    lock = new VirtualLock("LHLock");
	    cond = lock.newCondition();
	    lock.lock();
	    setAlive(true);
	    boolean simtime = (!VirtualThread.realtime());
	    if (simtime) stepSize = 8;
	    int refresh = stepSize-1;
	    while (true) {
		cond.await(300, TimeUnit.MILLISECONDS);
		if (history!=null) {
		    if (len==my.width) {
			System.arraycopy(history,1,history,0,my.width-1);
			len--;
		    }
		    history[len] = current; len++;
		}
		if (simtime) {
		    refresh++;
		    if (refresh==stepSize) { drawSelf(); refresh = 0; }
		} else drawSelf();
	    }
	} catch (InterruptedException ex) {
	    System.err.println(ex);
	} finally {
	    lock.unlock();
	}
    }

    public LevelHistory(int maxLevel, int initialLevel)
    {
	super();
	setMinimumSize(my);
	max = maxLevel;
	current = initialLevel;
	gridLineCount = 15;
	VirtualThread t = new VirtualThread(this, "LevelHistory");
	t.setPriority(Thread.MIN_PRIORITY);
	t.start();
    }

    public void levelChanged(int level)
    {
	current = level;
    }

    public synchronized void paint(Graphics g){
	if (img != null) {
	    g.drawImage(img, 0, 0, this);
	}
    }

    public synchronized void update(Graphics g){
	if (img != null) {
	    g.drawImage(img, 0, 0, this);
	}
    }

    void drawSelf(){
	Dimension size = getSize();
	if (size.width != my.width || size.height != my.height) {
	    if (size.width>=2) my.width = size.width;
	    else my.width = 2;
	    if (size.height>=2) my.height = size.height;
	    else my.height = 2;
	    int hist[] = new int[my.width];
	    if (history!=null) {
		if (len>=my.width) {
		    System.arraycopy(history, len-my.width, hist, 
				     0, my.width);
		    len = my.width;
		} else System.arraycopy(history, 0, hist, 0, len);
	    }
	    history = hist;
	    img = createImage(my.width, my.height);
	}
	if (img!=null) {
	    Graphics g = img.getGraphics();
	    phase += stepSize;
	    if ((my.width-1)>=gridLineCount)
		phase %= ((my.width-1)/gridLineCount);
	    else phase = 0;
	    g.setColor(Color.black);
	    g.fillRect(0, 0, my.width, my.height);
	    g.setColor(Color.blue);
	    for (int i=1; i<=gridLineCount; i++) { //vertical grid lines
		int x = i*(my.width-1)/gridLineCount;
		g.drawLine(x-phase, 0, x-phase, my.height);
	    }
	    for (int i=1; i<max; i++) { //horizontal grid lines
		int y = i*(my.height-1)/max;
		g.drawLine(0, y, my.width, y);
	    }
	    g.drawRect(0, 0, my.width-1, my.height-1);
	    g.setColor(Color.yellow);
	    for (int i=0; i<len-1; i++) {
		int v1 = my.height-1-history[i]*(my.height-1)/max;
		int v2 = my.height-1-history[i+1]*(my.height-1)/max;
		g.drawLine(my.width-1-len+i, v1, my.width-len+i, v2);
	    }
	}
	repaint(20);
    }
}
