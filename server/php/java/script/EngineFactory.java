/*-*- mode: Java; tab-width:8 -*-*/

package php.java.script;

import java.io.File;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.Reader;
import java.net.MalformedURLException;
import java.net.URISyntaxException;

import javax.servlet.Servlet;
import javax.servlet.ServletContext;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import php.java.bridge.Util;


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

public class EngineFactory {
    public static final String ROOT_ENGINE_FACTORY_ATTRIBUTE = EngineFactory.class.getName()+".ROOT";
    public EngineFactory() {}
    private Object getScriptEngine(Servlet servlet, 
		     ServletContext ctx, 
		     HttpServletRequest req, 
		     HttpServletResponse res) throws MalformedURLException {
	    return new PhpServletScriptEngine(servlet, ctx, req, res);
    }
    private Object getInvocableScriptEngine(Servlet servlet, 
		     ServletContext ctx, 
		     HttpServletRequest req, 
		     HttpServletResponse res) throws MalformedURLException, URISyntaxException {
	    return new InvocablePhpServletScriptEngine(servlet, ctx, req, res);
    }
    public static EngineFactory getEngineFactory(ServletContext ctx) {
	EngineFactory attr = (EngineFactory) 
	    ctx.getAttribute(php.java.script.EngineFactory.ROOT_ENGINE_FACTORY_ATTRIBUTE);
	return attr;
    }

    public static EngineFactory getRequiredEngineFactory(ServletContext ctx) throws IllegalStateException {
	EngineFactory attr = getEngineFactory (ctx);
	if (attr==null) 
	    throw new IllegalStateException("No EngineFactory found. Have you registered a listener?");
	return attr;
    }
	    
    public static javax.script.ScriptEngine getPhpScriptEngine (Servlet servlet, 
								ServletContext ctx, 
								HttpServletRequest req, 
								HttpServletResponse res) throws 
								    MalformedURLException, IllegalStateException {
	return (javax.script.ScriptEngine)EngineFactory.getRequiredEngineFactory(ctx).getScriptEngine(servlet, ctx, req, res);
    }
	    
    public static javax.script.ScriptEngine getInvocablePhpScriptEngine (Servlet servlet, 
									 ServletContext ctx, 
									 HttpServletRequest req, 
									 HttpServletResponse res) throws 
									     MalformedURLException, IllegalStateException, URISyntaxException {
	    return (javax.script.ScriptEngine)EngineFactory.getRequiredEngineFactory(ctx).getInvocableScriptEngine(servlet, ctx, req, res);
    }

    private static File getFile(File file, Reader reader) throws IOException {
	FileOutputStream fout = new FileOutputStream(file);
	OutputStreamWriter writer = new OutputStreamWriter(fout);
	char[] cbuf = new char[Util.BUF_SIZE];
	int length;
	while((length=reader.read(cbuf, 0, cbuf.length))>0) 
	    writer.write(cbuf, 0, length);
	writer.close();
	return file;
    }
    
    public static File getPhpScript (String path, Reader reader) {
	try {
	    return getFile(new File(path+"._cache_.php"), reader);
	} catch (IOException e) {
	    Util.printStackTrace(e);
        }
	return null;
    }
    public static File getPhpScript (String path) {
	return new File(path+"._cache_.php");
    }
    
    public static FileReader createPhpScriptFileReader (File phpScriptFile) {
	try {
	    return new ScriptFileReader(phpScriptFile);
        } catch (IOException e) {
	    Util.printStackTrace(e);
        }
	return null;
    }
}
