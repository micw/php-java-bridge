/*-*- mode: Java; tab-width:8 -*-*/

package php.java.bridge;

import java.io.IOException;


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
public class JavaBridgeClassLoader extends StandaloneJavaBridgeClassLoader {
    protected boolean clEnabled = false;
    /**
     * Create a new JavaBridge class loader 
     * @param xloader The real class loader
     */
    public JavaBridgeClassLoader(ClassLoader xloader) {
	super(xloader);
    }
    protected boolean checkCl() {
	if(cl==null) {
	    if(cachedPath!=null) throw new IllegalStateException("java_require() not allowed for the HTTP tunnel. Use a context runner instead.");
	    return false;
	}
	return true;
    }
    /**
     * reset loader to the loader to its initial state, clear the VM cache and set a new ThreadContextClassLoader
     * This is only called by the API function "java_reset()", used only for tests.
     */
    public void reset() {
	if (checkCl())
	    super.doReset();
    }
    /**
     * clear the caches and the input vectors, clear the VM cache and set a new ThreadContextClassLoader
     */
    public void clear() {
	if(checkCl()) {
	    super.doClear();
	}
    }
    /**
     * Append the path to the current library path
     * @param path A file or url list, separated by ';' 
     * @param extensionDir Usually ini_get("extension_dir"); 
     * @param cwd The current working dir
     * @param searchpath The search path
     * @throws IOException 
     */
    public void updateJarLibraryPath(String path, String extensionDir, String cwd, String searchpath) throws IOException {
        if(!clEnabled) {
            /*
             * We must check the path now. But since we don't have
             * a thread from our pool yet, we don't have a
             * DynamicJavaBridgeClassLoader instance.  Save the path
             * for the next statement, which is usually executed from
             * the ContextRunner. -- If this is a HTTP tunnel, which
             * doesn't have a ContextRunner, an exception will be
             * thrown.
             */
            cachedPath = DynamicJavaBridgeClassLoader.checkJarLibraryPath(path, extensionDir, cwd, searchpath);
            return;
        } else super.updateJarLibraryPath(path, extensionDir, cwd, searchpath);
    }
    /**
     * Enable the DynamicJavaBridgeClassLoader, if needed.
     * This method changes the current thread context classLoader as a side effect.
     */
    public void switcheThreadContextClassLoader() {
        clEnabled = true;
        if(cl==null && cachedPath!=null) {
            DynamicJavaBridgeClassLoader loader = DynamicJavaBridgeClassLoader.newInstance(scl);
            setClassLoader(loader);
        }
        try {
            Thread.currentThread().setContextClassLoader(getClassLoader());
        } catch (SecurityException e) {if (Util.logLevel>4) Util.printStackTrace(e);}
    }    
}
