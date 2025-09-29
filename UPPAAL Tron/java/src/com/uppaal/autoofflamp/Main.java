package com.uppaal.autoofflamp;

import java.awt.Frame;
import java.awt.Toolkit;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.event.MouseListener;
import java.awt.event.MouseEvent;
import java.awt.event.WindowListener;
import java.awt.event.WindowEvent;

import com.uppaal.tron.Reporter;
import com.uppaal.tron.VirtualThread;

import com.uppaal.smartlamp.Dimmer;
import com.uppaal.smartlamp.LampInterface;
import com.uppaal.smartlamp.LevelHistory;
import com.uppaal.smartlamp.TestIOHandler;

public class Main extends com.uppaal.smartlamp.Main
{
    @Override
    protected void initialize()
    {
	assert(levelCount>0);
	//Dimmer is only used to manage graphics easily
	dimmer = Dimmer.create(mutant, levelCount);
	lamp = new AutoOffLamp(dimmer);
    }

    @Override
    public void mousePressed(MouseEvent e){}

    @Override
    public void mouseReleased(MouseEvent e){}

    @Override
    public void mouseClicked(MouseEvent e)
    {
	try {
	    if (VirtualThread.realtime()) lamp.handleTouch();
	    else {
		System.err.println("##### Virtual time framework does not allow outside interraction. #####");
		java.awt.Toolkit.getDefaultToolkit().beep();
	    }
	} catch (InterruptedException ex){}
    }

    public Main(String args[])
    {
	super(args);
    }

    public static void main(String args[])
    {
	Main main = new Main(args);
	main.play();
	System.out.println("AutoOffLamp terminated");
    }
}
