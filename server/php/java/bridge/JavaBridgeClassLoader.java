/*-*- mode: Java; tab-width:8 -*-*/

package php.java.bridge;


/**
 * A bridge pattern which allows us to vary the class loader as run-time.
 * The decision is based on whether we are allowed to use a dynamic
 * classloader or not (cl==null) or security exception at run-time.
 * @see DynamicJavaBridgeClassLoader
 * @see java.lang.ClassLoader
 */
public class JavaBridgeClassLoader {

    DynamicJavaBridgeClassLoader cl = null;
    ClassLoader scl = null;
    private JavaBridge bridge;

    public JavaBridgeClassLoader(JavaBridge bridge1, DynamicJavaBridgeClassLoader loader) {
    	this.bridge = bridge1;
    	this.cl = loader;

    	if(this.cl==null) 
	    this.scl = bridge.getClass().getClassLoader();
	else 
	    cl.clear();
    }


    private String cachedPath, cachedExtensionDir;
    private boolean checkCl() {
	if(cl==null) {
	    if(cachedPath!=null) throw new IllegalStateException("cachedPath");
	    return false;
	}
	return true;
    }

    /**
     * Set a DynamicJavaBridgeClassLoader.
     * @param loader The dynamic class loader
     */
    public void setClassLoader(DynamicJavaBridgeClassLoader loader) {
        if(loader==null) { cachedPath = null; cl = null; return; }
	if(cl != null) throw new IllegalStateException("cl");
	cl = loader;
	if(cachedPath != null) 
	    cl.updateJarLibraryPath(cachedPath, cachedExtensionDir);
    }

    /**
     * Append the path to the current library path
     * @param path A file or url list, separated by ';' 
     * @param extensionDir Usually ini_get("extension_dir"); 
     */
    public void updateJarLibraryPath(String path, String extensionDir)  {
	if(!checkCl()) {
	    cachedPath = path;
	    cachedExtensionDir = extensionDir;
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

    /**
     * reset loader to the initial state
     */
    public void reset() {
	if (checkCl()) cl.reset();
    }

    /**
     * clear all loader caches but
     * not the input vectors
     */
    public void clearCaches() {
	if (checkCl()) cl.clearCaches();
    }

    /**
     * Load a class.
     * @param name The class, for example java.lang.String
     * @return the class
     * @throws ClassNotFoundException
     */
    public Class forName(String name) throws ClassNotFoundException {
    	if(!checkCl()) return Class.forName(name, false, scl);
    	return cl.loadClass(name);
    }
    /**
     * clear the input vectors
     */
    public void clear() {
	if(checkCl()) cl.clear();
    }
    
    /** re-initialize for keep alive */
    protected void recycle() {
      	clear();
      	cachedPath = null;
	cachedExtensionDir = null;
    }
}
