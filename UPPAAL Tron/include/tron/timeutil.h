// -*- mode: C++; c-file-style: "stroustrup"; c-basic-offset: 4; indent-tabs-mode: nil; -*-
///////////////////////////////////////////////////////////////////////////////
// Filename: timeutil.hh
// Utilities for handling time specific data and operations.
//   Author: Marius Mikucionis marius@cs.aau.dk
///////////////////////////////////////////////////////////////////////////////

#ifndef TRON_TIMEUTIL_HH
#define TRON_TIMEUTIL_HH

#ifdef __MINGW32__
#define __CLEANUP_CXX
#define __STL_PTHREADS
#include <pthread.h>
#endif

#include <inttypes.h>
#include <sys/time.h>
#include <iostream>

const struct timespec MIN_TIME = { 0, 0 };
const struct timespec MAX_TIME = { 1<<30, 1<<30 };

inline bool operator<(const struct timespec& t1,
		      const struct timespec& t2)
{
    if (t1.tv_sec < t2.tv_sec) return true;
    else if (t1.tv_sec > t2.tv_sec) return false;
    else if (t1.tv_nsec < t2.tv_nsec) return true;
    else return false;
}

inline bool operator==(const struct timespec& t1,
		       const struct timespec& t2)
{
    if (t1.tv_sec == t2.tv_sec &&
	t1.tv_nsec == t2.tv_nsec) return true;
    else return false;
}

inline bool operator<=(const struct timespec& t1,
		       const struct timespec& t2)
{
    if (t1.tv_sec < t2.tv_sec) return true;
    else if (t1.tv_sec > t2.tv_sec) return false;
    else if (t1.tv_nsec <= t2.tv_nsec) return true;
    else return false;
}

// increments time value by a specified amout of micro-seconds
inline struct timespec& operator+=(struct timespec &now, int64_t delay)
{
    now.tv_nsec = now.tv_nsec + (delay % 1000000L)*1000L;
    if (now.tv_nsec > 1000000000L) {
	now.tv_sec = now.tv_sec + (delay / 1000000L) + 1;
	now.tv_nsec -= 1000000000L;
    } else now.tv_sec = now.tv_sec + (delay / 1000000L);
    return now;
}

inline std::ostream& operator<<(std::ostream& out, const struct timespec& t)
{
    out << t.tv_sec << ".";
    out.fill('0'); out.width(6);
    out << t.tv_nsec/1000 << "s";
    return out;
}

inline int64_t diff2ms(const struct timespec& future,
		       const struct timespec& past)
{
    int64_t result = future.tv_sec - past.tv_sec;
    result *= 1000000L;
    result += (future.tv_nsec - past.tv_nsec)/1000L;
    return result;
}

// compare two time values
inline bool operator<=(const struct timeval &val1,
		       const struct timeval &val2)
{
    if (val1.tv_sec < val2.tv_sec) return true;
    else if (val1.tv_sec == val2.tv_sec) return (val1.tv_usec <= val2.tv_usec);
    else return false;
}

// subtract and compute time value difference
inline struct timeval& operator-=(struct timeval &future,
				  const struct timeval &past)
{
    if (future.tv_usec < past.tv_usec) {
	future.tv_usec = future.tv_usec + 1000000L - past.tv_usec;
	future.tv_sec = future.tv_sec - past.tv_sec - 1;
    } else {
	future.tv_usec = future.tv_usec - past.tv_usec;
	future.tv_sec = future.tv_sec - past.tv_sec;
    }
    return future;
}

// increment time value by a specified amout of micro-seconds
inline struct timeval& operator+=(struct timeval &now, int64_t delay)
{
    now.tv_usec = now.tv_usec + delay % 1000000L;
    if (now.tv_usec > 1000000L) {
	now.tv_sec = now.tv_sec + delay / 1000000L + 1;
	now.tv_usec -= 1000000L;
    } else now.tv_sec = now.tv_sec + delay / 1000000L;
    return now;
}

// convert time value into amount of micro-seconds
inline int64_t timeval2ms(const struct timeval &value)
{
    int64_t result = value.tv_sec;
    result *=1000000;
    return result + value.tv_usec;
}

// compute time value difference in micro-seconds
inline int64_t diff2ms(const struct timeval &future,
		       const struct timeval &past)
{
    int64_t result = future.tv_sec - past.tv_sec;
    result *= 1000000;
    return result + future.tv_usec - past.tv_usec;
}

// display time value to output stream
inline std::ostream& operator<<(std::ostream &out, const struct timeval &value)
{
    out << value.tv_sec << ".";
    out.fill('0'); out.width(6);
    out << value.tv_usec << "s";
    return out;
}

#if defined(__linux) || defined(__MINGW32__)
typedef int64_t hrtime_t;
inline hrtime_t gethrtime()
{
    struct timeval __now;
    hrtime_t __hrnow;
    gettimeofday(&__now, NULL);
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
#endif /* Linux or MinGW */

#endif /* TRON_TIMEUTIL_H */
