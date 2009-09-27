/*-*- mode: Java; tab-width:8 -*-*/

package php.java.script.servlet;

import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URI;
import java.net.URISyntaxException;

import javax.script.ScriptException;
import javax.servlet.Servlet;
import javax.servlet.ServletContext;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import php.java.bridge.Util;
import php.java.bridge.http.ContextServer;
import php.java.bridge.http.IContext;
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

/**
 * This script engine connects a remote PHP container with the current servlet container.
 * 
 * There must not be a firewall in between, and both components should be behind a firewall. The remote
 * PHP application must end with the line <code>java_call_with_continuation(<yourClosure>)</code>, otherwise invocation will fail.
 * The following description uses the <code>JavaProxy.php</code> sample script (from the <code>JavaBridge.jar</code> or <code>JavaBridge.war</code> zip file). 
 * It is an empty script which ends with <code>java_call_with_continuation(java_closure())</code>.
 * <br>	
 * 
 * In order to evaluate PHP methods follow these steps:<br>
 * <ol>
 * <li> Acquire a PHP invocable script engine from the {@link EngineFactory}. The following example links the PHP app server "diego" with the current Java app server "timon":
 * <blockquote>
 * <code>
 * ScriptEngine scriptEngine = EngineFactory.getInvocablePhpScriptEngine(this, ctx, req, res, new java.net.URI("http://diego.intern.com:80/phpApp/JavaProxy.php"), "timon.intern.com"));
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
 */
public class InvocablePhpServletRemoteHttpServerScriptEngine extends InvocablePhpServletLocalHttpServerScriptEngine {
    
    /** The official FIXED(!) IP# of the current host, */
    protected String localName;
    protected ContextServer contextServer;
    
    protected InvocablePhpServletRemoteHttpServerScriptEngine(Servlet servlet, 
		   ServletContext ctx, 
		   HttpServletRequest req, 
		   HttpServletResponse res,
    		   URI uri,
    		   String localName) throws MalformedURLException, URISyntaxException {
	super(servlet, ctx, req, res);

	this.localName = localName;
	
	this.protocol = uri.getScheme();
	this.port = uri.getPort();
	this.proxy = uri.getPath();
	this.url = uri.toURL();

	this.contextServer = PhpJavaServlet.getContextServer(ctx, promiscuous);
    }
    protected ContextServer getContextServer() {
	return contextServer;
    }
    protected void addNewContextFactory() {
	ctx = InvocableRemotePhpServletContextFactory.addNew((IContext)getContext(), servlet, servletCtx, req, res, localName);
    }
    /**
     * Create a new context ID and a environment map which we send to the client.
     * @throws IOException 
     *
     */
    protected void setNewScriptFileContextFactory(ScriptFileReader fileReader) throws IOException, ScriptException {
	setNewContextFactory();

	String path = fileReader.getResourcePath(servletCtx);
	URI include;
        try {
	    include = new URI(req.getScheme(), null, localName, req.getServerPort(), req.getContextPath()+path, null, null);
        } catch (URISyntaxException e) {
           Util.printStackTrace(e);
	   throw new ScriptException(e);
        }
	env.put("X_JAVABRIDGE_INCLUDE", include.toASCIIString());
    }
}
