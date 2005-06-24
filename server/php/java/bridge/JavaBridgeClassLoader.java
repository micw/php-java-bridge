/*-*- mode: Java; tab-width:8 -*-*/

package php.java.bridge;


/**
 * Basic interface a Classloader needs to implement (apart from being a ClassLoader).
 * The static Bridge abstracts everything even further if we don't have permission to create our own classloader
 *
 * By default, the old "ClassicJavaBridgeClassLoader" will be used.
 * If classloader=Dynamic is configured in the PHPJavaBridge.ini in the PHP Config Dir (where java.so and so on also resides),
 * the new DynamicJavaBridgeClassLoader
 * (See JavaBridge.initGlobals() for details)
 * The exact location of the PHPJavaBridge.ini is also recorded at the start of the Logfile.
 */
public interface JavaBridgeClassLoader {

	/*
	 * A bridge pattern which allows us to vary the class loader as run-time.
	 * The decision is based on whether we are allowed to use a dynamic
	 * classloader or not.
	 */
	public static class Bridge {
		JavaBridgeClassLoader cl = null;
		ClassLoader scl = null;

		public Bridge() {
			try {
				cl = new DynamicJavaBridgeClassLoader();
			} catch (java.security.AccessControlException ex) {
				scl = getClass().getClassLoader();
			}
		}

		public void updateJarLibraryPath(String path)  {
			if(cl==null) {
			Util.logMessage("You don't have permission to call java_set_library_path() or java_require(). Please store your libraries in the lib folder within JavaBridge.war");
			return;
			}

			cl.updateJarLibraryPath(path);
		}

		public ClassLoader getClassLoader() {
			if(cl!=null) return (ClassLoader)cl;
			return scl;
		}

        public void reset() {
                if (cl!=null) cl.reset();
        }
    }

    public void updateJarLibraryPath(String _path);

    public Class loadClass(String name) throws ClassNotFoundException;

    public void reset();

}
