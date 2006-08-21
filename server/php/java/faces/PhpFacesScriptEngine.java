/*-*- mode: Java; tab-width:8 -*-*/

package php.java.faces;

/*
 * Copyright (C) 2006 Jost Boekemeier
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

import java.util.Map;

import javax.script.Bindings;
import javax.script.Invocable;
import javax.script.ScriptContext;
import javax.servlet.ServletContext;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import php.java.bridge.Util;
import php.java.bridge.http.IContextFactory;
import php.java.script.IPhpScriptContext;
import php.java.script.PhpScriptEngine;
import php.java.script.PhpScriptWriter;


/**
 * A custom ScriptEngine, keeps the custom ScriptContext
 * @author jostb
 *
 */
public class PhpFacesScriptEngine extends PhpScriptEngine implements Invocable {

    private ServletContext ctx;
    private HttpServletRequest request;
    private HttpServletResponse response;
    private PhpScriptWriter writer;

    /**
     * Creates a new ScriptEngine.
     * @param ctx The ServletContext
     * @param request The HttpServletRequest
     * @param response The HttpServletResponse
     * @param writer The PhpScriptWriter
     */
    public PhpFacesScriptEngine(ServletContext ctx, HttpServletRequest request, HttpServletResponse response, PhpScriptWriter writer) {
        super(false);
	this.ctx = ctx;
	this.request = request;
	this.response = response;
	this.writer = writer;
	initialize();
    }

    /**
     * Create a new context ID and a environment map which we send to the client.
     *
     */
    protected void setNewContextFactory() {
        IContextFactory kontext;
        IPhpScriptContext context = (IPhpScriptContext)getContext(); 
	env = (Map) this.processEnvironment.clone();
	kontext = PhpFacesScriptContextFactory.addNew(context, ctx, request, response);
	
	/* send the session context now, otherwise the client has to 
	 * call handleRedirectConnection */
	env.put("X_JAVABRIDGE_CONTEXT", kontext.getId());
	/* redirect to ourself */
	StringBuffer buf = new StringBuffer();
	if(!request.isSecure())
	    buf.append("h:");
	else 
	    buf.append("s:");
	buf.append(Util.getHostAddress());
	buf.append(":");
	buf.append(php.java.servlet.CGIServlet.getLocalPort(request));
	
	buf.append("/");
	buf.append(request.getRequestURI());
	buf.append(".phpjavabridge"); // it doesn't matter what we
	// send here, as long as it ends
	// with .phpjavabridge
	env.put("X_JAVABRIDGE_OVERRIDE_HOSTS", buf.toString());
    }
    protected ScriptContext getPhpScriptContext() {
        Bindings namespace;
    	PhpFacesScriptContext scriptContext = new PhpFacesScriptContext();
        
        namespace = createBindings();
        scriptContext.setBindings(namespace,ScriptContext.ENGINE_SCOPE);
        scriptContext.setBindings(getBindings(ScriptContext.GLOBAL_SCOPE), ScriptContext.GLOBAL_SCOPE);
        
	try {
	    scriptContext.initialize(ctx, request, response, writer);
	} catch (ServletException e) {
	    Util.printStackTrace(e);
	}

	return scriptContext;
    }
}
