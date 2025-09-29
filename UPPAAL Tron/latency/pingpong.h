// -*- mode: C++; c-file-style: "stroustrup"; c-basic-offset: 4; indent-tabs-mode: nil; -*-
//////////////////////////////////////////////////////////////////////
// File: pingpong.h
// IUT which issues ping and consumes pong.
// The goal is to evaluate the TRON simulation capabilities.
// Authors:
//   Marius Mikucionis marius@cs.aau.dk
//////////////////////////////////////////////////////////////////////
#ifndef  PINGPONG_HH
#define  PINGPONG_HH

#include "tron/adapter.h"
#include "tron/Executable.h"

#include <pthread.h>
#include <semaphore.h>
#include <sys/time.h>
#include <fstream>

class Ping: public TestAdapter, private Executable
{
    struct timespec started, pingTime, pongTime;

public:
    Ping(Reporter* rep, const char* filename);
    virtual ~Ping();
    void start_imp();
    void perform_imp(int32_t chan, uint16_t n, const int32_t data[]);
private:
    std::ofstream log;
    volatile bool abort;
    int32_t timeUnit;

    pthread_mutex_t input_m; // input exclusion
    pthread_cond_t msg_c, ack_c;
    virtual void* threadExecute();
};

#endif
