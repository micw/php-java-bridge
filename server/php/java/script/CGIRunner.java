/*-*- mode: Java; tab-width:8 -*-*/

package php.java.script;

/*
 * Copyright (C) 2003-2007 Jost Boekemeier
 *
 * Permission is hereby granted, free of charge, to any person obtaining a
 * copy of this software and associated documentation files (the "Software"),
 * to deal in the Software without restriction, including without limitation
 * the rights to use, copy, modify, merge, publish, distribute, sublicense,
 * and/or sell copies of the Software, and to permit persons to whom the
 * Software is furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT.  IN NO EVENT SHALL
 * THE COPYRIGHT HOLDER(S) OR AUTHOR(S) BE LIABLE FOR ANY CLAIM, DAMAGES OR
 * OTHER LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE,
 * ARISING FROM, OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR
 * OTHER DEALINGS IN THE SOFTWARE.
 */

import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.Reader;
import java.io.Writer;
import java.util.Map;

import php.java.bridge.PhpProcedureProxy;
import php.java.bridge.Util;
import php.java.bridge.Util.HeaderParser;

/**
 * This class can be used to run a PHP CGI binary. Used only when
 * running local php scripts.  To allocate and invoke remote scripts
 * please use a HttpProxy and a URLReader instead.
 *  
 * @author jostb
 *
 * @see php.java.bridge.http.HttpServer
 * @see php.java.script.URLReader
 * @see php.java.script.HttpProxy
 */

public abstract class CGIRunner extends Thread {
	
    protected Map env;
    protected OutputStream out, err;
    protected Reader reader;
    
    protected ScriptLock scriptLock = new ScriptLock();
    protected Lock phpScript = new Lock();
    protected HeaderParser headerParser;

    // used to wait for the script to terminate
    private static class ScriptLock {
	    private boolean running = true;
	    public synchronized void waitForRunner () throws InterruptedException {
		    if (running)
                        wait();
	    }
	    public synchronized void finish () {
		    running = false;
		    notify();
	    }
    }
    
    // used to wait for the cont.call(cont) call from the script
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
    protected CGIRunner(String name, Reader reader, Map env, OutputStream out, OutputStream err, HeaderParser headerParser) {
	super(name);
    	this.reader = reader;
	this.env = env;
	this.out = out;
	this.err = err;
	this.headerParser = headerParser;
    }
    public void run() {
	try {
	    doRun();
	} catch (IOException e) {
	    Util.printStackTrace(e);
	    phpScript.val = e;
	} catch (Util.Process.PhpException e1) {
	    phpScript.val = e1;	    
	} catch (Exception ex) {
	    Util.printStackTrace(ex);
        } finally {
	    phpScript.finish();
	    scriptLock.finish();
	}
    }
    private Writer writer;
    protected void doRun() throws IOException, Util.Process.PhpException {
        Util.Process proc = Util.ProcessWithErrorHandler.start(new String[] {null, "-d", "allow_url_include=On"}, null, env, true, true, err);

	InputStream natIn = null;
	try {
	natIn = proc.getInputStream();
	OutputStream natOut = proc.getOutputStream();
	writer = new BufferedWriter(new OutputStreamWriter(natOut));

	(new Thread() { // write the script asynchronously to avoid deadlock
	    public void doRun() throws IOException {
		char[] cbuf = new char[Util.BUF_SIZE]; 
		int n;    
		while((n = reader.read(cbuf))!=-1) 
			writer.write(cbuf, 0, n);
	    }
	    public void run() { 
		    try {
			    doRun(); 
		    } catch (IOException e) {
			    Util.printStackTrace(e);
		    } finally {
			    try {
				    writer.close();
			    } catch (IOException ex) {
				    /*ignore*/
			    }
		    }
	    }
	}).start();

	byte[] buf = new byte[Util.BUF_SIZE];
	Util.parseBody(buf, natIn, out, headerParser);
	proc.waitFor();
	} catch (IOException e) {
	    Util.printStackTrace(e);
	    throw e;
	} catch (InterruptedException e) {
		/*ignore*/
	} finally {
	    if(natIn!=null) try {natIn.close();} catch (IOException ex) {/*ignore*/}
	    try { out.flush(); } catch (IOException e) {/*ignore*/}
	    try { err.flush(); } catch (IOException e) {/*ignore*/}
	    try {proc.destroy(); } catch (Exception e) { Util.printStackTrace(e); }
	}
	
	proc.checkError();
    }

    /**
     * The PHP script must call this function with the current
     * continuation as an argument.<p>
     * 
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
     */
    public PhpProcedureProxy getPhpScript() throws Exception {
        Object val = phpScript.getVal(); 
        try {
            return (PhpProcedureProxy)val;
        } catch (ClassCastException e) {
            throw (Exception)val; 
        }
    }

    /* Release the cont.call(cont) from PHP. After that the PHP script may terminate */
    private synchronized void releaseContinuation () {
	notify();
    }
    /**
     * This function must be called to release the allocated php continuation.
     * Note that simply calling this method does not guarantee that
     * the script is finished, as the ContextRunner may still produce output.
     * Use contextFactory.waitFor() to wait for the script to terminate.
     * @throws InterruptedException 
     *
     */
    public void release() throws InterruptedException {
	    releaseContinuation();
	    scriptLock.waitForRunner();
    }
}
