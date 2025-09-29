package com.uppaal.dummy;

import com.uppaal.tron.Reporter;

public interface DummyInterface
{
    public void start();

    public void waitForStart() throws InterruptedException;

    public void join() throws InterruptedException;

    public void setDummyListener(DummyListener listener);

    public void handleMyInput1() throws InterruptedException;
    public void handleMyInput2() throws InterruptedException;
}
