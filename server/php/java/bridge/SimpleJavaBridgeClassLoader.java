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
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.Map;


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
public class SimpleJavaBridgeClassLoader {

    protected DynamicJavaBridgeClassLoader cl = null;
    protected ClassLoader scl = null;
    
    private static final Map map = Collections.synchronizedMap(new HashMap()); //TODO: keep only recent entries
    private static final URL[] urlArray = getUrlArray();
    public static final ClassLoader getContextClassLoader() {
        ClassLoader loader = null;
        try {loader = Thread.currentThread().getContextClassLoader();} catch (SecurityException ex) {/*ignore*/}
	if(loader==null) loader = JavaBridge.class.getClassLoader();
        return loader;
    }
    public static ClassLoader getDefaultClassLoader(ClassLoader contextLoader) {
	ClassLoader el = (ClassLoader) map.get(contextLoader);
	if(el!=null) 
	    return el;
	
	try {
	    URL url[] = urlArray;
	    if(url!=null && url.length > 0)
		map.put(contextLoader, el=new URLClassLoader(url, contextLoader));
	    else
		map.put(contextLoader, el=contextLoader);
	} catch (Exception e) {
	    Util.printStackTrace(e);
	    map.put(contextLoader, el=contextLoader);
	}
	return el;
    }
    private static URL[] getUrlArray() {
	URL[] urls = null;
	try {
	    urls = buildUrlArray();
	} catch (Throwable t) { t.printStackTrace(); }
	return urls;
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
        String ext = Util.JAVABRIDGE_BASE;
	if(ext!=null && ext.length()>0) {
            StringBuffer buf = new StringBuffer(ext);
            File f = new File(buf.toString());
            if(!ext.endsWith(File.separator)) buf.append(File.separator);
            if(Util.HOME_DIR==null || (Util.HOME_DIR!=null && !Util.HOME_DIR.equals(f)))        	
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
    private SimpleJavaBridgeClassLoader(DynamicJavaBridgeClassLoader loader, ClassLoader xloader) {
	cl = loader;
    	if(cl==null)
    	    scl = xloader; 
	else 
	    cl.clear();
    }

    public SimpleJavaBridgeClassLoader() {
	scl = getDefaultClassLoader(getContextClassLoader());
    }

    public SimpleJavaBridgeClassLoader(ClassLoader xloader) {
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
	if(cl != null) throw new IllegalStateException("cl");
	cl = loader;
	if(cachedPath != null) {
	    cl.updateJarLibraryPath(cachedPath);
	}
    }
    /**
     * Append the path to the current library path
     * @param path A file or url list, separated by ';' 
     * @param extensionDir Usually ini_get("extension_dir"); 
     * @throws IOException 
     */
    public void updateJarLibraryPath(String path, String extensionDir) throws IOException  {
	if(!checkCl()) {
	    DynamicJavaBridgeClassLoader loader = DynamicJavaBridgeClassLoader.newInstance(scl);
	    setClassLoader(loader);
	    try {
		Thread.currentThread().setContextClassLoader(loader);
	    }catch (SecurityException e) {Util.printStackTrace(e);}
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
    
    public ClassLoader getDefaultClassLoader() {
	return scl;
    }
    
    /**
     * Reset the loader.
     * This is only called by the API function "java_reset()", which is deprecated.
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
     * This is only called by the API function "java_reset()", which is deprecated.
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
    	if(!checkCl()) return Class.forName(name, false, scl);
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
      	}catch (SecurityException e) {Util.printStackTrace(e);}
    }
    /**
     * Switch the current thread context class loader.
     * @throws IllegalStateException A SimpleJavaBridgeClassLoader cannot be used in a web environment.
     */
    public void switcheThreadContextClassLoader() {
	throw new IllegalStateException("Use the JavaBridgeClassLoader instead");
    }
}