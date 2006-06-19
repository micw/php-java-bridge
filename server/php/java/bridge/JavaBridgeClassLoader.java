/*-*- mode: Java; tab-width:8 -*-*/

package php.java.bridge;



/**
 * A bridge pattern which allows us to vary the class loader as run-time.
 * The decision is based on whether we are allowed to use a dynamic
 * classloader or not (cl==null) or security exception at run-time.
 * @see DynamicJavaBridgeClassLoader
 * @see java.lang.ClassLoader
 */
public class JavaBridgeClassLoader extends SimpleJavaBridgeClassLoader {
    /**
     * Create a bridge ClassLoader using a dynamic loader.
     * @param loader The dynamic loader, may be null.
     */
    public JavaBridgeClassLoader(DynamicJavaBridgeClassLoader loader) {
        super(loader);
    }
    /**
     * Create a bridge class loader using the default class loader.
     *
     */
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
}
