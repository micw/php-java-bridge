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

    public void updateJarLibraryPath(String path)  {
	if(cl==null) {
	    bridge.logMessage("You don't have permission to call java_set_library_path() or java_require(). Please store your libraries in the lib folder within JavaBridge.war");
	    return;
	}

	cl.updateJarLibraryPath(path);
    }

//    public ClassLoader getClassLoader() {
//	if(cl!=null) return (ClassLoader)cl;
//	return scl;
//    }

    public void reset() {
	if (cl!=null) cl.reset();
    }

    public Class forName(String name) throws ClassNotFoundException {
    	if(cl==null) return Class.forName(name, false, scl);
    	return cl.loadClass(name);
    }

}
