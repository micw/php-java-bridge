/*-*- mode: Java; tab-width:8 -*-*/

package php.java.script;

import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.Reader;
import java.io.Writer;
import java.util.Map;

import php.java.bridge.PhpProcedureProxy;
import php.java.bridge.SessionFactory;
import php.java.bridge.Util;

/**
 * This class can be used to run a PHP CGI binary. Useful during development when there's no 
 * Apache/IIS or Servlet engine available to run PHP scripts. 
 * @author jostb
 *
 */

public abstract class CGIRunner extends Thread {
	
    protected boolean running = true;
    protected Map env;
    protected SessionFactory ctx;
    protected OutputStream out;
    protected Reader reader;
    
    protected Lock phpScript = new Lock();

    protected class Lock {
	private Object val = null;
	private boolean finish = false;
		
	public synchronized Object getVal() {
	    if(!finish && val==null)
		try {
		    wait();
		} catch (InterruptedException e) {
		    e.printStackTrace();
		}
	    return val;
	}
	public synchronized void setVal(Object val) {
	    this.val = val;
	    notify();
	}
	public synchronized void finish() {
	    finish = true;
	    notify();
	}
    }
    protected CGIRunner(String name, Reader reader, Map env, SessionFactory ctx, OutputStream out) {
	super(name);
    	this.reader = reader;
	this.ctx = ctx;
	this.env = env;
	this.out = out;
    }

    public void run() {
	try {
	    doRun();
	} catch (Exception e) {
	    Util.printStackTrace(e);
	} finally {
	    synchronized(this) {
		notify();
		running = false;
		phpScript.finish();
	    }
	}
    }
    
    protected void doRun() throws IOException {
	int n;    
        Process proc = Util.ProcessWithErrorHandler.start(null, null, env);

	InputStream natIn = null;
	Writer writer = null;
	try {
	natIn = proc.getInputStream();
	OutputStream natOut = proc.getOutputStream();
	char[] cbuf = new char[Util.BUF_SIZE];
	writer = new BufferedWriter(new OutputStreamWriter(natOut));
	while((n = reader.read(cbuf))!=-1) {
	    writer.write(cbuf, 0, n);
	}
	writer.close();
	byte[] buf = new byte[Util.BUF_SIZE];
	Util.parseBody(buf, natIn, out, Util.DEFAULT_HEADER_PARSER);
	try {
        proc.waitFor();
    } catch (InterruptedException e1) {
        // TODO Auto-generated catch block
        e1.printStackTrace();
    }
	} catch (IOException e) {
	    Util.printStackTrace(e);
	    throw e;
	} finally {
	    if(natIn!=null) try {natIn.close();} catch (IOException ex) {/*ignore*/}
	    if(writer!=null) try {writer.close();} catch (IOException ex) {/*ignore*/}
	    proc.destroy();
	}
    }

    /**
     * The PHP script must call this function with the current continuation as an argument.<p>
     * Example:<p>
     * <code>
     * java_context()-&gt;call(java_closure());<br>
     * </code>
     * @param script - The php continuation
     * @throws InterruptedException
     */
    public synchronized void call(PhpProcedureProxy script) throws InterruptedException {
	phpScript.setVal(script);
	wait();
    }
	
    /**
     * One must call this function if one is interested in the php continuation.
     * @return The php continuation.
     * @throws InterruptedException
     */
    public PhpProcedureProxy getPhpScript() throws InterruptedException {
	return (PhpProcedureProxy)phpScript.getVal();
    }
	
    /**
     * This function must be called to release the allocated php continuation.
     *
     */
    public synchronized void release() {
	notify();
	if(running)
	    try {
		wait();
	    } catch (InterruptedException e) {
		Util.printStackTrace(e);
	    }
    }
}
