Demo of TRON with Smartlamp controller Java application
========================================================

Introduction
------------
This example demonstrates TRON online test against Smartlamp Java application 
with test specification in LightContr.xml UPPAAL model.
Online test consists of running two processes in parallel: Smartlamp and TRON.
The processes communicate via TCP/IP socket protocol. By default example 
setting Smartlamp creates a SocketAdapter and listens for incoming connections 
on server socket, whereas TRON connects to it by SocketAdapter via client 
socket. In virtual time setup, in addition TRON creates virtual clock server 
on another TCP/IP socket whereas Smartlamp connects to it and redirects all 
time-related system calls. Directory tron contains Java source code for both 
TRON SocketAdapter and TRON virtual clock protocols. Directory smartlamp 
contains Smartlamp source code with main method in LightController class.
Smartlamp accepts the following arguments:
-M number
     specifies a mutant identifier, the mutant 0 is considered correct one.
     Please see the source code for specifics about other mutants.
-C hostname portnumber
     specifies the location of a virtual clock server. Application uses host 
     machines clock if this option is omitted.
-N
     suppresses a graphical user interface window.

Requirements
------------
1) Microsoft Windows NT/XP/2003
2) Sun Java 5 JRE (Sun Java 6 is also fine, install Java SDK for development)
3) ANT http://ant.apache.org (optionally for development)

Contents
--------
src       -- Java source files
build     -- Java build class files
dist/smartlamp.jar -- executable jar package
LightContr.xml -- Uppaal model specifying realtime requirements and assumptions.
build.xml -- ANT build script
start-light.bat/sh -- script for starting Smartlamp in Windows/Unix.
start-tron.bat/sh  -- script for starting TRON in Windows/Unix.
start-light-v.bat/sh -- starts Smartlamp in virtual time in Windows/Unix.
start-tron-v.bat/sh -- starts TRON in virtual time in Windows/Unix.
jester* -- config. and scripts for running automated mutant generator Jester.


Quick startup
-------------

1) Start command line window (Start->Run, cmd.exe, enter)
2) go to java directory (type "cd uppaal-tron-RELEASE/java")
3) start another instance of cmd.exe (type "start cmd.exe")

Virtual time case:
4) in one command line window start smart-lamp by typing:
   java -cp dist/smartlamp.jar com.uppaal.smartlamp.Main -C localhost 6521 -M 0
5) in another command line window start TRON by typing:
   ..\tron.exe -Q 6521 -P 10,200 -F 300 -I SocketAdapter -v 9 LightContr.xml -- localhost 9999

Realtime case:
4) in one command line window start smart-lamp by typing:
   java -cp dist/smartlamp.jar com.uppaal.smartlamp.Main -M 0
5) in another command line window start TRON by typing:
   ..\tron.exe -P 10,200 -F 300 -I SocketAdapter -v 9 LightContr.xml -- localhost 9999

CONTRIBUTORS
------------
UPPAAL team http://www.uppaal.com/
Kim G. Larsen kgl@cs.aau.dk
Brian Nielsen bnielsen@cs.aau.dk
Marius Mikucionis marius@cs.aau.dk
