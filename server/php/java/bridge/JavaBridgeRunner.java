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

import java.io.BufferedWriter;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.io.Reader;
import java.io.Writer;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Iterator;
import java.util.List;

import php.java.bridge.http.AbstractChannelName;
import php.java.bridge.http.ContextFactory;
import php.java.bridge.http.ContextServer;
import php.java.bridge.http.HttpRequest;
import php.java.bridge.http.HttpResponse;
import php.java.bridge.http.HttpServer;
import php.java.bridge.http.IContextFactory;

/**
 * This is the main entry point for the PHP/Java Bridge library.
 * Example:<br>
 * public MyClass { <br>
 * &nbsp;&nbsp;public static void main(String s[]) {<br>
 * &nbsp;&nbsp;&nbsp;&nbsp; JavaBridgeRunner runner = JavaBridgeRunner.getInstance();<br>
 * &nbsp;&nbsp;&nbsp;&nbsp; // connect to port 9267 and send protocol requests ... <br>
 * &nbsp;&nbsp;&nbsp;&nbsp;runner.destroy();<br>
 * &nbsp;&nbsp;}<br>
 * }<br>
 * @author jostb
 * @see php.java.script.PhpScriptContext
 */
public class JavaBridgeRunner extends HttpServer {

    private static Class JavaInc;
    private boolean isStandalone = false;
    protected static JavaBridgeRunner runner;

    static {
	try {
	    JavaInc = Class.forName("php.java.bridge.JavaInc");
        } catch (Exception e) {/*ignore*/}
    }
    
    protected JavaBridgeRunner(String serverPort) throws IOException {
	super(serverPort);
	ctxServer = new ContextServer(ContextFactory.EMPTY_CONTEXT_NAME);	
    }
    /**
     * Create a new JavaBridgeRunner and ContextServer.
     * @throws IOException 
     * @see ContextServer
     */
    protected JavaBridgeRunner() throws IOException {
	super();
	ctxServer = new ContextServer(ContextFactory.EMPTY_CONTEXT_NAME);
    }
    /**
     * Return a instance.
     * @param serverPort The server port name
     * @return a standalone runner
     * @throws IOException
     */
    public static synchronized JavaBridgeRunner getRequiredInstance(String serverPort) throws IOException {
	if(runner!=null) return runner;
	runner = new JavaBridgeRunner(serverPort);
	return runner;
    }
    /**
     * Return a instance.
     * @param serverPort The server port name
     * @return a standalone runner
     */
    public static synchronized JavaBridgeRunner getInstance(String serverPort) {
	if(runner!=null) return runner;
	try {
	    runner = new JavaBridgeRunner(serverPort);
        } catch (IOException e) {
	    Util.printStackTrace(e);
        }
	return runner;
    }
    /**
     * Return a instance.
     * @return a standalone runner
     * @throws IOException
     */
    public static synchronized JavaBridgeRunner getRequiredInstance() throws IOException {
	if(runner!=null) return runner;
	runner = new JavaBridgeRunner();
	return runner;
    }
    
    /**
     * Return a standalone instance. 
     * It sets a flag which indicates that the runner will be used as a standalone component outside of the Servlet environment.
     * @return a standalone runner
     * @throws IOException
     */
    private static synchronized JavaBridgeRunner getRequiredStandaloneInstance(String serverPort) throws IOException {
	if(runner!=null) return runner;
	runner = new JavaBridgeRunner(serverPort);
	runner.isStandalone = true;
	return runner;
    }
    /**
     * Return a standalone instance. 
     * It sets a flag which indicates that the runner will be used as a standalone component outside of the Servlet environment.
     * @return a standalone runner
     * @throws IOException
     */
    public static synchronized JavaBridgeRunner getRequiredStandaloneInstance() throws IOException {
	if(runner!=null) return runner;
	runner = new JavaBridgeRunner();
	runner.isStandalone = true;
	return runner;
    }
    private static ContextServer ctxServer = null;

    /**
     * Create a server socket.
     * @param addr The host address, either INET:port or INET_LOCAL:port
     * @return The server socket.
     * @throws IOException 
     */
    public ISocketFactory bind(String addr) throws IOException {
	socket =  JavaBridge.bind(addr);
	return socket;
    }

    private static IContextFactory getContextFactory(HttpRequest req, HttpResponse res, ContextFactory.ICredentials credentials) {
    	String id = getHeader("X_JAVABRIDGE_CONTEXT", req);
    	IContextFactory ctx = ContextFactory.get(id, credentials);
	if(ctx==null) ctx = ContextFactory.addNew();
     	res.setHeader("X_JAVABRIDGE_CONTEXT", ctx.getId());
    	return ctx;
    }
    private static String getHeader(String key, HttpRequest req) {
  	String val = req.getHeader(key);
  	if(val==null) return null;
  	if(val.length()==0) val=null;
  	return val;
    }

    /**
     * Handles both, override-redirect and redirect, see
     * see php.java.servlet.PhpJavaServlet#handleSocketConnection(HttpServletRequest, HttpServletResponse, String, boolean)
     * see php.java.servlet.PhpJavaServlet#handleRedirectConnection(HttpServletRequest, HttpServletResponse)
     */
    protected void doPut (HttpRequest req, HttpResponse res) throws IOException {
	InputStream sin=null; ByteArrayOutputStream sout; OutputStream resOut = null;
    	String channel = getHeader("X_JAVABRIDGE_CHANNEL", req);
	ContextFactory.ICredentials credentials = ctxServer.getCredentials(channel);
	IContextFactory ctx = getContextFactory(req, res, credentials);
	
    	JavaBridge bridge = ctx.getBridge();

	bridge.in = sin=req.getInputStream();
	bridge.out = sout = new ByteArrayOutputStream();
	Request r = bridge.request = new Request(bridge);
        if(r.init(sin, sout)) {
        	AbstractChannelName channelName = 
                    ctxServer.getFallbackChannelName(channel, ctx);
        	res.setHeader("X_JAVABRIDGE_REDIRECT", channelName.getName());
        	r.handleRequests();
        
        	// redirect and re-open
        	if(bridge.logLevel>3) 
        	    bridge.logDebug("re-directing to port# "+ channelName);
         	res.setContentLength(sout.size());
        	resOut = res.getOutputStream();
        	sout.writeTo(resOut);
        	resOut.close();
                ctxServer.start(channelName);
                if(bridge.logLevel>3) 
                    bridge.logDebug("waiting for context: " +ctx.getId());
                try { ctx.waitFor(Util.MAX_WAIT); } catch (InterruptedException e) { Util.printStackTrace(e); }
        	}
        else {
            ctx.destroy();
        }
    }
    /**
     * Return the current directory to the browser
     * @param fullName The full name of the file
     * @param f The full name as a file
     * @param length The length of the file
     * @param req The HTTP request object
     * @param res The HTTP response object
     * @return true if the runner could show the directory, false otherwise
     * @throws IOException
     */
    protected boolean showDirectory(String fullName, File f, int length, HttpRequest req, HttpResponse res) throws IOException {
	if(!f.isDirectory()) return false;
	ByteArrayOutputStream xout = new ByteArrayOutputStream();
	PrintWriter out = new PrintWriter(new BufferedWriter(new OutputStreamWriter(xout, Util.UTF8)));
	out.println("<html>");
	out.println("<head>");
	out.println("<title>Directory Listing for "+fullName+"/</title>");
	out.println("<STYLE><!--H1{font-family : sans-serif,Arial,Tahoma;color : white;background-color : #0086b2;} H3{font-family : sans-serif,Arial,Tahoma;color : white;background-color : #0086b2;} BODY{font-family : sans-serif,Arial,Tahoma;color : black;background-color : white;} B{color : white;background-color : #0086b2;} A{color : black;} HR{color : #0086b2;} --></STYLE> </head>");
	File parentFile = f.getParentFile();
	String parentName = parentFile==null?"/":parentFile.getName();
	out.println("<body><h1>Directory Listing for "+fullName+" - <a href=\"../\"><b>Up To "+parentName+"</b></a></h1><HR size=\"1\" noshade><table width=\"100%\" cellspacing=\"0\" cellpadding=\"5\" align=\"center\">");
	out.println("<tr>");
	out.println("<td align=\"left\"><font size=\"+1\"><strong>Filename</strong></font></td>");
	out.println("<td align=\"left\"><font size=\"+1\"><strong>Type</strong></font></td>");
	out.println("<td align=\"center\"><font size=\"+1\"><strong>Size</strong></font></td>");
	out.println("<td align=\"right\"><font size=\"+1\"><strong>Last Modified</strong></font></td>");
	out.println("");
	out.println("</tr>");
	File[] dir=f.listFiles();
	int count=0;
	StringBuffer b = new StringBuffer();
	for(int i=0; i<dir.length; i++) {
	    File file = dir[i];
	    if(file.isHidden()) continue;
	    boolean even = count++%2==0;
	    if (even)
		out.println("<tr>");
	    else
		out.println("<tr bgcolor=\"eeeeee\">");
	    
	    // mozilla replaces everything after the last slash: 
	    // foo/bar baz becomes foo/baz and foo/bar/ baz becomes foo/bar/baz
	   if(fullName.length()!=0 && !fullName.endsWith("/")) {
		b.append(f.getName());
		b.append("/");
	    }
	    b.append(file.getName());
	    if(file.isDirectory()) b.append("/");

	    out.println("<td align=\"left\">&nbsp;&nbsp;");

	    if(file.isDirectory()) {
	    	out.println("<a href=\""+b.toString()+"\"><tt>"+file.getName()+"/</tt></a></td>");
	    	out.println("<td align=\"left\">&nbsp;&nbsp;");
	    	out.println("<a href=\""+b.toString()+"?edit\"><tt>directory</tt></a></td>");
	    }
	    else {
	    	out.println("<a href=\""+b.toString()+"\"><tt>"+file.getName()+"</tt></a></td>");
	    	out.println("<td align=\"left\">&nbsp;&nbsp;");
	    	out.println("<a href=\""+b.toString()+"?show\"><tt>file</tt></a></td>");
	    }
	    out.println("<td align=\"right\"><tt>"+file.length()+"</tt></td>");
	    out.println("<td align=\"right\"><tt>"+Util.formatDateTime(file.lastModified())+"</tt></td>");
	    out.println("</tr>");
	    b.setLength(0);
	}
	out.println("</table>");
	out.println("<HR size=\"1\" noshade><h3>Simple JSR 223 enabled web server version 0.0.1</h3>");
	out.println("<h4>Available script engines</h4><ul>");
	try {
		Class c = Class.forName("javax.script.ScriptEngineManager");
		Object o = c.newInstance();
		Method e = c.getMethod("getEngineFactories", new Class[] {});
		List factories = (List) e.invoke(o, new Object[]{});
		StringBuffer buf = new StringBuffer();
		for(Iterator ii = factories.iterator(); ii.hasNext(); ) {
		    o = ii.next();
		    Method getName = o.getClass().getMethod("getEngineName", new Class[] {});
		    Method getVersion = o.getClass().getMethod("getEngineVersion", new Class[] {});
		    Method getNames = o.getClass().getMethod("getNames", new Class[] {});
		    Method getExtensions = o.getClass().getMethod("getExtensions", new Class[] {});
		    buf.append("<li>");
		    buf.append(getName.invoke(o, new Object[] {})); buf.append(", ");
		    buf.append("ver.: "); buf.append(getVersion.invoke(o, new Object[] {})); buf.append(", ");
		    buf.append("alias: "); buf.append(getNames.invoke(o, new Object[] {})); buf.append(", ");
		    buf.append(".ext: "); buf.append(getExtensions.invoke(o, new Object[] {}));
		    buf.append("</li>");
		    out.println(buf);
		    buf.setLength(0);
		}
	} catch (Exception e) {
	  e.printStackTrace();
	}
	out.println("</font></ul>");
	out.println("</body>");
	out.println("</html>");
	res.addHeader("Content-Type", "text/html; charset=UTF-8");
	res.addHeader("Last-Modified", Util.formatDateTime(f.lastModified()));
	out.close();
	int outLength = xout.size();
	res.setContentLength(outLength);
	xout.writeTo(res.getOutputStream());
	return true;
    }
    /**
     * Evaluate the script engine. The engine is searched through the discovery mechanism. Add the "php-script.jar" or some other
     * JSR223 script engine to the java ext dirs (usually /usr/share/java/ext or /usr/java/packages/lib/ext) and start the HTTP server:
     * java -jar JavaBridge.jar SERVLET_LOCAL:8080. Browse to http://localhost:8080/test.php. 
     * @param f The full name as a file
     * @param params The request parameter
     * @param length The length of the file
     * @param req The HTTP request object
     * @param res The HTTP response object
     * @return true if the runner could evaluate the script, false otherwise.
     * @throws IOException
     */
    protected boolean handleScriptContent(String name, String params, File f, int length, HttpRequest req, HttpResponse res) throws IOException {
		if("show".equals(params)) 
		    return false;
		
		int extIdx = name.lastIndexOf('.');
		if(extIdx == -1) return false;
		String ext = name.substring(extIdx+1);
		try {
		Class c = Class.forName("javax.script.ScriptEngineManager");
		Object o = c.newInstance();
		Method e = c.getMethod("getEngineByExtension", new Class[] {String.class});
		if("php".equals(ext)) 
		    ext="phtml"; // we don't want bug reports from "quercus" users
		Object engine = e.invoke(o, (Object[])new String[]{ext});
		if(engine==null) {
		    e = c.getMethod("getEngineByName", new Class[] {String.class});
		    engine = e.invoke(o, (Object[])new String[]{ext});
		}
		if(engine==null) return false;
		ByteArrayOutputStream xout = new ByteArrayOutputStream();

		Method getContext = engine.getClass().getMethod("getContext", new Class[] {});
		Method eval = engine.getClass().getMethod("eval", new Class[] {Reader.class});
		Object ctx = getContext.invoke(engine, new Object[] {});
		Method setWriter = ctx.getClass().getMethod("setWriter", new Class[] {Writer.class});
		Method setErrorWriter = ctx.getClass().getMethod("setErrorWriter", new Class[] {Writer.class});
		PrintWriter writer = new PrintWriter(new OutputStreamWriter(xout, Util.UTF8));
		setWriter.invoke(ctx, new Object[] {writer});

		setErrorWriter.invoke(ctx, new Object[] {writer});

		Method getBindings = engine.getClass().getMethod("getBindings", new Class[] {int.class});
		Object bindings = getBindings.invoke(engine, new Object[] {new Integer(200)});
		Method put = bindings.getClass().getMethod("put", new Class[] {Object.class, Object.class});
		StringBuffer buf = new StringBuffer("/");
		buf.append(name);
		if(params!=null) {
		    buf.append("?");
		    buf.append(params);
		}
		put.invoke(bindings, new Object[] {"REQUEST_URI", buf.toString()});
		put.invoke(bindings, new Object[] {"SCRIPT_FILENAME", f.getAbsolutePath()});

		FileReader r = null;;
		try {
		    eval.invoke(engine, new Object[] {r=new FileReader(f)});
                } catch (Throwable e1) {
                    e1.printStackTrace(writer);
                    Util.printStackTrace(e1);
                } finally {	
                    try { eval.invoke(engine, new Object[] {null});} catch (Exception e1) {/*ignore*/};
                    if(r!=null) try { r.close(); } catch (Exception e1) {/*ignore*/};                
                 }
                res.addHeader("Content-Type", "text/html; charset=UTF-8");
                res.setContentLength(xout.size());
		OutputStream out = res.getOutputStream();
		writer.close();
                xout.writeTo(out);
		} catch (Exception e) {
		    Util.printStackTrace(e);
		    return false;
		}
                return true;
    }
    /**
     * Display a simple text file
     * @param f The full name as a file
     * @param params The request parameter
     * @param length The length of the file
     * @param req The HTTP request object
     * @param res The HTTP response object
     * @throws IOException
     */
    protected void showTextFile(String name, String params, File f, int length, HttpRequest req, HttpResponse res) throws IOException {
	boolean show = "show".equals(params);
	byte[] buf;
	OutputStream out;
	int c;
	if(Util.logLevel>4) Util.logDebug("web server: show text file:" + name);
	res.addHeader("Last-Modified", Util.formatDateTime(f.lastModified()));
	if(show) res.addHeader("Content-Type", "text/plain");
	res.setContentLength(length);
	InputStream in = new FileInputStream(f);
	buf = new byte[Util.BUF_SIZE];
	out = res.getOutputStream();
	while((c = in.read(buf))!=-1) out.write(buf, 0, c);
	in.close();
    }

    private int count = 0;
    /**
     * Handle doGet requests. For example java_require("http://localhost:8080/JavaBridge/java/Java.inc");
     *
     * Since each script may also consume a HttpServer continuation (PUT ...), there
     * cannot be more than THREAD_POOL_MAX_SIZE/2 GET requests.
     * @param req The HttpRequest
     * @param res The HttpResponse
     */
    protected void doGet (HttpRequest req, HttpResponse res) throws IOException {
	handleDoGet (req, res);
    }
	
    private byte[] cache;
    /**
     * Handle doGet requests. For example java_require("http://localhost:8080/JavaBridge/java/Java.inc");
     * @param req The HttpRequest
     * @param res The HttpResponse
     */
    protected void handleDoGet (HttpRequest req, HttpResponse res) throws IOException {
	
	byte[] buf;
	OutputStream out;
	int c;
	boolean ignoreCache = false;
	String name =req.getRequestURI();

	if(name==null) { super.doGet(req, res); return; }
	if(!name.startsWith("/JavaBridge")) {
	    if(name.startsWith("/")) name = name.substring(1);
	    String params = null;
	    int idx = name.indexOf('?');
	    if(idx!=-1) { 
		params = name.substring(idx+1);
		name = name.substring(0, idx);
	    }
	    File f = Standalone.getCanonicalWindowsFile(name);
	    if(f==null || !f.exists()) f = new File(Util.HOME_DIR, name);
	    if(f==null || !f.exists()) return;
	    if(f.isHidden()) return;
	    long l = f.length();
	    if(l>= Integer.MAX_VALUE) throw new IOException("file " + name + " too large");
	    int length = (int)l;
	    if(showDirectory(name, f, length, req, res)) return;
	    if(handleScriptContent(name, params,  f, length , req, res)) return;
	    showTextFile(name, params, f, length, req, res);
	    return;
	}
	if(cache != null) {
	    res.setContentLength(cache.length);
	    res.getOutputStream().write(cache);
	    return;
	}
	    
	if(JavaInc!=null && name.endsWith("Java.inc")) {
	    try {
	        Field f = JavaInc.getField("bytes");
	        cache = buf = (byte[]) f.get(JavaInc);
	        res.setContentLength(buf.length);
		out =res.getOutputStream();
		out.write(buf);
		return;
            } catch (SecurityException e) {/*ignore*/        	
            } catch (Exception e) {Util.printStackTrace(e);
            }
	} else {
	    ignoreCache = true;
	}
	name = name.replaceFirst("/JavaBridge", "META-INF");
	InputStream in = JavaBridgeRunner.class.getClassLoader().getResourceAsStream(name);
	if(in==null) { // Java.inc may not exist in the source download, use JavaBridge.inc instead.
	    name = name.replaceFirst("Java\\.inc", "JavaBridge.inc");
	    ignoreCache = true;
	    in = JavaBridgeRunner.class.getClassLoader().getResourceAsStream(name);
	    if(in==null) {
		res.setContentLength(ERROR.length); res.getOutputStream().write(ERROR); 
		return;
	    } else {
		if(Util.logLevel>4) 
		    Util.logDebug("Java.inc not found, using JavaBridge.inc instead");
	    }
	 }
	try {
	    ByteArrayOutputStream bout = new ByteArrayOutputStream();
	    buf = new byte[Util.BUF_SIZE];

	    while((c=in.read(buf))>0) bout.write(buf, 0, c);
	    res.addHeader("Last-Modified", "Wed, 17 Jan 2007 19:52:43 GMT"); // bogus
	    res.setContentLength(bout.size());
	    out = res.getOutputStream();
	    byte[] cache = bout.toByteArray();
	    out.write(cache);
	    if(!ignoreCache) this.cache = cache;
	} catch (IOException e) { 
	    /* may happen when the client is not interested, see require_once() */
	 } finally {
	     try {in.close();} catch (IOException e) {/*ignore*/}
	 }
    }
    /**
     * Return true if this is a standalone server
     * @return true if this runner is a standalone runner (see {@link #main(String[])}) , false otherwise.
     */
    public boolean isStandalone() {
	return isStandalone;
    }
    
    /**
     * Wait for the runner to finish
     * @throws InterruptedException 
     */
    public void waitFor () throws InterruptedException {
        runner.httpServer.join();
    }
    
    /**
     * For internal tests only.
     * @param s The argument array
     * @throws InterruptedException 
     * @throws IOException 
     */
    public static void main(String s[]) throws InterruptedException, IOException {
	String serverPort = null; 
	if(s!=null) {
	     if(s.length>0 && s[0]!=null) serverPort = (Util.JAVABRIDGE_PROMISCUOUS ? "INET:" :"INET_LOCAL:") +s[0];
	 }
	 Util.logMessage("JavaBridgeRunner started on port " + serverPort);
	 JavaBridgeRunner r = getRequiredStandaloneInstance(serverPort);
	 r.httpServer.join();
	 r.destroy();
    }
}
