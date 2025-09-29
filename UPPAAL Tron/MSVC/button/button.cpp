// button.cpp : Defines the entry point for the DLL application.
//

#include "stdafx.h"
#include "button.h"

#include <iostream>
#include <assert.h>

using namespace std;

#define resolver(target, resolve, name) \
*(void**)(&target) = resolve(name); \
if (target==NULL) { cerr << "AdapterLibrary could not resolve "<< name << endl; exit(EXIT_FAILURE);}

extern "C" {

	BUTTON_API TestAdapter* adapter_new(Reporter* r,
		int argc, const char* args[]) 
    {
        return new MouseButton(r);
    }
    BUTTON_API void adapter_delete(void* adapter)
    {
        delete (MouseButton*)adapter;
    }
    BUTTON_API void adapter_start(TestAdapter* adapter)
    {
        static_cast<MouseButton*>(adapter)->start_imp();
    }
    BUTTON_API void adapter_perform(TestAdapter* adapter, 
                    int32_t chan, uint16_t n, const int32_t data[])
    {
        static_cast<MouseButton*>(adapter)->perform_imp(chan, n, data);
    }
}

const char* MouseButton::inpStr[inpCount] = {"Click"};
const char* MouseButton::outStr[outCount] = {
    "SingleClick", "DoubleClick"};

MouseButton::MouseButton(Reporter* reporter):
    TestAdapter(reporter), stop(false), state(SID_IDLE),
    click(0), singleC(0), doubleC(1), timeUnit(10000)
{
	start = adapter_start;
	perform = adapter_perform;
    if (NULL == (input_m = CreateMutex (NULL, FALSE, NULL))) {
		cerr << "MB: error while creating mutex" << endl;
		exit(EXIT_FAILURE);
    }
	if (NULL == (input_s = CreateSemaphore(NULL, 0, 1000, NULL))) {
		cerr << "MB: error while creating semaphore" << endl;
		exit(EXIT_FAILURE);
	}
	if (NULL == (ack_s = CreateSemaphore(NULL, 0, 1, NULL))) {
		cerr << "MB: error while creating semaphore" << endl;
		exit(EXIT_FAILURE);
	}
	int i, e=0;
	try {
		for (i = 0; i < inpCount; ++i) {
		    e = rep->getInputEncoding(rep, inpStr[i]);
			if (e < 0) throw e;
			else inps[i] = e;
		}
		for (i = 0; i < outCount; ++i) {
			e = rep->getOutputEncoding(rep, outStr[i]);
			if (e < 0) throw e;
			else outs[i] = e;
		}
		e = rep->setTimeUnit(rep, timeUnit);
		if (e < 0) throw e;
		e = rep->setTimeout(rep, 100000);
		if (e < 0) throw e;
	} catch (int error) {
		cerr << "MB: " << rep->getErrorMessage(rep, error) << endl;
		exit(EXIT_FAILURE);
    }
}

MouseButton::~MouseButton()
{
	DWORD dwWaitResult; 
    dwWaitResult = WaitForSingleObject(input_m, INFINITE);
    switch (dwWaitResult) {
	case WAIT_OBJECT_0:
		stop = true;
		if (!ReleaseMutex(input_m)) {
			cerr << "~MB: error releasing mutex" << endl;
			exit(EXIT_FAILURE);
        }
		ReleaseSemaphore(input_s, 1, NULL);
		WaitForSingleObject(ack_s, INFINITE);
		CloseHandle(input_s);
		CloseHandle(ack_s);
		CloseHandle(input_m);
        break;
	default:
		cerr << "~MB: unexpected result of mutex wait: "<<dwWaitResult<<endl;
		exit(EXIT_FAILURE);
    }
}

DWORD WINAPI MBStart(LPVOID lpParameter)
{
	MouseButton* mb = (MouseButton*)lpParameter;
	return (DWORD)mb->threadExecute();
}


inline void MouseButton::start_imp()
{
	DWORD dwWaitResult;
    dwWaitResult = WaitForSingleObject(input_m, INFINITE);
    switch (dwWaitResult) {
	case WAIT_OBJECT_0:
		cerr << "Creating IUT thread" << endl;
		CreateThread(NULL, 0, MBStart, this, 0, &threadId);
		cerr << "Waiting for IUT thread to arrive" << endl;
		WaitForSingleObject(ack_s, INFINITE);
		ReleaseMutex(input_m);
		cerr << "IUT is ready for testing" << endl;
        break;
	default:
		cerr << "~MB: unexpected result of mutex wait: "<<dwWaitResult<<endl;
		exit(EXIT_FAILURE);
    }
}

inline void MouseButton::perform_imp(int32_t chan, uint16_t n, const int32_t data[])
{
	DWORD dwWaitResult;
	//cerr << "Perform is waiting for input lock" << endl;
    dwWaitResult = WaitForSingleObject(input_m, INFINITE);
    switch (dwWaitResult) {
	case WAIT_OBJECT_0:		
	    if (chan == inps[click]) {
			assert(n == 0); // expect no data
			//cerr << "Perform is putting into buffer" << endl;
			input.push(chan); // fill buffer
			ReleaseSemaphore(input_s, 1, NULL); // notify
		} else {
			cerr << "MB: unacceptable action " << chan << ", ignoring.." << endl;
		}
		ReleaseMutex(input_m);
        break;
	default:
		cerr << "MB: unexpected result of mutex wait: "<<dwWaitResult<<endl;
		exit(EXIT_FAILURE);
    }
}

void* MouseButton::threadExecute()
{
	ReleaseSemaphore(ack_s, 1, NULL);
	DWORD dwWaitResult = WaitForSingleObject(input_m, INFINITE);
	assert(dwWaitResult== WAIT_OBJECT_0);

    while (!stop) switch (state) {
	case SID_IDLE:
		while (input.empty() && !stop) {
			ReleaseMutex(input_m);
			WaitForSingleObject(input_s, INFINITE);
			dwWaitResult = WaitForSingleObject(input_m, INFINITE);
			assert(dwWaitResult == WAIT_OBJECT_0);
		}
		if (stop) break;
		assert(!input.empty());
		assert(input.front() == inps[click]);// must have been click...
		input.pop();
		cerr << "IUT got input, updating state" << endl;
		state = SID_WAIT;
		break;
    case SID_WAIT:
		if (input.empty()) {
			ReleaseMutex(input_m);
			dwWaitResult = WaitForSingleObject(input_s, 200);
			WaitForSingleObject(input_m, INFINITE);
		} else dwWaitResult=WAIT_OBJECT_0;

		if (stop) break;

		if (!input.empty()) {
			//assert(dwWaitResult==WAIT_OBJECT_0);
			assert(input.front() == inps[click]);// must have been click...
			input.pop();			
			cerr << "IUT got input, sending DOUBLE" << endl;
			ReleaseMutex(input_m);
			rep->report_now(rep, outs[doubleC], 0, NULL);
			WaitForSingleObject(input_m, INFINITE);			
		} else {
			//assert(dwWaitResult==WAIT_TIMEOUT);
			cerr << "IUT timeout, sending SINGLE" << endl;
			ReleaseMutex(input_m);
			rep->report_now(rep, outs[singleC], 0, NULL);
			WaitForSingleObject(input_m, INFINITE);
		}		
		state = SID_IDLE; 
		break;
    }
    cerr << "MB final state: ";
    switch(state) {
    case SID_IDLE: cerr << "IDLE"; break;
    case SID_WAIT: cerr << "WAIT"; break;
    default: cerr << "(unknown)"; break;
    }
    if (!input.empty()) cerr << " inputs pending: " << input.size();
    cerr << endl;

	ReleaseMutex(input_m);
    ReleaseSemaphore(ack_s, 1, NULL);
    return NULL;
}


BOOL APIENTRY DllMain( HANDLE hModule, 
                       DWORD  ul_reason_for_call, 
                       LPVOID lpReserved
					 )
{
    switch (ul_reason_for_call)
	{
		case DLL_PROCESS_ATTACH:
#ifndef NDEBUG
			cerr << "DllProcessAttach" << endl;
#endif
			break;
		case DLL_THREAD_ATTACH:
#ifndef NDEBUG
			cerr << "DllThreadAttach" << endl;
#endif
			break;
		case DLL_THREAD_DETACH:
#ifndef NDEBUG
			cerr << "DllThreadDetach" << endl;
#endif
			break;
		case DLL_PROCESS_DETACH:
#ifndef NDEBUG
			cerr << "DllProcessDetach" << endl;
#endif
			break;
    }
    return TRUE;
}
