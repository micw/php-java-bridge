/*-*- mode: Java; tab-width:8 -*-*/

package php.java.bridge;

import java.io.File;
import java.lang.ref.WeakReference;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.Iterator;
import java.util.StringTokenizer;

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

	public static class Bridge {
		JavaBridgeClassLoader cl = null;
		ClassLoader scl = null;
		JavaBridge bridge;

		public Bridge(JavaBridge bridge) {
			this.bridge = bridge;
			try {
				cl=bridge.createJavaBridgeClassLoader();
			} catch (java.security.AccessControlException ex) {
				scl = bridge.getClass().getClassLoader();
			}
		}

		public void updateJarLibraryPath(String path)  {
			if(cl==null) {
			bridge.logMessage("You don't have permission to call java_set_library_path() or java_require(). Please store your libraries in the lib folder within JavaBridge.war");
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
