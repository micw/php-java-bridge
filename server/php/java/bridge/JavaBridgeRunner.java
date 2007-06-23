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
    private static String serverPort;
    private boolean isStandalone = false;

    static {
	try {
	    JavaInc = Class.forName("php.java.bridge.JavaInc");
        } catch (ClassNotFoundException e) {/*ignore*/}
    }
    /**
     * Create a new JavaBridgeRunner and ContextServer.
     * @throws IOException 
     * @see ContextServer
     */
    private JavaBridgeRunner() throws IOException {
	super();
	ctxServer = new ContextServer(ContextFactory.EMPTY_CONTEXT_NAME);
    }
    private static JavaBridgeRunner runner;
    /**
     * Return a instance.
     * @return a standalone runner
     * @throws IOException
     */
    public static synchronized JavaBridgeRunner getInstance() throws IOException {
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
    public static synchronized JavaBridgeRunner getStandaloneInstance() throws IOException {
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
     */
    public ISocketFactory bind(String addr) throws IOException {
	if(serverPort!=null) addr = (Util.JAVABRIDGE_PROMISCUOUS ? "INET:" :"INET_LOCAL:") +serverPort;  
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
     * @see php.java.servlet.PhpJavaServlet#handleSocketConnection(HttpServletRequest, HttpServletResponse, String, boolean)
     * @see php.java.servlet.PhpJavaServlet#handleRedirectConnection(HttpServletRequest, HttpServletResponse)
     * 
     * @param req The HttpRequest
     * @param res The HttpResponse
     */
    protected void doPut (HttpRequest req, HttpResponse res) throws IOException {
	String overrideRedirectString = getHeader("X_JAVABRIDGE_REDIRECT", req);
	short overrideRedirectValue = (short) (overrideRedirectString==null?0:Integer.parseInt(overrideRedirectString));
    	boolean override_redirect = (3 & overrideRedirectValue) == 1;
	InputStream sin=null; ByteArrayOutputStream sout; OutputStream resOut = null;
    	String channel = getHeader("X_JAVABRIDGE_CHANNEL", req);
	String kontext = getHeader("X_JAVABRIDGE_CONTEXT_DEFAULT", req);
	ContextFactory.ICredentials credentials = ctxServer.getCredentials(channel, kontext);
	IContextFactory ctx = getContextFactory(req, res, credentials);
	
    	JavaBridge bridge = ctx.getBridge();
	// save old state for override_redirect
	InputStream bin = bridge.in;
	OutputStream bout = bridge.out;
	Request br  = bridge.request;

	bridge.in = sin=req.getInputStream();
	bridge.out = sout = new ByteArrayOutputStream();
	Request r = bridge.request = new Request(bridge);
        if(r.init(sin, sout)) {
        	AbstractChannelName channelName = 
                    ctxServer.getFallbackChannelName(channel, kontext, ctx);
        	boolean hasDefault = ctxServer.schedule(channelName) != null;
        	res.setHeader("X_JAVABRIDGE_REDIRECT", channelName.getDefaultName());
        	if(hasDefault) res.setHeader("X_JAVABRIDGE_CONTEXT_DEFAULT", kontext);
        	r.handleRequests();
        
        	// redirect and re-open
        	if(override_redirect) {
        	    bridge.logDebug("restore state");
        	    bridge.in = bin; bridge.out = bout; bridge.request = br; 
        	} else {
        	    if(bridge.logLevel>3) 
        		bridge.logDebug("re-directing to port# "+ channelName);
        	}
        	res.setContentLength(sout.size());
        	resOut = res.getOutputStream();
        	sout.writeTo(resOut);
            resOut.close();
            if(!override_redirect) {
                ctxServer.start(channelName);
        	    if(bridge.logLevel>3) 
        		bridge.logDebug("waiting for context: " +ctx.getId());
        	    try { ctx.waitFor(); } catch (InterruptedException e) { Util.printStackTrace(e); }
        	}
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
	PrintWriter out = new PrintWriter(new BufferedWriter(new OutputStreamWriter(xout, "UTF-8")));
	out.println("<html>");
	out.println("<head>");
	out.println("<title>Directory Listing for "+fullName+"/</title>");
	out.println("<STYLE><!--H1{font-family : sans-serif,Arial,Tahoma;color : white;background-color : #0086b2;} H3{font-family : sans-serif,Arial,Tahoma;color : white;background-color : #0086b2;} BODY{font-family : sans-serif,Arial,Tahoma;color : black;background-color : white;} B{color : white;background-color : #0086b2;} A{color : black;} HR{color : #0086b2;} --></STYLE> </head>");
	String parentName = f.getParentFile().getName();
	out.println("<body><h1>Directory Listing for "+fullName+" - <a href=\"../\"><b>Up To "+parentName+"</b></a></h1><HR size=\"1\" noshade><table width=\"100%\" cellspacing=\"0\" cellpadding=\"5\" align=\"center\">");
	out.println("<tr>");
	out.println("<td align=\"left\"><font size=\"+1\"><strong>Filename</strong></font></td>");
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

	    if(file.isDirectory())
	    	out.println("<a href=\""+b.toString()+"\"><tt>"+file.getName()+"/</tt></a></td>");
	    else
	    	out.println("<a href=\""+b.toString()+"\"><tt>"+file.getName()+"</tt></a></td>");
		
	    out.println("<td align=\"right\"><tt>"+file.length()+"</tt></td>");
	    out.println("<td align=\"right\"><tt>"+Util.formatDateTime(file.lastModified())+"</tt></td>");
	    out.println("</tr>");
	    b.setLength(0);
	}
	out.println("</table>");
	out.println("<HR size=\"1\" noshade><h3>PHP/Java Bridge "+(Util.VERSION==null?"":Util.VERSION)+"</h3></body>");
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
     * @param fullName The full name of the file
     * @param f The full name as a file
     * @param length The length of the file
     * @param req The HTTP request object
     * @param res The HTTP response object
     * @return true if the runner could evaluate the script, false otherwise.
     * @throws IOException
     */
    protected boolean handleScriptContent(String name, File f, int length, HttpRequest req, HttpResponse res) throws IOException {
		int extIdx = name.lastIndexOf('.');
		if(extIdx == -1) return false;
		name = name.substring(extIdx+1);
		try {
		Class c = Class.forName("javax.script.ScriptEngineManager");
		Object o = c.newInstance();
		Method e = c.getMethod("getEngineByName", new Class[] {String.class});
		Object engine = e.invoke(o, new String[]{name});
		if(engine==null) return false;
		ByteArrayOutputStream xout = new ByteArrayOutputStream();

		Method getContext = engine.getClass().getMethod("getContext", new Class[] {});
		Method eval = engine.getClass().getMethod("eval", new Class[] {Reader.class});
		Object ctx = getContext.invoke(engine, new Object[] {});
		Method setWriter1 = ctx.getClass().getMethod("setWriter", new Class[] {Writer.class});
		Method setErrorWriter = ctx.getClass().getMethod("setErrorWriter", new Class[] {Writer.class});
		Writer writer = new java.io.OutputStreamWriter(xout);
		Method setWriter = null;
		try {
		    setWriter = ctx.getClass().getMethod("setWriter", new Class[] {Writer.class, String.class});
		} catch (NoSuchMethodException ex) {/*ignore*/}
		if(setWriter==null)
		    setWriter1.invoke(ctx, new Object[] {writer});
		else
		    setWriter.invoke(ctx, new Object[] {writer, "UTF-8"});

		setErrorWriter.invoke(ctx, new Object[] {writer});
		
		try {
		    eval.invoke(engine, new Object[] {new FileReader(f)});
                } catch (Exception e1) {
                    Util.printStackTrace(e1);
                } finally {	
                    try { eval.invoke(engine, new Object[] {null});} catch (Exception e1) {/*ignore*/};
                }
                res.addHeader("Content-Type", "text/html" +((setWriter==null)?"":"; charset=UTF-8"));
                res.setContentLength(xout.size());
		OutputStream out = res.getOutputStream();
                xout.writeTo(out);
                //out.close();
                res.getOutputStream().close();
                req.getInputStream().close();
		} catch (Exception e) {
		    Util.printStackTrace(e);
		    return false;
		}
                return true;
    }
    /**
     * Display a simple text file
     * @param fullName The full name of the file
     * @param f The full name as a file
     * @param length The length of the file
     * @param req The HTTP request object
     * @param res The HTTP response object
     * @throws IOException
     */
    protected void showTextFile(String name, File f, int length, HttpRequest req, HttpResponse res) throws IOException {
	byte[] buf;
	OutputStream out;
	int c;
	res.addHeader("Last-Modified", Util.formatDateTime(f.lastModified()));
	res.setContentLength(length);
	InputStream in = new FileInputStream(f);
	buf = new byte[Util.BUF_SIZE];
	out = res.getOutputStream();
	while((c = in.read(buf))!=-1) out.write(buf, 0, c);
	in.close();
    }

    private byte[] cache;
    /**
     * Handle doGet requests. For example java_require("http://localhost:8080/JavaBridge/java/Java.inc");
     * @param req The HttpRequest
     * @param res The HttpResponse
     */
    protected void doGet (HttpRequest req, HttpResponse res) throws IOException {
	byte[] buf;
	OutputStream out;
	int c;
	boolean ignoreCache = false;
	String name =req.getRequestURI();

	if(name==null) { super.doGet(req, res); return; }
	if(!name.startsWith("/JavaBridge")) {
	    if(name.startsWith("/")) name = name.substring(1);
	    File f = new File(name);
	    if(f==null || !f.exists()) f = new File(Util.HOME_DIR, name);
	    if(f==null || !f.exists()) return;
	    if(f.isHidden()) return;
	    long l = f.length();
	    if(l>= Integer.MAX_VALUE) throw new IOException("file " + name + " too large");
	    int length = (int)l;
	    if(showDirectory(name, f, length, req, res)) return;
	    if(handleScriptContent(name, f, length , req, res)) return;
	    showTextFile(name, f, length, req, res);
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
	ByteArrayOutputStream bout = new ByteArrayOutputStream();
	buf = new byte[Util.BUF_SIZE];

	while((c=in.read(buf))>0) bout.write(buf, 0, c);
	res.addHeader("Last-Modified", "Wed, 17 Jan 2007 19:52:43 GMT"); // bogus
	res.setContentLength(bout.size());
	out = res.getOutputStream();
	try {
	    byte[] cache = bout.toByteArray();
	    out.write(cache);
	    if(!ignoreCache) this.cache = cache;
	} catch (IOException e) { /* may happen when the client is not interested, see require_once() */}
    }
    /**
     * Return true if this is a standalone server
     * @return true if this runner is a standalone runner (see {@link #main(String[])}) , false otherwise.
     */
    public boolean isStandalone() {
	return isStandalone;
    }
    /**
     * For internal tests only.
     * @throws InterruptedException 
     * @throws IOException 
     */
    public static void main(String s[]) throws InterruptedException, IOException {
	 if(s!=null) {
	     if(s.length>0 && s[0]!=null) serverPort = s[0];
	 }
	 Util.logMessage("JavaBridgeRunner started on port " + serverPort);
	 JavaBridgeRunner r = getStandaloneInstance();
	 r.httpServer.join();
	 r.destroy();
    }
}
