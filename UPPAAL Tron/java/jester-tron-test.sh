#!/bin/bash

# run smartlamp and remember its PID:
java -cp dist/smartlamp.jar com.uppaal.smartlamp.Main -C localhost 6521 -M 0 -N > /dev/null 2>&1 &
lamp=$! 
sync

# run TRON and remember its PID:
../tron -qQ 6521 -P 10,100 -F 300 -I SocketAdapter -v 0 LightContr.xml -- localhost 9999 2> /dev/null &
tron=$! 

# count 40sec at most (usually it takes 20s), adjust if PC is slow:
c=0
while [ "$c" != "40" ] ; do
    if [ -e "/proc/$tron" ] ; then
	sleep 1
    else
	break
    fi
    c=$(( $c + 1 ))
done

# allow 1s for lamp to cleanup and terminate
sleep 1

# if test has not finished by now, assume deadlock and kill lamp:
if [ -e "/proc/$lamp" ] ; then
    kill -9 $lamp 
fi

# allow 1sec for TRON to cleanup and terminate
sleep 1

# if TRON has not finished by now, assume deadlock and kill TRON:
if [ -e "/proc/$tron" ] ; then
    echo "Timeout, killing TRON"
    kill -9 $tron
fi

# allow socket cleanup in OS, so that it would not spoil the next run
sleep 1
