package junittest;

import java.io.InputStream;
import java.io.OutputStream;
import java.io.IOException;

public class ProcRunner
{
    Process p;
    StreamProc stdout;
    public ProcRunner(String cmd, OutputStream os) throws IOException
    {
	Runtime r = Runtime.getRuntime();
	ProcessBuilder pb = new ProcessBuilder(cmd).redirectErrorStream(true);
	p = pb.start();
	p.getOutputStream().close();
	stdout = new StreamProc(p.getInputStream(), os);
	stdout.start();
    }
    
    public int getResult() throws InterruptedException {
	int result = p.waitFor();
	stdout.join();
	return result;
    }

    private class StreamProc extends Thread
    {
	InputStream is;
	OutputStream os;
	public StreamProc(InputStream is, OutputStream os)
	{
	    assert(is != null);
	    this.is = is;
	    this.os = os;
	}
	public void run()
	{
	    byte buffer[] = new byte[1024];
	    int len = 1024;
	    try {
		while (len > 0) {
		    len = is.read(buffer);
		    if (os != null && len>0) {
			os.write(buffer, 0, len);
			os.flush();
		    }
		}
		is.close();
	    } catch (IOException ioe) {
		ioe.printStackTrace(System.err);
	    }	    
	}
    }
    public static void main(String args[]) 
    {
	try {
	    String cmd = args[0];
	    System.out.println(cmd);
	    ProcRunner lamp = new ProcRunner(cmd, System.out);
	    System.out.println(lamp.getResult());
	} catch (InterruptedException ie) {
	    ie.printStackTrace(System.err);
	} catch (IOException ioe) {
	    ioe.printStackTrace(System.err);
	}
	System.exit(0);
    }
}
