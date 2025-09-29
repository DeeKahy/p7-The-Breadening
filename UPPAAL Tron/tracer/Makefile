# GNU Makefile for running test scripts against TRON using TraceAdapter.

FASTTRACES=simpleStep.trc simple.trc fork.trc intDelay.trc

MEDTRACES=triggers.trc bounds.trc transfer.trc instant.trc one2many.trc stopwatch.trc arrays.trc

SLOWTRACES=response.trc guards.trc

ALLTRCS=$(FASTTRACES) $(MEDTRACES) $(SLOWTRACES)

NEGTRACES=guardNeg.trc invariantNeg.trc forkNeg.trc transferNeg.trc stopwatchNeg.trc
NEGVERDICTS=$(addsuffix .log, $(basename $(NEGTRACES)))

TRONOPTS=-qQ log -F 25 -I TraceAdapter -v 9 -o /dev/stderr -S testsuite.log testsuite.xml -- testsuite.trn > /dev/null

testsuite: intro test-negative test-lazy test-bounded test-random test-eager

intro:
	@echo "--------------- Short Introduction -------------------------"
	@echo "Each test will take a few minutes. You should see progress "
	@echo "indicator in the error stream. If still not sure, monitor "
	@echo "the CPU consumption: tron and bash processes should share "
	@echo "the CPU about evenly. You may leave it running and check "
	@echo "testsuite.log file later."
	@echo "------------------------------------------------------------"
	@echo "Testing negative traces (TRON should produce FAILED verdicts):"

test-lazy: testsuite.xml testsuite.trn $(ALLTRACES)
	@echo "\nTesting lazy option (TRON should wait as much as possible):"
	@./repeater $(FASTTRACES) $(MEDTRACES) | ../tron -P lazy $(TRONOPTS)

test-bounded: testsuite.xml testsuite.trn $(ALLTRACES)
	@echo "\nTesting bounded option (TRON should wait at most 15 or 30 mtu):"
	@./repeater $(FASTTRACES) $(MEDTRACES) | ../tron -P 15,30 $(TRONOPTS)

test-random: testsuite.xml testsuite.trn $(ALLTRACES)
	@echo "\nTesting random option (TRON should wait randomly):"
	@./repeater $(FASTTRACES) $(MEDTRACES) | ../tron -P random $(TRONOPTS)

test-eager: testsuite.xml testsuite.trn $(ALLTRACES)
	@echo "\nTesting eager option (TRON should act ASAP):"
	@./repeater $(MEDTRACES) $(SLOWTRACES) | ../tron -P eager $(TRONOPTS)

test-negative: testsuite.xml testsuite.trn $(NEGVERDICTS)
	@echo "Number of negative tests that PASSED: "`grep -h -c -v FAILED $(NEGVERDICTS) | gawk '{ C=$$C+$$1 } END { print $$C }'`

%Neg.log: %Neg.trc
	@echo -n "Running negative test \"$<\" trace 100 times... "
	@./negativetest.sh $< $@ 100
	@echo "done"

%.dot: %.trn testsuite.xml
	../tron -v 1 -i dot testsuite.xml < $< > $@

%.eps: %.dot
	dot -Teps -o $@ $<

clean:
	$(RM) testsuite.log $(NEGVERDICTS) testsuite.eps testsuite.dot
