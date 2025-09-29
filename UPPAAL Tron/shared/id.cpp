#include <iostream>
#include <sys/utsname.h>

using namespace std;

int main()
{
  struct utsname info;
  if (uname(&info)>=0) {
    if (strcmp("Linux", info.sysname)==0) {
      cout << info.sysname << "-" << info.machine
	   << "/gcc-" << __GNUC__ << endl;
    } else {
      cout << info.sysname << "-" << info.release << "-" << info.machine
	   << "/gcc-" << __GNUC__ << endl;
    }
    return 0;
  } else {
    cerr << "Error getting host info via uname(2)" << endl;
    return -1;
  }
}
