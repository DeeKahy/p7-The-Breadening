// -*- mode: C++; c-file-style: "stroustrup"; c-basic-offset: 4; indent-tabs-mode: nil; -*-
//////////////////////////////////////////////////////////////////////
// File: iut_console.cc
// Authors:
//   Marius Mikucionis marius@cs.aau.dk
//////////////////////////////////////////////////////////////////////

#include "console.h"
#include "tron/timing.h"

#include <iostream>
#include <fstream>
#include <tr1/unordered_map>
#include <vector>

#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <inttypes.h>
#include <sys/errno.h>

using std::cerr;
using std::endl;

extern "C" {
    TestAdapter* adapter_new(Reporter* rep, int argc, const char* args[])
    {
	if (argc < 2) {
	    cerr<<"Adapter: provide a log filename as adapter parameter"<<endl;
	    return NULL;
	} else return new Console(rep, args[1]);
    }
    void adapter_delete(TestAdapter* ptr){ delete ptr; };
    void adapter_start(TestAdapter* adapter)
    {
        ((Console*)adapter)->start_imp();
    }
    void adapter_perform(TestAdapter* adapter,
                         int32_t chan, uint16_t n, const int32_t data[])
    {
        ((Console*)adapter)->perform_imp(chan, n, data);
    }
}

using std::cerr;
using std::endl;
using std::cout;
using std::vector;

// hash-maps for fast input output event translation:
typedef std::tr1::unordered_map<int32_t, int32_t> intint_t;
typedef std::tr1::unordered_map<const char*, int32_t> str2int_t;
typedef std::tr1::unordered_map<int32_t, const char*> int2str_t;
typedef std::tr1::unordered_map<int32_t, vector<const char*> > int2list_t;
int2str_t inStr, outStr;
str2int_t inInt, outInt;
int2list_t inData, outData;

Console::Console(Reporter* rep, const char* fname):
    SampleAdapter(rep), log(fname), abort(false), timeUnit(1000),
    maxInPars(0), maxOutPars(0)
{
    start = adapter_start;
    perform = adapter_perform;

    if (0 != tron_mutex_init(&input_m, NULL/*use defaults*/)) {
	cerr << "TK: error while init mutex" << endl;
	exit(EXIT_FAILURE);
    }
    tron_cond_init(&msg_c, NULL);
    tron_cond_init(&ack_c, NULL);

    bool ready=false;
    char* name;
    int chNo = 0, id, params, res;
    while (!ready)
	try {
	    switch(dlg.askChar("Console adapter initialization menu:\n"
			       "  1) add input channel\n"
			       "  2) add output channel\n"
			       "  3) set the time unit\n"
			       "  4) set the timeout\n"
			       "  0) start testing.\n"
			       "  q) abort testing.\n"
			       "Your choice: ",
			       "12340qQ")) {
	    case 'q':
	    case 'Q': exit(EXIT_FAILURE); break;
	    case '0': ready = true; break;
	    case '1': // add input channel
		name = dlg.askString("Enter input channel name: ");
		if (strlen(name)>0) {
		    id = rep->getInputEncoding(rep, name);
                    if (id<0) throw id;
		    inStr[id] = name;
		    inInt[name] = id;
		    params = 0;
		    while (strlen(name =
				  dlg.askString("Enter variable name: "))>0) {
			res = rep->addVarToInput(rep, id, name);
                        if (res<0) throw res;
			inData[id].push_back(name);
			params++;
		    }
		    if (params > maxInPars) maxInPars = params;
		}
		break;
	    case '2': // add output channel
		name = dlg.askString("Enter output channel name: ");
		if (strlen(name)>0) {
		    id = rep->getOutputEncoding(rep, name);
                    if (id<0) throw id;
		    outStr[id] = name;
		    outInt[name] = id;
		    params = 0;
		    while (strlen(name =
				  dlg.askString("Enter variable name: "))>0) {
			res = rep->addVarToOutput(rep, id, name);
                        if (res<0) throw res;
			outData[id].push_back(name);
			params++;
		    }
		    if (params > maxOutPars) maxOutPars = params;
		}
		break;
	    case '3': // time units
		res = rep->setTimeUnit(rep,
		    dlg.askInt("Enter time unit length in microseconds: ",
			       1, 1<<31-1));
                if (res<0) throw res;
		break;
	    case '4': // timeout
		res = rep->setTimeout(rep,
		    dlg.askInt("Enter the timeout in time units: ",1,1<<31-1));
                if (res<0) throw res;
		break;
	    default:
		cout << "Console interface bug" << endl;
		exit(EXIT_FAILURE); break;
	    }
	} catch (int err) {
            const char* errMsg = rep->getErrorMessage(rep, err);
	    cerr << "Console: "<< errMsg << " with chNo=" << chNo 
                 << " name=" << name << endl;
	    exit(EXIT_FAILURE);
	}
    input = new int32_t[maxInPars+1]; input[0] = -1;
    output = new int32_t[maxOutPars+1];
}

// we assume that the calling thread is registered in globalclock
Console::~Console()
{
    tron_mutex_lock(&input_m);
    abort = true;
    tron_cond_broadcast(&msg_c);// wake up the listening thread
    tron_mutex_unlock(&input_m);
    joinThread(NULL); // wait for completion

    tron_cond_destroy(&msg_c);
    tron_cond_destroy(&ack_c);
    tron_mutex_destroy(&input_m);
    log.close();
}

inline void Console::start_imp()
{
    input[0] = -1;// fill with smth the input buffer
    startonce();
}

inline void Console::perform_imp(int32_t chan, uint16_t n, const int32_t data[])
{
    tron_mutex_lock(&input_m);
    while (input[0] != 0) // wait for input buffer
	tron_cond_wait(&ack_c, &input_m);
    int2str_t::const_iterator it = inStr.find(chan);
    if (it != inStr.end()) {
	input[0] = chan;
	for (int i=0; i<n; i++) input[i+1] = data[i];
	tron_cond_broadcast(&msg_c);
    } else {
	cerr << "Console: unknown action "<<chan<<", ignoring.." << endl;
    }
    inputDelivered();// must be protected by input_m
    tron_mutex_unlock(&input_m);
}

void* Console::threadExecute()
{
    struct timespec timeout;
    int32_t delay, n;
    char* reply;
    tron_mutex_lock(&input_m);
    input[0] = 0; tron_cond_broadcast(&ack_c);
    while (!abort) {
	tron_gettime(&timeout);
	cout << "Now it is " << timeout << ". ";
	char c;
	switch (c=main_menu()) {
	case '0':
	    delay = dlg.askInt("Enter the delay in microseconds: ",0,1<<30);
	    timeout += delay;
	    cout << "Waiting until " << timeout << "... "; cout.flush();
	    if (ETIMEDOUT == tron_cond_timedwait(&msg_c, &input_m, &timeout))
		cout << "done.\n";
	    else {
		if (abort) break;
		tron_gettime(&timeout);
		cout << "got input ";
		c = input[0];
		vector<const char*> &vars = inData[c];
		n = vars.size();
		cout << inStr[c] <<"(";
		if (vars.size()) cout << vars[0] << "="<< input[1] << ",";
		for (int i=1; i<n; i++) cout << vars[i] << "=" << input[i+1];
		cout << ") at " << timeout <<"\n";
		input[0] = 0; tron_cond_broadcast(&ack_c);
	    }
	    break;
	default: 
	{
	    int chan;
	    char *name;
	    c -= 'a';
	    int2str_t::const_iterator it = outStr.begin();
	    for (int i=0; i<c; i++, it++);
	    chan = it->first;
	    name = (char*) it->second;
	    output[0] = chan;
	    vector<const char*> &vars = outData[chan];
	    n = vars.size();
	    if (n) {
		cout << "Enter data values for " << name << ":\n";
		int i=1;
		for (vector<const char*>::const_iterator it=vars.begin();
		     it != vars.end(); it++, i++){
		    cout << "  " << *it;
		    output[i] = dlg.askInt(" = ", -1<<31, 1<<31);
		}
	    }
	    cout << "Sending output ";
	    cout << name <<"(";
	    if (vars.size()) cout << vars[0] << "="<< output[1];
	    for (int i=1; i<n; i++)
		cout << "," << vars[i] << "=" << output[i+1];
	    cout << ")... "; cout.flush();
	    if (tryReport(output[0], n, output+1))
		cout << "done\n";
	    else {
		cout << "failed\n";
		tron_cond_wait(&msg_c, &input_m);
		if (abort) break;
		tron_gettime(&timeout);
		chan = input[0];
		name = (char*) inStr[chan];
		vector<const char*> &vars = inData[chan];
		n = vars.size();
		cout << "Received input ";
		cout << name <<"(";
		if (vars.size()) cout << *vars.begin() << "="<< input[1];
		int i=1;
		for (vector<const char*>::const_iterator it=vars.begin();
		     it != vars.end(); it++, i++)
		    cout << "," << *it << "=" << input[i];
		cout << ") at " << timeout << '\n';
		input[0] = 0; tron_cond_broadcast(&ack_c);
	    }
	    break;
	}}
    }    
    tron_mutex_unlock(&input_m);
    return NULL;
}

char Console::main_menu()
{
    int n = outStr.size()+2;
    char choices[n];
    cout << "Console adapter outputs menu:\n"
	"  0) delay\n";
    choices[0] = '0';
    int2str_t::const_iterator it = outStr.begin();
    char i=1;
    while (it!=outStr.end()) {
	char c = 'a'-1+i;
	cout << "  " << c << ") " << it->second << "\n";
	choices[i] = c;
	it++; i++;
    }
    choices[i] = 0;
    return dlg.askChar("Your choice: ", choices);
}
