/*-*- mode: Java; tab-width:8 -*-*/

package php.java.script.servlet;

import java.io.IOException;
import java.io.Reader;
import java.net.MalformedURLException;
import java.net.URISyntaxException;
import java.net.URL;
import java.util.Map;

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
import php.java.script.IScriptReader;
import php.java.script.PhpScriptEngine;
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

abstract class PhpServletLocalHttpServerScriptEngine extends PhpScriptEngine {
    protected Servlet servlet;
    protected ServletContext servletCtx;
    protected HttpServletRequest req;
    protected HttpServletResponse res;
    
    protected PhpSimpleHttpScriptContext scriptContext;

    protected String webPath; 
    
    protected boolean overrideHosts = true;
    
    protected ContextServer contextServer;
    protected String localHostAddr;
    
    private URL url;
    private int port;
    private String protocol;
    protected URL getURL(String filePath) throws MalformedURLException, URISyntaxException {
	if (url!=null) return url;
	
	return url = new java.net.URI(protocol, null, localHostAddr, port, filePath, null, null).toURL();
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
	    
	this.port = port;
	this.protocol = protocol;

	this.contextServer = (ContextServer)ctx.getAttribute(ContextServer.ROOT_CONTEXT_SERVER_ATTRIBUTE);
	this.localHostAddr = (String)ctx.getAttribute(ServletUtil.HOST_ADDR_ATTRIBUTE);
    }

    protected ContextServer getContextServer() {
	return contextServer;
    }
    /**
     * Create a new context ID
     *
     */
    protected void addNewContextFactory() {
	ctx = PhpServletContextFactory.addNew(getContextServer(), (IContext)getContext(), servlet, servletCtx, req, res);
    }
    
    protected Object doEvalPhp(final Reader reader, final ScriptContext context, final String name) throws ScriptException {
  	if(reader==null) return null;
	if (!(reader instanceof ScriptFileReader)) throw new IllegalArgumentException("reader must be a ScriptFileReader");
    	ScriptFileReader fileReader = (ScriptFileReader) reader;
    	ServletReader localReader = null;
    	
        try {
            String resourcePath = fileReader.getResourcePath(servletCtx);
	    webPath = req.getContextPath()+resourcePath;
	    setNewContextFactory();
	        
            /* now evaluate our script */
	    localReader = new ServletReader(servletCtx, resourcePath, getURL(webPath), req, res);
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
	setStandardEnvironmentValues(ctx, env, req, webPath);
    }
    static void setStandardEnvironmentValues(IContextFactory ctx,
            Map env, HttpServletRequest req,
            String webPath) {
	
	/* send the session context now, otherwise the client has to 
	 * call handleRedirectConnection */
	env.put(IScriptReader.X_JAVABRIDGE_CONTEXT, ctx.getId());
	
	/* the client should connect back to us */
	env.put(IScriptReader.X_JAVABRIDGE_OVERRIDE_HOSTS, ctx.getRedirectString(webPath));
    }
}
