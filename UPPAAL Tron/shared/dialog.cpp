// -*- mode: C++; c-file-style: "stroustrup"; c-basic-offset: 4; indent-tabs-mode: nil; -*-
/**
 * File: dialog.cc
 * Authors:
 *   Marius Mikucionis marius@cs.aau.dk
 */

#include "dialog.h"

#include <string.h>
#include <stdlib.h>

using std::endl;
using std::istream;
using std::ostream;

char* Dialog::askString(const char* msg)
{
    int max = max_reply; // just to make it thread-safe
    char buffer[max];
    buffer[max-1] = 0;
    os << msg;
    is.getline(buffer, max-1);
    return strcpy(new char[strlen(buffer)+1], buffer);
}

char Dialog::askChar(const char* msg, const char* choices)
{
    char* reply;
    char c;
    while (true) {
	reply = askString(msg);
	c = reply[strlen(reply)-1]; // take the last character
	delete [] reply;
	if (choices==NULL) return c;
	else for (int i=0; i<strlen(choices); i++)
	  if (choices[i]==c) return c;
	os << "Bad input '"<< c << "', good inputs are: " << choices << endl;
    }
}

int Dialog::askInt(const char* msg, int min, int max)
{
    char* reply;
    int res;
    while (true) {
      reply = askString(msg);
      res = atoi(reply);
      delete [] reply;
      if (res>=min && res<=max) return res;
      else {
	os << "Bad input ("<< res << "), good inputs belong to [" 
	   << min << "," << max << "]" << endl;
      }
    }
}

#ifdef DIALOG_TEST

using std::cout;

int main()
{
    Dialog dlg;
    char* reply;
    char c;
    int i;

    reply = dlg.askString("Pick some random line: ");
    cout << "You chose \"" << reply << "\", good." << endl;
    
    i = dlg.askInt("Type in some interesting number: ", 7, 11);
    cout << "You chose " << i << ", good." << endl;

    c = dlg.askChar("Now guess a letter: ", "q");
    cout << "You chose '" << c << "', good." << endl;
    return 0;
}

#endif
