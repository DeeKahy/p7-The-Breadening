Uppaal for Testing Real-time systems ONline v.1.5 for Windows
-------------------------------------------------------------

Uppaal License
--------------

    We (the licensee) understand that UPPAAL includes but is not
    limited to the programs: uppaal2k.jar, uppaal, uppaal.bat, server, 
    socketserver, atg2ugi, atg2ta, atg2hs2ta, hs2ta, checkta, simta, 
    verifyta, tron, uppaal, and xuppaal and that they are supplied 
    "as is", without expressed or implied warranty. We agree on the 
    following:

       1. You (the licensers) do not have any obligation to provide
          any maintenance or consulting help with respect to UPPAAL.

       2. You neither have any responsibility for the correctness of
          systems verified using UPPAAL, nor for the correctness of
          UPPAAL itself.

       3. We will never distribute or modify any part of the UPPAAL
          code (i.e. the source code and the object code) without a
          written permission of Wang Yi (Uppsala University) or
	  Kim G Larsen (Aalborg University).

       4. We will only use UPPAAL for non-profit research
          purposes. This implies that neither UPPAAL nor any part of
          its code should be used or modified for any commercial
          software product.

    In the event that you should release new versions of UPPAAL to us,
    we agree that they will also fall under all of these terms.


NEW in this Release
-------------------
 - Revised virtual clock framework.
 - Compiled pthread-win32 library from repository (2.9 release preview).
 - Fixed deadlock exhibited on SMP setup.
 - Cleaned up restructured java examples.
 - Added Jester script for automatic mutations in java examples.

Software requirements
---------------------
 - Platform: Windows NT/2000/XP/2003
 - Optional: Java 5 (or 6) for java examples, JDK and ANT for development.
 - Optional: Jester for automated mutant generation.
 - Optionally: Microsoft Visual Studio (C/C++ IDE) for MSVC example.
 - Optionally: Cygwin and MinGW environment for tracer example.
 - Optionally: DOT/graphviz for displaying the signal flow and
   partitioning of the system model (see "tron -i dot").


Contents
--------
 - tron.exe
     Uppaal TRON executable binary for Windows 32bit API.
 - pthreadGCE2.dll
     POSIX thread library for Win32 GNU Compiler Environment,
     compiled from http://sourceware.org/pthreads-win32/.
 - MSVC
     Microsoft Visual Studio examples. Currently only simple button
     with C library adapter is available in real-time.
 - java
     Java examples. Currently contains smart-lamp controller example
     with Java Socket adapter, available in real-time and virtual time.
 - tracer
     A test suite against TRON, testing a few Uppaal modeling features
     using TraceAdapter interface (in virtual time). Serves as a
     TraceAdapter example which implements textual communication with 
     TRON. Assumes that GNU make and bash are available for manipulation 
     of various traces in different setups (see tracer/Makefile).


Brief Usage
-----------
 - Check the contents of java/README.TXT on how to run smart-lamp
   example. There's also javadoc available: java/doc/index.html.

 - Try clicking on MSVC/button/start_test.bat, load the project in
   Microsoft Visual Studio and modify to suit your needs.

 - To see the signal flow of system model and partitioning in testing
   use the "-i" option ("-v 3" is useful when experimenting).
   The following produces the system model signal flow diagram:
   "..\tron -i dot LightContr.xml -I TraceAdapter < LightContr.trn > signalflow.dot"
   The following will produce PNG picture of the signal flow diagram:
   "dot signalflow.dot -Tpng -o signalflow.png"
   The legend of the diagram: environment-controlled entities are in 
   green, IUT entities are in blue; ellipse means a process, diamond 
   -- a channel, rectangle -- data variable. Arrows denote a signal flow.

For a detailed usage look at output of "tron -h", check the related
examples, read publications about TRON/T-Uppaal. Perhaps there'll be
thesis about it soon. You are encouraged to try the Linux version
which has a few more examples.

If you are planning to use it for your research paper, I would be more
than happy to help.


Bugreports are welcome at:
    http://bugsy.grid.aau.dk/cgi-bin/bugzilla/index.cgi

Written, built and maintained by 
    Marius Mikucionis <marius@cs.aau.dk>

in close collaboration with:
    Brian Nielsen <bnielsen@cs.aau.dk>,
    Kim Guldstrand Larsen <kgl@cs.aau.dk>

and last but not least, based on the work by
    Uppaal Team <uppaal@list.it.uu.se>, http://www.uppaal.com/
