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
import java.io.File;
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
import php.java.bridge.Util.Process;

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
	
    protected boolean running = true;
    protected Map env;
    protected OutputStream out, err;
    protected Reader reader;
    
    protected Lock phpScript = new Lock();
    protected HeaderParser headerParser;

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
    private static class ProcessWithErrorHandler extends Util.ProcessWithErrorHandler {
	protected ProcessWithErrorHandler(String[] args, File homeDir, Map env, boolean tryOtherLocations, OutputStream err) throws IOException {
	    super(args, homeDir, env, tryOtherLocations, true, err);
	}
	protected String checkError(String s) {
	    return s;
	}
        public static Process start(String[] args, File homeDir, Map env, boolean tryOtherLocations, OutputStream err) throws IOException {
            ProcessWithErrorHandler proc = new ProcessWithErrorHandler(args, homeDir, env, tryOtherLocations, err);
            proc.start();
            return proc;
        }	
    }
    public void run() {
	try {
	    doRun();
	} catch (IOException e) {
	    Util.printStackTrace(e);
	    phpScript.val = e;
	} catch (Util.ProcessWithErrorHandler.PhpException e1) {
	    phpScript.val = e1;	    
	} catch (Exception ex) {
	    Util.printStackTrace(ex);
	} finally {
	    synchronized(this) {
		notify();
		running = false;
		phpScript.finish();
	    }
	}
    }
    private Writer writer;
    protected void doRun() throws IOException {
        Process proc = ProcessWithErrorHandler.start(new String[] {null, "-d", "allow_url_include=On"}, null, env, true, err);

	InputStream natIn = null;
	try {
	natIn = proc.getInputStream();
	OutputStream natOut = proc.getOutputStream();
	writer = new BufferedWriter(new OutputStreamWriter(natOut));
	(new Thread() { // write the script asynchronously to avoid deadlock
	    public void doRun() throws IOException {
		char[] cbuf = new char[Util.BUF_SIZE]; int n;    
		while((n = reader.read(cbuf))!=-1) writer.write(cbuf, 0, n);
		try { writer.close(); } catch (IOException ex) {/*ignore*/}
	    }
	    public void run() { try {doRun(); } catch (IOException e) {Util.printStackTrace(e); }}
	}).start();
	byte[] buf = new byte[Util.BUF_SIZE];
	Util.parseBody(buf, natIn, out, headerParser);
	try {
        proc.waitFor();
    } catch (InterruptedException e1) {
        Util.printStackTrace(e1);
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
     * @throws InterruptedException
     */
    public PhpProcedureProxy getPhpScript() throws Exception {
        Object val = phpScript.getVal(); 
        try {
            return (PhpProcedureProxy)val;
        } catch (ClassCastException e) {
            throw (Exception)val; 
        }
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
	try { out.close(); } catch (IOException e) {/*ignore*/}
	try { err.close(); } catch (IOException e) {/*ignore*/}
    }
}
