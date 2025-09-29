# Smart lamp light controller example

# Use GNU Compiler Collection to compile C++ sources:
GCC=g++
# Produce IUT adapters as dynamic libraries:
INCLUDE=-I../include
LDFLAGS=-g -shared -fPIC -DPIC
RM=rm -f
EMMA=/usr/share/java/emma.jar
JUNIT=/usr/share/java/junit.jar

BINS=dist/smartlamp.jar

TRON=../tron

# virtual-time example:
test-light-s: $(BINS) $(TRON)
	@xterm -T LightController-console -e java -cp dist/smartlamp.jar com.uppaal.smartlamp.Main -C localhost 6521 -M 0 &
	$(TRON) -Q 6521 -P 10,100 -F 300 -I SocketAdapter \
		-v 9 LightContr.xml -- localhost 9999

test-s: $(TRON)
	$(TRON) -Q 6521 -P 10,100 -F 300 -I SocketAdapter -D light.log \
		-v 9 LightContr.xml -- localhost 9999

light-s: $(BINS)
	java -cp dist/smartlamp.jar com.uppaal.smartlamp.Main -C localhost 6521 -M 0

replay: light.log
	$(TRON) -Q log -P 100,100 -F 300 -I TraceAdapter LightContr.xml -v 8 -- -m < light.log

light-nogui-s: $(BINS)
	java -cp dist/smartlamp.jar com.uppaal.smartlamp.Main -C localhost 6521 -M 0 -N

# real-time example:
test-light: $(BINS) $(TRON)
	@xterm -T LightController-console -e java -cp dist/smartlamp.jar com.uppaal.smartlamp.Main -M 0 &
	$(TRON) -P 10,100 -F 300 -u 4000,4000 -I SocketAdapter \
		-v 10 LightContr.xml -- localhost 9999

test: $(TRON)
	$(TRON) -P 10,100 -F 300 -u 4000,4000 -I SocketAdapter \
		-v 10 LightContr.xml -- localhost 9999

light: $(BINS)
	java -cp $< com.uppaal.smartlamp.Main -M 0


# virtual-time example without GUI:
test-light-nogui-s: $(BINS) $(TRON)
	@xterm -T LightController-console -e java -cp dist/smartlamp.jar com.uppaal.smartlamp.Main -C localhost 6521 -M 0 -N &
	@sync
	$(TRON) -qQ 6521 -P 10,100 -F 300 -I SocketAdapter \
		-v 9 LightContr.xml -- localhost 9999

# real-time example without GUI:
test-light-nogui: $(BINS) $(TRON)
	@xterm -T LightController-console -e java -cp dist/smartlamp.jar com.uppaal.smartlamp.Main -M 0 -N &
	@sync
	$(TRON) -P 10,100 -F 300 -u 4000,4000 -I SocketAdapter \
		-v 10 LightContr.xml -- localhost 9999

# virtual-time example mutant 3 kill with custom test case:
test-light3-s: $(BINS) $(TRON)
	@xterm -T LightController-console -e java -cp dist/smartlamp.jar com.uppaal.smartlamp.Main -C localhost 6521 -M 3 -N &
	@sync
	$(TRON) -qQ 6521 -P 10,100 -F 300 -I SocketAdapter \
		-v 9 LightContrM3.xml -- localhost 9999

# virtual-time example mutant 3 kill with custom test case:
light3-s: $(BINS)
	java -cp dist/smartlamp.jar com.uppaal.smartlamp.Main -C localhost 6521 -M 3 -N

test3-s: $(TRON)
	$(TRON) -qQ 6521 -P 10,100 -F 300 -I SocketAdapter \
		-v 9 LightContrM3.xml -- localhost 9999

# AutoOff lamp example in virtual time:
test-autooff-s: $(BINS) $(TRON)
	@xterm -T LightController-console -e java -cp dist/smartlamp.jar com.uppaal.autoofflamp.Main -C localhost 6521 -M 0 &
	$(TRON) -qQ 6521 -P 100,500 -F 300 -I SocketAdapter -v 9 autoOffLight.xml -- localhost 9999

test-autooff: $(BINS) $(TRON)
	@xterm -T LightController-console -e java -cp dist/smartlamp.jar com.uppaal.autoofflamp.Main -M 0 &
	$(TRON) -P 100,500 -F 300 -I SocketAdapter -v 9 autoOffLight.xml -- localhost 9999

autooff: $(BINS) $(TRON)
	java -cp dist/smartlamp.jar com.uppaal.autoofflamp.Main -M 0 


# Dummy example in virtual time:
test-dummy-s: $(BINS) $(TRON)
	@xterm -T LightController-console -e java -cp dist/smartlamp.jar com.uppaal.dummy.Main -C localhost 6521 -M 0 &
	$(TRON) -qQ 6521 -P 1000,2000 -F 300 -I SocketAdapter -v 9 dummy.xml -- localhost 9999


dist/smartlamp.jar:
	ant jar

coverage.txt: dist/smartlamp.jar
	java -cp $(EMMA) emmarun -jar $<

light-cov: dist/smartlamp.jar
	java -cp $(EMMA) emmarun -r html -sp . -jar $< -C localhost 6521 -M 0 -N

light3-cov: dist/smartlamp.jar
	java -cp $(EMMA) emmarun -r html -sp . -jar $< -C localhost 6521 -M 3 -N

junittest/SmartLampTest.class: junittest/SmartLampTest.java
	javac -cp $(JUNIT):. $<

junit: junittest/SmartLampTest.class junittest/ProcRunner.class $(BINS)
	java -cp $(JUNIT):. junit.swingui.TestRunner junittest.SmartLampTest

jester-test: $(BINS) $(TRON)
	@./jester-tron-test.sh

jester-real-test: $(BINS) $(TRON)
	@./jester-tron-real.sh

jumble-dimmer: junittest/SmartLampTest.class junittest/ProcRunner.class $(BINS)
	rm j
	ja

clean:
	ant clean
	$(RM) $(BINS)


doc:
	mkdir doc
	javadoc -link "http://java.sun.com/j2se/1.5.0/docs/api" -windowtitle "Uppaal TRON Java Adapter Framework" -d doc tron smartlamp 
