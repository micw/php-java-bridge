/*-*- mode: Java; tab-width:8 -*-*/

package php.java.servlet;

import java.io.IOException;
import java.io.Writer;

import javax.script.http.HttpScriptContext;
import javax.servlet.ServletContext;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;


/**
 * A custom context which keeps the HttpServletResponse. Used when JSR223 is not available.
 * 
 * @author jostb
 *
 */
public class Context extends php.java.bridge.http.Context {
    protected HttpServletResponse response;
    protected ServletContext context;
    protected HttpServletRequest request;

    /** Integer value for the level of SCRIPT_SCOPE */
    public static final int REQUEST_SCOPE = javax.script.http.HttpScriptContext.REQUEST_SCOPE;
    
    /** Integer value for the level of SESSION_SCOPE */   
    public static final int SESSION_SCOPE = javax.script.http.HttpScriptContext.SESSION_SCOPE;
    
    /** Integer value for the level of APPLICATION_SCOPE */
    public static final int APPLICATION_SCOPE = javax.script.http.HttpScriptContext.APPLICATION_SCOPE;

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
	
    /**
     * Create a new context.
     * @param res The HttpServletResponse
     */
    public Context(ServletContext kontext, HttpServletRequest req, HttpServletResponse res) {
      this.context = kontext;
      this.response = res;
      this.request = req;
    }
	
    public Writer getWriter() throws IOException {
	return response.getWriter();
    }
    /**
     * Returns the HttpServletRequest
     * @return The HttpServletRequest.
     */
    public HttpServletRequest getHttpServletRequest() {
    	return this.request;
    }
    
    /**
     * Returns the ServletContext
     * @return The ServletContext.
     */
    public ServletContext getServletContext() {
        return this.context;
    }
    
    /**
     * Returns the ServletResponse
     * @return The ServletResponse.
     */
    public HttpServletResponse getHttpServletResponse() {
        return this.response;
    }
}
