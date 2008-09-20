/*-*- mode: Java; tab-width:8 -*-*/

package php.java.script;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
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


abstract class InvocablePhpServletLocalScriptEngine extends InvocablePhpScriptEngine {
    protected Servlet servlet;
    protected ServletContext servletCtx;
    protected HttpServletRequest req;
    protected HttpServletResponse res;
    
    protected PhpSimpleHttpScriptContext scriptContext;

    protected URL url;
    private File scriptFile = null;
  
    public InvocablePhpServletLocalScriptEngine(Servlet servlet, 
					   ServletContext ctx, 
					   HttpServletRequest req, 
					   HttpServletResponse res) throws MalformedURLException, URISyntaxException {
	super();

	this.servlet = servlet;
	this.servletCtx = ctx;
	this.req = req;
	this.res = res;

	scriptContext.initialize(servlet, servletCtx, req, res);
	String filePath;
        url = new java.net.URL((req.getRequestURL().toString()));
	if (!new File(CGIServlet.getRealPath(ctx, "java/JavaProxy.php")).exists())
	    filePath = "/JavaBridge/java/JavaProxy.php";
	else
	    filePath = req.getContextPath()+"/java/JavaProxy.php";
	url = new java.net.URI(url.getProtocol(), null, url.getHost(), url.getPort(), filePath, null, null).toURL();
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

	ctx = InvocablePhpServletContextFactory.addNew((IContext)context, servlet, servletCtx, req, res);
    	
	/* send the session context now, otherwise the client has to 
	 * call handleRedirectConnection */
	env.put("X_JAVABRIDGE_CONTEXT", ctx.getId());
	env.put("X_JAVABRIDGE_INCLUDE", scriptFile.getPath());
    }
    
    protected Object eval(Reader reader, ScriptContext context, String name) throws ScriptException {
  	ScriptFileReader scriptReader = (ScriptFileReader) reader;
  	
        if(continuation != null) release();
     	FileOutputStream fout = null;
        Reader localReader = null;
  	if(reader==null) return null;
  	
        try {
            scriptFile = scriptReader.getFile().getAbsoluteFile();
            
	    setNewContextFactory();
	    setName(name);

            /* now evaluate our script */
	    localReader = new URLReader(url);
            try { this.script = doEval(localReader, context);} catch (Exception e) {
        	Util.printStackTrace(e);
        	throw this.scriptException = new PhpScriptException("Could not evaluate script", e);
            }
            try { localReader.close(); localReader=null; } catch (IOException e) {throw this.scriptException = new PhpScriptException("Could not close script", e);}
            /* get the proxy, either the one from the user script or our default proxy */
            try { this.scriptClosure = this.script.getProxy(new Class[]{}); } catch (Exception e) { return null; }
            handleRelease();
	} catch (FileNotFoundException e) {
	    Util.printStackTrace(e);
	} catch (IOException e) {
	    Util.printStackTrace(e);
        } finally {
            if(localReader!=null) try { localReader.close(); } catch (IOException e) {/*ignore*/}
            if(fout!=null) try { fout.close(); } catch (IOException e) {/*ignore*/}
        }
	return null;
    }
}
