/*-*- mode: Java; tab-width:8 -*-*/

package php.java.bridge.http;

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

import java.io.IOException;
import java.io.PrintWriter;
import java.io.Writer;
import java.util.HashMap;
import java.util.Map;


import php.java.bridge.Invocable;
import php.java.bridge.PhpProcedureProxy;


/**
 * Emulates a JSR223 script context when the JSR223 classes are not available. 
 * The method call(kont) returns false, so that it can be used to check if a script was called from java:<br>
 * <code>
 * function toString() {return "hello java, I am a php script, but in your eyes I am an ordinary java object...";}<br>
 * java_context()-&gt;call(java_closure()) || die("This script must be called from java!");
 * </code>
 * @see php.java.script.IPhpScriptContext
 * @see javax.script.ScriptContext
 * @see php.java.script.PhpScriptContext
 * @author jostb
 *
 */
public class Context implements Invocable, IContext {

    /** Map of the scope of level GLOBAL_SCOPE */
    private Map globalScope;
    
    /** Map of the scope of level ENGINE_SCOPE */
    private Map engineScope;

    protected Context() {}
    /**{@inheritDoc}*/
    public Object getAttribute(String name) throws IllegalArgumentException{
      
        if (name == null) {
            throw new IllegalArgumentException("name cannot be null");
        }
        
        if (getEngineScope().get(name) != null) {
            return getEngineScope().get(name);
        } else if (getGlobalScope().get(name) != null) {
            return getGlobalScope().get(name);
        } else {
            return null;            
        }
    }

    /**{@inheritDoc}*/
    public Object getAttribute(String name, int scope) 
	throws IllegalArgumentException{
        
        if (name == null) {
            throw new IllegalArgumentException("name cannot be null");
        }
        
        switch (scope) {
	case ENGINE_SCOPE:
	    return getEngineScope().get(name);
	case GLOBAL_SCOPE:
	    return getGlobalScope().get(name);
	default:
	    throw new IllegalArgumentException("invalid scope");
        }
    }

    /**{@inheritDoc}*/
    public int getAttributesScope(String name) {
        if (getEngineScope().containsKey(name)) {
            return ENGINE_SCOPE;
        } else if(getGlobalScope().containsKey(name)) {
            return GLOBAL_SCOPE;
        }
        
        return -1;
    }
    
    /**{@inheritDoc}*/
    public Writer getWriter() throws IOException {
        
        // autoflush is true so that I can see the output immediately
        return new PrintWriter(System.out, true); 
    }
    
    /** {@inheritDoc}*/
    public Object removeAttribute(String name, int scope) 
	throws IllegalArgumentException{ 
        
        if (name == null) {
            throw new IllegalArgumentException("name is null");
        }
        
        switch (scope) {
	case ENGINE_SCOPE:
	    return getEngineScope().remove(name);
	case GLOBAL_SCOPE:
	    return getGlobalScope().remove(name);
	default:
	    throw new IllegalArgumentException("invalid scope");
        }    
    }
    
    /** {@inheritDoc}*/
    public void setAttribute(String name, Object value, int scope) 
	throws IllegalArgumentException{
        
        if (name == null) {
            throw new IllegalArgumentException("name is null");
        }
        
        switch (scope) {
	case ENGINE_SCOPE:
	    getEngineScope().put(name, value);
	    break;
	case GLOBAL_SCOPE:
	    getGlobalScope().put(name, value);
	    break;
	default:
	    throw new IllegalArgumentException("invalid scope");
        }
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
     * @param kont dummy
     * @return false
     */
    public boolean call(PhpProcedureProxy kont) {
	return false;
    }
    protected void setGlobalScope(Map globalScope) {
	this.globalScope = globalScope;
    }
    protected Map getGlobalScope() {
	if(globalScope==null) globalScope=new HashMap();
	return globalScope;
    }
    protected void setEngineScope(Map engineScope) {
	this.engineScope = engineScope;
    }
    protected Map getEngineScope() {
	if(engineScope==null) engineScope=new HashMap();
	return engineScope;
    }
}
