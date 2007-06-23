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
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.lang.ref.ReferenceQueue;
import java.lang.ref.WeakReference;
import java.net.JarURLConnection;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.zip.ZipFile;

/**
 * This class works around a problem in the URLClassLoader implementation, which fetches remote 
 * jar files and caches them forever. This behaviour makes the DynamicClassLoader unusable.
 * 
 * This implementation fetches the jar file and caches them until the associated URL is
 * finalized.
 * 
 * @author jostb
 *
 */
public class DynamicJarURLConnection extends JarURLConnection {

    private static final ReferenceQueue TEMP_FILE_QUEUE = new ReferenceQueue();
    private static class DeleteTempFileAction extends WeakReference {
	private File file;
	public DeleteTempFileAction(Object arg0, ReferenceQueue arg1, File file) {
	    super(arg0, arg1);
	    this.file = file;
        }
	public void command() {
	    file.delete();
	}	
    }
    private static final class TempFileObserver extends Thread{
	public TempFileObserver(String name) {
	    super(name);
	    setDaemon(true);
	    start();
	}
	public void run() {
	    try {
	        DeleteTempFileAction action = (DeleteTempFileAction) TEMP_FILE_QUEUE.remove();
	        action.command();
            } catch (InterruptedException e) {
	        e.printStackTrace();
            }
	}

	public static void observe(File f, URL url) {
	    new DeleteTempFileAction(url, TEMP_FILE_QUEUE, f);
	}
    }
    static final TempFileObserver THE_TEMP_FILE_OBSERVER = new TempFileObserver("JavaBridgeTempFileObserver");
    
    protected DynamicJarURLConnection(URL u) throws MalformedURLException {
	super(u);
    }
    public void connect() throws IOException
    {
	if(!connected) {
	    jarFileURLConnection = getJarFileURL().openConnection();
	    jarFileURLConnection.connect();
	    connected = true;
	}
    }
    public int getContentLength() {
	int i = super.getContentLength();
	return i;
    }

    private Map headerFields;
    public Map getHeaderFields() {
	if(this.headerFields!=null) return this.headerFields;
	try {
	    if(!connected) connect();
	    this.headerFields = new HashMap();
	    Map headerFields = jarFileURLConnection.getHeaderFields();
	    StringBuffer b = new StringBuffer();
	    for(Iterator ii = headerFields.entrySet().iterator(); ii.hasNext(); ) {
		Entry e = (Entry) ii.next();
		Object key = e.getKey();
		if(key==null) continue;
		Object value = e.getValue();
		if(value==null) continue;
		List list= (List)value;
		Iterator ii1 = list.iterator();
		if(ii1.hasNext()) {
		    b.append(ii1.next());
		}
		while(ii1.hasNext()) {
		    b.append(", ");
		    b.append(ii1.next());
		}
		String k = (String)key;
		k = k.toLowerCase();
		this.headerFields.put(k, b.toString());
		b.setLength(0);
	    }
	    return this.headerFields;
	} catch (IOException e) {
	    Util.printStackTrace(e);
	    throw new RuntimeException(e);
	}
    }
 
    public String getHeaderField(String key) {
	String val = (String) getHeaderFields().get(key);
	return val;
    }
    private JarFile jarFile;
    public JarFile getJarFile() throws IOException {
	if(this.jarFile!=null) return this.jarFile;
	if(!connected) connect();
	InputStream is = jarFileURLConnection.getInputStream();
	byte[] buf = new byte[Util.BUF_SIZE];
	File f = File.createTempFile("cache", "jar");
	f.deleteOnExit();
	TempFileObserver.observe(f, getURL());
	FileOutputStream fos = new FileOutputStream(f);
	int len = 0;
	while((len = is.read(buf)) != -1)  fos.write(buf, 0, len);
        fos.close();
	JarFile jarfile = new JarFile(f, true, ZipFile.OPEN_READ);
	return this.jarFile = jarfile;
    }
    private JarEntry entry;
    public InputStream getInputStream()  throws IOException {
	if(entry!=null) return getJarFile().getInputStream(entry);
	if(!connected) connect();
	entry = getJarEntry();
	long size = entry.getSize();
	if(size>Integer.MAX_VALUE) throw new IOException("zip file too large");
	int len = (int) size;
	getHeaderFields().put("content-length", String.valueOf(len));
	return getJarFile().getInputStream(entry);
    }
}
