/*-*- mode: Java; tab-width:8 -*-*/

package php.java.script.servlet;

import java.io.IOException;
import java.io.Reader;
import java.net.MalformedURLException;
import java.net.URISyntaxException;
import java.net.URL;
import java.security.AccessController;
import java.security.PrivilegedActionException;
import java.security.PrivilegedExceptionAction;
import java.util.Map;

import javax.script.ScriptContext;
import javax.script.ScriptException;
import javax.servlet.Servlet;
import javax.servlet.ServletContext;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import php.java.bridge.Util;
import php.java.bridge.http.ContextServer;
import php.java.script.InvocablePhpScriptEngine;
import php.java.script.URLReader;
import php.java.servlet.ServletUtil;

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

abstract class InvocablePhpServletLocalHttpServerScriptEngine extends InvocablePhpScriptEngine {
    private static final String DUMMY_PHP_SCRIPT_NAME = "dummy php script";

    protected Servlet servlet;
    protected ServletContext servletCtx;
    protected HttpServletRequest req;
    protected HttpServletResponse res;
    
    protected PhpSimpleHttpScriptContext scriptContext;
    protected String webPath; 

    protected int port;
    protected String protocol;
    protected URL url;
    protected String proxy;

    protected String localHostAddr;
    protected ContextServer contextServer;
    
    protected String getProxy() {
	if (proxy!=null) return proxy;
	return proxy=req.getContextPath()+"/java/JavaProxy.php";
    }
    protected URL getURL(ServletContext ctx) throws MalformedURLException, URISyntaxException {
	return new java.net.URI(protocol, null, localHostAddr, port, getProxy(), null, null).toURL();
    }
    protected InvocablePhpServletLocalHttpServerScriptEngine(Servlet servlet, 
					   ServletContext ctx, 
					   HttpServletRequest req, 
					   HttpServletResponse res,
					   String protocol,
					   int port) throws MalformedURLException, URISyntaxException {
	this(servlet, ctx, req, res, protocol, port, null);
    }
    protected InvocablePhpServletLocalHttpServerScriptEngine(Servlet servlet, 
		   ServletContext ctx, 
		   HttpServletRequest req, 
		   HttpServletResponse res) throws MalformedURLException, URISyntaxException {
	super();

	this.servlet = servlet;
	this.servletCtx = ctx;
	this.req = req;
	this.res = res;

	this.localHostAddr = (String)ctx.getAttribute(ServletUtil.HOST_ADDR_ATTRIBUTE);
	this.contextServer = (ContextServer)ctx.getAttribute(ContextServer.ROOT_CONTEXT_SERVER_ATTRIBUTE);
    }
    protected InvocablePhpServletLocalHttpServerScriptEngine(Servlet servlet, 
		   ServletContext ctx, 
		   HttpServletRequest req, 
		   HttpServletResponse res,
		   String protocol,
		   int port,
		   String proxy) throws MalformedURLException, URISyntaxException {
	this(servlet, ctx, req, res);

	this.protocol = protocol;
	this.port = port;
	this.proxy = proxy;
	this.url = getURL(ctx);
    }
    protected ContextServer getContextServer() {
	return contextServer;
    }
    protected abstract void addNewContextFactory ();
    /**
     * Create a new context ID and a environment map which we send to the client.
     * @throws IOException 
     *
     */
    abstract protected void setNewScriptFileContextFactory(ScriptFileReader fileReader) throws IOException, ScriptException;

    protected Object invoke(String methodName, Object[] args)
	throws ScriptException, NoSuchMethodException {
	if(scriptClosure==null) {
	    if (Util.logLevel>4) Util.warn("Evaluating an empty script either because eval() has not been called or release() has been called.");
	    evalShortPath();
	}
	
	try {
	    return invoke(scriptClosure, methodName, args);
	} catch (php.java.bridge.Request.AbortException e) {
	    release ();
	    throw new ScriptException(e);
	} catch (NoSuchMethodError e) { // conform to jsr223
	    throw new NoSuchMethodException(String.valueOf(e.getMessage()));
	}
    }
    protected Object doEvalPhp(final Reader reader, final ScriptContext context, final String name) throws ScriptException {
	try {
	    return AccessController.doPrivileged(new PrivilegedExceptionAction(){ 
	        public Object run() throws Exception {
	    	return evalWithPrivileges(reader, context, name);
	        }
	    });
        } catch (PrivilegedActionException e) {
            Throwable cause = e.getCause();
            if (cause instanceof RuntimeException) throw (RuntimeException)cause;
            throw (ScriptException) e.getCause();
        }
    }
    /** Short path used when eval() is missing */
    private Object evalShortPath() throws ScriptException {
	Reader localReader = null; 
	if((continuation != null)) release();
	
	 try {
	    StringBuffer buf = new StringBuffer(req.getContextPath());
	    String pathInfo = req.getPathInfo();
	    if (pathInfo != null) buf.append(pathInfo);
	    buf.append("/JavaBridge"); // dummy for PhpJavaServlet
	    webPath = buf.toString();
	    setNewContextFactory();
	    	
	    /* send the session context now, otherwise the client has to 
	     * call handleRedirectConnection */
	    env.put("X_JAVABRIDGE_INCLUDE", EMPTY_INCLUDE);
            /* now evaluate JavaProxy.php */
	    EngineFactory.addManaged(servletCtx, this);

	    continuation = getContinuation(localReader = new URLReader(url), getContext());
	    pool.start(continuation);
	    this.script = continuation.getPhpScript();
	    if (this.script != null) this.scriptClosure = script;
        } catch (Exception e) {
	    Util.printStackTrace(e);
            if (e instanceof RuntimeException) throw (RuntimeException)e;
            if (e instanceof ScriptException) throw (ScriptException)e;
            throw new ScriptException(e);
        }  finally {
            if(localReader!=null) try { localReader.close(); } catch (IOException e) {/*ignore*/}
        }
	return resultProxy;
    }
    private Object evalWithPrivileges(Reader reader, ScriptContext context, String name) throws ScriptException {
        if((continuation != null) || (reader == null) ) release();
        Reader localReader = null;
  	if(reader==null) return null;

	if (!(reader instanceof ScriptFileReader)) throw new IllegalArgumentException("reader must be a ScriptFileReader");
  	ScriptFileReader fileReader = (ScriptFileReader) reader;
  	
        try {
	    webPath = req.getContextPath()+fileReader.getResourcePath(servletCtx);
	    setNewScriptFileContextFactory(fileReader);

            /* now evaluate our script */

	    EngineFactory.addManaged(servletCtx, this);
	    localReader = new URLReader(url);
            this.script = doEval(localReader, context);
            /* get the proxy, either the one from the user script or our default proxy */
	    if (this.script != null)
		this.scriptClosure = script;
	} catch (Exception e) {
	    Util.printStackTrace(e);
            if (e instanceof RuntimeException) throw (RuntimeException)e;
            if (e instanceof ScriptException) throw (ScriptException)e;
            throw new ScriptException(e);
	} finally {
            if(localReader!=null) try { localReader.close(); } catch (IOException e) {/*ignore*/}
        }
	return resultProxy;
    }
    protected void releaseReservedContinuation() {}
    protected void reserveContinuation() throws ScriptException {}
    /**{@inheritDoc}*/
    public void release() {
	try {
	    super.release();
	} finally {
	    releaseReservedContinuation();
	}
    }
    /**
     * Set the context id (X_JAVABRIDGE_CONTEXT) and the override flag (X_JAVABRIDGE_OVERRIDE_HOSTS) into env
     * @param env the environment which will be passed to PHP
     */
    protected void setStandardEnvironmentValues (Map env) {
	PhpServletLocalHttpServerScriptEngine.setStandardEnvironmentValues(ctx, env, req, webPath);
    }
}
