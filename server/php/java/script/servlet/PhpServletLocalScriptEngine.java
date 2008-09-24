/*-*- mode: Java; tab-width:8 -*-*/

package php.java.script.servlet;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.Reader;
import java.net.MalformedURLException;
import java.net.URISyntaxException;
import java.net.URL;
import java.util.Map;

import javax.script.Bindings;
import javax.script.ScriptContext;
import javax.script.ScriptException;
import javax.servlet.Servlet;
import javax.servlet.ServletContext;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import php.java.bridge.Util;
import php.java.bridge.http.IContext;
import php.java.script.IPhpScriptContext;
import php.java.script.PhpScriptEngine;
import php.java.script.PhpScriptException;
import php.java.script.URLReader;
import php.java.servlet.CGIServlet;

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


abstract class PhpServletLocalScriptEngine extends PhpScriptEngine {
    protected Servlet servlet;
    protected ServletContext servletCtx;
    protected HttpServletRequest req;
    protected HttpServletResponse res;
    
    protected URL url;
   
    protected PhpSimpleHttpScriptContext scriptContext;
    
    public PhpServletLocalScriptEngine(Servlet servlet, 
				  ServletContext ctx, 
				  HttpServletRequest req, 
				  HttpServletResponse res) throws MalformedURLException {
	super ();

	this.servlet = servlet;
	this.servletCtx = ctx;
	this.req = req;
	this.res = res;
	    
	scriptContext.initialize(servlet, servletCtx, req, res);

	url = new java.net.URL((req.getRequestURL().toString()));
    }

    protected ScriptContext getPhpScriptContext() {
        Bindings namespace;
        scriptContext = new PhpSimpleHttpScriptContext();
        
        namespace = createBindings();
        scriptContext.setBindings(namespace,ScriptContext.ENGINE_SCOPE);
        scriptContext.setBindings(getBindings(ScriptContext.GLOBAL_SCOPE),
				  ScriptContext.GLOBAL_SCOPE);
        
        return scriptContext;
    }    

    /**
     * Create a new context ID and a environment map which we send to the client.
     *
     */
    protected void setNewContextFactory() {
        IPhpScriptContext context = (IPhpScriptContext)getContext(); 
	env = (Map) this.processEnvironment.clone();

	ctx = PhpServletContextFactory.addNew((IContext)context, servlet, servletCtx, req, res);
    	
	/* send the session context now, otherwise the client has to 
	 * call handleRedirectConnection */
	env.put("X_JAVABRIDGE_CONTEXT", ctx.getId());
	// X_JAVABRIDGE_OVERRIDE_REDIRECT is set in the URLReader
    }
    
    protected Object eval(Reader reader, ScriptContext context, String name) throws ScriptException {
  	if(reader==null) return null;
    	ScriptFileReader fileReader = (ScriptFileReader) reader;
    	URLReader localReader = null;
    	
  	setNewContextFactory();
        setName(name);
        
        try {
            String path1 = new File(CGIServlet.getRealPath(servletCtx, "")).getCanonicalPath();
            path1=path1.replace('\\', '/');
            if(!path1.endsWith("/")) path1+="/";
            String path2 = fileReader.getFile().getCanonicalPath();
            path2=path2.replace('\\', '/');
            String path3 = path2.substring(path1.length());
	    String filePath = req.getContextPath()+"/"+path3;
	    url = new java.net.URI(url.getProtocol(), null, url.getHost(), url.getPort(), filePath, null, null).toURL();
	    
            /* now evaluate our script */
	    
	    localReader = new URLReader(url);
            try { this.script = doEval(localReader, context);} catch (Exception e) {
        	Util.printStackTrace(e);
        	throw this.scriptException = new PhpScriptException("Could not evaluate script", e);
            }
            try { localReader.close(); localReader=null; } catch (IOException e) {throw this.scriptException = new PhpScriptException("Could not close script", e);}
	} catch (FileNotFoundException e) {
	    Util.printStackTrace(e);
	} catch (IOException e) {
	    Util.printStackTrace(e);
        } catch (URISyntaxException e) {
            Util.printStackTrace(e);
        } finally {
            if(localReader!=null) try { localReader.close(); } catch (IOException e) {/*ignore*/}
            release ();
        }
	return null;
    }
}
