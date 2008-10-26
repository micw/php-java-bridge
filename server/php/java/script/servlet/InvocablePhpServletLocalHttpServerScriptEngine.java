/*-*- mode: Java; tab-width:8 -*-*/

package php.java.script.servlet;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.Reader;
import java.net.MalformedURLException;
import java.net.URISyntaxException;
import java.net.URL;
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
import php.java.script.IPhpScriptContext;
import php.java.script.InvocablePhpScriptEngine;
import php.java.script.PhpScriptException;
import php.java.script.URLReader;
import php.java.servlet.ContextLoaderListener;

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
 * A PHP script engine which implements the Invocable interface for Servlets. See {@link ContextLoaderListener} for details.
 * 
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
 * <li> Release the invocable by evaluating the engine again with a NULL value.
 * <blockquote>
 * <code>
 * scriptEngine.eval((Reader)null);
 * </code>
 * </blockquote> 
 * </ol>
 * <br>
 */
public class InvocablePhpServletLocalHttpServerScriptEngine extends InvocablePhpScriptEngine {
    protected Servlet servlet;
    protected ServletContext servletCtx;
    protected HttpServletRequest req;
    protected HttpServletResponse res;
    
    protected PhpSimpleHttpScriptContext scriptContext;
    private String webPath; 

    protected int port;
    protected String protocol;
    protected URL url;
    protected String proxy;
    
    protected String getProxy() {
	if (proxy!=null) return proxy;
	return proxy=req.getContextPath()+"/java/JavaProxy.php";
    }
    protected URL getURL(ServletContext ctx) throws MalformedURLException, URISyntaxException {
	return new java.net.URI(protocol, null, Util.getHostAddress(), port, getProxy(), null, null).toURL();
    }
    public InvocablePhpServletLocalHttpServerScriptEngine(Servlet servlet, 
					   ServletContext ctx, 
					   HttpServletRequest req, 
					   HttpServletResponse res,
					   String protocol,
					   int port) throws MalformedURLException, URISyntaxException {
	this(servlet, ctx, req, res, protocol, port, null);
    }
    public InvocablePhpServletLocalHttpServerScriptEngine(Servlet servlet, 
		   ServletContext ctx, 
		   HttpServletRequest req, 
		   HttpServletResponse res,
		   String protocol,
		   int port,
		   String proxy) throws MalformedURLException, URISyntaxException {
	super();

	this.servlet = servlet;
	this.servletCtx = ctx;
	this.req = req;
	this.res = res;

	scriptContext.initialize(servlet, servletCtx, req, res);
	
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
    private void setNewLocalContextFactory(ScriptFileReader fileReader) throws IOException {
        IPhpScriptContext context = (IPhpScriptContext)getContext(); 
	env = (Map) this.processEnvironment.clone();

	ctx = InvocablePhpServletContextFactory.addNew((IContext)context, servlet, servletCtx, req, res);
    	
	/* send the session context now, otherwise the client has to 
	 * call handleRedirectConnection */
	setStandardEnvironmentValues(context, env);
	env.put("X_JAVABRIDGE_INCLUDE", fileReader.getFile().getCanonicalPath());
    }
    
    protected Object eval(Reader reader, ScriptContext context, String name) throws ScriptException {
        if(continuation != null) release();
        Reader localReader = null;
  	if(reader==null) return null;
  	
	if (!(reader instanceof ScriptFileReader)) throw new IllegalArgumentException("reader must be a ScriptFileReader");
  	ScriptFileReader fileReader = (ScriptFileReader) reader;
  	
        try {
	    webPath = fileReader.getFile().getWebPath(fileReader.getFile().getCanonicalPath(), req, servletCtx);
	    setNewLocalContextFactory(fileReader);
	    setName(name);

            /* now evaluate our script */

	    EngineFactory.addManaged(req, this);
	    localReader = new URLReader(url);
            try { this.script = doEval(localReader, context);} catch (Exception e) {
        	Util.printStackTrace(e);
        	throw new PhpScriptException("Could not evaluate script", e);
            }
            try { localReader.close(); localReader=null; } catch (IOException e) {throw new PhpScriptException("Could not close script", e);}
            /* get the proxy, either the one from the user script or our default proxy */
            try { this.scriptClosure = this.script.getProxy(new Class[]{}); } catch (Exception e) { return null; }
	} catch (FileNotFoundException e) {
	    Util.printStackTrace(e);
	} catch (IOException e) {
	    Util.printStackTrace(e);
        } finally {
            if(localReader!=null) try { localReader.close(); } catch (IOException e) {/*ignore*/}
        }
	return null;
    }
    protected void releaseReservedContinuation() {}
    protected void reserveContinuation() throws ScriptException {}
    /**
     * Set the context id (X_JAVABRIDGE_CONTEXT) and the override flag (X_JAVABRIDGE_OVERRIDE_HOSTS) into env
     * @param context the new context ID
     * @param env the environment which will be passed to PHP
     */
    protected void setStandardEnvironmentValues (IPhpScriptContext context, Map env) {
	PhpServletLocalHttpServerScriptEngine.setStandardEnvironmentValues(context, env, ctx, req, webPath);
    }
}
