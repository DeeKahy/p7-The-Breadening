// -*- mode: C++; c-file-style: "stroustrup"; c-basic-offset: 4; indent-tabs-mode: nil; -*-
/////////////////////////////////////////////////////////////////////////////
// Filename: sampleadapter.cc
// File contains test execution connection to implementation under test (IUT)
// and provide sample locking protocol for synchronous input/output delivery.
// Authors:
//    Marius Mikucionis marius@cs.aau.dk
////////////////////////////////////////////////////////////////////////////

#include "sampleadapter.h"
#include "tron/timing.h"
#include <assert.h>

SampleAdapter::SampleAdapter(Reporter *r):
    TestAdapter(r), input_wish(false), output_wish(false)
{
    tron_mutex_init(&wish_m, NULL);
}

SampleAdapter::~SampleAdapter()
{
    tron_mutex_destroy(&wish_m);
}

bool SampleAdapter::tryPerform(int32_t chan, uint16_t n, const int32_t* data)
{
    bool res;
    tron_mutex_lock(&wish_m);
    if (output_wish) res = false;
    else {
	assert(!input_wish);// input_wish to be cleared when input delivered
	input_wish = true;
	res = true;
    }
    tron_mutex_unlock(&wish_m);
    if (res) perform_imp(chan, n, data);
    return res;
}

void SampleAdapter::outputDelivered()
{
    tron_mutex_lock(&wish_m);
    assert(output_wish);
    output_wish = false;
    tron_mutex_unlock(&wish_m);
}

void SampleAdapter::inputDelivered()
{
    tron_mutex_lock(&wish_m);
    assert(input_wish);
    input_wish = false;
    tron_mutex_unlock(&wish_m);
}

bool SampleAdapter::tryReport(int32_t chan, uint16_t n, const int32_t* data)
{
    bool res;
    tron_mutex_lock(&wish_m);
    if (input_wish) res = false;
    else {
	assert(!output_wish);//output_wish to be cleared when output delivered
	output_wish = true;
	res = true;
    }
    tron_mutex_unlock(&wish_m);
    if (res) rep->report_now(rep, chan, n, data);
    return res;
}
