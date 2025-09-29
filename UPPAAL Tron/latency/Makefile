# CPU latency measurement experiments

# Use GNU Compiler Collection to compile C++ sources:
GCC=g++
# Produce IUT adapters as dynamic libraries:
INCLUDE=-I../include
LDFLAGS=-g -shared -fPIC -DPIC
RM=rm -f

LIBS=ticking pingpong
LOGS=ticking.log ticking.eps pingpong.log pingpong.eps response.log response.eps
TRON=../tron
TRONS=../tron-s

# Ticking simulation observer:
TK_CSRC=ticking.cpp ../shared/sampleadapter.cpp
TK_SRCS=$(TK_CSRC) ticking.h

# Ping-pong IUT which issues ping and expects pong:
PING_CSRC=pingpong.cpp ../shared/sampleadapter.cpp
PING_SRCS=$(PING_CSRC) pingpong.h


# ticking observer:
ticking: $(TK_SRCS)
	$(GCC) $(INCLUDE) $(LDFLAGS) -o $@ $(TK_CSRC)

ticking.log: ticking ticking.xml
	sudo nice -n -2 $(TRON) -F 400 -P eager -l 1000 -v 9 -I $< ticking.xml -- $@ 1000

ticking.eps: ticking.log ticking.R
	R --no-save -q < ticking.R

test-ticking: ticking.eps
	gv $<

# ping-pong server:
pingpong: $(PING_SRCS)
	$(GCC) $(INCLUDE) $(LDFLAGS) -o $@ $(PING_CSRC)

pingpong.log: pingpong pingpong.xml
	sudo nice -n -2 $(TRON) -F 400 -P eager -l 1000 -D driver.log -v 9 -I $< pingpong.xml -- $@

pingpong.eps: pingpong.log ping.R
	R --no-save -q < ping.R

test-ping: pingpong.eps
	gv $<

# ASAP response benchmark:
response.log: pingpong response.xml
	sudo nice -n -2 $(TRON) -F 400 -P random -l 1000 -D driver.log -v 9 -I $< response.xml -- $@

response.eps: response.log response.R
	R --no-save -q < response.R

test-response: response.eps
	gv $<


cleanall: clean
	$(RM) $(BINS) $(LOGS)

clean:
	$(RM) $(LIBS)
