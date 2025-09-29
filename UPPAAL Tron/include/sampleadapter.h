// -*- mode: C++; c-file-style: "stroustrup"; c-basic-offset: 4; indent-tabs-mode: nil; -*-
/////////////////////////////////////////////////////////////////////////////
// Filename: sampleadapter.hh
// File contains test execution connection to implementation under test (IUT)
// and provide sample locking protocol for synchronous input/output delivery.
// Authors:
//    Marius Mikucionis marius@cs.aau.dk
////////////////////////////////////////////////////////////////////////////

#ifndef SAMPLEADAPTER_HH
#define SAMPLEADAPTER_HH

#include "tron/adapter.h"

//#define _REENTRANT
#define __STL_PTHREADS
#include <pthread.h>

/*****************************************************************************
 * SampleAdapter class provides Adapter interface for UPPAAL TRON and IUT.
 */
class SampleAdapter: public TestAdapter
{
private:
    volatile bool input_wish, output_wish;
    pthread_mutex_t wish_m;

protected:
    SampleAdapter(Reporter *r);
/**
 * perform() will be called by tryPerform() when there is no output pending
 * make sure that the implementation calls inputDelivered()
 */
    virtual void perform_imp(int32_t chan, uint16_t n, const int32_t data[])=0;

public:
    virtual ~SampleAdapter();
    virtual void start_imp()=0;
/**
 * don't overwrite tryPerform, overwrite perform() instead or use TestAdapter
 */
    virtual bool tryPerform(int32_t chan, uint16_t n, const int32_t data[]);
    virtual void outputDelivered();
/**
 * call inputDelivered inside perform() after input recorded, but before unlock
 */
    virtual void inputDelivered();
/**
 * call tryReport instead of reporter->report_now(), this assures synchrony
 */
    virtual bool tryReport(int32_t chan, uint16_t n, const int32_t data[]);//true=OK, false=input pending
};

#endif
