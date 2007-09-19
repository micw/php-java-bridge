/*-*- mode: Java; tab-width:8 -*-*/

package php.java.script;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.Reader;
import java.net.URL;

import javax.script.ScriptContext;
import javax.script.ScriptException;

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


public class RemotePhpScriptEngine extends InvocablePhpScriptEngine{
 
    	protected URL url;
    	protected String scriptName;
    	
	public RemotePhpScriptEngine(URL url, String scriptName) {
    	    this.url = url;
    	    this.scriptName = scriptName;
    	}
    	protected Object eval(Reader reader, ScriptContext context, String name) throws ScriptException {
        File tempfile = null;
        Reader localReader = null;
	if(continuation != null) release();
  	if(reader==null) return null;
  	
  	setNewContextFactory();
        setName(name);
        
        try {
	FileOutputStream fout = new FileOutputStream(tempfile=File.createTempFile("tempfile", "php"));
        OutputStreamWriter writer = new OutputStreamWriter(fout);
        char[] cbuf = new char[Util.BUF_SIZE];
        int length;
        while((length=reader.read(cbuf, 0, cbuf.length))>0) 
            writer.write(cbuf, 0, length);
        writer.close();
            /* now evaluate our script */
            localReader = new URLReader(url);
            try { this.script = doEval(localReader, context);} catch (Exception e) {
        	Util.printStackTrace(e);
        	throw this.scriptException = new PhpScriptException("Could not evaluate script", e);
            }
            try { localReader.close(); } catch (IOException e) {throw this.scriptException = new PhpScriptException("Could not evaluate footer", e);}
    
            /* get the proxy, either the one from the user script or our default proxy */
            try { this.scriptClosure = this.script.getProxy(new Class[]{}); } catch (Exception e) { return null; }
            handleRelease();
       } catch (FileNotFoundException e) {
	    // TODO Auto-generated catch block
	    e.printStackTrace();
        } catch (IOException e) {
	    // TODO Auto-generated catch block
	    e.printStackTrace();
        } finally {
            if(localReader!=null) try { localReader.close(); } catch (IOException e) {/*ignore*/}
            if(tempfile!=null) tempfile.delete();
        }
       return null;
    }
    protected void setNewContextFactory() {
	super.setNewContextFactory();
	 env.put("X_JAVABRIDGE_SCRIPT", scriptName);
    }
}
