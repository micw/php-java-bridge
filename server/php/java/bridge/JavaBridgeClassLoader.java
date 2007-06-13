/*-*- mode: Java; tab-width:8 -*-*/

package php.java.bridge;


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
 * A bridge pattern which allows us to vary the class loader as run-time.
 * The decision is based on whether we are allowed to use a dynamic
 * classloader or not (cl==null) or security exception at run-time.
 * @see DynamicJavaBridgeClassLoader
 * @see java.lang.ClassLoader
 */
public class JavaBridgeClassLoader extends SimpleJavaBridgeClassLoader {
    private boolean mustReset;
    public JavaBridgeClassLoader(ClassLoader xloader) {
	super(null, xloader);
    }
    public JavaBridgeClassLoader() {
	super();
    }
    protected boolean checkCl() {
	if(cl==null) {
	    if(cachedPath!=null) throw new IllegalStateException("java_require() not allowed for the HTTP tunnel. Use a context runner instead.");
	    return false;
	}
	return true;
    }
    public void setClassLoader(DynamicJavaBridgeClassLoader loader) {
        if(loader!=null && mustReset) { 
            loader.reset(); 
        }
        super.setClassLoader(loader);
    }
    /**
     * reset loader to the loader to its initial state, clear the VM cache and set a new ThreadContextClassLoader
     * This is only called by the API function "java_reset()", which is deprecated.
     */
    public void reset() {
	if (!checkCl()) mustReset=true;
	else { 
	    super.doReset();
	}
    }
    /**
     * clear all loader caches but
     * not the input vectors, clear the VM cache and set a new ThreadContextClassLoader
     */
    public void clearCaches() {
	if (checkCl()) {
	    super.doClearCaches();
	}
    }
    /**
     * clear the caches and the input vectors, clear the VM cache and set a new ThreadContextClassLoader
     */
    public void clear() {
	if(checkCl()) {
	    super.doClear();
	}
    }    
}
