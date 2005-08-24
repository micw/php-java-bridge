/*-*- mode: Java; tab-width:8 -*-*/

package php.java.servlet;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

import javax.servlet.ServletConfig;
import javax.servlet.ServletContext;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import php.java.bridge.DynamicJavaBridgeClassLoader;
import php.java.bridge.JavaBridge;
import php.java.bridge.JavaBridgeClassLoader;
import php.java.bridge.Request;
import php.java.bridge.ThreadPool;
import php.java.bridge.Util;

/**
 * Handles requests from PHP clients. This servlet can either run PHP as a CGI 
 * sub-process, when Apache is not available, or it can communicate with 
 * Apache/php, which is recommended.
 */
public class PhpJavaServlet extends CGIServlet {

    // IO buffer size
    static final int BUF_SIZE = 8192;

    // "internal" pool for SocketRunner's
    // channel redirects
    static final String DEFAULT_CHANNEL = "9567";

    public static class Logger extends Util.Logger {
	private ServletContext ctx;
	public Logger(ServletContext ctx) {
	    this.ctx = ctx;
	}
	public void log(String s) {
	    if(Util.logLevel>5) System.out.println(s);
	    ctx.log(s); 
	}
	public String now() { return ""; }
	public void printStackTrace(Throwable t) {
	    ctx.log(Util.EXTENSION_NAME + " Exception: ", t);
	    if(Util.logLevel>5) t.printStackTrace();
	}
    }
    /*
     * The name of the php executable.
     */
    static protected String php = "php"; 
    static protected File phpFile = new File(php);
    static protected boolean override_hosts = true;
    static File unixLocation=null, windowsLocation=null;
    static SocketRunner socketRunner = null;
    static int threadPoolSize = 20;
    static ThreadPool threadPool = null;
    
    public void init(ServletConfig config) throws ServletException {
	super.init(config);
	Util.logLevel=Util.DEFAULT_LOG_LEVEL; /* java.log_level in php.ini overrides */
	String value;
        try {
	    value = getServletConfig().getInitParameter("php_exec");
	    if(value!=null && value.trim().length()!=0) {
		php=value;
		phpFile=new File(php);
	    }
        } catch (Throwable t) {Util.printStackTrace(t);}      
        try {
	    value = getServletConfig().getInitParameter("servlet_log_level");
	    if(value!=null && value.trim().length()!=0) Util.logLevel=Integer.parseInt(value);
        } catch (Throwable t) {Util.printStackTrace(t);}      

        try {
	    value = Util.THREAD_POOL_MAX_SIZE;
	    if(value!=null && value.trim().length()!=0) threadPoolSize=Integer.parseInt(value);
	    if(threadPoolSize>0) threadPool=new ThreadPool("JavaBridgeContextRunner", threadPoolSize);
        } catch (Throwable t) {Util.printStackTrace(t);}      

    	try {
	    value = getServletConfig().getInitParameter("override_hosts");
	    if(value!=null && value.trim().equalsIgnoreCase("off")) override_hosts=false;
    	} catch (Throwable t) {Util.printStackTrace(t);}      
        
	Util.logger=new Logger(config.getServletContext());
        DynamicJavaBridgeClassLoader.initClassLoader(Util.EXTENSION_DIR);

	unixLocation = new File("/usr/bin/php");
	if(!unixLocation.exists()) unixLocation=null;
	windowsLocation = new File("c:/php5/php-cgi.exe");
	if(!windowsLocation.exists()) windowsLocation=null;
	
	Util.TCP_SOCKETNAME = DEFAULT_CHANNEL;
	socketRunner = new SocketRunner();
    }

    public void destroy() {
    	super.destroy();
    	if(socketRunner!=null) socketRunner.destroy();
    }
    
    /**
     * Adjust the standard tomcat CGI env. CGI only.
     */
    protected class CGIEnvironment extends CGIServlet.CGIEnvironment {
    	protected String cgi_bin;
    	protected Context sessionFactory;
    	
	protected CGIEnvironment(HttpServletRequest req, ServletContext context) {
	    super(req, context);
	}

	protected String getCommand() {
	    return cgi_bin;
	}
	protected boolean setCGIEnvironment(HttpServletRequest req) {
	    boolean ret = super.setCGIEnvironment(req);
	    if(ret) {
	    	/* Inform the client that we are a cgi servlet and send the re-direct port */
		if(override_hosts) 
		    this.env.put("X_JAVABRIDGE_OVERRIDE_HOSTS", "127.0.0.1:"+this.env.get("SERVER_PORT"));
		else
		    this.env.put("X_JAVABRIDGE_OVERRIDE_HOSTS", "");
		this.env.put("REDIRECT_STATUS", "1");
		this.env.put("SCRIPT_FILENAME", this.env.get("PATH_TRANSLATED"));

		/* send the session context now, otherwise the client has to 
		 * call handleRedirectConnection */
		this.env.put("X_JAVABRIDGE_CONTEXT", Context.addNew(req).getId());
	    }
	    return ret;
	        	
	}
	protected String[] findCGI(String pathInfo, String webAppRootDir,
				   String contextPath, String servletPath,
				   String cgiPathPrefix) {
	    String cgiDir=webAppRootDir+  cgiPathPrefix;
	    if(!phpFile.isAbsolute()) {
		File currentLocation=null;
		try {
				
		    if((currentLocation=new File(cgiDir, php)).isFile()||
		       (currentLocation=new File(Util.EXTENSION_DIR+"/../../../../bin",php)).isFile() ||
		       ((currentLocation=unixLocation)!=null)||
		       (currentLocation=windowsLocation)!=null) 
			cgi_bin=currentLocation.getCanonicalPath();
		} catch (IOException e) {
                    if(currentLocation!=null)
		        cgi_bin=currentLocation.getAbsolutePath();
		}
	    }

	    // incorrect but reasonable values for display only.
	    String display_cgi="php";
	    this.pathInfo = "/"+display_cgi+servletPath;
	    return new String[] {
		cgiDir+ File.separator+display_cgi,
		contextPath+servletPath+File.separator+display_cgi, File.separator+display_cgi, display_cgi};
	}
    }
    
    /**
     * Create a cgi environment. Used by cgi only.
     * @param req The request
     * @param servletContext The servlet context
     * @return The new cgi environment.
     */
    protected CGIServlet.CGIEnvironment createCGIEnvironment(HttpServletRequest req, ServletContext servletContext) {
	return new CGIEnvironment(req, servletContext);
    }

    /**
     * Get the current context handle.
     * @param req
     * @param res
     * @return The context handle.
     */
    private Context getContext(HttpServletRequest req, HttpServletResponse res) {
    	Context ctx = null;
    	String id = req.getHeader("X_JAVABRIDGE_CONTEXT");
    	if(id!=null) ctx = (Context)Context.get(id);
    	if(ctx==null) ctx = Context.addNew(null); // no session sharing
    	if(ctx.bridge==null) {
   	    ctx.bridge = new JavaBridge(null, null);
    	    ctx.bridge.cl = new JavaBridgeClassLoader(ctx.bridge, DynamicJavaBridgeClassLoader.newInstance(getClass().getClassLoader()));
    	    ctx.bridge.setSessionFactory(ctx);
    	    JavaBridge.load++;
	    ctx.bridge.logDebug("first request (session is new).");
    	} else {
    	    ctx.bridge.logDebug("cont. session");
    	}
    	res.setHeader("X_JAVABRIDGE_CONTEXT", ctx.getId());
    	return ctx;
    }
    
    /**
     * Handle the override re-direct for "java_get_session()" when php runs within apache. 
     * Instead of connecting back to apache, we execute one statement and return the
     * result and the allocated session.  Used by Apache only.
     * 
     * @param req
     * @param res
     * @throws ServletException
     * @throws IOException
     */
    public void handleRedirectConnection(HttpServletRequest req, HttpServletResponse res) 
	throws ServletException, IOException {
	InputStream in; ByteArrayOutputStream out; OutputStream resOut;
	Context ctx = getContext(req, res);
	JavaBridge bridge = ctx.bridge;
	ctx.setSession(req);
	if(bridge.logLevel>3) bridge.logDebug("override redirect starts for " + ctx.getId());		
	// save old state
	InputStream bin = bridge.in;
	OutputStream bout = bridge.out;
	Request br  = bridge.request;
	
	bridge.in = in = req.getInputStream();
	bridge.out = out = new ByteArrayOutputStream();
	Request r = bridge.request = new Request(bridge);
	try {
	    if(r.initOptions(in, out)) {
		r.handleRequests();
	    }
	} catch (Throwable e) {
	    Util.printStackTrace(e);
	}
	// restore state
	bridge.in = bin; bridge.out = bout; bridge.request = br;
	
	res.setContentLength(out.size());
	resOut = res.getOutputStream();
	out.writeTo(resOut);
	in.close();
	resOut.close();
	if(bridge.logLevel>3) bridge.logDebug("override redirect finished for " + ctx.getId());
    }
    
    /**
     * Handle a standard HTTP tunnel connection. Used when the local channel is not available
     * (security restrictions). Used by Apache and cgi.
     * 
     * @param req
     * @param res
     * @throws ServletException
     * @throws IOException
     */
    protected void handleHttpConnection (HttpServletRequest req, HttpServletResponse res, boolean session)
	throws ServletException, IOException {
	InputStream in; ByteArrayOutputStream out;
	Context ctx = getContext(req, res);
	JavaBridge bridge = ctx.bridge;
	if(session) ctx.setSession(req);

	if(req.getContentLength()==0) {
	    if(req.getHeader("Connection").equals("Close")) {
		JavaBridge.load--;
		bridge.logDebug("session closed.");
		ctx.remove();
	    }
	    return;
	}
	bridge.in = in = req.getInputStream();
	bridge.out = out = new ByteArrayOutputStream();
	Request r = bridge.request = new Request(bridge);
	try {
	    if(r.initOptions(in, out)) {
		r.handleRequests();
	    }
	} catch (Throwable e) {
	    Util.printStackTrace(e);
	}
	res.setContentLength(out.size());
	out.writeTo(res.getOutputStream());
	in.close();
	out.close();
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
    protected void handleSocketConnection (HttpServletRequest req, HttpServletResponse res, boolean session)
	throws ServletException, IOException {
	InputStream sin=null; ByteArrayOutputStream sout; OutputStream resOut = null;
	Context ctx = getContext(req, res);
	JavaBridge bridge = ctx.bridge;
	if(session) ctx.setSession(req);

	bridge.in = sin=req.getInputStream();
	bridge.out = sout = new ByteArrayOutputStream();
	Request r = bridge.request = new Request(bridge);

	try {
	    if(r.initOptions(sin, sout)) {
		res.setHeader("X_JAVABRIDGE_REDIRECT", socketRunner.socket.getSocketName());
	    	r.handleRequests();

		// redirect and re-open
	    	socketRunner.schedule();
		res.setContentLength(sout.size());
		resOut = res.getOutputStream();
		sout.writeTo(resOut);
		if(bridge.logLevel>3) bridge.logDebug("re-directing to port# "+ socketRunner.socket.getSocketName());
	    	sin.close();
	    	resOut.close();
	    	if(bridge.logLevel>3) bridge.logDebug("waiting for context: " +ctx.getId());
	    	ctx.waitFor();
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
     * Dispatcher for the "http tunnel", "local channel" or "override redirect".
     */
    protected void doPut (HttpServletRequest req, HttpServletResponse res)
	throws ServletException, IOException {
    	short redirect = (short) req.getIntHeader("X_JAVABRIDGE_REDIRECT");
	try {
	    if(redirect==1) 
		handleRedirectConnection(req, res); /* override re-direct */
	    else if(socketRunner.isAvailable()) 
		handleSocketConnection(req, res, redirect==2); /* re-direct */
	    else
		handleHttpConnection(req, res, redirect==2); /* standard http tunnel */

	} catch (Throwable t) {
	    Util.printStackTrace(t);
	    try {res.getOutputStream().close();} catch (IOException x1) {}
	    try {req.getInputStream().close();} catch (IOException x2) {}
	}
    }

    /**
     * Used when running as a cgi binary only.
     * 
     *  (non-Javadoc)
     * @see javax.servlet.http.HttpServlet#doGet(javax.servlet.http.HttpServletRequest, javax.servlet.http.HttpServletResponse)
     */
    protected void doGet(HttpServletRequest req, HttpServletResponse res)
	throws ServletException, IOException {

    	try {
	    super.doGet(req, res);
    	} catch (IOException e) {
    	    ServletException ex = new ServletException("An IO exception occured. Probably php was not installed as \"/usr/bin/php\" or \"c:/php5/php-cgi.exe\".\nPlease copy your PHP binary (\""+php+"\", see JavaBridge/WEB-INF/web.xml) into the JavaBridge/WEB-INF/cgi directory.\nSee webapps/JavaBridge/WEB-INF/cgi/README for details.", e);
    	    throw ex;
    	}
    }
}
