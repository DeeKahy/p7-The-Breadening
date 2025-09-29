package com.uppaal.smartlamp;

import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.LayoutManager;
import java.awt.Panel;
import java.awt.Toolkit;
import java.awt.Image;

/**
 * @author marius
 *
 */
public class LevelLamp extends Panel implements LevelListener
{
    int max, current;
    Image image = Toolkit.getDefaultToolkit().createImage(
	       getClass().getClassLoader().
	       getSystemResource("com/uppaal/smartlamp/bigbulb.png"));
    /**
     *
     */
    public LevelLamp(int maxLevel, int initLevel)
    {
	super();
	max = maxLevel;
	current = initLevel;
	setBackground(Color.black);
    }

    public void update(Graphics g) { paint(g); }

    Dimension size = getSize();
    public void paint(Graphics g)
    {
	Color c;
	float r = 1.0f*current/max;
	if (current==0) c = new Color(0.4f, 0.4f, 0.4f);
	else if (current==max) c = Color.white;
	else c = new Color(0.5f+0.5f*r, 0.5f+0.5f*r, 1.0f*r*r*r);
	Dimension t = getSize();
	if (t.width!=size.width || t.height!=size.height) {
	    size = t;
	    g.clearRect(0, 0, size.width, size.height);
	}

	if (size.width>size.height)
	    g.drawImage(image, (size.width-size.height)/2, 0,
			size.height, size.height, c, this);
	else
	    g.drawImage(image, 0, (size.height-size.width)/2,
			size.width, size.width, c, this);
    }

    /* (non-Javadoc)
     * @see LightController.LevelListener#levelChanged(int)
     */
    public void levelChanged(int level)
    {
	current = level;
	repaint(20);
    }
}
