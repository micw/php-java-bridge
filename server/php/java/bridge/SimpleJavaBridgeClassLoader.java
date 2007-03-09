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

import java.io.File;
import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.LinkedList;


/**
 * A bridge pattern which allows us to vary the class loader as run-time.
 * The decision is based on whether we are allowed to use a dynamic
 * classloader or not (cl==null) or security exception at run-time.
 * @see java.lang.ClassLoader
 */
public class SimpleJavaBridgeClassLoader {

    DynamicJavaBridgeClassLoader cl = null;
    ClassLoader scl = null;
    
    /** The default class loader used by the PHP/Java Bridge */
    public static final ClassLoader DEFAULT_CLASS_LOADER = getDefaultClassLoader(); 
    private static final ClassLoader getContextClassLoader() {
        ClassLoader loader = Thread.currentThread().getContextClassLoader();
	if(loader==null) loader = JavaBridge.class.getClassLoader();
        return loader;
    }
    private static ClassLoader getDefaultClassLoader() {
	ClassLoader contextLoader = getContextClassLoader();
	try {
	    URL url[] = buildUrlArray();
	    if(url!=null && url.length > 0) return new URLClassLoader(url, contextLoader); 
	    return contextLoader;
	} catch (Exception e) {
	    e.printStackTrace();
	    return contextLoader;
	}
    }
    private static URL[] buildUrlArray() throws MalformedURLException {
        LinkedList list = new LinkedList();
        for(int i=0; i<Util.DEFAULT_EXT_DIRS.length; i++) {
            String arg = Util.DEFAULT_EXT_DIRS[i];
            File f = new File(arg);
            DynamicJavaBridgeClassLoader.addJars(list, f);
            list.add(new URL("file", null, f.getAbsolutePath()+"/"));
        }
        // PR1502480
        String ext = System.getProperty("php.java.bridge.base",  System.getProperty("user.home"));
	if(ext!=null && ext.length()>0) {
            StringBuffer buf = new StringBuffer(ext);
            if(!ext.endsWith(File.separator)) buf.append(File.separator);
            File f = new File(buf.toString());
            DynamicJavaBridgeClassLoader.addJars(list, f);
            buf.append("lib");
            f = new File(buf.toString());
            DynamicJavaBridgeClassLoader.addJars(list, f);
            list.add(new URL("file", null, f.getAbsolutePath()+"/"));
        }
        
        return (URL[]) list.toArray(new URL[list.size()]);
    }
    /**
     * Create a bridge ClassLoader using a dynamic loader.
     * @param loader The dynamic loader, may be null.
     */
    protected SimpleJavaBridgeClassLoader(DynamicJavaBridgeClassLoader loader) {
    	cl = loader;
    	if(cl==null)
    	    scl = DEFAULT_CLASS_LOADER; 
	else 
	    cl.clear();
    }

    /**
     * Create a bridge class loader using the default class loader.
     *
     */
    public SimpleJavaBridgeClassLoader() {
        this((DynamicJavaBridgeClassLoader)null);
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
    public void setClassLoader(DynamicJavaBridgeClassLoader loader) throws IOException {
        if(loader==null) { cachedPath = null; cl = null; return; }
	if(cl != null) throw new IllegalStateException("cl");
	cl = loader;
	if(cachedPath != null) 
	    cl.updateJarLibraryPath(cachedPath);
    }

    /**
     * Append the path to the current library path
     * @param path A file or url list, separated by ';' 
     * @param extensionDir Usually ini_get("extension_dir"); 
     * @throws IOException 
     */
    public void updateJarLibraryPath(String path, String extensionDir) throws IOException  {
	if(!checkCl()) {
	    /*
	     * We must check the path now. But since we don't have
	     * a thread from our pool yet, we don't have a
	     * DynamicJavaBridgeClassLoader instance.  Save the path
	     * for the next statement, which is usually executed from
	     * the ContextRunner. -- If this is a HTTP tunnel, which
	     * doesn't have a ContextRunner, an exception will be
	     * thrown.
	     */
	    cachedPath = DynamicJavaBridgeClassLoader.checkJarLibraryPath(path, extensionDir);
	    return;
	}

	cl.updateJarLibraryPath(path, extensionDir);
    }

    /**
     * Only for internal use
     * @return the classloader
     */
    public ClassLoader getClassLoader() {
	if(checkCl()) return (ClassLoader)cl;
	return scl;
    }
    protected void doReset() {
	cl.reset(); 
	cl=cl.clearVMLoader();
    }
    /**
     * reset loader to the initial state
     */
    public void reset() {
	if (checkCl()) doReset();
    }
    protected void doClearCaches() {
	 cl.clearCaches(); 
	 cl=cl.clearVMLoader();
    }
    /**
     * clear all loader caches but
     * not the input vectors
     */
    public void clearCaches() {
	if (checkCl()) doClearCaches();
    }

    /**
     * Load a class.
     * @param name The class, for example java.lang.String
     * @return the class
     * @throws ClassNotFoundException
     */
    public Class forName(String name) throws ClassNotFoundException {
    	if(!checkCl()) return Class.forName(name, false, scl);
    	return Class.forName(name, false, cl);
    }
    protected void doClear() {
	cl.clear(); 
	cl=cl.clearVMLoader();
    }
    /**
     * clear caches and the input vectors
     */
    public void clear() {
	if(checkCl()) doClear();
    }
    
    /** re-initialize for keep alive */
    protected void recycle() {
      	clear();
      	cachedPath = null;
    }
}
