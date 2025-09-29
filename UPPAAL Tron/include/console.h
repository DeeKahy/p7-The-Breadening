// -*- mode: C++; c-file-style: "stroustrup"; c-basic-offset: 4; indent-tabs-mode: nil; -*-
//////////////////////////////////////////////////////////////////////
// File: iut_console.hh
// Special adapter to interact through console (with user).
// The goal is education and further connectivity with other tools.
// Authors:
//   Marius Mikucionis marius@cs.aau.dk
//////////////////////////////////////////////////////////////////////
#ifndef  CONSOLE_HH
#define  CONSOLE_HH

#include <pthread.h>
#include <semaphore.h>
#include "sampleadapter.h"
#include <sys/time.h>
#include <fstream>
#include "tron/timing.h"
#include "tron/timeutil.h"
#include "tron/Executable.h"
#include "dialog.h"

class Console: public SampleAdapter, private Executable
{
    hrtime_t started, pingTime;

public:
    Console(Reporter* rep, const char* filename);
    virtual ~Console();
    void start_imp();
    void perform_imp(int32_t chan, uint16_t n, const int32_t data[]);

private:
    std::ofstream log;
    volatile bool abort;
    int32_t timeUnit;

    pthread_mutex_t input_m; // input exclusion
    
    pthread_cond_t msg_c, ack_c;
    virtual void* threadExecute();
    Dialog dlg;
    int32_t maxInPars, maxOutPars;
    int32_t *input, *output;
    char main_menu();
};

#endif
