// -*- mode: C++; c-file-style: "stroustrup"; c-basic-offset: 4; indent-tabs-mode: nil; -*-
//////////////////////////////////////////////////////////////////////
// File: ticking.cpp
// Authors:
//   Marius Mikucionis marius@cs.aau.dk
//////////////////////////////////////////////////////////////////////

#include "ticking.h"

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
        if (argc < 3) {
            cerr<<"TK: provide a log filename and model time unit size"<<endl;
            return NULL;
        } else {
            return new Ticking(rep, args[1], atoi(args[2]));
        }
    }
    void adapter_delete(TestAdapter* ptr){ delete ptr; };
    void adapter_start(TestAdapter* a){ ((Ticking*)a)->start_imp(); };
    void adapter_perform(TestAdapter* a,
                         int32_t chan, uint16_t n, const int32_t data[]) {
        ((Ticking*)a)->perform_imp(chan, n, data);
    }
}

using std::cerr;
using std::endl;

enum { tick=0 };
const char* inputStrings[] = {"tick", "Ticker.n", 0, 0};

const char* outputStrings[] = {0};

// hash-maps for fast input output event translation:
typedef std::tr1::unordered_map<int32_t, int32_t> intint_t;
intint_t ins, outs;

Ticking::Ticking(Reporter* rep, const char* fname, int mtu):
    TestAdapter(rep), log(fname), abort(false), timeUnit(mtu)
{
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
                r=rep->addVarToInput(rep, id, inputStrings[i]);
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
        r = rep->setTimeout(rep, 30000);  if (r<0) throw r;
    } catch (int error) {
        cerr << "TK: "<< rep->getErrorMessage(rep, error) << endl;
        exit(EXIT_FAILURE);
    }
}

// we assume that the calling thread is registered in globalclock
Ticking::~Ticking()
{
    log.close();
    pthread_cond_destroy(&msg_c);
    pthread_cond_destroy(&ack_c);
    pthread_mutex_destroy(&input_m);
}

inline void Ticking::start_imp()
{
    tron_gettime(&started);
    log << timeUnit << endl;
}

inline void Ticking::perform_imp(int32_t chan, uint16_t n,
                                 const int32_t data[])
{
    pthread_mutex_lock(&input_m);
    tron_gettime(&now);
    timenow = diff2ms(now, started);

    intint_t::const_iterator it = ins.find(chan);
    if (it != ins.end()) switch (it->second) {
    case tick:
        assert(n==1);
        log << timenow << " " << data[0] << endl;
        break;
    default:
        cerr << "TK: unknown action "<<chan<<", ignoring.." << endl;
    }
    pthread_mutex_unlock(&input_m);
}
