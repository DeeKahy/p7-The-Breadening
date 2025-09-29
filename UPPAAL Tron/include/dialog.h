// -*- mode: C++; c-file-style: "stroustrup"; c-basic-offset: 4; indent-tabs-mode: nil; -*-

#ifndef DIALOG_HH
#define DIALOG_HH

/**
 * File: dialog.hh
 * Purpose: generic interface for dialog with human or text-based API.
 * The default implementation assumes console interface with human.
 * The default implementation is thread-safe, hope the others are too.
 * Authors: Marius Mikucionis marius@cs.aau.dk
 */

#include <iostream>

class Dialog 
{
protected:
    int max_reply;
    std::istream &is;
    std::ostream &os;  
    
public:
    
    Dialog(std::istream &in=std::cin, std::ostream &out=std::cout, 
	   int max_reply_string_length=128): 
	is(in), os(out), max_reply(max_reply_string_length) {};
    
    virtual ~Dialog() {};
    
    virtual void setMaxReply(int max_reply_string_length) 
	{ max_reply = max_reply_string_length; }
    
    /**
     * Outputs msg and reads a line. Returns new string containing line.
     * The returned string has to be disposed via delete [].
     */
    virtual char* askString(const char* msg);
    
    /**
     * Outputs msg, reads a line and returns the last character in that line.
     * The character must be contained in choices string, if it is not, 
     * function repeats the question.
     * If choices is NULL then no constrains are applied.
     */
    virtual char askChar(const char* msg, const char* choices);
    
    /**
     * Outputs msg, reads a line and returns the integer interpretation.
     * The integer must be within min and max inclusive, otherwise function 
     * repeats the question.
     */
    virtual int askInt(const char* msg, int min, int max);
};

#endif
