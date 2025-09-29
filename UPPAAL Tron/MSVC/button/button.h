/****
 * Mouse button IUT in MSVC dll example for UPPAAL TRON.
 * Author: Marius Mikucionis marius@cs.aau.dk
 */


// The following ifdef block is the standard way of creating macros which make exporting 
// from a DLL simpler. All files within this DLL are compiled with the BUTTON_EXPORTS
// symbol defined on the command line. this symbol should not be defined on any project
// that uses this DLL. This way any other project whose source files include this file see 
// BUTTON_API functions as being imported from a DLL, wheras this DLL sees symbols
// defined with this macro as being exported.
#ifdef BUTTON_EXPORTS
#define BUTTON_API __declspec(dllexport)
#else
#define BUTTON_API __declspec(dllimport)
#endif

#include "adapter.h"

#include <queue>

// MouseClick sample implementation which outputs DblClick immediately
// when two successive clicks received within 200ms.

const uint16_t inpCount = 1, outCount = 2;

class MouseButton: public TestAdapter
{
	friend DWORD WINAPI MBStart(LPVOID lpParameter);
public:
    MouseButton(Reporter* reporter);
    virtual ~MouseButton();
    void start_imp();
    void perform_imp(int32_t chan, uint16_t n, const int32_t data[]);

private:
    volatile bool stop;
    volatile enum { SID_IDLE, SID_WAIT } state;

    /** Testing interface data variables: */
	std::queue<int32_t> input; // input buffer
    static const char* inpStr[inpCount];
    static const char* outStr[outCount];
    int32_t inps[inpCount], outs[outCount];
    uint16_t click, singleC, doubleC;
    int32_t timeUnit;

    /** Thread synchronization variables: */
    HANDLE input_m; // click transfer section mutex
	HANDLE input_s; // click queue semaphore
	HANDLE ack_s; // acknowledgement for thread creation and termination
	DWORD threadId;
    virtual void* threadExecute();
};




BUTTON_API int fnButton(void);

