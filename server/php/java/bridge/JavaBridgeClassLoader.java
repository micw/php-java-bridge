package php.java.bridge;

import java.io.File;
import java.io.InputStream;
import java.lang.ref.WeakReference;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLConnection;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.Hashtable;
import java.util.Iterator;
import java.util.StringTokenizer;
import java.util.Vector;

/*
 * Created on Feb 13, 2005
 *
 * TODO To change the template for this generated file go to
 * Window - Preferences - Java - Code Style - Code Templates
 */

public class JavaBridgeClassLoader extends ClassLoader {
    // class hash
    private static final HashMap classes = new HashMap(); 

    // the list of jar files in which we search for user classes.
    static private Collection sysUrls = null;

    // the list of jar files in which we search for user classes.  can
    // be changed with setLibraryPath
    private Collection urls = null;		

    //
    // add all jars found in the phpConfigDir/lib and /usr/share/java
    // to our classpath
    //
    static void addSystemLibraries(String phpConfigDir) {
	try {
	    String[] paths = {phpConfigDir+"/lib", "/usr/share/java"};
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
			file = "jar:file:" + d.getAbsolutePath() + File.separator + file + "!/";
			url = new URL(file);
			if(sysUrls==null) sysUrls=new ArrayList();
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
    // Read the class from input stream and return bytes or null
    private byte[] read (InputStream in, int length) throws java.io.IOException {
	int c, pos;
	byte[] b = new byte[length];
 
	for(pos=0; (c=in.read(b, pos, b.length-pos))<length-pos; pos+=c) {
	    if(c<0) { in.close(); return null; }
	}

	in.close();
	return b;
    }

    // Read the class in 8K chunks until EOF, return bytes or null
    private byte[] readChunks(InputStream in) throws java.io.IOException {
	int c, pos;
	int len = 8192;
	byte[] b = new byte[len];

	Collection buffers = new ArrayList();
	while(true) {
	    for(pos=0; (c=in.read(b, pos, len-pos))<len-pos; pos+=c) {
		if(c<0) break;
	    }
	    if(c<0) break;
	    buffers.add(b);
	    b=new byte[len];
	}
	byte[] result = new byte[buffers.size() * len + pos];
	int p=0;
	for (Iterator i=buffers.iterator(); i.hasNext(); p+=len){
	    byte[] _b = (byte[])i.next();
	    System.arraycopy(_b, 0, result, p, len);
	}
	System.arraycopy(b, 0, result, p, pos);
	in.close();
	return result;
    }	

    byte[] load(URL u, String name) throws Exception {
	Util.logMessage("try to load class " + name + " from " + u);
	byte b[]=null;
	int pt;
	String p, h, f;
		
	p = u.getProtocol(); h = u.getHost(); 
	pt = u.getPort(); f = u.getFile();
	URL url = new URL(p ,h , pt, f+name.replace('.','/')+".class");
		
	URLConnection con = url.openConnection();
	con.connect();
	int length = con.getContentLength();
	InputStream in = con.getInputStream();
		
	if(length > 0) 
	    b = read(in, length);
	else if(length < 0) // bug in gcj
	    b = readChunks(in);
		
	return b;
    }
    public Class findClass(String name) throws ClassNotFoundException {
	Class c = null;
	byte[] b = null;

	synchronized(classes) {
	    Object o = classes.get(name);
	    if(o!=null) o = ((WeakReference)o).get();
	    if(o!=null) c = (Class)o;
	    if(c!=null) return c;
	    try {
		return ClassLoader.getSystemClassLoader().loadClass(name);
	    } catch (ClassNotFoundException e) {};
 
	    Collection[] allUrls = {urls, sysUrls};
	    ArrayList list = new ArrayList();
	    for(int n=0; b==null && n<allUrls.length; n++) {
		Collection urls = allUrls[n];
		if(urls!=null) 
		    for (Iterator i=urls.iterator(); i.hasNext(); ) {
			URL url = (URL)i.next();
			try {
			    if ((b=load(url, name))!=null) break;
			} catch (Exception e) {
			    Vector v = new Vector();
			    v.add(url); v.add(e);
			    list.add(v);
			}
		    }
	    }
	    if (b==null) throw new ClassNotFoundException(name + " not found: " + String.valueOf(list));

	    if((c = this.defineClass(name, b, 0, b.length)) != null) classes.put(name, new WeakReference(c));
	}
	return c;
    }

    private URL getResource(URL url, String name) {
	try{
	    URL res = new URL(url.getProtocol(), url.getHost(), url.getPort(), url.getFile() + name);
	    Util.logMessage("try to find resource " + res);
	    res.openStream().close();
	    return res;
	} catch (Exception e) {
	    return null;
	}
    }
    public URL findResource(String name) {
	URL res;
	Collection[] allUrls = {urls, sysUrls};
	for(int n=0; n<allUrls.length; n++) {
	    Collection urls = allUrls[n];
	    if(urls!=null) 
		for (Iterator i=urls.iterator(); i.hasNext(); ) 
		    if ((res=getResource((URL)i.next(), name))!=null) return res;
	}
	return null;
    }
    public Enumeration findResources(String name) {
	URL res;
	Hashtable e = new Hashtable();
	Collection[] allUrls = {urls, sysUrls};
	for(int n=0; n<allUrls.length; n++) {
	    Collection urls = allUrls[n];
	    if(urls!=null) 
		for (Iterator i=urls.iterator(); i.hasNext(); ) 
		    if ((res=getResource((URL)i.next(), name))!=null) e.put(new Object(), res);
	}
	return e.elements();
    }
    // Set the library path for the java bridge. Examples:
    // setJarLibPath(";file:///tmp/test.jar;file:///tmp/my.jar");
    // setJarLibPath("|file:c:/t.jar|http://.../a.jar|jar:file:///tmp/x.jar!/");
    // The first char must be the token separator.
    public void setJarLibraryPath(String _path) {
	urls = new ArrayList();
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

	    if(p.equals("jar")) {
		urls.add(url);
		continue;
	    }
	    try {
		urls.add(new URL("jar:"+s+"!/"));
	    } catch (MalformedURLException e) {
		Util.printStackTrace(e);
	    }
	}
    }

} 
