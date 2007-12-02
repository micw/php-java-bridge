/*-*- mode: Java; tab-width:8 -*-*/

package php.java.script;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.Reader;
import java.io.StringReader;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.Map;

import javax.script.ScriptContext;
import javax.script.ScriptException;
import javax.servlet.Servlet;
import javax.servlet.ServletContext;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import php.java.bridge.Util;
import php.java.bridge.http.IContext;
import php.java.servlet.CGIServlet;

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


public class PhpServletScriptEngine extends PhpScriptEngine {
    protected Servlet servlet;
    protected ServletContext servletCtx;
    protected HttpServletRequest req;
    protected HttpServletResponse res;
    
    protected File path;
    protected URL url;
   
    public PhpServletScriptEngine(Servlet servlet, 
				  ServletContext ctx, 
				  HttpServletRequest req, 
				  HttpServletResponse res) throws MalformedURLException {
	this.servlet = servlet;
	this.servletCtx = ctx;
	this.req = req;
	this.res = res;
	    
	url = new java.net.URL((req.getRequestURL().toString()));
	path = new File(CGIServlet.getRealPath(ctx, ""));
    }
    /**
     * Create a new context ID and a environment map which we send to the client.
     *
     */
    protected void setNewContextFactory() {
        IPhpScriptContext context = (IPhpScriptContext)getContext(); 
	env = (Map) this.processEnvironment.clone();

	ctx = PhpServletContextFactory.addNew((IContext)context, servlet, servletCtx, req, res);
    	
	/* send the session context now, otherwise the client has to 
	 * call handleRedirectConnection */
	env.put("X_JAVABRIDGE_CONTEXT", ctx.getId());
	/* the client should connect back to us */
	StringBuffer buf = new StringBuffer("h:");
	buf.append(Util.getHostAddress());
	buf.append(':');
	buf.append(PhpScriptContext.getHttpServer().getSocket().getSocketName());
	env.put("X_JAVABRIDGE_OVERRIDE_HOSTS",buf.toString());

    }
    
    protected Object eval(Reader reader, ScriptContext context, String name) throws ScriptException {
    	File tempfile = null;
    	FileOutputStream fout = null;
        Reader localReader = null;
  	if(reader==null) return null;
  	
  	setNewContextFactory();
        setName(name);
        
        try {
	    fout = new FileOutputStream(tempfile=File.createTempFile("tempfile", ".php", path));
	    OutputStreamWriter writer = new OutputStreamWriter(fout);
	    char[] cbuf = new char[Util.BUF_SIZE];
	    int length;

	    /* header: <? require_once("http://localhost:<ourPort>/JavaBridge/java/Java.inc"); ?> */
            localReader = new StringReader("<?php if(!extension_loaded('java')) {(@include_once(\"java/Java.inc\"));}?>");
	    while((length=localReader.read(cbuf, 0, cbuf.length))>0) 
		writer.write(cbuf, 0, length);
            try { localReader.close(); localReader=null;} catch (IOException e) {throw this.scriptException = new PhpScriptException("Could not close header", e);}

	    while((length=reader.read(cbuf, 0, cbuf.length))>0) 
		writer.write(cbuf, 0, length);
	    writer.close();

	    url = new URL (url.getProtocol(), 
			   url.getHost(), url.getPort(), 
			   (new File(new File(url.getFile()).getParentFile(), 
				     tempfile.getName())).getPath());

	    
            /* now evaluate our script */
	    
	    localReader = new URLReader(url);
            try { this.script = doEval(localReader, context);} catch (Exception e) {
        	Util.printStackTrace(e);
        	throw this.scriptException = new PhpScriptException("Could not evaluate script", e);
            }
            try { localReader.close(); localReader=null; } catch (IOException e) {throw this.scriptException = new PhpScriptException("Could not close script", e);}
	} catch (FileNotFoundException e) {
	    Util.printStackTrace(e);
	} catch (IOException e) {
	    Util.printStackTrace(e);
        } finally {
            if(localReader!=null) try { localReader.close(); } catch (IOException e) {/*ignore*/}
            if(fout!=null) try { fout.close(); } catch (IOException e) {/*ignore*/}
            if(tempfile!=null) tempfile.delete();
            release ();
        }
	return null;
    }
}
