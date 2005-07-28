/*-*- mode: Java; tab-width:8 -*-*/

package php.java.bridge;

import java.io.File;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.ArrayList;
import java.util.Collection;
import java.util.StringTokenizer;

public class DynamicJavaBridgeClassLoader extends DynamicClassLoader {

    // the local library directory (global one is /usr/share/java)
    static private String phpLibDir, phpConfigDir;

    // the list of jar files in which we search for user classes.
    static private Collection sysUrls = null;

    protected DynamicJavaBridgeClassLoader(ClassLoader parent) {
    	super(parent);
    }
    protected DynamicJavaBridgeClassLoader() {
    	super();
    }
	// Set the library path for the java bridge. Examples:
    // setJarLibPath(";file:///tmp/test.jar;file:///tmp/my.jar");
    // setJarLibPath("|file:c:/t.jar|http://.../a.jar|jar:file:///tmp/x.jar!/");
    // The first char must be the token separator.
    public void updateJarLibraryPath(String _path) {

	ArrayList toAdd = new ArrayList();
        if(_path==null || _path.length()<2) return;
        String oldp = _path;
	// add a token separator if first char is alnum
	char c=_path.charAt(0);
	if((c>='A' && c<='Z') || (c>='a' && c<='z') ||
	   (c>='0' && c<='9') || (c!='.' || c!='/'))
	    _path = ";" + _path;

	String path = _path.substring(1);
	StringTokenizer st = new StringTokenizer(path, _path.substring(0, 1));
	while (st.hasMoreTokens()) {
	    URL url;
	    String p, s;
	    s = st.nextToken();

	    try {
		url = new URL(s);
		p = url.getProtocol();
	    } catch (MalformedURLException e) {
		try {
		    if(new File(s).isFile()) {
			s = "file:" + s;
		    } else if (new File(phpLibDir + s).isFile()) {
			s = "file:" + phpLibDir + s;
		    } else if (new File("/usr/share/java/" + s).isFile()) {
			s = "file:" + "/usr/share/java/" + s;
		    } else {
			s = "file:" + s;
		    }
		    url = new URL(s);
		    p = url.getProtocol();
		}  catch (MalformedURLException e1) {
		    Util.printStackTrace(e1);
		    continue;
		}
	    }
	    toAdd.add(url);
	}
        URL urls[] = new URL[toAdd.size()];
        toAdd.toArray(urls);
        addURLs(oldp, urls, false); // Uses protected method to explicitly set the classpath entry that is added.
    }
    //
    // add all jars found in the phpConfigDir/lib and /usr/share/java
    // to the list of our URLs.  The user is expected to symbol .jar
    // libraries explicitly with java_set_library_path, e.g.
    // java_set_library_path("foo.jar;bar.jar"); For backward
    // compatibility we add all URLs we encountered during startup
    // before throwing a "ClassNotFoundException".
    //
    public static synchronized void initClassLoader(String phpConfigDir) {
        DynamicJavaBridgeClassLoader.phpLibDir=phpConfigDir + "/lib/";
        DynamicJavaBridgeClassLoader.phpConfigDir=phpConfigDir;
	sysUrls=new ArrayList();
	try {
	    String[] paths;
	    if(null!=phpConfigDir)
		paths = new String[] {phpConfigDir+"/lib", "/usr/share/java", "/usr/share/java/ext"};
	    else
		paths = new String[] {"/usr/share/java", "/usr/share/java/ext"};

	    for(int i=0; i<paths.length; i++) {
		File d = new File(paths[i]);
		String[] files=d.list();
		if(files==null) continue;
		for(int j=0; j<files.length; j++) {
		    String file = files[j];
		    int len = file.length();
		    if(len<4) continue;
		    if(!file.endsWith(".jar")) continue;
		    try {
			URL url;
			file = "file:" + d.getAbsolutePath() + File.separator + file;
			url = new URL(file);
			Util.logMessage("Found system library: "+ files[j] +" (url: " + url +").");
			sysUrls.add(url);
		    }  catch (MalformedURLException e1) {
			Util.printStackTrace(e1);
		    }
		}
	    }
	} catch (Exception t) {
	    Util.printStackTrace(t);
	}
    }

    public void addSysUrls() {
        URL urls[] = new URL[sysUrls.size()];
	synchronized(getClass()) {
            sysUrls.toArray(urls);
	 }
         this.addURLs(urls, true);
    }

    /*
     *  (non-Javadoc)
     * @see php.java.bridge.DynamicClassLoader#clear()
     */
    public void clear() {
    	super.clear();
    	addSysUrls();
    }
    /*
     * Reset to initial state.
     */
    public void reset() {
	synchronized(getClass()) {
	    initClassLoader(Util.EXTENSION_DIR);
	    clear();
	    clearCache();
	}
    }

    /*
     * Create an instance of the dynamic java bridge classloader
     * It may return null due to security restrictions on certain systems, so don't
     * use this method directly but call: 
     * new JavaBridgeClassLoader(bridge, DynamicJavaBridgeClassLoader.newInstance()) instead.
     */
    public static synchronized DynamicJavaBridgeClassLoader newInstance() {
    	try {
    	    DynamicJavaBridgeClassLoader cl = new DynamicJavaBridgeClassLoader();
    	    return cl;
   	} catch (java.security.AccessControlException e) {
    	    return null;
    	}
    }
    public static synchronized DynamicJavaBridgeClassLoader newInstance(ClassLoader parent) {
    	try {
    	    DynamicJavaBridgeClassLoader cl = new DynamicJavaBridgeClassLoader(parent);
    	    return cl;
   	} catch (java.security.AccessControlException e) {
    	    return null;
    	}
    }
    
}
