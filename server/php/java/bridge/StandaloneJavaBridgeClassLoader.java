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

import java.io.IOException;


/**
 * <p>
 * A bridge pattern which allows us to vary the class loader as run-time.
 * The decision is based on whether we are allowed to use a dynamic
 * classloader or not (cl==null) or security exception at run-time.
 * </p>
 * <p>
 * The SimpleJavaBridgeClassLoader should be used outside of the J2EE environment. 
 * If you need to switch from a servlet to a ContextRunner thread, use the JavaBridgeClassLoader instead.
 * </p>
 * @see php.java.bridge.JavaBridgeClassLoader
 */
public class StandaloneJavaBridgeClassLoader extends SimpleJavaBridgeClassLoader {

    protected DynamicJavaBridgeClassLoader cl = null;
    
    /**
     * Create a bridge ClassLoader using a dynamic loader.
     * @param loader The dynamic loader, may be null.
     */
    private StandaloneJavaBridgeClassLoader(DynamicJavaBridgeClassLoader loader, ClassLoader xloader) {
	super(xloader); 
	cl = loader;
	if (checkCl()) cl.clear();
    }

    /**
     * Create a new JavaBridgeClassLoader
     * @param xloader The delegate
     */
    public StandaloneJavaBridgeClassLoader(ClassLoader xloader) {
	this(null, xloader);
    }
    /** pass path from the first HTTP statement to the ContextRunner */
    protected JarLibraryPath cachedPath;
    protected boolean checkCl() {
      	return cl!=null;
    }
    /**
     * Set a DynamicJavaBridgeClassLoader.
     * @param loader The dynamic class loader
     * @throws IOException 
     */
    protected void setClassLoader(DynamicJavaBridgeClassLoader loader) {
        if(loader==null) { cachedPath = null; cl = null; return; }
	if(cl!=null) throw new IllegalStateException("cl");
	cl = loader;
	if(cachedPath != null) {
	    cl.updateJarLibraryPath(cachedPath);
	}
    }
    /**
     * Append the path to the current library path
     * @param path A file or url list, separated by ';' 
     * @param extensionDir Usually ini_get("extension_dir"); 
     * @param searchpath 
     * @param cwd 
     * @throws IOException 
     */
    public void updateJarLibraryPath(String path, String extensionDir, String cwd, String searchpath) throws IOException  {
	if(!checkCl()) {
	    DynamicJavaBridgeClassLoader loader = DynamicJavaBridgeClassLoader.newInstance(scl);
	    setClassLoader(loader);
	    try {
		Thread.currentThread().setContextClassLoader(getClassLoader());
	    }catch (SecurityException e) {Util.printStackTrace(e);}
	}
	    
	cl.updateJarLibraryPath(path, extensionDir, cwd, searchpath);
    }

    /**
     * Only for internal use
     * @return the classloader
     */
    public ClassLoader getClassLoader() {
	if(checkCl()) return (ClassLoader)cl;
	return scl;
    }
        
    /**
     * Reset the loader.
     * This is only called by the API function "java_reset()", used only for testing.
     */
    protected void doReset() {
	cl.reset(); 
	cl=cl.clearVMLoader();
	try {
	    Thread.currentThread().setContextClassLoader(cl);
	}catch (SecurityException e) {Util.printStackTrace(e);}
    }
    /**
     * reset loader to the initial state
     * This is only called by the API function "java_reset()", used only for testing.
     */
    public void reset() {
	if (checkCl()) doReset();
    }

    /**
     * Load a class.
     * @param name The class, for example java.lang.String
     * @return the class
     * @throws ClassNotFoundException
     */
    public Class forName(String name) throws ClassNotFoundException {
    	if(!checkCl()) return super.forName(name);
    	return Class.forName(name, false, cl);
    }
    protected void doClear() {
	cl.clear(); 
    }
    /**
     * clear caches and the input vectors
     */
    public void clear() {
	if(checkCl()) doClear();
    }
    
    /** re-initialize for keep alive */
    protected void recycle() {
      	cl = null;
      	cachedPath = null;
      	try {
      	    Thread.currentThread().setContextClassLoader(scl);
      	}catch (SecurityException e) {if (Util.logLevel>4) Util.printStackTrace(e);}
    }
    /**
     * Switch the current thread context class loader.
     * @throws IllegalStateException A SimpleJavaBridgeClassLoader cannot be used in a web environment.
     */
    public void switcheThreadContextClassLoader() {
	throw new IllegalStateException("Use the JavaBridgeClassLoader instead");
    }
}