// -*- mode: C++; c-file-style: "stroustrup"; c-basic-offset: 4; indent-tabs-mode: nil; -*-
///////////////////////////////////////////////////////////////////////////////
// Filename: simclock.h
// Simulated clock for testing. The clock simulates the time elapse according
// to thread requests for an idle sleep. It also considers releasing the mutex
// while waiting for resource (condition) to proceed.
//   The advantage of such clock is that it ignores the computation time and
// adds up only the idle (sleep) time to "simulated time". You may experience
// long computations in zero "simulated time".
//   Author: Marius Mikucionis marius@cs.aau.dk
///////////////////////////////////////////////////////////////////////////////

#ifndef TRON_EXECUTABLE_H
#define TRON_EXECUTABLE_H

#ifdef __MINGW32__
#define __CLEANUP_CXX
#endif /* MINGW32 */

#define __STL_PTHREADS
//#define _REENTRANT
#include <pthread.h>
#include <assert.h>

extern void* thread_fun(void* ptr);

class Executable {
    friend void* thread_fun(void* ptr);
private:
    int error;
    static int schedPolicy;
    static int priority;
    static struct timespec quantum;
    bool started;
    pthread_t pth;

    void createThread(pthread_t* ptid, bool reg=true);

protected:
    /** this class does not have any meaning on its own, so extend it */
    Executable(): error(0), started(false) {};
    /** make sure to implement */
    virtual void* threadExecute()=0;
    void joinThread(pthread_t ptid, void** result);

public:
    /** blocks the caller thread until this thread finishes. 
     Optionally one can provide a pointer to store the result. */
    void joinThread(void** result=NULL) {
        if (started) joinThread(pth, result);
    }
    /** sends cancel signal to this thread, the signal handler will be called */
    void cancelThread();
    /** starts a new asynchronous thread which is scheduled immediately. */
    void startonce(bool reg = true) {
        assert(!started); started = true;
        createThread(&pth, reg);
    }
    virtual ~Executable(){};
    static int getSchedPolicy(){ return schedPolicy; }
    static int getPriority(){ return priority; }
    static const struct timespec& getQuantum(){ return quantum; }
    /** checks for realtime scheduling policy and sets schedPolicy if available
     * returns true if succeeded in setting RT Round-Robin scheduling policy.
     * returns false if permission denied. See "man sched_setscheduler". */
    static bool checkSetSchedPolicy();
};

/*
////////////////////////////////////////////////////////////////////////
// Generic lock for multithreaded volatile shared objects, taken from:
// http://www.cuj.com/documents/s=7998/cujcexp1902alexandr/
// usage:
// declare your data ar volatile: volatile std::vector<int> vi;
// access via lockp: {lockp<std::vector<int> > pvi(vi,mymutex); pvi->.. ;}
// remember: lock is acquired and release with constructor and destructor
template <typename T>
class lockp {
public:
// Constructors/destructors
    lockp(volatile T& obj, pthread_mutex_t& mtx)
        : pObj_(const_cast<T*>(&obj)), pMtx_(&mtx) {
        int res;
        if (0!=(res=pthread_mutex_lock(pMtx_))){
            std::cerr << "Error on pthread_mutex_lock: " << res << std::endl;
            exit(1);
        }
    }
    ~lockp() {
        int res;
        if (0!=(res=pthread_mutex_unlock(pMtx_))){
            std::cerr << "Error on pthread_mutex_unlock: " << res << std::endl;
            exit(1);
        }
    }
// Pointer behavior
    T& operator*()  { return *pObj_; }
    T* operator->() { return pObj_;  }
private:
    T* pObj_;
    pthread_mutex_t* pMtx_;
    lockp(const lockp&);
    lockp& operator=(const lockp&);
};

*/

#endif /* TRON_EXECUTABLE_H */
