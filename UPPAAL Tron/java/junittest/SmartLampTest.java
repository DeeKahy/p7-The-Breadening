package junittest;

import java.io.IOException;

import junit.framework.*;

public class SmartLampTest extends TestCase 
{
    public static final String tronCmd = "./start-test-v.sh";
    public static final String lampCmd = "./start-light-v.sh";
    public void testSmartLamp() {
	try {
	    ProcRunner tron = new ProcRunner(tronCmd, System.out);
	    ProcRunner lamp = new ProcRunner(lampCmd, null);
	    int lampres = lamp.getResult();
	    int tronres = tron.getResult();
	    System.out.println("Lamp="+lampres+" Tron="+tronres);
	    assertTrue(tronres==0);
	} catch (InterruptedException ie) {
	    ie.printStackTrace(System.err);
	    fail(ie.toString());
	} catch (IOException ioe) {
	    ioe.printStackTrace(System.err);
	    fail(ioe.toString());	    
	}
    }
    public static Test suite() {
        return new TestSuite(SmartLampTest.class);
    }

    public static void main(String args[]) {
	junit.textui.TestRunner.run(suite());
    }
}
