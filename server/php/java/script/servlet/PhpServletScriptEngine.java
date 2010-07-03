/*-*- mode: Java; tab-width:8 -*-*/

package php.java.script.servlet;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.Reader;
import java.net.MalformedURLException;
import java.security.AccessController;
import java.security.PrivilegedActionException;
import java.security.PrivilegedExceptionAction;

import javax.script.ScriptContext;
import javax.script.ScriptException;
import javax.servlet.Servlet;
import javax.servlet.ServletContext;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import php.java.bridge.Util;
import php.java.script.URLReader;
import php.java.servlet.ContextLoaderListener;
import php.java.servlet.ServletUtil;

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
 * A PHP script engine for Servlets. See {@link ContextLoaderListener} for details.
 * 
 * In order to evaluate PHP scripts follow these steps:<br>
 * <ol>
 * <li> Create a factory which creates a PHP script file from a reader using the methods from {@link EngineFactory}. 
 * The following line is static final so that a READER is created whenever the JSP is compiled to Java code:
 * <blockquote>
 * <code>
 * private static final Reader HELLO_SCRIPT_READER = EngineFactory.createPhpScriptReader("&lt;?php echo 'Hello java world!'; ?&gt;");
 * </code>
 * </blockquote>
 * <li> Acquire a PHP script engine from the {@link EngineFactory}:
 * <blockquote>
 * <code>
 * ScriptEngine scriptEngine = EngineFactory.getPhpScriptEngine(this, ctx, req, res);
 * </code>
 * </blockquote> 
 * <li> Create a FileReader for the PHP script reader:
 * <blockquote>
 * <code>
 * Reader readerHello = EngineFactory.createPhpScriptFileReader(this.getClass().getName()+"._cache_.php", HELLO_SCRIPT_READER);
 * </code>
 * </blockquote>
 * or simply, if your script file doesn't need to be re-created for each JSP to Java compilation:
 * <blockquote>
 * <code>
 * Reader readerHello = EngineFactory.createPhpScriptFileReader("/myScriptFile.php");
 * </code>
 * </blockquote>
 * <li> Connect its output:
 * <blockquote>
 * <code>
 * scriptEngine.getContext().setWriter (out);
 * </code>
 * </blockquote>
 * <li> Evaluate the engine:
 * <blockquote>
 * <code>
 * scriptEngine.eval(readerHello);
 * </code>
 * </blockquote> 
 * <li> Close the reader:
 * <blockquote>
 * <code>
 * readerHello.close();
 * </code>
 * </blockquote> 
 * </ol>
 * <br>
 * Alternatively one may use the following "quick and dirty" code which creates a new PHP script file for 
 * each eval:
 * <blockquote>
 * <code>
 * ScriptEngine e = EngineFactory.getPhpScriptEngine(this, ctx, req, res);<br>
 * e.getContext().setWriter (out);<br>
 * e.eval("&lt;?php echo "hello java world"; ?&gt;");<br>
 * </code>
 * </blockquote>
 */


public class PhpServletScriptEngine extends PhpServletLocalHttpServerScriptEngine {
    private File path;
    protected PhpServletScriptEngine(Servlet servlet, 
				  ServletContext ctx, 
				  HttpServletRequest req, 
				  HttpServletResponse res,
				  File compilerOutputFile, String protocol,
				  int port) throws MalformedURLException {
	super (servlet, ctx, req, res, protocol, port);
	this.compilerOutputFile = compilerOutputFile;
	
	path = new File(ServletUtil.getRealPath(ctx, ""));
    }
    protected Object doEvalPhp(final Reader reader, final ScriptContext context, final String name) throws ScriptException {
	try {
	    return AccessController.doPrivileged(new PrivilegedExceptionAction(){ 
	        public Object run() throws Exception {
	    	return evalWithPrivileges(reader, context, name);
	        }
	    });
        } catch (PrivilegedActionException e) {
            Throwable cause = e.getCause();
            if (cause instanceof RuntimeException) throw (RuntimeException)cause;
            throw (ScriptException) e.getCause();
        }
    }   
    private Object evalWithPrivileges(Reader reader, ScriptContext context, String name) throws ScriptException {

	// use a short path if the script file already exists
	if (reader instanceof ScriptFileReader) return super.doEvalPhp(reader, context, name);
	
    	File tempfile = null;
    	FileOutputStream fout = null;
        Reader localReader = null;
  	if(reader==null) return null;
  	
        try {
	    fout = new FileOutputStream(tempfile=File.createTempFile("tempfile", ".php", path));
	    OutputStreamWriter writer = new OutputStreamWriter(fout);
	    char[] cbuf = new char[Util.BUF_SIZE];
	    int length;

	    while((length=reader.read(cbuf, 0, cbuf.length))>0) 
		writer.write(cbuf, 0, length);
	    writer.close();

	    webPath = req.getContextPath()+"/"+tempfile.getName();

	    setNewContextFactory();
	        
	    
            /* now evaluate our script */
	    
	    localReader = new URLReader(getURL(webPath));
            this.script = doEval(localReader, context);
        } catch (Exception e) {
            Util.printStackTrace(e);
            if (e instanceof RuntimeException) throw (RuntimeException)e;
            if (e instanceof ScriptException) throw (ScriptException)e;
            throw new ScriptException(e);
        } finally {
            if(localReader!=null) try { localReader.close(); } catch (IOException e) {/*ignore*/}
            if(fout!=null) try { fout.close(); } catch (IOException e) {/*ignore*/}
            if(tempfile!=null) tempfile.delete();
            release ();
        }
	return resultProxy;
    }
}
