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

import javax.script.Bindings;
import javax.script.ScriptContext;
import javax.script.ScriptException;
import javax.servlet.Servlet;
import javax.servlet.ServletContext;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import php.java.bridge.Util;
import php.java.bridge.http.IContext;
import php.java.bridge.http.IContextFactory;
import php.java.script.IPhpScriptContext;
import php.java.script.InvocablePhpScriptEngine;
import php.java.script.URLReader;

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

/**
 * This script engine connects a local PHP container with the current servlet container.
 *
 * There must not be a firewall in between, the servlet thread pool must not be 
 * limited or twice the size of the PHP container's pool size, the PHP option 
 * "allow_url_include" and the Java <code>WEB-INF/web.xml</code> "promiscuous" 
 * option must be enabled. Both components should be behind a firewall.
 * <br>	
 * PHP scripts are evaluated as follows:
 * <ol>
 * <li> JavaProxy.php is requested from Java<br>
 * <li> Your script is included and then evaluated
 * <li> &lt;?php java_context()-&gt;call(java_closure());?&gt; is called in order to make the script invocable<br>
 * </ol>
 * In order to evaluate PHP methods follow these steps:<br>
 * <ol>
 * <li> Create a factory which creates a PHP script file from a reader using the methods from {@link EngineFactory}:
 * <blockquote>
 * <code>
 * private static File script;<br>
 * private static final File getScriptF() {<br>
 * &nbsp;&nbsp; if (script!=null) return script;<br><br>
 * &nbsp;&nbsp; String webCacheDir = ctx.getRealPath(req.getServletPath());<br>
 * &nbsp;&nbsp; Reader reader = new StringReader ("&lt;?php function f($v) {return "passed:".$v;} ?&gt;");<br>
 * &nbsp;&nbsp; return EngineFactory.getPhpScript(webCacheDir, reader);<br>
 * }<br>
 * </code>
 * </blockquote>
 * <li> Acquire a PHP invocable script engine from the {@link EngineFactory}:
 * <blockquote>
 * <code>
 * ScriptEngine scriptEngine = EngineFactory.getInvocablePhpScriptEngine(this, ctx, req, res, "HTTP", 80, "/JavaProxy.php");
 * </code>
 * </blockquote> 
 * <li> Create a FileReader for the created script file:
 * <blockquote>
 * <code>
 * Reader readerF = EngineFactory.createPhpScriptFileReader(getScriptF());
 * </code>
 * </blockquote>
 * <li> Evaluate the engine:
 * <blockquote>
 * <code>
 * scriptEngine.eval(readerF);
 * </code>
 * </blockquote> 
 * <li> Close the reader obtained from the {@link EngineFactory}:
 * <blockquote>
 * <code>
 * readerF.close();
 * </code>
 * </blockquote> 
 * <li> Cast the engine to Invocable:
 * <blockquote>
 * <code>
 * Invocable invocableEngine = (Invocable)scriptEngine;
 * </code>
 * </blockquote> 
 * <li> Call PHP functions or methods:
 * <blockquote>
 * <code>
 * System.out.println("result from PHP:" + invocableEngine.invoceFunction(f, new Object[]{"arg1"}));
 * </code>
 * </blockquote> 
 * <li> Release the invocable:
 * <blockquote>
 * <code>
 * ((Closeable)scriptEngine).close();
 * </code>
 * </blockquote> 
 * </ol>
 * <br>
 */
public class InvocablePhpServletLocalHttpServerScriptEngine extends InvocablePhpScriptEngine {
    private static final Object EMPTY_INCLUDE = "@";
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
    
    protected boolean overrideHosts = true;
    
    protected String getProxy() {
	if (proxy!=null) return proxy;
	return proxy=req.getContextPath()+"/java/JavaProxy.php";
    }
    protected URL getURL(ServletContext ctx) throws MalformedURLException, URISyntaxException {
	return new java.net.URI(protocol, null, Util.getHostAddress(), port, getProxy(), null, null).toURL();
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

	try {
	    String value = servlet.getServletConfig().getServletContext().getInitParameter("override_hosts");
	    if(value==null) value="";
	    value = value.trim();
	    value = value.toLowerCase();
	    if(value.equals("off") || value.equals("false")) overrideHosts = false;
	} catch (Exception t) {Util.printStackTrace(t);}

	scriptContext.initialize(servlet, servletCtx, req, res);
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
    protected ScriptContext getPhpScriptContext() {
	        Bindings namespace;
	        scriptContext = new PhpSimpleHttpScriptContext();
	        
	        namespace = createBindings();
	        scriptContext.setBindings(namespace,ScriptContext.ENGINE_SCOPE);
	        scriptContext.setBindings(getBindings(ScriptContext.GLOBAL_SCOPE),
					  ScriptContext.GLOBAL_SCOPE);
	        
	        return scriptContext;
	    }    
    /**
     * Create a new context ID and a environment map which we send to the client.
     * @throws IOException 
     *
     */
    protected void setNewScriptFileContextFactory(ScriptFileReader fileReader) throws IOException, ScriptException {
        IPhpScriptContext context = (IPhpScriptContext)getContext(); 
	env = (Map) processEnvironment.clone();

	ctx = getPhpScriptContextFactory(context);
    	
	/* send the session context now, otherwise the client has to 
	 * call handleRedirectConnection */
	setStandardEnvironmentValues(env);
	env.put("X_JAVABRIDGE_INCLUDE", fileReader.getFile().getCanonicalPath());
    }
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
    protected Object eval(final Reader reader, final ScriptContext context, final String name) throws ScriptException {
	try {
	    return AccessController.doPrivileged(new PrivilegedExceptionAction(){ 
	        public Object run() throws Exception {
	    	return evalInternal(reader, context, name);
	        }
	    });
        } catch (PrivilegedActionException e) {
            Throwable cause = e.getCause();
            if (cause instanceof RuntimeException) throw (RuntimeException)cause;
            throw (ScriptException) e.getCause();
        }
    }
    protected IContextFactory getPhpScriptContextFactory (IPhpScriptContext context) {
	return InvocablePhpServletContextFactory.addNew((IContext)context, servlet, servletCtx, req, res);
    }
    /** Short path used when eval() is missing */
    protected Object evalShortPath() throws ScriptException {
	Reader localReader = null; 
	if((continuation != null)) release();
	 try {
	    StringBuffer buf = new StringBuffer(req.getContextPath());
	    String pathInfo = req.getPathInfo();
	    if (pathInfo != null) buf.append(pathInfo);
	    buf.append("/JavaBridge"); // dummy for PhpJavaServlet
	    webPath = buf.toString();
	    IPhpScriptContext context = (IPhpScriptContext)getContext(); 
	    env = (Map) processEnvironment.clone();
	    ctx = getPhpScriptContextFactory (context);
	    	
	    /* send the session context now, otherwise the client has to 
	     * call handleRedirectConnection */
	    setStandardEnvironmentValues(env);
	    setName(DUMMY_PHP_SCRIPT_NAME);
	    env.put("X_JAVABRIDGE_INCLUDE", EMPTY_INCLUDE);
            /* now evaluate JavaProxy.php */
	    EngineFactory.addManaged(servletCtx, this);

	    continuation = getContinuation(localReader = new URLReader(url), getContext());
	    continuation.start();
	    this.script = continuation.getPhpScript();
	    if (this.script != null)
		try { this.scriptClosure = this.script.getProxy(new Class[]{}); } catch (Exception e) { return null; }
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
    private Object evalInternal(Reader reader, ScriptContext context, String name) throws ScriptException {
        if((continuation != null) || (reader == null) ) release();
        Reader localReader = null;
  	if(reader==null) return null;
  	
	if (!(reader instanceof ScriptFileReader)) throw new IllegalArgumentException("reader must be a ScriptFileReader");
  	ScriptFileReader fileReader = (ScriptFileReader) reader;
  	
        try {
	    webPath = fileReader.getFile().getWebPath(fileReader.getFile().getCanonicalPath(), req, servletCtx);
	    setNewScriptFileContextFactory(fileReader);
	    setName(name);

            /* now evaluate our script */

	    EngineFactory.addManaged(servletCtx, this);
	    localReader = new URLReader(url);
            this.script = doEval(localReader, context);
            /* get the proxy, either the one from the user script or our default proxy */
	    if (this.script != null)
		try { this.scriptClosure = this.script.getProxy(new Class[]{}); } catch (Exception e) { return null; }
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
	PhpServletLocalHttpServerScriptEngine.setStandardEnvironmentValues(ctx, env, req, webPath, overrideHosts);
    }
}
