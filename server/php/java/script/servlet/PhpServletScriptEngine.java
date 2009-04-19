/*-*- mode: Java; tab-width:8 -*-*/

package php.java.script.servlet;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.Reader;
import java.net.MalformedURLException;

import javax.script.ScriptContext;
import javax.script.ScriptException;
import javax.servlet.Servlet;
import javax.servlet.ServletContext;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import php.java.bridge.Util;
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
 * A PHP script engine for Servlets. See {@link ContextLoaderListener} for details.
 * 
 * In order to evaluate PHP scripts follow these steps:<br>
 * <ol>
 * <li> Create a factory which creates a PHP script file from a reader using the methods from {@link EngineFactory}:
 * <blockquote>
 * <code>
 * private static final Reader HELLO_SCRIPT_READER = new StringReader("&lt;?php echo 'Hello java world!'; ?&gt;");
 * </code>
 * </blockquote>
 * <li> Acquire a PHP script engine from the {@link EngineFactory}:
 * <blockquote>
 * <code>
 * ScriptEngine scriptEngine = EngineFactory.getPhpScriptEngine(this, ctx, req, res);
 * </code>
 * </blockquote> 
 * <li> Create a FileReader for the created script file:
 * <blockquote>
 * <code>
 * Reader readerHello = EngineFactory.createPhpScriptFileReader(request.getServletPath()+"._cache_.php", HELLO_SCRIPT_READER);
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
 * Alternatively one may use the following "quick and dirty" code which creates a new PHP script for 
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
				  String protocol,
				  int port) throws MalformedURLException {
	super (servlet, ctx, req, res, protocol, port);
	path = new File(CGIServlet.getRealPath(ctx, ""));
    }
    protected Object eval(Reader reader, ScriptContext context, String name) throws ScriptException {

	// use a short path if the script file already exists
	if (reader instanceof ScriptFileReader) return super.eval(reader, context, name);
	
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
	    setName(name);
	        
	    
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
