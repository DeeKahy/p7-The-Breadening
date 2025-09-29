# This is GNU Makefile for running UPPAAL TRON examples.

DIRS=button latency shared java tracer train-gate
VERSION=1.4a2

help: tron
	@./tron -V
	@echo
	@echo "There are the following examples as make targets available:"
	@echo "  test-button-s -- the mouse button test in simulated time."
	@echo "  test-button   -- the mouse button test in real time."
	@echo "  test-train-s  -- the train-gate controller test in simulated time."
	@echo "  test-train    -- the train-gate controller test in real time."
	@echo "  test-ping     -- communication latency evaluation experiment."
	@echo "  test-ticking  -- environment emulation latency experiment."
	@echo "  test-light-s  -- smart-lamp Java applet test in simulated time."
	@echo "  test-light    -- smart-lamp Java applet test in real time."
	@echo "  test-light-nogui-s -- smart-lamp without GUI in simulated time."
	@echo "  test-light-nogui -- smart-lamp wihtout GUI in real time."
	@echo "  testsuite     -- testsuite testing UPPAAL model features."
	@echo "Type \"make test-light-s\" to run smart-lamp Java applet test."
	@echo
	@echo "Please report bugs and errors to maintainer: marius@cs.aau.dk"
	@echo "or use bugzilla: http://bugsy.grid.aau.dk/cgi-bin/bugzilla/index.cgi"
	@echo "                     Many thanks!"
	@echo "                                             Marius Mikucionis"

tron:
	@if [ -e `uname`-tron ] ; \
	then ln -s `uname`-tron tron ; \
	else echo "No tron binary for this architecture, ask maintainer."; fi

test-button-s: tron
	@cd button ; make test-button-s ; cd ..

test-button: tron
	@cd button ; make test-button ; cd ..

test-train-s: tron
	@cd train-gate ; make test-timedgate-s ; cd ..

test-train: tron
	@cd train-gate ; make test-timedgate ; cd ..

test-ping: tron
	@cd latency ; make test-ping ; cd ..

test-ticking: tron
	@cd latency ; make test-ticking ; cd ..

test-light-s: tron
	@cd java ; make test-light-s ; cd ..

test-light: tron
	@cd java ; make test-light ; cd ..

test-light-nogui-s: tron
	@cd java ; make test-light-nogui-s ; cd ..

test-light-nogui: tron
	@cd java ; make test-light-nogui ; cd ..

testsuite: tron
	@cd tracer ; make testsuite ; cd ..

clean:
	rm -f tron
	@for dir in $(DIRS) ; do cd $$dir ; make cleanall ; cd .. ; done

`uname`-tron: ../tron
	cp $< $@
