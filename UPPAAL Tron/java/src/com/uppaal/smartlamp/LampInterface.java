package com.uppaal.smartlamp;

import com.uppaal.tron.Reporter;

public interface LampInterface
{
    public void start();

    public void waitForStart() throws InterruptedException;

    public void join() throws InterruptedException;

    public void setReporter(Reporter r);

    public void handleGrasp() throws InterruptedException;
    public void handleRelease() throws InterruptedException;

    /* AutoOffLightController-specific: */
    public void handleTouch() throws InterruptedException;
}
