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

import php.java.bridge.PhpProcedureProxy;


/**
 * A simple ScriptContext which can be used in servlet- or standalone environments.
 * 
 * @author jostb
 *
 */
public abstract class AbstractPhpScriptContext extends SimpleScriptContext implements IPhpScriptContext {

   protected HttpProxy kont;

    /** Integer value for the level of SCRIPT_SCOPE */
    public static final int REQUEST_SCOPE = 0;
    
    /** Integer value for the level of SESSION_SCOPE */   
    public static final int SESSION_SCOPE = 150;
    
    /** Integer value for the level of APPLICATION_SCOPE */
    public static final int APPLICATION_SCOPE = 175;

    /** {@inheritDoc} */
   public Writer getWriter() {
	if(writer == null) writer =  super.getWriter ();
	if(! (writer instanceof PhpScriptWriter)) setWriter(writer);
	return writer;
   }

   /** {@inheritDoc} */
   public Writer getErrorWriter() {
	if(errorWriter == null) errorWriter = super.getErrorWriter ();
	if(! (errorWriter instanceof PhpScriptWriter)) setErrorWriter(errorWriter);
	return errorWriter;	
   }


   /**
    * Ignore the default java_context()-&gt;call(java_closure()) call at the end
    * of the invocable script, if the user has provided its own.
    */
   private boolean continuationCalled;

    /**@inheritDoc*/
    public void setContinuation(HttpProxy kont) {
	    this.kont = kont;
	    continuationCalled = false;
    }
    /**@inheritDoc*/
    public HttpProxy getContinuation() {
	    return kont;
    }
    /**@inheritDoc*/
    public boolean call(PhpProcedureProxy kont) throws Exception {
	    if(!continuationCalled) {
		    this.kont.call(kont);
		    continuationCalled = true;
	    }
	    return true;
    }

    /**
     * Sets the <code>Writer</code> for scripts to use when displaying output.
     *TODO: test
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
}
