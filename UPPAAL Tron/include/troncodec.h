// -*- mode: C++; c-file-style: "stroustrup"; c-basic-offset: 4; indent-tabs-mode: nil; -*-
/**
 * File: troncodec.h
 * Contains structures for maintaining codings (maps) between action strings
 * and TRON integers. The structures are filled during preamble parsing and
 * used during trace interpretation.
 * Authors:
 *   Marius Mikucionis marius@cs.aau.dk
 */

#ifndef TRONCODEC_H
#define TRONCODEC_H

#include "tron/timeutil.h"
#include "tron/adapter.h"

#include <inttypes.h>
#include <stdlib.h>
#include <string>
#include <vector>
#include <map>
#include <ext/hash_map>
#include <functional>

class action_t {
public:
    uint16_t size;
    int32_t* data; // array of [chanid][value_1][value_2]..[value_size]
		   // note that array length is size+1
    struct timespec from, till;
    bool good;     // flag used to mark whether the action is suitable
    std::string str; // string representation of this action

    action_t(): size(0), data(NULL), from(MIN_TIME), till(MAX_TIME),
		good(true), str("") {}
    ~action_t() { if (data) { delete [] data; data = NULL; } }

    action_t(const action_t& a): size(a.size), data(a.data),
				 from(a.from), till(a.till) {
	if (data)
	    data = (int32_t*) memcpy(new int32_t[size+1], data, (size+1)*4);
    }
    void operator=(const action_t& a) {
	size = a.size; from = a.from; till = a.till;
	if (data) delete [] data;
	data = a.data;
	if (data)
	    data = (int32_t*) memcpy(new int32_t[size+1], data, (size+1)*4);
    }
    void resize(uint16_t sz) {
	if (data) delete [] data;
	data = new int32_t[sz+1];
    }
};

struct contain {
    const struct action_t &a;
    contain(const struct action_t& act): a(act) {}
    bool operator()(const struct action_t& b) {
	if (a.size != b.size) return false;
	else {
	    if (!std::equal(a.data, a.data+a.size+1, b.data)) return false;
	    if (b.from <= a.from && a.till <= b.till) return true;
	    else return false;
	}
    }
};

inline char* mystrdup(const char* str) {
    return strcpy(new char[strlen(str)+1], str);
}

class TronCodec
{
    struct less_str {
	bool operator()(const char* s1, const char* s2) {
	    return (strcmp(s1, s2)<0);
	}
    };

    typedef __gnu_cxx::hash_map<int32_t, int32_t> intint_t;
    typedef std::map<const char*, int32_t, less_str> str2int_t;
    typedef __gnu_cxx::hash_map<int32_t, const char*> int2str_t;
    typedef std::vector<const char*> vstr_t;
    typedef __gnu_cxx::hash_map<int32_t, vstr_t> int2vstr_t;

    size_t maxInpPars, maxOutPars;
    int2str_t inpStr, outStr;
    str2int_t inpInt, outInt;
    int2vstr_t inpData, outData;
    int64_t mtu, timeout;
    Reporter& rep;

/** Temporary variables */
    int32_t chanId;
    size_t params;
    char *name;

public:
    TronCodec(Reporter& r):
	maxInpPars(0), maxOutPars(0), mtu(0), timeout(0), rep(r) { }

    ~TronCodec() {
	int2str_t::const_iterator c;
	for (c = inpStr.begin(); c != inpStr.end(); c++)
	    delete [] c->second;
	inpStr.clear();
	for (c = outStr.begin(); c != outStr.end(); c++)
	    delete [] c->second;
	outStr.clear();

	int2vstr_t::const_iterator v;
	vstr_t::const_iterator var;
	for (v = inpData.begin(); v != inpData.end(); v++)
	    for (var = v->second.begin(); var != v->second.end(); var++)
		delete [] (*var);
	inpData.clear();

	for (v = outData.begin(); v != outData.end(); v++)
	    for (var = v->second.begin(); var != v->second.end(); var++)
		delete [] (*var);
	outData.clear();
    }

    const int64_t& MTU() { return mtu; }
    const size_t& getMaxInpPars() { return maxInpPars; }
    const size_t& getMaxOutPars() { return maxOutPars; }

/** declares channame to be an input channel */
    void addInpChan(const char* channame) {
	name = mystrdup(channame);
	chanId = rep.getInputEncoding(name);
	inpStr[chanId] = name;
	inpInt[name] = chanId;
	params = 0;
    }

/** declares channame to be an output channel */
    void addOutChan(const char* channame) {
	name  = mystrdup(channame);
	chanId = rep.getOutputEncoding(name);
	outStr[chanId] = name;
	outInt[name] = chanId;
	params = 0;
    }

/** binds a variable to the most recently added (input!) channel */
    void bindInpVar(const char* varname){
	name = mystrdup(varname);
	rep.addVarToInput(chanId, name);
	inpData[chanId].push_back(name);
	if (++params > maxInpPars) maxInpPars = params;
    }
/** binds a variable to the most recently added (output!) channel */
    void bindOutVar(const char* varname){
	name = mystrdup(varname);
	rep.addVarToOutput(chanId, name);
	outData[chanId].push_back(name);
	if (++params > maxOutPars) maxOutPars = params;
    }
/** sets the precision (the length of time unit in microseconds) */
    void setPrecision(const int64_t& _mtu) { mtu = _mtu; rep.setTUnit(mtu); }

/** sets the timeout (in the number of time units) */
    void setTimeout(const int64_t& _timeout){
	timeout = _timeout;
	rep.setTimeout(timeout);
    }
/** encodes channel info into action structure suitable for test adapter */
    bool encodeInpChan(const char* chan, action_t& action) {
	params = 0;
	str2int_t::const_iterator sig_it = inpInt.find(chan);
	if (sig_it != inpInt.end()) {
	    action.str = chan;
	    int2vstr_t::const_iterator data_it = inpData.find(sig_it->second);
	    if (data_it == inpData.end()) {
		action.size = 0;
		action.str += "()";
	    } else {
		action.size = data_it->second.size();
		action.str += '(';
	    }
	    action.data = new int32_t[action.size+1];
	    action.data[0] = sig_it->second;
	    return true;
	} else return false;
    }
/** encodes channel info into action structure suitable for test reporter */
    bool encodeOutChan(const char* chan, action_t& action) {
	params = 0;
	str2int_t::const_iterator sig_it = outInt.find(chan);
	if (sig_it != outInt.end()) {
	    action.str = chan;
	    int2vstr_t::const_iterator data_it = outData.find(sig_it->second);
	    if (data_it == outData.end()) {
		action.size = 0;
		action.str += "()";
	    } else {
		action.size = data_it->second.size();
		action.str += '(';
	    }
	    action.data = new int32_t[action.size+1];
	    action.data[0] = sig_it->second;
	    return true;
	} else return false;
    }
/** adds variable value into action structure */
    bool addValue(const char* value, action_t& action) {
	if (++params <= action.size) {
	    if (params>1) action.str += ',';
	    action.str += value;
	    if (params == action.size) action.str += ')';
	    action.data[params] = atoi(value);
	    return true;
	} else return false;
    }
/** decodes the input into friendly action structure */
    bool decodeInp(const int32_t& c, const uint16_t& n, const int32_t d[],
		   action_t& action) {
	int2str_t::const_iterator it = inpStr.find(c);
	if (it != inpStr.end()) {
	    action.data[0] = c; action.size = n;
	    if (n) memcpy(action.data+1, d, n*4);
	    action.str = it->second;
	    action.str += '(';
	    for (int i = 0; i<n; i++) {
		if (i>0) action.str += ',';
		char buffer[12];
		snprintf(buffer, sizeof(buffer), "%d", d[i]);
		action.str += buffer;
	    }
	    action.str += ')';
	    return true;
	} else return false;
    }
};

/** random number from [lower, upper] */
inline int64_t rand(const int64_t& lower, const int64_t& upper)
{
    return lower+(int64_t)(drand48()*(upper-lower+1));
}

#endif
