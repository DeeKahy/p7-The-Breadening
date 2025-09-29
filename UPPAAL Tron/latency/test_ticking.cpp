// -*- mode: C++; c-file-style: "stroustrup"; c-basic-offset: 4; indent-tabs-mode: nil; -*-
/**
 * This file is a part of UPPAAL TRON for scheduling latency demonstration.
 * Please send your corrections to the author or report here:
 * http://bugsy.grid.aau.dk/cgi-bin/bugzilla/index.cgi
 *
 * Author: Marius Mikucionis <marius@cs.aau.dk>
 */

#define _REENTRANT
#define __STL_PTHREADS

#include <pthread.h>
#include <iostream>
#include <sys/time.h>
#include <time.h>
#include <assert.h>


using namespace std;

int error;

#ifdef __linux
typedef long long hrtime_t;
inline hrtime_t gethrtime()
{
    struct timeval __now;
    hrtime_t __hrnow;
    error=gettimeofday(&__now, NULL); assert(!error);
    __hrnow = __now.tv_sec;
    __hrnow *= 1000000;
    __hrnow += __now.tv_usec;
    __hrnow *= 1000;
    return __hrnow;
}
inline hrtime_t gethrvtime()
{
    return gethrtime();
}
#endif /* linux */

// increments time value by a specified amout of micro-seconds
inline struct timespec& operator+=(struct timespec &now, int64_t delay)
{
    now.tv_nsec = now.tv_nsec + (delay % 1000000)*1000;
    if (now.tv_nsec > 1000000000) {
        now.tv_sec = now.tv_sec + delay / 1000000 + 1;
        now.tv_nsec -= 1000000000;
    } else now.tv_sec = now.tv_sec + delay / 1000000;
    return now;
}

inline std::ostream& operator<<(std::ostream& out, const struct timespec& t)
{
    out << t.tv_sec << ".";
    out.fill('0'); out.fill(9);
    out << t.tv_nsec << "s";
    return out;
}

int j, mtu=0;
pthread_mutex_t mutex = PTHREAD_MUTEX_INITIALIZER;
pthread_cond_t cond = PTHREAD_COND_INITIALIZER;

int main(int argc, char* args[])
{
    if (argc<2) {
	cout << "Please specify the length of time unit in microseconds"<<endl;
	return -1;
    } else mtu=atoi(args[1]);
    if (mtu<=0) {
	cout << "Time unit length must be positive integer"<<endl;
	return -1;
    }
    hrtime_t started, now;
    struct timespec start, timeout;
    int64_t n = 1, p=250*mtu, t=50*mtu;
    cerr << "p=" << p << " t=" << t << endl;
    cout << mtu << endl;

    error = pthread_mutex_lock(&mutex); assert(!error);

    error = gettimeofday((struct timeval*) &start, NULL); assert(!error);
    start.tv_nsec *= 1000;
    started = gethrtime();

    while (n<120) {
	timeout = start;
	timeout += n*p-t;
	cerr << n << " " << start << " " << timeout << endl;
	error=pthread_cond_timedwait(&cond, &mutex, &timeout); 
	assert(error==ETIMEDOUT);
	now = (gethrtime() - started)/1000;
	cout << now << " " << n << endl;
	n++;
    }

    error=pthread_mutex_unlock(&mutex); assert(!error);
    return 0;
}
