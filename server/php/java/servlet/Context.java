/*-*- mode: Java; tab-width:8 -*-*/

package php.java.servlet;

import java.io.IOException;
import java.io.Writer;

import javax.servlet.ServletContext;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import php.java.bridge.http.IContext;
import php.java.script.servlet.PhpSimpleHttpScriptContext;

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
    public static final int REQUEST_SCOPE = PhpSimpleHttpScriptContext.REQUEST_SCOPE;
    
    /** Integer value for the level of SESSION_SCOPE */   
    public static final int SESSION_SCOPE = PhpSimpleHttpScriptContext.SESSION_SCOPE;
    
    /** Integer value for the level of APPLICATION_SCOPE */
    public static final int APPLICATION_SCOPE = PhpSimpleHttpScriptContext.APPLICATION_SCOPE;

    /** {@inheritDoc}*/
    public Object getAttribute(String key, int scope){
	if(scope == PhpSimpleHttpScriptContext.REQUEST_SCOPE){
	    return request.getAttribute(key);
	}else if(scope == SESSION_SCOPE){
	    return request.getSession().getAttribute(key);
	}else if(scope == APPLICATION_SCOPE){
	    return context.getAttribute(key);	                        
	}else{
	    return super.getAttribute(key, scope);
	}
  }
  /**{@inheritDoc}*/  
  public Object getAttribute(String name) throws IllegalArgumentException{
	Object result;
	if (name == null) {
	    throw new IllegalArgumentException("name cannot be null");
	}
	          
	if ((getEngineScope()!=null) && (result=getEngineScope().get(name)) != null) {
	    return result;
	} else if ((getGlobalScope()!=null) && (result=getGlobalScope().get(name)) != null) {
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
     * @param kontext The servlet context
     * @param req The servlet request
     * @param res The HttpServletResponse
     */
    public Context(ServletContext kontext, HttpServletRequest req, HttpServletResponse res) {
      this.context = kontext;
      this.response = res;
      this.request = req;
    }
	
    /**{@inheritDoc}*/  
    public Writer getWriter() throws IOException {
	return response.getWriter();
    }
    /**
     * Return the http servlet response
     * @return The http servlet reponse
     */
     public Object getHttpServletResponse() {
 	return getAttribute(IContext.SERVLET_RESPONSE);
     }
     /**
      * Return the http servlet request
      * @return The http servlet request
      */
     public Object getHttpServletRequest() {
 	return getAttribute(IContext.SERVLET_REQUEST);
     }
     /**
      * Return the http servlet
      * @return The http servlet
      */
     public Object getServlet() {
 	return getAttribute(IContext.SERVLET);
     }
     /**
      * Return the servlet config
      * @return The servlet config
      */
      public Object getServletConfig() {
  	return getAttribute(IContext.SERVLET_CONFIG);
      }
      /**
       * Return the servlet context
       * @return The servlet context
       */
       public Object getServletContext() {
   	return getAttribute(IContext.SERVLET_CONTEXT);
       }
}
