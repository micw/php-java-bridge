/*-*- mode: Java; tab-width:8 -*-*/

package php.java.script.servlet;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.Reader;
import java.net.MalformedURLException;
import java.net.URISyntaxException;
import java.util.Map;

import javax.script.ScriptContext;
import javax.script.ScriptException;
import javax.servlet.Servlet;
import javax.servlet.ServletContext;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import php.java.bridge.Util;
import php.java.bridge.http.IContext;
import php.java.script.IPhpScriptContext;
import php.java.script.PhpScriptException;
import php.java.script.URLReader;
import php.java.servlet.CGIServlet;
import php.java.servlet.ContextLoaderListener;

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
 * A PHP script engine which implements the Invocable interface for Servlets. See {@link ContextLoaderListener} for details.
 * 
 * PHP scripts are evaluated as follows:
 * <ol>
 * <li> "http://127.0.0.1:CURRENT_PORT/CURRENT_WEBAPP/java/JavaProxy.php" is requested from Java<br>
 * <li> Your script is evaluated
 * <li> &lt;?php java_context()-&gt;call(java_closure());?&gt; is called in order to make the script invocable<br>
 * </ol>
 * In order to evaluate PHP methods follow these steps:<br>
 * <ol>
 * <li> Create a factory which creates a PHP script file from a reader using the methods from {@link EngineFactory}:
 * <blockquote>
 * <code>
 * private static File script;<br>
 * private static final File getScriptF() {<br>
 * &nbsp;&nbsp; if (script!=null) return script;<br><br>
 * &nbsp;&nbsp; String webCacheDir = ctx.getRealPath(req.getServletPath());<br>
 * &nbsp;&nbsp; Reader reader = new StringReader ("&lt;?php function f($v) {return "passed:".$v;} ?&gt;");<br>
 * &nbsp;&nbsp; return EngineFactory.getPhpScript(webCacheDir, reader);<br>
 * }<br>
 * </code>
 * </blockquote>
 * <li> Acquire a PHP invocable script engine from the {@link EngineFactory}:
 * <blockquote>
 * <code>
 * ScriptEngine scriptEngine = EngineFactory.getInvocablePhpScriptEngine(this, ctx, req, res);
 * </code>
 * </blockquote> 
 * <li> Create a FileReader for the created script file:
 * <blockquote>
 * <code>
 * Reader readerF = EngineFactory.createPhpScriptFileReader(getScriptF());
 * </code>
 * </blockquote>
 * <li> Evaluate the engine:
 * <blockquote>
 * <code>
 * scriptEngine.eval(readerF);
 * </code>
 * </blockquote> 
 * <li> Close the reader obtained from the {@link EngineFactory}:
 * <blockquote>
 * <code>
 * readerF.close();
 * </code>
 * </blockquote> 
 * <li> Cast the engine to Invocable:
 * <blockquote>
 * <code>
 * Invocable invocableEngine = (Invocable)scriptEngine;
 * </code>
 * </blockquote> 
 * <li> Call PHP functions or methods:
 * <blockquote>
 * <code>
 * System.out.println("result from PHP:" + invocableEngine.invoceFunction(f, new Object[]{"arg1"}));
 * </code>
 * </blockquote> 
 * <li> Release the invocable:
 * <blockquote>
 * <code>
 * ((Closeable)scriptEngine).close();
 * </code>
 * </blockquote> 
 * </ol>
 * <br>
 * Alternatively one may use the following "quick and dirty" code which creates a new PHP script for 
 * each eval and removes it when the invocable is released:
 * <blockquote>
 * <code>
 * ScriptEngine e = EngineFactory.getInvocablePhpScriptEngine(this, ctx, req, res);<br>
 * e.eval("&lt;?php function f($v) {return "passed:".$v;} ?&gt;");<br>
 * ((Invocable)e).invoceFunction("f", new Object[]{"arg1"};<br>
 * ((Closeable)e).close();<br>
 * </code>
 * </blockquote>
 */
public class InvocablePhpServletScriptEngine extends InvocablePhpServletLocalScriptEngine {
    private File path;
    private File tempfile = null;
    
    protected InvocablePhpServletScriptEngine(Servlet servlet, 
					   ServletContext ctx, 
					   HttpServletRequest req, 
					   HttpServletResponse res,
					   String protocol,
					   int port) throws MalformedURLException, URISyntaxException {
	super(servlet, ctx, req, res, protocol, port);
	path = new File(CGIServlet.getRealPath(ctx, ""));
    }
 
    /**
     * Create a new context ID and a environment map which we send to the client.
     *
     */
    protected void setNewContextFactory() {
        IPhpScriptContext context = (IPhpScriptContext)getContext(); 
	env = (Map) processEnvironment.clone();

	ctx = InvocablePhpServletContextFactory.addNew((IContext)context, servlet, servletCtx, req, res);
    	
	/* send the session context now, otherwise the client has to 
	 * call handleRedirectConnection */
	setStandardEnvironmentValues(env);
	env.put("X_JAVABRIDGE_INCLUDE", tempfile.getPath());
    }
 
    protected Object eval(Reader reader, ScriptContext context, String name) throws ScriptException {

	// use a short path, if the script file already exists
	if (reader instanceof ScriptFileReader) return super.eval(reader, context, name);
	
        if((continuation != null) || (reader == null) ) release();
     	FileOutputStream fout = null;
        Reader localReader = null;
  	if(reader==null) return null;
  	
        try {
	    fout = new FileOutputStream(tempfile=File.createTempFile("tempfile", ".php", path));
	    setNewContextFactory();
	    setName(name);
	    OutputStreamWriter writer = new OutputStreamWriter(fout);
	    char[] cbuf = new char[Util.BUF_SIZE];
	    int length;

	    while((length=reader.read(cbuf, 0, cbuf.length))>0) 
		writer.write(cbuf, 0, length);
	    writer.close();
	  	
            /* now evaluate our script */
	    
	    EngineFactory.addManaged(servletCtx, this);
	    localReader = new URLReader(url);
            try { this.script = doEval(localReader, context);} catch (Exception e) {
        	Util.printStackTrace(e);
        	throw new PhpScriptException("Could not evaluate script", e);
            }
            try { localReader.close(); localReader=null; } catch (IOException e) {throw new PhpScriptException("Could not close script", e);}
            /* get the proxy, either the one from the user script or our default proxy */
            try { this.scriptClosure = this.script.getProxy(new Class[]{}); } catch (Exception e) { return null; }
	} catch (FileNotFoundException e) {
	    Util.printStackTrace(e);
	} catch (IOException e) {
	    Util.printStackTrace(e);
        } finally {
            if(localReader!=null) try { localReader.close(); } catch (IOException e) {/*ignore*/}
            if(fout!=null) try { fout.close(); } catch (IOException e) {/*ignore*/}
        }
	return resultProxy;
    }
    /**
     * Release the continuation. Must be called explicitly 
     */
    public void release() {
	super.release();
	if(tempfile!=null) tempfile.delete();
    }
}
