/*-*- mode: Java; tab-width:8 -*-*/

package php.java.bridge;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

import php.java.bridge.http.ContextFactory;
import php.java.bridge.http.ContextServer;
import php.java.bridge.http.HttpRequest;
import php.java.bridge.http.HttpResponse;
import php.java.bridge.http.HttpServer;

/**
 * This is the main entry point for the PHP/Java Bridge library.
 * Example:<br>
 * public MyClass { <br>
 * &nbsp;&nbsp;public static void main(String s[]) {<br>
 * &nbsp;&nbsp;&nbsp;&nbsp; JavaBridgeRunner runner = new JavaBridgeRunner();<br>
 * &nbsp;&nbsp;&nbsp;&nbsp; // connect to port 9267 and send protocol requests ... <br>
 * &nbsp;&nbsp;&nbsp;&nbsp;runner.destroy();<br>
 * &nbsp;&nbsp;}<br>
 * }<br>
 * @author jostb
 * @see php.java.script.PhpScriptContext
 */
public class JavaBridgeRunner extends HttpServer {

    /**
     * Create a new JavaBridgeRunner and ContextServer.
     * @see ContextServer
     */
    public JavaBridgeRunner() {
	super();
	DynamicJavaBridgeClassLoader.initClassLoader(Util.DEFAULT_EXTENSION_DIR);
	socketRunner = new ContextServer(null);
    }

    private InputStream in;
    private OutputStream out;
    private static ContextServer socketRunner;

    /**
     * Create a server socket.
     */
    public ISocketFactory bind() {
	try {
	    return JavaBridge.bind("INET:0");
	} catch (Exception e) {
	    Util.printStackTrace(e);
	    return null;
	}
    }

    private static ContextFactory getContextManager(HttpRequest req, HttpResponse res) {
    	String id = req.getHeader("X_JAVABRIDGE_CONTEXT");
    	ContextFactory ctx = ContextFactory.get(id);
     	res.setHeader("X_JAVABRIDGE_CONTEXT", id);
    	return ctx;
    }

    /**
     * Handle a redirected connection. The local channel is more than 50 
     * times faster than the HTTP tunnel. Used by Apache and cgi.
     * 
     * @param req The HttpRequest
     * @param res The HttpResponse
     */
    protected void parseBody (HttpRequest req, HttpResponse res) throws IOException {
    	super.parseBody(req, res);
	InputStream sin=null; ByteArrayOutputStream sout; OutputStream resOut = null;
	ContextFactory ctx = getContextManager(req, res);
	JavaBridge bridge = ctx.getBridge();
	//if(session) ctx.setSession(req);

	bridge.in = sin=req.getInputStream();
	bridge.out = sout = new ByteArrayOutputStream();
	Request r = bridge.request = new Request(bridge);

	try {
	    if(r.init(sin, sout)) {
		res.setHeader("X_JAVABRIDGE_REDIRECT", socketRunner.getSocket().getSocketName());
	    	r.handleOneRequest();

		// redirect and re-open
	    	socketRunner.schedule();
		res.setContentLength(sout.size());
		resOut = res.getOutputStream();
		sout.writeTo(resOut);
		if(bridge.logLevel>3) bridge.logDebug("re-directing to port# "+ socketRunner.getSocket().getSocketName());
	    	sin.close();
	    	resOut.flush();
	    	if(bridge.logLevel>3) bridge.logDebug("waiting for context: " +ctx.getId());
	    	ctx.waitFor();
	    	resOut.close();
	    	if(bridge.logLevel>3) bridge.logDebug("context finished: " +ctx.getId());
	    }
	    else {
	        sin.close();
	        ctx.remove();
	    }
	} catch (Exception e) {
	    Util.printStackTrace(e);
	    try {if(sin!=null) sin.close();} catch (IOException e1) {}
	    try {if(resOut!=null) resOut.close();} catch (IOException e2) {}
	}
    }
    
    /**
     * Returns the server socket.
     * @return The server socket.
     */
    public ISocketFactory getSocket() {
	return socket;
    }

}
