// -*- mode: C++; c-file-style: "stroustrup"; c-basic-offset: 4; indent-tabs-mode: nil; -*-
/////////////////////////////////////////////////////////////////////////////
// Filename: testadapter.hh
// File contains test execution connection to implementation under test (IUT)
// Defines interfaces between UPPAAL TRON engine and IUT.
// Authors:
//    Marius Mikucionis marius@cs.aau.dk
////////////////////////////////////////////////////////////////////////////

#ifndef TRON_TESTADAPTER_H
#define TRON_TESTADAPTER_H

#ifdef __MINGW32__
#include <stdint.h>
#elif WIN32
typedef unsigned __int16 uint16_t;
typedef signed  __int32  int32_t;
typedef signed __int64 int64_t;
#else
#include <inttypes.h>
#endif


typedef enum {SL_NONE=0, SL_LOCATION=1, SL_DATA=2, SL_CLOCK=4} logflags_t;

/****************************************************************************
 * Reporter provides TRON test driver interface for reporting outputs.
 */

extern "C" {

struct Reporter
{
/**
 * Action is a varying length array of int32_t: <chanId> <data>^n
 * chanId is an input/output action id. Use get*Encoding to get mapping.
 * data are variable value(s) associated with channel synchronization.
 * n is a number of data values in array, n>=0.
 */
    void (*report_now)(Reporter*, 
                       int32_t chan, uint16_t n, const int32_t data[]);
/**
 * Use these functions to map observable channels to their ids,
 * return chanId>=0 upon success and error code<0 upon error.
 */
    int32_t (*getInputEncoding)(Reporter*, const char* inputChanName);
    int32_t (*getOutputEncoding)(Reporter*, const char* outputChanName);
/**
 * Use the following functions to attach variables to channels,
 * return 0 upon success and error code<0 upon error.
 */
    int32_t (*addVarToInput)(Reporter*, int32_t chan, const char* variable);
    int32_t (*addVarToOutput)(Reporter*, int32_t chan, const char* variable);
    int32_t (*setTimeUnit)(Reporter*, const int64_t& microsecs_per_unit);
    int32_t (*setTimeout)(Reporter*, int32_t timeout_in_units);
/**
 * Use getErrorMessage to retrieve the description of given error code.
 */
    const char* (*getErrorMessage)(Reporter*, int32_t error_code);
};

/**********************************************************************
 * TestAdapter class provides C++ Adapter interface for driver.
 * This is the minimal interface that IUT must provide for driver.
 * Communication is assumed to be non-blocking.
 */
struct TestAdapter
{
/*
 * TRON will call start() whenever IUT is to be reset to initial state
 * use this for internal (re-)initialization.
 * Clock starts counting from zero when thread returns.
 */
    void (*start)(TestAdapter*);
/*
 * TRON will deliver inputs through tryPerform(action),
 * No wait/delay is permitted in this method.
 * Any queueing locks should be acquired ASAP.
 * The only exception is socket adapter using pthread_* in virtual time.
 */
    void (*perform)(TestAdapter*,
                    int32_t chan, uint16_t n, const int32_t data[]);
    Reporter* const rep;
    TestAdapter(Reporter* r): rep(r) 
	{ 
        // remember to initialize pointers to your implementation functions
        // otherwise TRON will complain!
        start = 0; perform = 0;
    }
    virtual ~TestAdapter(){};
};

}/* extern "C" */

#endif /* TRON_TESTADAPTER_H */
