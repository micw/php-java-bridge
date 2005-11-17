/*-*- mode: Java; tab-width:8 -*-*/

package php.java.script;

import java.io.IOException;
import java.io.Writer;

import javax.script.SimpleScriptContext;
import javax.script.http.HttpScriptContext;
import javax.servlet.ServletContext;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

/**
 * @author jostb
 *
 */
public class PhpSimpleHttpScriptContext extends SimpleScriptContext {

    /** Integer value for the level of SCRIPT_SCOPE */
    public static final int REQUEST_SCOPE = javax.script.http.HttpScriptContext.REQUEST_SCOPE;
    
    /** Integer value for the level of SESSION_SCOPE */   
    public static final int SESSION_SCOPE = javax.script.http.HttpScriptContext.SESSION_SCOPE;
    
    /** Integer value for the level of APPLICATION_SCOPE */
    public static final int APPLICATION_SCOPE = javax.script.http.HttpScriptContext.APPLICATION_SCOPE;

    protected HttpServletRequest request;
    protected HttpServletResponse response;
    protected ServletContext context;

    public void initialize(ServletContext ctx,
			   HttpServletRequest req,
			   HttpServletResponse res) throws ServletException {
	this.context = ctx;
	this.request = req;
	this.response = res;
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
	try {
	    return getResponse().getWriter();
	} catch (IOException e) {
	    return null;
	}
    }
		
    public HttpServletResponse getResponse() {
	return response;
    }



}
