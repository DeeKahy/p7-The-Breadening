#!/bin/sh
# This script runs negative test specified number of times.
# It takes trace file and produces test verdict to a log file.
# Expects three arguments: trace_file_name log_file_name count
# Returns exit status 0 if test failed (i.e. tron exited with 2=FAILED)
#    else returns 1 (i.e. tron exited with 0=PASSED or 3=INCONCL)

c=0
while [ $c != $3 ] ; do
    ../tron -qQ log -H 20,20 -P random -F 25 -I TraceAdapter -v 8 -o /dev/null -S $2 testsuite.xml -- testsuite.trn > /dev/null < $1
    if [ $? != 2 ] ; then exit 1 ; fi
    c=$(($c+1))
done

exit 0
