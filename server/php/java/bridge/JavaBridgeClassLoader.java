/*-*- mode: Java; tab-width:8 -*-*/

package php.java.bridge;

import java.io.File;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.ArrayList;
import java.util.Collection;
import java.util.StringTokenizer;

public class JavaBridgeClassLoader extends URLClassLoader {
	
    public JavaBridgeClassLoader() {
    	super((URL[]) sysUrls.toArray(new URL[0]));
    }
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
		    s = "file:" + s;
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
    // to our classpath
    //
    static void initClassLoader(String phpConfigDir) {
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
			Util.logMessage("added system library: " + url);
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

} 
