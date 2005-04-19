/*-*- mode: Java; tab-width:8 -*-*/

package php.java.bridge;

import java.io.File;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Iterator;
import java.util.StringTokenizer;

public class JavaBridgeClassLoader extends URLClassLoader {
	
    public JavaBridgeClassLoader() {
        // Since 2.0.6 this is an empty list. jpackage requires that
        // the user names the .jar file explicitly, for example via
        // java_set_library_path("kawa-1.6.jar") or
        // java_set_library_path("kawa.jar");
	super(new URL[] {});	
    }
    // the local library directory (global one is /usr/share/java)
    static private String phpLibDir;

    // the list of jar files in which we search for user classes.
    static private Collection sysUrls = null;

    // Set the library path for the java bridge. Examples:
    // setJarLibPath(";file:///tmp/test.jar;file:///tmp/my.jar");
    // setJarLibPath("|file:c:/t.jar|http://.../a.jar|jar:file:///tmp/x.jar!/");
    // The first char must be the token separator.
    public void setJarLibraryPath(String _path) {
	if(_path==null || _path.length()<2) return;

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
	    addURL(url);
	}
    }
    //
    // add all jars found in the phpConfigDir/lib and /usr/share/java
    // to the list of our URLs.  The user is expected to name .jar
    // libraries explicitly with java_set_library_path, e.g.
    // java_set_library_path("foo.jar;bar.jar"); For backward
    // compatibility we add all URLs we encountered during startup
    // before throwing a "ClassNotFoundException".
    //
    static void initClassLoader(String phpConfigDir) {
        JavaBridgeClassLoader.phpLibDir=phpConfigDir + "/lib/";
	sysUrls=new ArrayList();
	try {
	    String[] paths;
	    if(null!=phpConfigDir) 
		paths = new String[] {phpConfigDir+"/lib", "/usr/share/java"};
	    else
		paths = new String[] {"/usr/share/java"};
			
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

    // For backward compatibility.  Since 2.0.6 we don't add sysUrls
    // automatically, the user is expected to reference them via
    // e.g. java_set_library_path("foo-x.y.jar;bar-v.z.jar;...").  Before
    // throwing a ClassNotFoundException we emulate the pre 2.0.6
    // behaviour by adding the sysUrls ourselfs.
    private void addSysUrls() {
	for(Iterator e = sysUrls.iterator(); e.hasNext();) {
	    addURL((URL)e.next());
	}
    }
    protected Class findClass(String name) throws ClassNotFoundException { 
	Class clazz=null;
	try {
	    clazz=super.findClass(name);
	} catch (ClassNotFoundException e) {   
	    Util.logMessage("Could not find class " + name + ". Adding all system libraries.  Please use java_set_library_path(\"...;<system library containing " + name + ">\") to avoid this message.");
	    addSysUrls();
	    clazz=super.findClass(name); 
	}
	return clazz;
    }
    public URL findResource(String name) {
	URL url = super.findResource(name);
	if(url==null) { 
	    Util.logMessage("Could not find resource " + name + ". Adding all sysUrls.  Please use java_set_library_path(\"...;<system library containing " + name + ">\") to avoid this message.");
	    addSysUrls();
	    url = super.findResource(name);
	}
	return url;
    }
} 
