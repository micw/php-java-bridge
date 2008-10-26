/*-*- mode: Java; tab-width:8 -*-*/

package php.java.script.servlet;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.Reader;
import java.io.UnsupportedEncodingException;
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
import php.java.bridge.http.IContextFactory;
import php.java.script.IPhpScriptContext;
import php.java.script.PhpScriptEngine;
import php.java.script.PhpScriptException;
import php.java.script.URLReader;
import php.java.servlet.ContextLoaderListener;
import php.java.servlet.PhpCGIServlet;

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
 * A PHP script engine for Servlets. See {@link ContextLoaderListener} for details.
 * 
 * In order to evaluate PHP methods follow these steps:<br>
 * <ol>
 * <li> Create a factory which creates a PHP script file from a reader using the methods from {@link EngineFactory}:
 * <blockquote>
 * <code>
 * private static File script;<br>
 * private static final File getHelloScript() {<br>
 * &nbsp;&nbsp; if (script!=null) return script;<br><br>
 * &nbsp;&nbsp; String webCacheDir = ctx.getRealPath(req.getServletPath());<br>
 * &nbsp;&nbsp; Reader reader = new StringReader ("&lt;?php echo 'hello from PHP'; ?&gt;");<br>
 * &nbsp;&nbsp; return EngineFactory.getPhpScript(webCacheDir, reader);<br>
 * }<br>
 * </code>
 * </blockquote>
 * <li> Acquire a PHP script engine from the {@link EngineFactory}:
 * <blockquote>
 * <code>
 * ScriptEngine scriptEngine = EngineFactory.getPhpScriptEngine(this, ctx, req, res, "HTTP", 80);
 * </code>
 * </blockquote> 
 * <li> Create a FileReader for the created script file:
 * <blockquote>
 * <code>
 * Reader readerHello = EngineFactory.createPhpScriptFileReader(getHelloScript());
 * </code>
 * </blockquote>
 * <li> Connect its output:
 * <blockquote>
 * <code>
 * scriptEngine.getContext().setWriter (out);
 * </code>
 * </blockquote>
 * <li> Evaluate the engine:
 * <blockquote>
 * <code>
 * scriptEngine.eval(readerHello);
 * </code>
 * </blockquote> 
 * <li> Close the reader:
 * <blockquote>
 * <code>
 * readerHello.close();
 * </code>
 * </blockquote> 
 * </ol>
 * <br>
 */

public class PhpServletLocalHttpServerScriptEngine extends PhpScriptEngine {
    protected Servlet servlet;
    protected ServletContext servletCtx;
    protected HttpServletRequest req;
    protected HttpServletResponse res;
    
    protected PhpSimpleHttpScriptContext scriptContext;

    protected String webPath; 
    
    private URL url;
    private int port;
    private String protocol;
    protected URL getURL(String filePath) throws MalformedURLException, URISyntaxException {
	if (url!=null) return url;
	
	return url = new java.net.URI(protocol, null, Util.getHostAddress(), port, filePath, null, null).toURL();
    }
    public PhpServletLocalHttpServerScriptEngine(Servlet servlet, 
				  ServletContext ctx, 
				  HttpServletRequest req, 
				  HttpServletResponse res,
				  String protocol,
				  int port) throws MalformedURLException {
	super ();

	this.servlet = servlet;
	this.servletCtx = ctx;
	this.req = req;
	this.res = res;
	    
	scriptContext.initialize(servlet, servletCtx, req, res);
	
	this.port = port;
	this.protocol = protocol;
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
     *
     */
    protected void setNewContextFactory() {
        IPhpScriptContext context = (IPhpScriptContext)getContext(); 
	env = (Map) this.processEnvironment.clone();

	ctx = PhpServletContextFactory.addNew((IContext)context, servlet, servletCtx, req, res);
    	
	/* send the session context now, otherwise the client has to 
	 * call handleRedirectConnection */
	setStandardEnvironmentValues(context, env);
    }
    
    protected Object eval(Reader reader, ScriptContext context, String name) throws ScriptException {
  	if(reader==null) return null;
	if (!(reader instanceof ScriptFileReader)) throw new IllegalArgumentException("reader must be a ScriptFileReader");
    	ScriptFileReader fileReader = (ScriptFileReader) reader;
    	URLReader localReader = null;
    	
        try {
	    webPath = fileReader.getFile().getWebPath(fileReader.getFile().getCanonicalPath(), req, servletCtx);
	    setNewContextFactory();
	    setName(name);
	        
            /* now evaluate our script */

	    reserveContinuation(); // engines need a PHP- and an optional Java continuation
	    localReader = new URLReader(getURL(webPath));
            try { this.script = doEval(localReader, context);} catch (Exception e) {
        	Util.printStackTrace(e);
        	throw new PhpScriptException("Could not evaluate script", e);
            }
            try { localReader.close(); localReader=null; } catch (IOException e) {throw new PhpScriptException("Could not close script", e);}
	} catch (FileNotFoundException e) {
	    Util.printStackTrace(e);
	} catch (IOException e) {
	    Util.printStackTrace(e);
        } catch (URISyntaxException e) {
            Util.printStackTrace(e);
        } finally {
            releaseReservedContinuation();
            if(localReader!=null) try { localReader.close(); } catch (IOException e) {/*ignore*/}
            release ();
        }
	return null;
    }
    protected void releaseReservedContinuation() {
	PhpCGIServlet.releaseReservedContinuation();
    }
    protected void reserveContinuation() throws ScriptException {
	PhpCGIServlet.reserveContinuation();
    }
    /**
     * Set the context id (X_JAVABRIDGE_CONTEXT) and the override flag (X_JAVABRIDGE_OVERRIDE_HOSTS) into env
     * @param context the new context ID
     * @param env the environment which will be passed to PHP
     */
    protected void setStandardEnvironmentValues (IPhpScriptContext context, Map env) {
	setStandardEnvironmentValues(context, env, ctx, req, webPath);
    }
    static void setStandardEnvironmentValues(IPhpScriptContext context,
            Map env, IContextFactory ctx, HttpServletRequest req,
            String webPath) {
	/* send the session context now, otherwise the client has to 
	 * call handleRedirectConnection */
	env.put("X_JAVABRIDGE_CONTEXT", ctx.getId());
	
	/* the client should connect back to us */
	StringBuffer buf = new StringBuffer();
	if(!req.isSecure())
	    buf.append("h:");
	else
	    buf.append("s:");
	buf.append(Util.getHostAddress());
	buf.append(':');
	buf.append(context.getSocketName());
	buf.append('/');
	try {
	    buf.append((new java.net.URI(null, null, webPath, null)).toASCIIString());
        } catch (URISyntaxException e) {
            Util.printStackTrace(e);
            buf.append(webPath);
        }
	buf.append("javabridge");
	env.put("X_JAVABRIDGE_OVERRIDE_HOSTS",buf.toString());
    }
}
