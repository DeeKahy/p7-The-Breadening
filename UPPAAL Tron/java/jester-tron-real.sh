#!/bin/bash

# run smartlamp and remember its PID:
sudo nice -n -3 java -cp dist/smartlamp.jar com.uppaal.smartlamp.Main -M 0 -N > /dev/null 2>&1 &
lamp=$! 
sync

# run TRON and remember its PID:
sudo nice -n -3 ../tron -qS verdict.txt -P 10,100 -F 300 -I SocketAdapter -v 0 LightContr.xml -- localhost 9999 2> /dev/null

# allow 1s for lamp to cleanup and terminate
sleep 1

# if test has not finished by now, assume deadlock and kill lamp:
if [ -e "/proc/$lamp" ] ; then
    kill -9 $lamp 
fi

# allow socket cleanup in OS, so that it would not spoil the next run
sleep 1
