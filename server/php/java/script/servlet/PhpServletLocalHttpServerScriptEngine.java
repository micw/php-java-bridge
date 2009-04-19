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
import php.java.bridge.http.ContextServer;
import php.java.bridge.http.IContext;
import php.java.bridge.http.IContextFactory;
import php.java.script.PhpScriptEngine;
import php.java.servlet.PhpJavaServlet;

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

abstract class PhpServletLocalHttpServerScriptEngine extends PhpScriptEngine {
    protected Servlet servlet;
    protected ServletContext servletCtx;
    protected HttpServletRequest req;
    protected HttpServletResponse res;
    
    protected PhpSimpleHttpScriptContext scriptContext;

    protected String webPath; 
    
    protected boolean overrideHosts = true;
    
    protected ContextServer contextServer;

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
	
    	try {
	    String value = servlet.getServletConfig().getServletContext().getInitParameter("override_hosts");
	    if(value==null) value="";
	    value = value.trim();
	    value = value.toLowerCase();
	    if(value.equals("off") || value.equals("false")) overrideHosts = false;
	} catch (Exception t) {Util.printStackTrace(t);}
	    
	scriptContext.initialize(servlet, servletCtx, req, res);
	
	this.port = port;
	this.protocol = protocol;

	this.contextServer = PhpJavaServlet.getContextServer(ctx);
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

    protected ContextServer getContextServer() {
	return contextServer;
    }
    /**
     * Create a new context ID
     *
     */
    protected void addNewContextFactory() {
	ctx = PhpServletContextFactory.addNew((IContext)getContext(), servlet, servletCtx, req, res);
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
    private Object evalInternal(Reader reader, ScriptContext context, String name) throws ScriptException {
  	if(reader==null) return null;
	if (!(reader instanceof ScriptFileReader)) throw new IllegalArgumentException("reader must be a ScriptFileReader");
    	ScriptFileReader fileReader = (ScriptFileReader) reader;
    	ServletReader localReader = null;
    	
        try {
            String resourcePath = fileReader.getResourcePath(servletCtx);
	    webPath = req.getContextPath()+resourcePath;
	    setNewContextFactory();
	    setName(name);
	        
            /* now evaluate our script */
	    localReader = new ServletReader(servletCtx, resourcePath, getURL(webPath), req);
            this.script = doEval(localReader, context);
        } catch (Exception e) {
            Util.printStackTrace(e);
            if (e instanceof RuntimeException) throw (RuntimeException)e;
            if (e instanceof ScriptException) throw (ScriptException)e;
            throw new ScriptException(e);
        } finally {
            if(localReader!=null) try { localReader.close(); } catch (IOException e) {/*ignore*/}
            release ();
        }
	return resultProxy;
    }
    /**
     * Set the context id (X_JAVABRIDGE_CONTEXT) and the override flag (X_JAVABRIDGE_OVERRIDE_HOSTS) into env
     * @param env the environment which will be passed to PHP
     */
    protected void setStandardEnvironmentValues (Map env) {
	setStandardEnvironmentValues(ctx, env, req, webPath, overrideHosts);
    }
    static void setStandardEnvironmentValues(IContextFactory ctx,
            Map env, HttpServletRequest req,
            String webPath, boolean overrideHosts) {
	/* send the session context now, otherwise the client has to 
	 * call handleRedirectConnection */
	env.put("X_JAVABRIDGE_CONTEXT", ctx.getId());
	
	/* the client should connect back to us */
	env.put("X_JAVABRIDGE_OVERRIDE_HOSTS", ctx.getRedirectString(webPath));
    }
}
