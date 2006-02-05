/*-*- mode: Java; tab-width:8 -*-*/

package php.java.script;

import java.io.Writer;

import javax.script.SimpleScriptContext;
import javax.script.http.HttpScriptContext;
import javax.servlet.ServletContext;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import php.java.bridge.DynamicJavaBridgeClassLoader;
import php.java.bridge.JavaBridgeRunner;
import php.java.bridge.PhpProcedureProxy;
import php.java.bridge.http.ContextFactory;


/**
 * A simple ScriptContext which can be used in servlet environments.
 * 
 * @author jostb
 *
 */
public class PhpSimpleHttpScriptContext extends SimpleScriptContext implements IPhpScriptContext {
    static {
	DynamicJavaBridgeClassLoader.initClassLoader();
    }

    /** Integer value for the level of SCRIPT_SCOPE */
    public static final int REQUEST_SCOPE = javax.script.http.HttpScriptContext.REQUEST_SCOPE;
    
    /** Integer value for the level of SESSION_SCOPE */   
    public static final int SESSION_SCOPE = javax.script.http.HttpScriptContext.SESSION_SCOPE;
    
    /** Integer value for the level of APPLICATION_SCOPE */
    public static final int APPLICATION_SCOPE = javax.script.http.HttpScriptContext.APPLICATION_SCOPE;

    protected HttpServletRequest request;
    protected HttpServletResponse response;
    protected ServletContext context;
    protected PhpScriptWriter writer;

    /**
     * Initilaize the context.
     * @param ctx The ServletContext
     * @param req The HttpServletRequest
     * @param res The HttpServletResponse
     * @param writer The PhpScriptWriter
     * @throws ServletException
     */
    public void initialize(ServletContext ctx,
			   HttpServletRequest req,
			   HttpServletResponse res, PhpScriptWriter writer) throws ServletException {
	this.context = ctx;
	this.request = req;
	this.response = res;
	this.writer = writer;
    }

    public Object getAttribute(String key, int scope){
	if(scope == HttpScriptContext.REQUEST_SCOPE){
	    return request.getAttribute(key);
	}else if(scope == SESSION_SCOPE){
	    return request.getSession().getAttribute(key);
	}else if(scope == APPLICATION_SCOPE){
	    return context.getAttribute(key);	                        
	}else{
	    return super.getAttribute(key, scope);
	}
    }
    public Object getAttribute(String name) throws IllegalArgumentException{
	Object result;
	if (name == null) {
	    throw new IllegalArgumentException("name cannot be null");
	}
	          
	if ((engineScope!=null) && (result=engineScope.get(name)) != null) {
	    return result;
	} else if ((globalScope!=null) && (result=globalScope.get(name)) != null) {
	    return result;
	} else if ((result=request.getAttribute(name)) != null)  {
	    return result;
	} else if ((result=request.getSession().getAttribute(name)) != null)  {
	    return result;
	} else if ((result=context.getAttribute(name)) != null) {
	    return result;
	}
	return null;
    }

    public void setAttribute(String key, Object value, int scope)
	throws IllegalArgumentException {    	
	if(scope == REQUEST_SCOPE){
	    request.setAttribute(key, value);
	}else if(scope == SESSION_SCOPE){
	    request.getSession().setAttribute(key, value);
	}else if(scope == APPLICATION_SCOPE){
	    context.setAttribute(key, value);
	}else{
	    super.setAttribute(key, value, scope);    
	}
    }
    public Writer getWriter() {
        return (Writer) writer;
    }
		
    /**
     * Get the servlet response
     * @return The HttpServletResponse
     */
    public HttpServletResponse getResponse() {
	return response;
    }
    
    /**
     * Get the HttpServletRequest
     * @return The HttpServletRequest
     */
    public HttpServletRequest getRequest() {
        return request;
    }
    
    /**
     * Get the ServletContext
     * @return The current ServletContext
     */
    public ServletContext getContext() {
        return context;
    }

    private php.java.bridge.http.ContextFactory ctx;
    private HttpProxy kont;

    public void setContextFactory(ContextFactory factory) {
      this.ctx = factory;
    }

    public ContextFactory getContextFactory() {
        return ctx;
    }

    public void setContinuation(HttpProxy kont) {
        this.kont = kont;
    }

    public JavaBridgeRunner getHttpServer() {
        throw new NullPointerException("PhpSimpleHttpScriptContext must be used in a HTTP server/Servlet Engine. Use PhpScriptContext instead.");
    }

    public boolean call(PhpProcedureProxy kont) throws Exception {
	this.kont.call(kont);
	return true;
    }
}
