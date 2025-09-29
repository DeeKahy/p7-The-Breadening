package com.uppaal.smartlamp;

import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.LayoutManager;
import java.awt.Panel;

/**
 * @author marius
 */
public class LevelBar extends Panel implements LevelListener
{
    int max, current;
    /**
     *
     */
    public LevelBar(int maxLevel, int initLevel)
    {
	super();
	max = maxLevel;
	current = initLevel;
	setBackground(Color.black);
    }

    /**
     * @param layout
     */
    public LevelBar(LayoutManager layout) { super(layout); }

    public void paint(Graphics g)
    {
	Dimension size = getSize();
	g.setColor(Color.yellow);
	g.drawRect(0, 0, size.width-1, size.height-1);
	int h = (size.height-5)/max-1;
	for (int i=1; i<=current; i++) {
	    g.fillRect(3, size.height-2-(size.height-5)*i/max,
		       size.width-6, h);
	}
	g.setColor(Color.darkGray);
	for (int i=current+1; i<=max; i++) {
	    g.fillRect(3, size.height-2-(size.height-5)*i/max,
		       size.width-6, h);
	}
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
