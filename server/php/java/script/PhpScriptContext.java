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

import php.java.bridge.JavaBridgeRunner;
import php.java.bridge.Util;

/**
 * This class implements a simple script context for PHP. It starts a standalone 
 * <code>JavaBridgeRunner</code> which listens for requests from php instances.<p>
 * 
 * In a servlet environment please use a <code>php.java.script.PhpSimpleHttpScriptContext</code> instead.
 * @see php.java.script.PhpScriptContext
 * @see php.java.bridge.JavaBridgeRunner
 * @author jostb
 *
 */
public class PhpScriptContext extends AbstractPhpScriptContext implements IPhpScriptContext {
    static JavaBridgeRunner bridgeRunner = null;

    static {
	try {
	    bridgeRunner = JavaBridgeRunner.getRequiredInstance();
	} catch (Exception e) {
	    Util.printStackTrace(e);
	}
    }
 	
    /**
     * Create a standalone PHP script context.
     *
     */
    public PhpScriptContext() {
        super();
    }

     private Writer getWriter(boolean isStandalone) {
	 return isStandalone ? PhpScriptLogWriter.getWriter(Util.getLogger()) : new PhpScriptWriter(System.out);
     }
     /**{@inheritDoc}*/
    public Writer getWriter() {
	if(writer == null) return writer =  getWriter(bridgeRunner.isStandalone());
	else if(! (writer instanceof PhpScriptWriter)) setWriter(writer);
	return writer;
    }
    private Writer getErrorWriter(boolean isStandalone) {
	 return isStandalone ? PhpScriptLogWriter.getWriter(Util.getLogger()) : new PhpScriptWriter(System.err);
    }
    /**{@inheritDoc}*/
    public Writer getErrorWriter() {
	if(errorWriter == null) return errorWriter =  getErrorWriter(bridgeRunner.isStandalone());
	else if(! (errorWriter instanceof PhpScriptWriter)) setErrorWriter(errorWriter);
	return errorWriter;	
    }

    /**{@inheritDoc}*/
    public Object init(Object callable) throws Exception {
	return php.java.bridge.http.Context.getManageable(callable);
    }
    /**{@inheritDoc}*/
    public void onShutdown(Object closeable) {
	php.java.bridge.http.Context.handleManaged(closeable);
    }
    /**
     * Throws IllegalStateException
     * @return none
     */
    public Object getHttpServletRequest() {
	throw new IllegalStateException("PHP not running in a servlet environment");
    }
    
    /**
     * Throws IllegalStateException
     * @return none
     */
    public Object getServletContext() {
	throw new IllegalStateException("PHP not running in a servlet environment");
    }
    
    /**
     * Throws IllegalStateException
     * @return none
     */
    public Object getHttpServletResponse() {
	throw new IllegalStateException("PHP not running in a servlet environment");
    }
    /**
     * Throws IllegalStateException
     * @return none
     */
    public Object getServlet() {
	throw new IllegalStateException("PHP not running in a servlet environment");
    }
    /**
     * Throws IllegalStateException
     * @return none
     */
    public Object getServletConfig() {
	throw new IllegalStateException("PHP not running in a servlet environment");
    }

    /**{@inheritDoc}*/
    public String getRealPath(String path) {
	return php.java.bridge.http.Context.getRealPathInternal(path);
    }
}
