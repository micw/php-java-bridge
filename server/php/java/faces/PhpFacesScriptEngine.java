/*-*- mode: Java; tab-width:8 -*-*/

package php.java.faces;

import java.io.Reader;
import java.util.Hashtable;

import javax.script.Bindings;
import javax.script.Invocable;
import javax.script.ScriptContext;
import javax.servlet.ServletContext;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import php.java.bridge.SessionFactory;
import php.java.bridge.Util;
import php.java.script.HttpProxy;
import php.java.script.PhpScriptEngine;
import php.java.script.PhpScriptWriter;


/**
 * @author jostb
 *
 */
public class PhpFacesScriptEngine extends PhpScriptEngine implements Invocable {

    private ServletContext ctx;
    private HttpServletRequest request;
    private HttpServletResponse response;
	
	
    /**
     * @param request
     */
    public PhpFacesScriptEngine(ServletContext kontext, HttpServletRequest request, HttpServletResponse response) {
	super();
	this.ctx = kontext;
	this.request = request;
	this.response = response;
    }

    protected HttpProxy getContinuation(Reader reader, ScriptContext context) throws ClassCastException {
    	PhpFacesScriptContext phpScriptContext = (PhpFacesScriptContext)context;
    	SessionFactory ctx = phpScriptContext.getContextManager();
    	Hashtable env = phpScriptContext.getEnvironment();
	HttpProxy kont = new HttpProxy(reader, env, ctx, ((PhpScriptWriter)context.getWriter()).getOutputStream());
	phpScriptContext.setContinuation(kont);
    	return kont;
    }

    protected ScriptContext getScriptContext(Bindings namespace) {
    	PhpFacesScriptContext scriptContext = new PhpFacesScriptContext();
        
        if(namespace==null) namespace = this.namespace;
        scriptContext.setNamespace(namespace,ScriptContext.ENGINE_SCOPE);
        scriptContext.setNamespace(this.globalspace, ScriptContext.GLOBAL_SCOPE);
        
        
	try {
	    scriptContext.initialize(ctx, request, response);
	} catch (ServletException e) {
	    Util.printStackTrace(e);
	}
        return scriptContext;
    }
    
}
