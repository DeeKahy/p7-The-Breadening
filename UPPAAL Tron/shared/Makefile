# Generic console menu-based adapter

# Use GNU Compiler Collection to compile C++ sources:
GCC=g++
# Produce IUT adapters as dynamic libraries:
INCLUDE=-I../include
LDFLAGS=-g -shared -fPIC -DPIC
RM=rm -f

LIBS=console-s button.xml
LOGS=console.log
TRON=../tron
TRONS=../tron -Q log

# example sources:
# Console IUT which interacts with user via console:
CSRC=console.cpp sampleadapter.cpp dialog.cpp
SRCS=$(CSRC) ../include/console.h ../include/sampleadapter.h ../include/dialog.h

# console simulated-time example:
test-console-s: console-s button.xml
	$(TRONS) -H 20,20 -P eager -F 25 -I console-s -v 8 button.xml -- console.log

console-s: $(SRCS)
	$(GCC) $(INCLUDE) $(LDFLAGS) -o $@ $(CSRC)

button.xml:
	cp ../button/button.xml ./

cleanall: clean
	$(RM) $(BINS) $(LOGS)

clean:
	$(RM) $(LIBS) console.log
