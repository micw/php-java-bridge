/*-*- mode: Java; tab-width:8 -*-*/

package php.java.bridge;

/*
 * Copyright (C) 2003-2007 Jost Boekemeier
 *
 * Permission is hereby granted, free of charge, to any person obtaining a
 * copy of this software and associated documentation files (the "Software"),
 * to deal in the Software without restriction, including without limitation
 * the rights to use, copy, modify, merge, publish, distribute, sublicense,
 * and/or sell copies of the Software, and to permit persons to whom the
 * Software is furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT.  IN NO EVENT SHALL
 * THE COPYRIGHT HOLDER(S) OR AUTHOR(S) BE LIABLE FOR ANY CLAIM, DAMAGES OR
 * OTHER LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE,
 * ARISING FROM, OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR
 * OTHER DEALINGS IN THE SOFTWARE.
 */

import java.io.File;
import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.StringTokenizer;

/** Holds a checked JarLibraryPath entry */
public class JarLibraryPath {
    private String path;

    private boolean isCached;
    private String rawPath, rawContextDir, cwd, searchpath;
    private URL[] urls;

    /** create an invalid entry */
    protected JarLibraryPath() { isCached = true; }
    /** Create a checked JarLibraryPath entry
     * @param rawPath The path or file in the local file system or url
     * @param rawContextDir The context directory, for example c:\php
 * @param searchpath 
 * @param cwd 
     * @throws IOException, if the local path or file does not exist or cannot be accessed
     */
    public JarLibraryPath(String rawPath, String rawContextDir, String cwd, String searchpath) throws IOException {
        if(rawContextDir == null) throw new NullPointerException("rawContextDir");
        this.rawPath = rawPath;
        // How to check that rawContextDir is really a symbol?
        this.rawContextDir = rawContextDir;
        this.path = makePath(rawPath);
        this.cwd = cwd;
        this.searchpath = searchpath;
        
        this.urls = checkURLs();
    }
    private boolean hasResult;
    private int result = 1;

    // maps rawPath -> URL[]
    static Map urlCache = Collections.synchronizedMap(new HashMap()); //TODO: keep only recent entries
    public int hashCode() {
        if(hasResult) return result;
        result = result * 31 + rawPath.hashCode(); 
        result = result * 31 + rawContextDir.hashCode();
        hasResult = true;
        return result;
    }
    public boolean equals(Object o) {
        if(o==null) return false;
        JarLibraryPath that = (JarLibraryPath) o;
        if(rawContextDir != that.rawContextDir) return false;
        if(!rawPath.equals(that.rawPath)) return false;
        return true;
    }
    
    private String makePath(String rawPath) {
      /*
       * rawPath always starts with a token separator, e.g. ";" 
         */
        // add a token separator if first char is alnum
        char c=rawPath.charAt(0);
        if((c>='A' && c<='Z') || (c>='a' && c<='z') ||
    	(c>='0' && c<='9') || (c!='.' || c!='/'))
          rawPath = ";" + rawPath;

        return rawPath;
    }
    private String makeContextDir(String rawContextDir) {
        rawContextDir = new File(rawContextDir, "lib").getAbsolutePath();
        return rawContextDir;
    }
    /**
     * Return the urls associated with this entry
     * @return The url value
     * @throws IOException 
     */
    public URL[] getURLs() {
        return urls;
    }
    private URL[] checkURLs() throws IOException {
        /*
         * Check the cache
         */
        URL[] urls = (URL[]) JarLibraryPath.urlCache.get(this);
        if(urls != null) { this.urls = urls; isCached = true; return urls; } 

        isCached = false;
        return createUrls();
    }
    static File checkSearchPath(String s, String searchpath) {
	if(searchpath==null) return null;
	
	StringTokenizer st = new StringTokenizer(searchpath, File.pathSeparator);
	File f = new File(s);
	boolean hasParent = f.getParent() != null;
	if(!hasParent) {       /* find path/ITEMNAME/ITEMNAME.jar */
	    int idx = s.lastIndexOf('.');
	    if(idx!=-1) s = s.substring(0, idx) + File.separator + s;
	}
	while (st.hasMoreTokens()) {
	    String el = st.nextToken();
	    if ((f=new File(el, s)).isFile()) {
		return f;
	    }
	}
	return null;
    }
    private URL[] createUrls() throws IOException {
      /*
       * Parse the path.
       */
	List toAdd = new LinkedList();
    String currentPath = path.substring(1);
    StringTokenizer st = new StringTokenizer(currentPath, path.substring(0, 1));
    String contextDir = makeContextDir(rawContextDir);
    while (st.hasMoreTokens()) {
        URL url;
        String s;
        s = st.nextToken();

        try {
    	url = new URL(s);
    	url = DynamicJavaBridgeClassLoader.checkUrl(url);
        } catch (MalformedURLException e) {
    	try {
    	    File f=null;
    	    File file=null;
    	    StringBuffer buf= new StringBuffer();
    	    if((f=new File(s)).isFile() || f.isAbsolute()) {
    	    	buf.append(s); file = f;
    	    } else if ((f=new File(contextDir, s)).isFile()) {
    	    	buf.append(f.getAbsolutePath()); file = f;
    	    } else if ((f=new File("/usr/share/java/"+ s)).isFile()) {
    		buf.append(f.getAbsolutePath()); file = f;
    	    } else if ((f=new File(Util.JAVABRIDGE_LIB, s)).isFile()) {
    		buf.append(f.getAbsolutePath()); file = f;    		
    	    } else if ((f=checkSearchPath(s, searchpath))!=null) {
    		buf.append(f.getAbsolutePath()); file = f;    		
    	    } else if ((cwd != null) && (f=new File(cwd, s)).isFile()) {
    		buf.append(f.getAbsolutePath()); file = f;    		
    	    } else {
    		buf.append(s); file = new File(s);
    	    }
    	    /* From URLClassLoader:
    	    ** This class loader is used to load classes and resources from a search
    	    ** path of URLs referring to both JAR files and directories. Any URL that
    	    ** ends with a '/' is assumed to refer to a directory. Otherwise, the URL
    	    *
    	    * So we must replace the last backslash with a slash or append a slash
    	    * if necessary.
    	    */
    	    if(file!=null && file.isDirectory()) {
                      DynamicJavaBridgeClassLoader.addJars(toAdd, f);
    	    	int l = buf.length();
    	    	if(l>0) {
    		    if(buf.charAt(l-1) == File.separatorChar) {
    			buf.setCharAt(l-1, '/');
    		    } else if(buf.charAt(l-1)!= '/') {
    			buf.append('/');
    		    }
    		}
    	    } 
    	    if(!file.isDirectory()) DynamicJavaBridgeClassLoader.checkJarFile(file);
    	    url = new URL("file", null, buf.toString());
    	}  catch (MalformedURLException e1) {
    	    Util.printStackTrace(e1);
    	    continue;
    	}
        }
        toAdd.add(url);
    }
    URL[] urls = new URL[toAdd.size()];
    toAdd.toArray(urls);
    return urls;
    }
    /** Return the path
     * @return the key
     */
    public String getPath() {
        return path;
    }
    /**
     * Adds this entry to the cache
     */
    public void addToCache() {
        if(!isCached) { JarLibraryPath.urlCache.put(this, urls); urls=null; }
    }
}
