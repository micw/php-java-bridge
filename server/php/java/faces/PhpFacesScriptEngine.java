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
     * @param kontext The ServletContext
     * @param request The HttpServletRequest
     * @param response The HttpServletResponse
     * @param writer The PhpScriptWriter
     */
    public PhpFacesScriptEngine(ServletContext kontext, HttpServletRequest request, HttpServletResponse response, PhpScriptWriter writer) {
        super(false);
	this.ctx = kontext;
	this.request = request;
	this.response = response;
	this.writer = writer;
	initialize();
    }

    protected ScriptContext getScriptContext(Bindings namespace) {
    	PhpFacesScriptContext scriptContext = new PhpFacesScriptContext();
        
        if(namespace==null) namespace = createBindings();
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
