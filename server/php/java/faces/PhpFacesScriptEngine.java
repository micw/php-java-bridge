/*-*- mode: Java; tab-width:8 -*-*/

package php.java.faces;

import javax.script.Bindings;
import javax.script.Invocable;
import javax.script.ScriptContext;
import javax.servlet.ServletContext;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import php.java.bridge.Util;
import php.java.bridge.http.ContextFactory;
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
        ContextFactory kontext;
        IPhpScriptContext context = (IPhpScriptContext)getContext(); 
	env.clear();
	
	context.setContextFactory(kontext=PhpFacesScriptContextFactory.addNew(context, ctx, request, response));

	/* send the session context now, otherwise the client has to 
	 * call handleRedirectConnection */
	this.env.put("X_JAVABRIDGE_CONTEXT", kontext.getId());
	/* redirect to ourself */
	StringBuffer buf = new StringBuffer("127.0.0.1:");
	buf.append(php.java.servlet.CGIServlet.getLocalPort(request));
	buf.append("/");
	buf.append(request.getRequestURI());
	buf.append(".php"); // it doesn't matter what we send here, as long as it ends with .php
	this.env.put("X_JAVABRIDGE_OVERRIDE_HOSTS", buf.toString()); 
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
