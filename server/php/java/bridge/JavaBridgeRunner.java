/*-*- mode: Java; tab-width:8 -*-*/

package php.java.bridge;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

import php.java.bridge.http.ContextServer;
import php.java.bridge.http.HttpRequest;
import php.java.bridge.http.HttpResponse;
import php.java.bridge.http.HttpServer;

/**
 * This is the main entry point for the PHP/Java Bridge library.
 * Example:<br>
 * public MyClass { <br>
 * public static void main(String s[]) {<br>
 *  JavaBridgeRunner runner = new JavaBridgeRunner();<br>
 *  // connect to port 9267 and send protocol requests ... <br>
 * runner.destroy();<br>
 * }<be>
 * @author jostb
 *
 */
public class JavaBridgeRunner extends HttpServer {

    /**
     * @throws Exception
     */
    public JavaBridgeRunner() {
	super();
	DynamicJavaBridgeClassLoader.initClassLoader(Util.DEFAULT_EXTENSION_DIR);
	socketRunner = new ContextServer(null);
    }

    private InputStream in;
    private OutputStream out;
    private static ContextServer socketRunner;
		
    public ISocketFactory bind() {
	try {
	    return JavaBridge.bind("INET:0");
	} catch (Exception e) {
	    Util.printStackTrace(e);
	    return null;
	}
    }

    private static ContextManager getContextManager(HttpRequest req, HttpResponse res) {
    	String id = req.getHeader("X_JAVABRIDGE_CONTEXT");
    	ContextManager ctx = ContextManager.get(id);
     	res.setHeader("X_JAVABRIDGE_CONTEXT", id);
    	return ctx;
    }

    /**
     * Handle a redirected connection. The local channel is more than 50 
     * times faster than the HTTP tunnel. Used by Apache and cgi.
     * 
     * @param req
     * @param res
     * @throws ServletException
     * @throws IOException
     */
    protected void parseBody (HttpRequest req, HttpResponse res) {
    	super.parseBody(req, res);
	InputStream sin=null; ByteArrayOutputStream sout; OutputStream resOut = null;
	ContextManager ctx = getContextManager(req, res);
	JavaBridge bridge = ctx.getBridge();
	//if(session) ctx.setSession(req);

	bridge.in = sin=req.getInputStream();
	bridge.out = sout = new ByteArrayOutputStream();
	Request r = bridge.request = new Request(bridge);

	try {
	    if(r.initOptions(sin, sout)) {
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
    public ISocketFactory getSocket() {
	return socket;
    }

}
