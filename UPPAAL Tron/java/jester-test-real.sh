#!/bin/bash

JESTERHOME=/opt/local/simple-jester-1.1

# ClassPath: include jester (and current dir for jester configuration files)
CP=$JESTERHOME/simple-jester.jar:.:$JESTERHOME

# run Jester mutant test:
java -cp $CP jester.TestTester "make jester-real-test" src/com/uppaal/smartlamp/Dimmer.java src/com/uppaal/smartlamp/DimmerM0.java src/com/uppaal/smartlamp/SmartLamp.java
res=$?
# make HTML report:
python $JESTERHOME/makeWebView.py -s -z

# display HTML report:
if [ $res == 0 ] ; then
    firefox jester.html &
fi
