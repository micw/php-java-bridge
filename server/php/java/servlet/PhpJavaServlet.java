/*-*- mode: Java; tab-width:8 -*-*/
package php.java.servlet;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.Socket;

import javax.servlet.ServletConfig;
import javax.servlet.ServletContext;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;

import php.java.bridge.DynamicJavaBridgeClassLoader;
import php.java.bridge.ISocketFactory;
import php.java.bridge.JavaBridge;
import php.java.bridge.JavaBridgeClassLoader;
import php.java.bridge.Request;
import php.java.bridge.Session;
import php.java.bridge.Util;


public class PhpJavaServlet extends CGIServlet {
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
    static ISocketFactory socket = null;
    static File unixLocation=null, windowsLocation=null;
    
    public void init(ServletConfig config) throws ServletException {
	super.init(config);
	Util.logLevel=Util.DEFAULT_LOG_LEVEL; /* java.log_level in php.ini overrides */
        try {
	    String value = getServletConfig().getInitParameter("php_exec");
	    if(value!=null && value.trim().length()!=0) {
		php=value;
		phpFile=new File(php);
	    }
                
	    value = getServletConfig().getInitParameter("servlet_log_level");
	    if(value!=null && value.trim().length()!=0) Util.logLevel=Integer.parseInt(value);

	    value = getServletConfig().getInitParameter("override_hosts");
	    if(value!=null && value.trim().equalsIgnoreCase("off")) override_hosts=false;
        } catch (Throwable t) {}
	Util.logger=new Logger(config.getServletContext());
        DynamicJavaBridgeClassLoader.initClassLoader(Util.EXTENSION_DIR);
        try {
	    socket = JavaBridge.bind("INET_LOCAL:0");
	} catch (Exception e) {}

	unixLocation = new File("/usr/bin/php");
	if(!unixLocation.exists()) unixLocation=null;
	windowsLocation = new File("c:/php5/php-cgi.exe");
	if(!windowsLocation.exists()) windowsLocation=null;
    }

    public void destroy() {
    	super.destroy();
    	if(socket!=null) { 
    	    try {
	        socket.close();
	    } catch (IOException e) {}
    	    socket=null;
    	}
    }
    protected class CGIEnvironment extends CGIServlet.CGIEnvironment {
    	protected String cgi_bin;
	protected CGIEnvironment(HttpServletRequest req, ServletContext context) {
	    super(req, context);
	}

	protected String getCommand() {
	    return cgi_bin;
	}
	protected boolean setCGIEnvironment(HttpServletRequest req) {
	    boolean ret = super.setCGIEnvironment(req);
	    if(ret) {
		if(override_hosts) 
		    this.env.put("X_JAVABRIDGE_OVERRIDE_HOSTS", "127.0.0.1:"+this.env.get("SERVER_PORT"));
		this.env.put("REDIRECT_STATUS", "1");
		this.env.put("SCRIPT_FILENAME", this.env.get("PATH_TRANSLATED"));
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
     * @param req The request
     * @param servletContext The servlet context
     * @return The new cgi environment.
     */
    protected CGIServlet.CGIEnvironment createCGIEnvironment(HttpServletRequest req, ServletContext servletContext) {
	return new CGIEnvironment(req, servletContext);
    }

    public void handleHttpConnection (HttpServletRequest req, HttpServletResponse res)
	throws ServletException, IOException {
	InputStream in; ByteArrayOutputStream out;
	HttpSession session = req.getSession();
	JavaBridge bridge = (JavaBridge) session.getAttribute("bridge");
	if(bridge==null) {
	    bridge = new JavaBridge(null, null);
	    bridge.cl =  new JavaBridgeClassLoader(bridge, DynamicJavaBridgeClassLoader.newInstance(getClass().getClassLoader()));
	    session.setAttribute("bridge", bridge);
	}
	if(req.getContentLength()==0) {
	    if(req.getHeader("Connection").equals("Close")) {
		session.invalidate();
		Session.expire(bridge);
		JavaBridge.load--;
		bridge.logDebug("session closed.");
	    }
	    return;
	}
	bridge.in = in = req.getInputStream();
	bridge.out = out = new ByteArrayOutputStream();
	if(session.isNew()) JavaBridge.load++;
	Request r = bridge.request = new Request(bridge);
	try {
	    if(r.initOptions(in, out)) {
		r.handleRequests();
	    }
	} catch (Throwable e) {
	    Util.printStackTrace(e);
	}
	if(session.isNew())
	    bridge.logDebug("first request terminated (session is new).");
	else
	    bridge.logDebug("request terminated (cont. session).");
	res.setContentLength(out.size());
	out.writeTo(res.getOutputStream());
    }
    public void handleSocketConnection (HttpServletRequest req, HttpServletResponse res)
	throws ServletException, IOException {
	InputStream sin, in=null; ByteArrayOutputStream sout; OutputStream out=null;
	JavaBridge bridge = new JavaBridge(sin=req.getInputStream(), sout = new ByteArrayOutputStream());
	bridge.cl =  new JavaBridgeClassLoader(bridge, DynamicJavaBridgeClassLoader.newInstance(getClass().getClassLoader()));
	Request r = bridge.request = new Request(bridge);
        JavaBridge.load++;
	try {
	    if(r.initOptions(sin, sout)) {
	    	r.handleRequests();
		res.setHeader("X_JAVABRIDGE_OVERRIDE_HOSTS", "127.0.0.1:"+socket.getSocketName());
		res.setContentLength(sout.size());
		OutputStream resOut = res.getOutputStream();
		sout.writeTo(resOut);
		if(bridge.logLevel>3) bridge.logDebug("re-directing to port# "+ socket.getSocketName());
		// redirect and re-open
	    	sin.close();
	    	resOut.close();
	    	Socket sock = socket.accept();
	    	in = bridge.in = sock.getInputStream(); 
	    	out = bridge.out = sock.getOutputStream();
		r = bridge.request = new Request(bridge);
		r.handleRequests();
	    }
	    else {
	        sin.close();
	    	sout.close();
	    }
	} catch (IOException e) {
	    Util.printStackTrace(e);
	    try {sin.close();} catch (IOException e1) {}
	    try {sout.close();} catch (IOException e2) {}
	}
        try {if(in!=null) in.close();} catch (IOException e1) {bridge.printStackTrace(e1);}
	try {if(out!=null) out.close();} catch (IOException e2) {bridge.printStackTrace(e2);}
	JavaBridge.load--;
	Session.expire(bridge);
    }
    
    protected void doPut (HttpServletRequest req, HttpServletResponse res)
	throws ServletException, IOException {
	try {
	    if(socket!=null) 
		handleSocketConnection(req, res); 
	    else
		handleHttpConnection(req, res);

	} catch (Throwable t) {
	    if(socket!=null) { 	
	    	Util.logMessage("Cannot use socket to communicate with client, using HTTP for the following requests.");
	        try {socket.close();} catch (IOException e1) {}
		socket=null;
	    }

	    Util.printStackTrace(t);
	    try {res.getOutputStream().close();} catch (IOException x1) {}
	    try {req.getInputStream().close();} catch (IOException x2) {}
	}
    }
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
