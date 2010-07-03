/*-*- mode: Java; tab-width:8 -*-*/

package php.java.script.servlet;

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

import java.io.IOException;
import java.io.OutputStream;
import java.io.Reader;
import java.io.Writer;
import java.util.Collections;
import java.util.Map;

import javax.servlet.Servlet;
import javax.servlet.ServletContext;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import php.java.bridge.ILogger;
import php.java.bridge.Util.HeaderParser;
import php.java.bridge.http.IContext;
import php.java.script.AbstractPhpScriptContext;
import php.java.script.Continuation;
import php.java.script.HttpProxy;
import php.java.script.IPhpScriptContext;
import php.java.script.PhpScriptLogWriter;
import php.java.script.PhpScriptWriter;
import php.java.script.ResultProxy;


/**
 * A simple ScriptContext which can be used in servlet environments.
 * 
 * @author jostb
 *
 */
public class PhpSimpleHttpScriptContext extends AbstractPhpScriptContext implements IPhpScriptContext {

    /** Integer value for the level of SCRIPT_SCOPE */
    public static final int REQUEST_SCOPE = 0;
    
    /** Integer value for the level of SESSION_SCOPE */   
    public static final int SESSION_SCOPE = 150;
    
    /** Integer value for the level of APPLICATION_SCOPE */
    public static final int APPLICATION_SCOPE = 175;


    protected HttpServletRequest request;
    protected HttpServletResponse response;
    protected ServletContext context;
    protected Servlet servlet;
    
    /**{@inheritDoc}*/
    public Object getAttribute(String key, int scope){
	if(scope == REQUEST_SCOPE){
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
	if ((result = super.getAttribute(name))!=null) return result;

	if ((result=request.getAttribute(name)) != null)  {
	    return result;
	} else if ((result=request.getSession().getAttribute(name)) != null)  {
	    return result;
	} else if ((result=context.getAttribute(name)) != null) {
	    return result;
	}
	return null;
    }

    /**{@inheritDoc}*/
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

    /** {@inheritDoc} */
    public Writer getWriter() {
	if(writer == null)
 		try {
 			writer =  response.getWriter(); 
 		} catch (IllegalStateException x) {
 			/*ignore*/
 		} catch (IOException e) {
 			/*ignore*/
 		}
 	
 	if(writer == null)
 		try { 
 			writer = new PhpScriptWriter (response.getOutputStream());
 		} catch (IOException ex) { 
 			throw new RuntimeException(ex); 
 		}

 	if(! (writer instanceof PhpScriptWriter)) setWriter(writer);
 	return writer;
    }

    /** {@inheritDoc} */
    public Writer getErrorWriter() {
 	if(errorWriter == null)
 		errorWriter = PhpScriptLogWriter.getWriter(new php.java.servlet.Logger());

 	if(! (errorWriter instanceof PhpScriptWriter)) setErrorWriter(errorWriter);
 	return errorWriter;	
    }

    protected Reader reader;
    /**{@inheritDoc}*/
    public Reader getReader() {
        if (reader == null)
	        try {
	                reader = request.getReader();
                } catch (IOException e) {
                	throw new RuntimeException(e);
                }
	return reader;
    }
    /**{@inheritDoc}*/
    public Object init(Object callable) throws Exception {
	 return php.java.bridge.http.Context.getManageable(callable);
    }
    /**{@inheritDoc}*/
    public void onShutdown(Object closeable) {
	php.java.servlet.Context.handleManaged(closeable, context);
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
      /**{@inheritDoc}*/
      public String getRealPath(String path) {
  	return php.java.servlet.Context.getRealPathInternal(path, context);
      }
      /**{@inheritDoc}*/
      public Object get(String key) {
	  return getBindings(IContext.ENGINE_SCOPE).get(key);
      }
      /**{@inheritDoc}*/
      public void put(String key, Object val) {
	  getBindings(IContext.ENGINE_SCOPE).put(key, val);
      }
      /**{@inheritDoc}*/
      public void remove(String key) {
	  getBindings(IContext.ENGINE_SCOPE).remove(key);
      }
      /**{@inheritDoc}*/
      public void putAll(Map map) {
	  getBindings(IContext.ENGINE_SCOPE).putAll(map);
      }
      /**{@inheritDoc}*/
      public Map getAll() {
  	return Collections.unmodifiableMap(getBindings(IContext.ENGINE_SCOPE));
      }
      /**{@inheritDoc}*/
      public Continuation createContinuation(Reader reader, Map env,
              OutputStream out, OutputStream err, HeaderParser headerParser,
              ResultProxy result, ILogger logger) {
  	return new HttpProxy(reader, env, out,  err, headerParser, result, logger); 
      }
}
