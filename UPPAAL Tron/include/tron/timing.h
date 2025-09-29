// -*- mode: C++; c-file-style: "stroustrup"; c-basic-offset: 4; indent-tabs-mode: nil; -*-
/**
 * Filename: timing.h
 * Time keeping with API similar to POSIX: host, logical and external clocks.
 * Author: Marius Mikucionis marius@cs.aau.dk
 */

#ifndef TRON_TIMING_H
#define TRON_TIMING_H

#ifdef __MINGW32__
#define __CLEANUP_CXX
#endif /* MINGW32 */
#define __STL_PTHREADS
#include <pthread.h>

enum TKMode_t { TKHostClock, TKLogClock, TKExtClock };

extern "C" {
    extern TKMode_t TKMode; // read-only variable for time keeping mode
/**
 * Sets abstract clock functions to use POSIX/Win32 library on current host.
 */
    int setHostClock();
/**
 * Sets the logical clock, where time ellapses only when all threads wait
 */
    int setLogicalClock(bool reg=true);
/**
 * Sets the logical clock and starts clock on socket service on specified port
 */
    int setLogicalClockService(bool reg=true, int port=0x1979);
/**
 * Sets the remote clock connected through TCP/IP socket on host:port
 */
    int setSocketClock(const char* host, int port=0x1979, bool reg=true);
/**
 * Abstract clock functions, set the clock first, then use them.
 */
    extern void (*tron_gettime) (struct timespec*);
    extern int (*tron_thread_create) (pthread_t*, const pthread_attr_t*,
                                      void* (*start)(void*), void* arg);

    extern int (*tron_mutex_init) (pthread_mutex_t*,
                                   const pthread_mutexattr_t*);
    extern int (*tron_mutex_destroy) (pthread_mutex_t*);
    extern int (*tron_mutex_lock) (pthread_mutex_t*);
    extern int (*tron_mutex_unlock) (pthread_mutex_t*);

    extern int (*tron_cond_init)(pthread_cond_t*, const pthread_condattr_t*);
    extern int (*tron_cond_destroy)(pthread_cond_t*);
    extern int (*tron_cond_wait) (pthread_cond_t*, pthread_mutex_t*);
    extern int (*tron_cond_timedwait) (pthread_cond_t*, pthread_mutex_t*,
                                       const struct timespec*);
    extern void (*tron_cond_signal) (pthread_cond_t*);
    extern void (*tron_cond_broadcast) (pthread_cond_t*);

/**
 * pthread_join can be used directly from POSIX library.
 * Let me know if you need other POSIX functions.
 */

/**
 * The following functions are for referencing potentially remote mutexes
 * and conditions (e.g. interprocess/interhost communication in adapter).
 * Short howto:
 *   Only dynamically (in heap) created object should be used, use new.
 *   call tron_mutex_init (or tron_cond_init) to initialize
 *   call publishMutex (or publishCondition) to get an ID for your object.
 *   send the object ID to another process.
 *   another process should retrive the condition by calling getConditionByID.
 * Note: once condition is published it should not be destroyed or deleted,
 * the cleanup will be made by builtin registry (hence only dynamic objects
 * are supported).
 *
 * Functions will return ID=-1 if simulated clock is not set.
 */
    extern int (*tron_mutex_publish) (pthread_mutex_t* mutex);
    extern pthread_mutex_t* (*tron_mutex_get) (int id);

    extern int (*tron_cond_publish) (pthread_cond_t* cond);
    extern pthread_cond_t* (*tron_cond_get) (int id);

    extern void (*tron_thread_deactivate)(void);
    extern void (*tron_thread_activate)(void);
}

#endif
