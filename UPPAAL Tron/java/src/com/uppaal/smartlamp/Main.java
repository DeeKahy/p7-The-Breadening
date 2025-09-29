package com.uppaal.smartlamp;

import java.awt.Frame;
import java.awt.Toolkit;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;

import com.uppaal.tron.Reporter;
import com.uppaal.tron.VirtualThread;

public class Main extends MouseAdapter
{
    protected LampInterface lamp = null;
    protected Dimmer dimmer = null;

    Reporter reporter = null; // sends output
    TestIOHandler testIOHandler = null;// receives and delivers inputs

    protected int mutant = 0, levelCount = 10;
    protected boolean withGUI = true;

    public Main(String args[])
    {
	handleArguments(args);
	initialize();
	initializeIO();
	if (withGUI) createGUI();
    }

    protected void handleArguments(String args[]) 
    {
	int i = 0;
	while (i<args.length) {
	    if ("-N".equals(args[i])){
		withGUI = false; i++;
	    } else if ("-M".equals(args[i])){
		if (i+1<args.length) mutant = Integer.parseInt(args[i+1]);
		else {
		    System.err.println("Specify mutant id.");
		    return ;
		}
		i += 2 ;
	    } else if ("-C".equals(args[i])) {
		if (i+2<args.length) {
		    int port = Integer.parseInt(args[i+2]);
		    if (port <= 0) {
			System.err.println("The specified port ("+args[i+2]+
					   ") is not in valid range.");
			return;
		    }
		    VirtualThread.setRemoteClock(args[i+1], port);
		    i += 3;
		} else {
		    System.err.println("Specify virtual clock, like: "+
				       "-C localhost 6521");
		    return ;
		}
	    } else {
		System.err.println("Uninterpreted option: "+args[i]);
		i++;
	    }
	}
    }

    protected void initialize()
    {
	assert(levelCount>0);
	dimmer = Dimmer.create(mutant, levelCount);
	if (mutant == 3) lamp = new SmartLampM3(dimmer);
	else lamp = new SmartLamp(dimmer);
    }

    protected void initializeIO()
    {
	testIOHandler = new TestIOHandler(lamp);
	reporter = new Reporter(testIOHandler, 9999);
	dimmer.addLevelListener(testIOHandler);
	lamp.setReporter(reporter);
	dimmer.start();
    }

    protected void createGUI()
    {
	Frame f = new Frame("LightController");
	f.setIconImage(Toolkit.getDefaultToolkit().createImage(
	       f.getClass().getClassLoader().
	       getSystemResource("com/uppaal/smartlamp/bulb.png")));
	f.addWindowListener(new WindowAdapter(){
		public void windowClosing(WindowEvent e) { System.exit(0); }
	    });
	LevelHistory lh = new LevelHistory(10, 0);
	lh.addMouseListener(this);
	dimmer.addLevelListener(lh);
	LevelBar lb = new LevelBar(10, 0);
	lb.addMouseListener(this);
	dimmer.addLevelListener(lb);
	LevelLamp lamp = new LevelLamp(10, 0);
	lamp.addMouseListener(this);
	dimmer.addLevelListener(lamp);
	
	GridBagLayout gridbag = new GridBagLayout();
	GridBagConstraints c = new GridBagConstraints();
	f.setLayout(gridbag);
	c.fill = GridBagConstraints.BOTH; c.weighty = 1.0;
	c.weightx = 9.0;
	gridbag.setConstraints(lh, c); f.add(lh);
	c.weightx = 1.0;
	gridbag.setConstraints(lb, c); f.add(lb);
	c.weightx = 5.0;
	c.gridwidth = GridBagConstraints.REMAINDER; //end row
	gridbag.setConstraints(lamp, c); f.add(lamp);
	f.setBounds(0, 0, 400, 202); f.setVisible(true);
	lh.waitForReady();
    }

    public void play(){
	lamp.start();
	System.out.println("LC started");
	try { lamp.join(); }
	catch (InterruptedException e) {}
    }

    public void mousePressed(MouseEvent e)
    {
	try {
	    if (VirtualThread.realtime()) lamp.handleGrasp();
	    else {
		System.err.println("##### Virtual time framework does not allow outside interraction. #####");
		java.awt.Toolkit.getDefaultToolkit().beep();
	    }
	} catch (InterruptedException ex){}
    }
    public void mouseReleased(MouseEvent e)
    {
	try {
	    if (VirtualThread.realtime()) lamp.handleRelease();
	    else {
		System.err.println("##### Virtual time framework does not allow outside interraction. #####");
		java.awt.Toolkit.getDefaultToolkit().beep();
	    }
	} catch (InterruptedException ex){}
    }

    public static void main(String args[])
    {
	Main main = new Main(args);
	main.play();
	System.out.println("SmartLamp terminated");
    }
}
