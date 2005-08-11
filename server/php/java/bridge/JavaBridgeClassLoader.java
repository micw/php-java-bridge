/*-*- mode: Java; tab-width:8 -*-*/

package php.java.bridge;


/*
 * A bridge pattern which allows us to vary the class loader as run-time.
 * The decision is based on whether we are allowed to use a dynamic
 * classloader or not (loader==null).
 */
public class JavaBridgeClassLoader {

    DynamicJavaBridgeClassLoader cl = null;
    ClassLoader scl = null;
    private JavaBridge bridge;

    public JavaBridgeClassLoader(JavaBridge bridge, DynamicJavaBridgeClassLoader loader) {
    	this.bridge = bridge;
    	this.cl = loader;

    	if(this.cl==null) 
	    this.scl = bridge.getClass().getClassLoader(); 
	else 
	    cl.clear();
    }

    /**
     * Append the path to the current library path
     * @param path A file or url list, separated by ';' 
     * @param extensionDir Usually ini_get("extension_dir"); 
     */
    public void updateJarLibraryPath(String path, String extensionDir)  {
	if(cl==null) {
	    bridge.logMessage("You don't have permission to call java_set_library_path() or java_require(). Please store your libraries in the lib folder within JavaBridge.war");
	    return;
	}

	cl.updateJarLibraryPath(path, extensionDir);
    }

    public ClassLoader getClassLoader() {
	if(cl!=null) return (ClassLoader)cl;
	return scl;
    }

    /*
     * reset loader to the initial state
     */
    public void reset() {
	if (cl!=null) cl.reset();
    }

    /*
     * clear all loader caches but
     * not the input vectors
     */
    public void clearCaches() {
	if (cl!=null) cl.clearCaches();
    }

    public Class forName(String name) throws ClassNotFoundException {
    	if(cl==null) return Class.forName(name, false, scl);
    	return cl.loadClass(name);
    }

}
