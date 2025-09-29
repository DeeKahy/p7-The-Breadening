// -*- mode: C++; c-file-style: "stroustrup"; c-basic-offset: 4; indent-tabs-mode: nil; -*-
//////////////////////////////////////////////////////////////////////
// File: ticking.h
// This file contains sample implementation which CONSUMES periodic ticks.
// The goal is to evaluate the TRON simulation capabilities.
// Authors:
//   Marius Mikucionis marius@cs.aau.dk
//////////////////////////////////////////////////////////////////////
#ifndef  TICKING_H
#define  TICKING_H

#include "tron/timing.h"
#include "tron/timeutil.h"
#include "tron/adapter.h"
#include "tron/Executable.h"

#include <pthread.h>
#include <semaphore.h>
#include <fstream>

class Ticking: public TestAdapter, private Executable
{
    struct timespec started, now;
    int64_t timenow;

public:
    Ticking(Reporter* rep, const char* filename, int mtu);
    virtual ~Ticking();
    void start_imp();
    void perform_imp(int32_t chan, uint16_t n, const int32_t data[]);

private:
    std::ofstream log;
    volatile bool abort;
    int32_t timeUnit;
    pthread_mutex_t input_m; // input exclusion
    pthread_cond_t msg_c, ack_c;
    pthread_t thr_id;
    void* threadExecute(){};
};

#endif
