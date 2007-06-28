/*-*- mode: Java; tab-width:8 -*-*/

package php.java.script;

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

import java.io.Writer;

import javax.script.SimpleScriptContext;

import php.java.bridge.JavaBridgeRunner;
import php.java.bridge.PhpProcedureProxy;
import php.java.bridge.Util;
import php.java.bridge.http.HttpServer;
import php.java.bridge.http.IContext;

/**
 * This class implements a simple script context for PHP. It starts a standalone 
 * <code>JavaBridgeRunner</code> which listens for requests from php instances.<p>
 * 
 * In a servlet environment please use a <code>php.java.script.PhpSimpleHttpScriptContext</code> instead.
 * @see php.java.script.PhpSimpleHttpScriptContext
 * @see php.java.bridge.JavaBridgeRunner
 * @author jostb
 *
 */
public class PhpScriptContext extends SimpleScriptContext implements IContext, IPhpScriptContext {
    static JavaBridgeRunner bridgeRunner = null;

    static {
	try {
	    bridgeRunner = JavaBridgeRunner.getInstance();
	} catch (Exception e) {
	    Util.printStackTrace(e);
	}
    }

    /**
     * Return the http server associated with this VM.
     * @return The http server.
     */
    public static HttpServer getHttpServer() {
      return bridgeRunner;
    }

    private HttpProxy kont;
	
    /**
     * Create a standalone PHP script context.
     *
     */
    public PhpScriptContext() {
        super();
    }

     private Writer getWriter(boolean isStandalone) {
	 return isStandalone ? new PhpScriptLogWriter() : new PhpScriptWriter(System.out);
     }
    public Writer getWriter() {
	if(writer == null) return writer =  getWriter(bridgeRunner.isStandalone());
	else if(! (writer instanceof PhpScriptWriter)) setWriter(writer);
	return writer;
    }
    private Writer getErrorWriter(boolean isStandalone) {
	 return isStandalone ? new PhpScriptLogWriter() : new PhpScriptWriter(System.err);
    }
    public Writer getErrorWriter() {
	if(errorWriter == null) return errorWriter =  getErrorWriter(bridgeRunner.isStandalone());
	else if(! (errorWriter instanceof PhpScriptWriter)) setErrorWriter(errorWriter);
	return errorWriter;	
    }
    /**
     * Ignore the default java_context()->call(java_closure()) call at the end
     * of the invocable script, if the user has provided its own.
     */
    private boolean continuationCalled;
    /**
     * Set the php continuation
     * @param kont - The continuation.
     */
    public void setContinuation(HttpProxy kont) {
	this.kont = kont;
	continuationCalled = false;
    }


    /* (non-Javadoc)
     * @see php.java.bridge.Invocable#call(php.java.bridge.PhpProcedureProxy)
     */
    /**@inheritDoc*/
    public boolean call(PhpProcedureProxy kont) throws InterruptedException {
    	if(!continuationCalled) this.kont.call(kont);
    	return continuationCalled = true;
    }

    /**
     * Sets the <code>Writer</code> for scripts to use when displaying output.
     *
     * @param writer The new <code>Writer</code>.
     */
    public void setWriter(Writer writer) {
	super.setWriter(new PhpScriptWriter(new OutputStreamWriter(writer)));
    }
    
    
    /**
     * Sets the <code>Writer</code> used to display error output.
     *
     * @param writer The <code>Writer</code>.
     */
    public void setErrorWriter(Writer writer) {
	super.setErrorWriter(new PhpScriptWriter(new OutputStreamWriter(writer)));
    }

    /**@inheritDoc*/
    public HttpProxy getContinuation() {
        return kont;
    }
}
