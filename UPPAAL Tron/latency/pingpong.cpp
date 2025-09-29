// -*- mode: C++; c-file-style: "stroustrup"; c-basic-offset: 4; indent-tabs-mode: nil; -*-
//////////////////////////////////////////////////////////////////////
// File: pingpong.cpp
// Authors:
//   Marius Mikucionis marius@cs.aau.dk
//////////////////////////////////////////////////////////////////////

#include "pingpong.h"
#include "tron/timing.h"
#include "tron/timeutil.h"

#include <errno.h>
#include <iostream>
#include <fstream>
#include <stdio.h>
#include <stdlib.h>
#include <inttypes.h>
#include <tr1/unordered_map>

using std::cerr;
using std::endl;

extern "C" {
    TestAdapter* adapter_new(Reporter* rep, int argc, const char* args[])
    {
        if (argc < 2) {
            cerr<<"PING: provide a log filename as adapter parameter"<<endl;
            return NULL;
        } else return new Ping(rep, args[1]);
    }
    void adapter_delete(TestAdapter* ptr){ delete ptr; }
    void adapter_start(TestAdapter* a){ ((Ping*)a)->start_imp(); };
    void adapter_perform(TestAdapter* a,
                         int32_t chan, uint16_t n, const int32_t data[]) {
        ((Ping*)a)->perform_imp(chan, n, data);
    }
}

using std::cerr;
using std::endl;

enum { pong=0 };
const char* inputStrings[] = {"pong", 0, 0};

enum { ping=0 };
const char* outputStrings[] = {"ping", 0, 0};

// hash-maps for fast input output event translation:
typedef std::tr1::unordered_map<int32_t, int32_t> intint_t;
intint_t ins, outs;

static volatile int instance;

Ping::Ping(Reporter* rep, const char* fname):
    TestAdapter(rep), log(fname), abort(false), timeUnit(1000)
{
    instance = 0;
    start = adapter_start;
    perform = adapter_perform;
    if (0 != pthread_mutex_init(&input_m, NULL/*use defaults*/)) {
        cerr << "TK: error while init mutex" << endl;
        exit(EXIT_FAILURE);
    }
    pthread_cond_init(&msg_c, NULL);
    pthread_cond_init(&ack_c, NULL);

    int i = 0, chNo = 0, r=0;
    try {
        while (inputStrings[i]!=0) {
            int id = rep->getInputEncoding(rep, inputStrings[i]);
            if (id<0) throw id;
            ins[id] = chNo++;
            while (inputStrings[++i] != 0) {// bind variables to channel
                r = rep->addVarToInput(rep, id, inputStrings[i]);
                if (r<0) throw r;
            }
            i++; // next channel
        }
        i = 0; chNo = 0;
        while (outputStrings[i]!=0) {
            int id =rep->getOutputEncoding(rep, outputStrings[i]);
            if (id<0) throw id;
            outs[chNo++] = id;
            while (outputStrings[++i] != 0) { // bind variables to channel
                r = rep->addVarToOutput(rep, id, outputStrings[i]);
                if (r<0) throw r;
            }
            i++; // next channel
        }
        r = rep->setTimeUnit(rep, timeUnit); if (r<0) throw r;
        r = rep->setTimeout(rep, 80000); if (r<0) throw r;
    } catch (int error) {
        cerr << "TK: "<< rep->getErrorMessage(rep, error) << endl;
        exit(EXIT_FAILURE);
    }
}

// we assume that the calling thread is registered in globalclock
Ping::~Ping()
{
    pthread_mutex_lock(&input_m);
    abort = true;
    tron_cond_broadcast(&msg_c);// wake up the listening thread
    pthread_mutex_unlock(&input_m);
    joinThread(NULL); // wait for completion

    pthread_cond_destroy(&msg_c);
    pthread_cond_destroy(&ack_c);
    pthread_mutex_destroy(&input_m);
    log.close();
}

inline void Ping::start_imp()
{
    pthread_mutex_lock(&input_m);
    startonce();
    tron_cond_wait(&ack_c, &input_m);
    pthread_mutex_unlock(&input_m);
}

inline void Ping::perform_imp(int32_t chan, uint16_t n, const int32_t data[])
{
    pthread_mutex_lock(&input_m);
    intint_t::const_iterator it = ins.find(chan);
    if (it != ins.end()) switch (it->second) {
    case pong:
        tron_gettime(&pongTime);
        log << instance << " " << diff2ms(pingTime,started) << " " 
            << diff2ms(pongTime,started) << endl;
        tron_cond_broadcast(&msg_c);
        break;
    default:
        cerr << "PING: unknown action "<<chan<<", ignoring.." << endl;
    }
    pthread_mutex_unlock(&input_m);
}

void* Ping::threadExecute()
{
    struct timespec timeout;
    long tmp;
    pthread_mutex_lock(&input_m);
    tron_cond_broadcast(&ack_c);
    pthread_mutex_unlock(&input_m);
    tron_gettime(&started);
    while (!abort) {
        pthread_mutex_lock(&input_m);
        tron_gettime(&timeout);
        instance++;
        tmp = (long)(drand48()*100+410*instance)*timeUnit;
        timeout = started;
        timeout += tmp;
        while (ETIMEDOUT != tron_cond_timedwait(&msg_c, &input_m, &timeout));
        pthread_mutex_unlock(&input_m);
        if (!abort) {
            tron_gettime(&pingTime);
            rep->report_now(rep, outs[ping], 0, NULL);
        }
    }
    return NULL;
}
