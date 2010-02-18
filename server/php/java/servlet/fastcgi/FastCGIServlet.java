/*-*- mode: Java; tab-width:8 -*-*/
package php.java.servlet.fastcgi;

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
import java.io.OutputStream;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.net.URI;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.Map;

import javax.servlet.ServletConfig;
import javax.servlet.ServletContext;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import php.java.bridge.ILogger;
import php.java.bridge.Util;
import php.java.bridge.http.AbstractChannelName;
import php.java.bridge.http.ContextServer;
import php.java.bridge.http.IContextFactory;
import php.java.servlet.Logger;
import php.java.servlet.PhpJavaServlet;
import php.java.servlet.ServletContextFactory;
import php.java.servlet.ServletUtil;

/**
 * A CGI Servlet which connects to a FastCGI server. If allowed by the
 * administrator and if a fast cgi binary is installed in the JavaBridge web application or
 * DEFAULT_CGI_LOCATIONS, the bridge can automatically start one FCGI
 * server on the computer. Default is Autostart.  
 * <p>The admin may start a FCGI
 * server for all users with the command:<br><code> cd /tmp<br>
 * REDIRECT_STATUS=200 X_JAVABRIDGE_OVERRIDE_HOSTS="/" PHP_FCGI_CHILDREN="5"
 * PHP_FCGI_MAX_REQUESTS="5000" /usr/bin/php-cgi -b 127.0.0.1:9667<br>
 * </code>
 * </p>
 * <p>When the program <code>/bin/sh</code> does not exist, a program called <code>launcher.exe</code>
 * is called instead:
 * <code>launcher.exe -a "path_to_php-cgi.exe" -b 9667</code>.</p>
 * @see php.java.bridge.Util#DEFAULT_CGI_LOCATIONS
 * @author jostb
 */
public class FastCGIServlet extends HttpServlet {
    private static final long serialVersionUID = 3545800996174312757L;

    private static final int JAVABRIDGE_RESERVE = 1;

    static final String cgiPathPrefix = "/WEB-INF/cgi";
 
    // IO buffer size
    static final int FCGI_BUF_SIZE = 65535;

    static final int FCGI_HEADER_LEN = 8;
    /*
     * Values for type component of FCGI_Header
     */
    protected static final int FCGI_BEGIN_REQUEST =      1;
    protected static final int FCGI_ABORT_REQUEST =      2;
    protected static final int FCGI_END_REQUEST   =      3;
    protected static final int FCGI_PARAMS        =      4;
    protected static final int FCGI_STDIN         =      5;
    protected static final int FCGI_STDOUT        =      6;
    protected static final int FCGI_STDERR        =      7;
    protected static final int FCGI_DATA          =      8;
    protected static final int FCGI_GET_VALUES    =      9;
    protected static final int FCGI_GET_VALUES_RESULT = 10;
    protected static final int FCGI_UNKNOWN_TYPE      = 11;
    protected static final byte[] FCGI_EMPTY_RECORD = new byte[0];
    
    /*
     * Mask for flags component of FCGI_BeginRequestBody
     */
    protected static final int FCGI_KEEP_CONN  = 1;

    /*
     * Values for role component of FCGI_BeginRequestBody
     */
    protected static final int FCGI_RESPONDER  = 1;
    protected static final int FCGI_AUTHORIZER = 2;
    protected static final int FCGI_FILTER     = 3;

    /**
     * The Fast CGI default port
     */ 
    public static final int FCGI_PORT = 9667;
    
    /**
     * This controls how many child processes the PHP process spawns.
     * Default is 5. The value should be less than THREAD_POOL_MAX_SIZE
     * @see Util#THREAD_POOL_MAX_SIZE
     */
    public static final String PHP_FCGI_CONNECTION_POOL_SIZE = "5"; // should be less than Util.THREAD_POOL_MAX_SIZE;
    /**
     * This controls how long the pool waits for a PHP script to terminate.
     * Default is -1, which means: "wait forever".
     */
    public static final String PHP_FCGI_CONNECTION_POOL_TIMEOUT = "-1"; // no timeout
    
    /**
     * This controls how many requests each child process will handle before
     * exitting. When one process exits, another will be created. Default is 5000.
     */
    public static final String PHP_FCGI_MAX_REQUESTS = "5000";

    /**
     * The default channel name on Windows
     */
    public static final String FCGI_PIPE = NPChannelFactory.PREFIX +"JavaBridge@9667";
    
    protected String php = null; 
    protected boolean phpTryOtherLocations = false;
    protected boolean preferSystemPhp = false; // prefer /usr/bin/php-cgi over WEB-INF/cgi/php-cgi?
    
    /** default: true. Switched off when fcgi is not configured */
    protected boolean fcgiIsConfigured;
    /** default: true. Switched off when fcgi is not configured or if fcgi server failed */
    protected boolean fcgiIsAvailable;

    protected boolean canStartFCGI = false;
    protected boolean override_hosts = true;
    protected boolean delegateToJavaBridgeContext = false;

    protected String php_fcgi_connection_pool_size = PHP_FCGI_CONNECTION_POOL_SIZE;
    protected String php_fcgi_connection_pool_timeout = PHP_FCGI_CONNECTION_POOL_TIMEOUT;
    protected boolean php_include_java;
    protected int php_fcgi_connection_pool_size_number = Integer.parseInt(PHP_FCGI_CONNECTION_POOL_SIZE);
    protected long php_fcgi_connection_pool_timeout_number = Long.parseLong(PHP_FCGI_CONNECTION_POOL_TIMEOUT);
    protected String php_fcgi_max_requests = PHP_FCGI_MAX_REQUESTS;
    protected int php_fcgi_max_requests_number = Integer.parseInt(PHP_FCGI_MAX_REQUESTS);

    protected ILogger logger;

    protected boolean promiscuous = false;
    
    private ConnectionPool connectionPool;
    protected IContextFactory ctx;

    private String contextPath;

    private String pathInfo;

    private String servletPath;

    private String queryString;

    private String requestUri;

    private HashMap environment;

    private boolean includedJava;

    private ArrayList allHeaders;
    
    /* There may be two connection pools accessing only one FastCGI server. 
     * We reserve 1 connection for the JavaBridge web context, the remaining 4 connections
     * can be used by the GlobalPhpJavaServlet.
     * The FastCGIServer is started in ChannelName#startServer (invoked by createConnectionPool).
     * by opening a dummy URLConnection  from the global- to the JavaBridge web context.
     * The following variables guard the creation of the global and "JavaBridge" connection pools.
     */
    private static final Object globalCtxLock = new Object();
    private static final Object javaBridgeCtxLock = new Object();
    private static ConnectionPool fcgiConnectionPool = null;
    
    private final IOFactory defaultPoolFactory = new IOFactory() {
	    public InputStream createInputStream() { return new FastCGIInputStream(FastCGIServlet.this); }
	    public OutputStream createOutputStream() { return new FastCGIOutputStream(); }
	    public Channel connect(ChannelFactory name) throws ConnectException {
		return name.connect();
	    }
	};
    protected ChannelFactory channelName;

    // workaround for a bug in jboss server, which uses the log4j port 4445 for its internal purposes(!)
    private boolean isJBoss;
    protected void checkCgiBinary(ServletConfig config) {
	String value;
	if (php==null) {
	    try {
		value = config.getInitParameter("php_exec");
		if(value==null || value.trim().length()==0) {
		    value = "php-cgi";
		    phpTryOtherLocations = true;
		}
		File f = new File(value);
		if(!f.isAbsolute()) {
		    value = ServletUtil.getRealPath(config.getServletContext(), cgiPathPrefix)+File.separator+value;
		}
		php = value;
	    }  catch (Throwable t) {Util.printStackTrace(t);}      
	}      
	if(fcgiIsAvailable)
	    try {
		// fcgiIsAvailable is initialized to true, it is switched off, if the FastCGIServlet fails
		value = config.getServletContext().getInitParameter("use_fast_cgi");
		if(value==null) try { value = System.getProperty("php.java.bridge.use_fast_cgi"); } catch (Exception e) {/*ignore*/}
		if("false".equalsIgnoreCase(value) || "off".equalsIgnoreCase(value)) fcgiIsConfigured=fcgiIsAvailable=false;
		else {
		    value = config.getInitParameter("use_fast_cgi");
		    if(value==null) value="auto";
		    value=value.trim();
		    value = value.toLowerCase();
		    boolean autostart = value.startsWith("auto");
		    boolean notAvailable = value.equals("false") || value.equals("off");
		    if(notAvailable) fcgiIsConfigured=fcgiIsAvailable=false;
		    if(autostart) canStartFCGI = true;
		}
	    }  catch (Throwable t) {Util.printStackTrace(t);}      
    }
    private String DOCUMENT_ROOT;
    private String SERVER_SIGNATURE;
    private ContextServer contextServer; // shared with PhpJavaServlet, FastCGIServlet

    private static final File winnt = new File("c:/winnt");
    private static final File windows = new File("c:/windows");
    private static final Class[] EMPTY_PARAM = new Class[0];
    private static final Object[] EMPTY_ARG = new Object[0];

    ServletContext context;
    static final HashMap PROCESS_ENVIRONMENT = getProcessEnvironment();


    /**
     * Get the current process environment which will be passed to the sub-process.
     * Requires jdk1.5 or higher. In jdk1.4, where System.getenv() is not available,
     * we allocate an empty map.<p>
     * To add custom environment variables (such as PATH=... or LD_ASSUME_KERNEL=2.4.21, ...),
     * use a custom PhpCgiServlet, for example:<br>
     * <code>
     * public class MyPhpCgiServlet extends PhpCgiServlet {<br>
     * &nbsp;&nbsp;protected HashMap getProcessEnvironment() {<br>
     * &nbsp;&nbsp;&nbsp;&nbsp;HashMap map = new HashMap();<br>
     * &nbsp;&nbsp;&nbsp;&nbsp;map.put("PATH", "/usr/local/bin");<br>
     * &nbsp;&nbsp;&nbsp;&nbsp;return map; <br>
     * &nbsp;&nbsp;}<br>
     * }<br>
     * </code>
     * @return The current process environment.
     */    
    private static HashMap getProcessEnvironment() {
	HashMap defaultEnv = new HashMap();
        String val = null;
	// Bug in WINNT and WINXP.
	// If SystemRoot is missing, php cannot access winsock.
	if(winnt.isDirectory()) val="c:\\winnt";
	else if(windows.isDirectory()) val = "c:\\windows";
	try {
	    String s = System.getenv("SystemRoot"); 
	    if(s!=null) val=s;
        } catch (Throwable t) {/*ignore*/}
        try {
	    String s = System.getProperty("Windows.SystemRoot");
	    if(s!=null) val=s;
        } catch (Throwable t) {/*ignore*/}
	if(val!=null) defaultEnv.put("SystemRoot", val);
        try {
	    Method m = System.class.getMethod("getenv", EMPTY_PARAM);
	    Map map = (Map) m.invoke(System.class, EMPTY_ARG);
	    defaultEnv.putAll(map);
	} catch (Exception e) {
	    defaultEnv.putAll(Util.COMMON_ENVIRONMENT);
	}
	return defaultEnv;
    }
    /**
     * Create a new FastCGI servlet which connects to a PHP FastCGI server using a connection pool.
     * 
     * If the JavaBridge context exists and the JavaBridge context can
     * start a FastCGI server and the current context is configured to
     * connect to a FastCGI server, the current context connects to
     * the JavaBridge context to start the server and then uses this
     * server for all subsequent requests until the server is
     * stopped. When FastCGI is not available (anymore), the parent
     * CGI servlet is used instead.
     * @param config The servlet config
     * @throws ServletException 
     * @see php.java.servlet.fastcgi.ConnectionPool
     * @see #destroy()
     */
    public void init(ServletConfig config) throws ServletException {
	String value;
	super.init(config);
	
	context = config.getServletContext();

	String servletContextName=ServletUtil.getRealPath(context, "");
	if(servletContextName==null) servletContextName="";
	contextServer = PhpJavaServlet.getContextServer(context, promiscuous);

	DOCUMENT_ROOT = ServletUtil.getRealPath(context, "");
	SERVER_SIGNATURE = context.getServerInfo();

	String javaDir = ServletUtil.getRealPath(context, "java");
	if (javaDir != null) {
	    File javaDirFile = new File (javaDir);
	    try {
		if (!javaDirFile.exists()) {
		    javaDirFile.mkdir();
    	    	}
	    } catch (Exception e) {/*ignore*/}
	    
	    File javaIncFile = new File (javaDir, "Java.inc");
	    try {
		if (!javaIncFile.exists()) {
		    Field f = Util.JAVA_INC.getField("bytes");
		    byte[] buf = (byte[]) f.get(Util.JAVA_INC);
		    OutputStream out = new FileOutputStream (javaIncFile);
		    out.write(buf);
		    out.close();
		}
	    } catch (Exception e) {
		e.printStackTrace();
	    }
	    
	    File javaProxyFile = new File (javaDir, "JavaProxy.php");
	    try {
		if (!javaProxyFile.exists()) {
		    Field f = Util.JAVA_PROXY.getField("bytes");
		    byte[] buf = (byte[]) f.get(Util.JAVA_PROXY);
		    OutputStream out = new FileOutputStream (javaProxyFile);
		    out.write(buf);
		    out.close();
		}
	    } catch (Exception e) {
		e.printStackTrace();
	    }
	}
	javaDir = ServletUtil.getRealPath(context, "WEB-INF/cgi");
	if (javaDir != null) {
	    File javaDirFile = new File (javaDir);
	    try {
		if (!javaDirFile.exists()) {
		    javaDirFile.mkdir();
		}
	    } catch (Exception e) {
		e.printStackTrace();
	    }
	    
	    File javaIncFile = new File (javaDir, "launcher.sh");
	    try {
		if (!javaIncFile.exists()) {
		    Field f = Util.LAUNCHER_UNIX.getField("bytes");
		    byte[] buf = (byte[]) f.get(Util.LAUNCHER_UNIX);
		    OutputStream out = new FileOutputStream (javaIncFile);
		    out.write(buf);
		    out.close();
		}
	    } catch (Exception e) {
		e.printStackTrace();
	    }
	    
	    File javaProxyFile = new File (javaDir, "launcher.exe");
	    try {
		if (!javaProxyFile.exists()) {
		    Field f =  Util.LAUNCHER_WINDOWS.getField("bytes");
		    Field f2 = Util.LAUNCHER_WINDOWS2.getField("bytes");
		    Field f3 = Util.LAUNCHER_WINDOWS3.getField("bytes");
		    byte[] buf =  (byte[]) f.get(Util.LAUNCHER_WINDOWS);
		    byte[] buf2 = (byte[]) f2.get(Util.LAUNCHER_WINDOWS2);
		    byte[] buf3 = (byte[]) f3.get(Util.LAUNCHER_WINDOWS3);
		    OutputStream out = new FileOutputStream (javaProxyFile);
		    out.write(buf);
		    out.write(buf2);
		    out.write(buf3);
		    out.close();
		}
	    } catch (Exception e) {
		e.printStackTrace();
	    }
	}

	
	String name = context.getServerInfo();
	if (name != null && (name.startsWith("JBoss")))    isJBoss    = true;

	logger = new Util.Logger(!isJBoss, new Logger(context));

	try {
	    value = context.getInitParameter("promiscuous");
	    if(value==null) value="";
	    value = value.trim();
	    value = value.toLowerCase();
	    
	    if(value.equals("on") || value.equals("true")) promiscuous=true;
	} catch (Throwable t) {t.printStackTrace();}
    	try {
	    value = context.getInitParameter("override_hosts");
	    if(value==null) value="";
	    value = value.trim();
	    value = value.toLowerCase();
	    if(value.equals("off") || value.equals("false")) override_hosts=false;
	} catch (Throwable t) {t.printStackTrace();}
    	try {
	    value = config.getInitParameter("prefer_system_php_exec");
	    if(value==null) value="";
	    value = value.trim();
	    value = value.toLowerCase();
	    if(value.equals("on") || value.equals("true")) preferSystemPhp=true;
	} catch (Throwable t) {t.printStackTrace();}	
	String val = null;
	try {
	    val = config.getInitParameter("php_fcgi_children");
	    if(val==null) val = config.getInitParameter("PHP_FCGI_CHILDREN");
	    if(val==null) val = System.getProperty("php.java.bridge.php_fcgi_children");
	    if(val==null) val = config.getInitParameter("php_fcgi_connection_pool_size");
	    if(val==null) val = System.getProperty("php.java.bridge.php_fcgi_connection_pool_size");
	    if(val!=null) php_fcgi_connection_pool_size_number = Integer.parseInt(val);
	} catch (Throwable t) {/*ignore*/}
	if(val!=null) php_fcgi_connection_pool_size = val;

	val = null;
	try {
	    val = config.getInitParameter("php_fcgi_connection_pool_timeout");
	    if(val==null) val = System.getProperty("php.java.bridge.php_fcgi_connection_pool_timeout");
	    if(val!=null) php_fcgi_connection_pool_timeout_number = Integer.parseInt(val);
	} catch (Throwable t) {/*ignore*/}
	if(val!=null) php_fcgi_connection_pool_timeout = val;
	
	val = null;
	php_include_java = false;
	try {
	    val  = config.getInitParameter("php_include_java");
	    if(val==null) val = config.getInitParameter("PHP_INCLUDE_JAVA");
	    if(val==null) val = System.getProperty("php.java.bridge.php_include_java");
	    if(val!=null && (val.equalsIgnoreCase("on") ||  val.equalsIgnoreCase("true")))
		php_include_java = true;
	} catch (Throwable t) {/*ignore*/}

	val = null;
	try {
	    val = config.getInitParameter("php_fcgi_max_requests");
	    if(val==null) val = getServletConfig().getInitParameter("PHP_FCGI_MAX_REQUESTS");
	    if(val==null) val = System.getProperty("php.java.bridge.php_fcgi_max_requests");
	    if(val != null) {
		php_fcgi_max_requests_number = Integer.parseInt(val);
		php_fcgi_max_requests = val;
	    }
	} catch (Throwable t) {/*ignore*/}
	fcgiIsAvailable = fcgiIsConfigured = true;
	checkCgiBinary(config);
	
	try {
	    value = config.getInitParameter("shared_fast_cgi_pool");
	    if(value==null) value="";
	    value = value.trim();
	    value = value.toLowerCase();
	    if(value.equals("on") || value.equals("true")) 
		delegateToJavaBridgeContext=true;
	} catch (Throwable t) {/*ignore*/}
	channelName = ChannelFactory.createChannelFactory(promiscuous);
	channelName.findFreePort(canStartFCGI && !delegateToJavaBridgeContext);
    }
    /**
     * Destroys the FastCGI connection pool, if it exists.
     */
    public void destroy() {
    	if(fcgiConnectionPool!=null) fcgiConnectionPool.destroy();
    	channelName.destroy();
    	super.destroy();
    	fcgiConnectionPool = null;
    }

    protected void setupRequestVariables(HttpServletRequest req) {
	this.allHeaders = new ArrayList();

	this.includedJava = php_include_java && PhpJavaServlet.getHeader("X_JAVABRIDGE_INCLUDE", req) == null;
            
	this.contextPath = (String) req.getAttribute("javax.servlet.include.context_path");
	if (this.contextPath == null) this.contextPath = (String) req.getAttribute("javax.servlet.forward.context_path");
	if (this.contextPath == null) this.contextPath = req.getContextPath();
    	    
	this.pathInfo =  (String) req.getAttribute("javax.servlet.include.path_info");
	if (this.pathInfo == null) this.pathInfo =  (String) req.getAttribute("javax.servlet.forward.path_info");
	if (this.pathInfo == null) this.pathInfo = req.getPathInfo();
            
	this.servletPath = (String) req.getAttribute("javax.servlet.include.servlet_path");
	if (this.servletPath == null) this.servletPath = (String) req.getAttribute("javax.servlet.forward.servlet_path");
	if (this.servletPath == null) this.servletPath = req.getServletPath();
            
	this.queryString = (String) req.getAttribute("javax.servlet.include.query_string");
	if (this.queryString == null) this.queryString = (String) req.getAttribute("javax.servlet.forward.query_string");
	if (this.queryString == null) this.queryString = req.getQueryString();
            
	this.requestUri = (String) req.getAttribute("javax.servlet.include.request_uri");
	if (this.requestUri == null) this.requestUri = (String) req.getAttribute("javax.servlet.forward.request_uri");
	if (this.requestUri == null) this.requestUri = req.getRequestURI();
    }
    
    private ConnectionPool createConnectionPool(HttpServletRequest req, int children) throws ConnectException {
	channelName.initialize(this, req, contextPath);

	// Start the launcher.exe or launcher.sh
	fcgiIsAvailable = channelName.startServer(logger);
	return new ConnectionPool(channelName, children, php_fcgi_max_requests_number, defaultPoolFactory, php_fcgi_connection_pool_timeout_number);
    }

    /** calculate PATH_INFO, PATH_TRANSLATED and SCRIPT_FILENAME */
    protected void setPathInfo(HttpServletRequest req, HashMap envp) {

	String pathInfo = req.getPathInfo();
	if (pathInfo!=null) {
	    envp.put("PATH_INFO", pathInfo);
	    envp.put("PATH_TRANSLATED", DOCUMENT_ROOT+pathInfo);
	}

        if (includedJava)
	    envp.put("SCRIPT_FILENAME", ServletUtil.getRealPath(context, "java/JavaProxy.php"));
	else
	    envp.put("SCRIPT_FILENAME", ServletUtil.getRealPath(context, servletPath));
        
    }

    protected void setupFastCGIServer(HttpServletRequest req) throws ServletException {
	boolean isJavaBridgeWc = ServletUtil.isJavaBridgeWc(contextPath);
	Object lockObject = isJavaBridgeWc?javaBridgeCtxLock:globalCtxLock;
	synchronized(lockObject) {
	    if(!fcgiIsAvailable) throw new ServletException("bleh");// FIXME
	    if(null == (connectionPool=fcgiConnectionPool))
		try {
		    int children = php_fcgi_connection_pool_size_number;
		    if(delegateToJavaBridgeContext) { 
			// NOTE: the shared_fast_cgi_pool options
			// from the GlobalPhpCGIServlet and from
			// the PhpCGIServlet must match.
			children = isJavaBridgeWc ? JAVABRIDGE_RESERVE : php_fcgi_connection_pool_size_number-JAVABRIDGE_RESERVE;
		    }
		    fcgiConnectionPool=connectionPool = createConnectionPool(req, children);
		} catch (Exception e) {
		    String base = ServletUtil.getRealPath(context, cgiPathPrefix);
		    Util.logDebug(e+": FastCGI channel not available, switching off fast cgi. " + 
				  channelName.getFcgiStartCommand(base, php_fcgi_max_requests));
                	
		    fcgiIsAvailable=false;
		}
	}

    }
    protected void setupCGIEnvironment(HttpServletRequest req, HttpServletResponse res) throws ServletException {
	HashMap envp = (HashMap)PROCESS_ENVIRONMENT.clone();

	envp.put("SERVER_SOFTWARE", "TOMCAT");
	envp.put("SERVER_NAME", ServletUtil.nullsToBlanks(req.getServerName()));
	envp.put("GATEWAY_INTERFACE", "CGI/1.1");
	envp.put("SERVER_PROTOCOL", ServletUtil.nullsToBlanks(req.getProtocol()));
	int port = ServletUtil.getLocalPort(req);
	Integer iPort = (port == 0 ? new Integer(-1) : new Integer(port));
	envp.put("SERVER_PORT", iPort.toString());
	envp.put("REQUEST_METHOD", ServletUtil.nullsToBlanks(req.getMethod()));
	envp.put("SCRIPT_NAME", contextPath+servletPath);
	envp.put("QUERY_STRING", ServletUtil.nullsToBlanks(queryString));
	envp.put("REMOTE_HOST", ServletUtil.nullsToBlanks(req.getRemoteHost()));
	envp.put("REMOTE_ADDR", ServletUtil.nullsToBlanks(req.getRemoteAddr()));
	envp.put("AUTH_TYPE", ServletUtil.nullsToBlanks(req.getAuthType()));
	envp.put("REMOTE_USER", ServletUtil.nullsToBlanks(req.getRemoteUser()));
	envp.put("REMOTE_IDENT", ""); //not necessary for full compliance
	envp.put("CONTENT_TYPE", ServletUtil.nullsToBlanks(req.getContentType()));
	setPathInfo(req, envp);

	/* Note CGI spec says CONTENT_LENGTH must be NULL ("") or undefined
	 * if there is no content, so we cannot put 0 or -1 in as per the
	 * Servlet API spec.
	 */
	int contentLength = req.getContentLength();
	String sContentLength = (contentLength <= 0 ? "" :
				 (new Integer(contentLength)).toString());
	envp.put("CONTENT_LENGTH", sContentLength);


	Enumeration headers = req.getHeaderNames();
	String header = null;
	StringBuffer buffer = new StringBuffer ();

	while (headers.hasMoreElements()) {
	    header = ((String) headers.nextElement()).toUpperCase();
	    if ("AUTHORIZATION".equalsIgnoreCase(header) ||
		"PROXY_AUTHORIZATION".equalsIgnoreCase(header)) {
		//NOOP per CGI specification section 11.2
	    } else if("HOST".equalsIgnoreCase(header)) {
		String host = req.getHeader(header);
		int idx =  host.indexOf(":");
		if(idx < 0) idx = host.length();
		envp.put("HTTP_" + header.replace('-', '_'),
			 host.substring(0, idx));
	    } else if (header.startsWith("X_")) {
		envp.put(header, req.getHeader(header));
	    } else {
		envp.put("HTTP_" + header.replace('-', '_'),
			 ServletUtil.getHeaders (buffer, req.getHeaders(header)));
	    }
	}


	this.environment = envp;

	    
	/* Inform the client that we are a cgi servlet and send the re-direct port */
	String override;
	if(override_hosts) { 
	    try {
		StringBuffer buf = new StringBuffer();
		buf.append(this.environment.get("SERVER_PORT"));
		buf.append("/");
		buf.append(contextPath);
		buf.append(servletPath);
		URI uri = new URI(req.isSecure()?"s:127.0.0.1":"h:127.0.0.1", buf.toString(), null);
		override = uri.toASCIIString()+".phpjavabridge";
	    } catch (Exception e) {
		Util.printStackTrace(e);
      		  
		StringBuffer buf = new StringBuffer();
		if(!req.isSecure())
		    buf.append("h:");
		else
		    buf.append("s:");
		buf.append("127.0.0.1");
		buf.append(":");
		buf.append(this.environment.get("SERVER_PORT")); 
		buf.append('/');
		buf.append(requestUri);
		buf.append(".phpjavabridge");
		override = buf.toString();
	    }
	}
	else 
	    override = "";

	if (includedJava) {
	    this.environment.put("X_JAVABRIDGE_INCLUDE_ONLY", "1");
	    this.environment.put("X_JAVABRIDGE_INCLUDE", ServletUtil.getRealPath(getServletContext(), servletPath));
	}
	this.environment.put("X_JAVABRIDGE_OVERRIDE_HOSTS", override);
	// same for fastcgi, which already contains X_JAVABRIDGE_OVERRIDE_HOSTS=/ in its environment
	this.environment.put("X_JAVABRIDGE_OVERRIDE_HOSTS_REDIRECT", override); 
	this.environment.put("REDIRECT_STATUS", "200");
	this.environment.put("SERVER_SOFTWARE", Util.EXTENSION_NAME);
	this.environment.put("HTTP_HOST", this.environment.get("SERVER_NAME")+":"+this.environment.get("SERVER_PORT"));
	String remotePort = null;
	try {
	    remotePort = String.valueOf(req.getRemotePort());
	} catch (Throwable t) {
	    remotePort = String.valueOf(t);
	}
	this.environment.put("REMOTE_PORT", remotePort);
	String query = queryString;
	if(query!=null)
	    this.environment.put("REQUEST_URI", ServletUtil.nullsToBlanks(requestUri + "?" + query));
	else
	    this.environment.put("REQUEST_URI", ServletUtil.nullsToBlanks(requestUri));	          
	        
	this.environment.put("SERVER_ADDR", req.getServerName());
	this.environment.put("SERVER_SIGNATURE", SERVER_SIGNATURE);
	this.environment.put("DOCUMENT_ROOT", DOCUMENT_ROOT);
	if(req.isSecure()) this.environment.put("HTTPS", "On");
	        
	        
	/* send the session context now, otherwise the client has to 
	 * call handleRedirectConnection */
	String id = PhpJavaServlet.getHeader("X_JAVABRIDGE_CONTEXT", req);
	if(id==null) {
	    id = (ctx=ServletContextFactory.addNew(contextServer, this, getServletContext(), req, req, res)).getId();
	    if (Util.USE_SHORT_PATH_S1) {
		// short path S1: no PUT request
		AbstractChannelName channelName = contextServer.getFallbackChannelName(null, ctx);
		if (channelName != null) {
		    this.environment.put("X_JAVABRIDGE_REDIRECT", channelName.getName());
		    ctx.getBridge();
		    contextServer.start(channelName, logger);
		}
	    }
	}
	this.environment.put("X_JAVABRIDGE_CONTEXT", id);
    }
    /**
     * Optimized run method for FastCGI. Makes use of the large FCGI_BUF_SIZE and the specialized in.read(). 
     * It is a modified copy of the parseBody. 
     * @throws InterruptedException 
     * @see Util#parseBody(byte[], InputStream, OutputStream, HeaderParser)
     */
    protected void parseBody(HttpServletRequest req, HttpServletResponse res) throws IOException, ServletException {
	InputStream in = null;
	final byte[] buf = new byte[FCGI_BUF_SIZE];// headers cannot be larger than this value!
	FastCGIInputStream natIn = null;
	FastCGIOutputStream natOut = null;
	ConnectionPool.Connection connection = null;
	try {
	    connection = connectionPool.openConnection();
	    natOut = (FastCGIOutputStream) connection.getOutputStream();
	    natIn = (FastCGIInputStream) connection.getInputStream();

	    in = req.getInputStream();
        
	    // send the FCGI header
	    natOut.writeBegin();
	    natOut.writeParams(environment);
		
	    String line = null;
	    int i=0, n, s=0;
	    boolean eoh=false;
	    boolean rn=false;

	    // the post variables
	    if (("chunked".equalsIgnoreCase(PhpJavaServlet.getHeader("Transfer-Encoding", req))) ||
		("upgrade".equalsIgnoreCase(PhpJavaServlet.getHeader("Connection", req)))) {
		// write the post data while reading the response 
		// used by either http/1.1 chunked connections or "WebSockets", 
		// see http://tools.ietf.org/html/draft-hixie-thewebsocketprotocol-70
		final InputStream inputStream = in; in = null;
		final FastCGIOutputStream outputStream = natOut; natOut = null;
		(new Thread() {
		    public void run() {
			int n;
			try {
			    while((n=inputStream.read(buf))!=-1) {
				outputStream.write(buf, n);
			    }
			} catch (IOException e) {
			    // TODO Auto-generated catch block
			    e.printStackTrace();
			} finally {
			    try {inputStream.close();} catch (IOException e) {}
			    try {outputStream.close();} catch (IOException e) {}
			}
		    }
		}).start();
	    } else {
		// write the post data before reading the response
		while((n=in.read(buf))!=-1) {
		    natOut.write(buf, n);
		}
		in.close(); in = null;
		natOut.close(); natOut = null;
	    } 
		
	    // the header and content
	    String remain = null;
	    while((n = natIn.read(buf)) !=-1) {
		int N = i + n;
		// header
		while(!eoh && i<N) {
		    switch(buf[i++]) {
			
		    case '\n':
			if(rn) {
			    eoh=true;
			} else {
			    if (remain != null) {
				line = remain + new String(buf, s, i-s, Util.ASCII);
				line = line.substring(0, line.length()-2);
				remain = null;
			    } else {
				line = new String(buf, s, i-s-2, Util.ASCII);
			    }
			    addHeader(res, line);
			    s=i;
			}
			rn=true;
			break;
			
		    case '\r': break;
			
		    default: rn=false;
		    }
		}
		// body
		if(eoh) {
		    if(i<N) ServletUtil.getServletOutputStream(res).write(buf, i, N-i);
		} else { 
		    if (remain != null) {
			remain += new String(buf, s, i-s, Util.ASCII);
		    } else {
			remain = new String(buf, s, i-s, Util.ASCII);
		    }
		}
		s = i = 0;
	    }
	    natIn.close(); 
	    String phpFatalError = natIn.checkError();
	    StringBuffer phpError = natIn.getError();
	    if ((phpError!=null) && (Util.logLevel>4)) Util.logDebug(phpError.toString());
	    natIn = null; connection = null;
		
	    if(phpFatalError!=null) 
		throw new RuntimeException(phpFatalError);
	} catch (InterruptedException e) {
	    throw new ServletException(e);
	} finally {
	    // Destroy physical connection if exception occured, 
	    // so that the PHP side doesn't keep unsent data
	    // A more elegant approach would be to use the FCGI ABORT request.
	    if(connection!=null) connection.setIsClosed(); 
	    if(in!=null) try {in.close();} catch (IOException e) {}
	    if(natIn!=null) try {natIn.close();} catch (IOException e) {}
	    if(natOut!=null) try {natOut.close();} catch (IOException e) {}
	}
    }

    protected void doExecute(HttpServletRequest req, HttpServletResponse res) throws IOException, ServletException {
	try {
	    parseBody(req, res);
	} catch (ConnectionException ex) {
	    Util.logError("PHP application terminated unexpectedly, have you started php-cgi with the environment setting PHP_FCGI_MAX_REQUESTS=" + php_fcgi_max_requests + "?  Error: " + ex);
	    throw ex;
	}
    }


    protected void execute(HttpServletRequest req, HttpServletResponse res) throws IOException, ServletException, InterruptedException {

	setupRequestVariables(req);
	setupFastCGIServer(req);
	setupCGIEnvironment(req, res);

	try {
	    doExecute(req, res);
	} catch (ConnectException ex) {
	    fcgiIsAvailable = false;
	    if(Util.logLevel>1) {
		Util.logDebug("PHP FastCGI server failed, switching off FastCGI SAPI: " + ex);
		Util.printStackTrace(ex);
	    }
	    throw new ServletException("PHP FastCGI server failed, switching off FastCGI SAPI.", ex);
	} catch (ConnectionException x) {
	    if(Util.logLevel>1) {
		Util.logDebug("PHP FastCGI instance failed: " + x);
		Util.printStackTrace(x);
	    }
	    throw new ServletException("PHP FastCGI instance failed.", x);
	} catch (IOException e) {
	    Util.printStackTrace(e);
	    throw e;
	} finally {
	    if(ctx!=null) ctx.releaseManaged();
	    ctx = null;
	}
	    
    }
    protected void addHeader(HttpServletResponse response, String line) {
	try {
	    if (line.startsWith("Status")) {
		line = line.substring(line.indexOf(":") + 1).trim();
		int i = line.indexOf(' ');
		if (i>0)
		    line = line.substring(0,i);

		response.setStatus(Integer.parseInt(line));
	    } else {
		if (!allHeaders.contains(line)) {
		    response.addHeader
			(line.substring(0, line.indexOf(":")).trim(),
			 line.substring(line.indexOf(":") + 1).trim());
		    allHeaders.add(line);
		}
	    }
	} catch (ArrayIndexOutOfBoundsException e) {/*not a valid header*/}
	catch (StringIndexOutOfBoundsException e){/*not a valid header*/}
    }
    protected void handle(HttpServletRequest req, HttpServletResponse res)
	throws ServletException, IOException {
	try {
	    Util.setLogger(logger);
	    execute(req, res);
	} catch (IOException e) {
	    try {res.reset();} catch (Exception ex) {/*ignore*/}
	    StringBuffer buf = new StringBuffer(ServletUtil.getRealPath(getServletConfig().getServletContext(), cgiPathPrefix));
	    buf.append(File.separator);
	    buf.append("php-cgi-");
	    buf.append(Util.osArch);
	    buf.append("-");
	    buf.append(Util.osName);
	    buf.append("[.sh]|[.exe]");
	    String wrapper = buf.toString();
	    IOException ex = new IOException("An IO exception occured. " +
					     "Probably php was not installed correctly in \"/usr/bin/php-cgi\" or \"c:/Program Files/PHP/php-cgi.exe\" or \""+wrapper+"\"." +
					     "See \"php_exec\" in your WEB-INF/web.xml and WEB-INF/cgi/README. \nReason follows:");
	    ex.initCause(e);
	    php=null;
	    checkCgiBinary(getServletConfig());
	    throw ex;
	} catch (ServletException e) {
	    try {res.reset();} catch (Exception ex) {/*ignore*/}
	    throw e;
	}
	catch (Throwable t) {
	    try {res.reset();} catch (Exception ex) {/*ignore*/}
	    if (Util.logLevel>4) Util.printStackTrace(t);
	    throw new ServletException(t);
	}
    }

    protected void doPut(HttpServletRequest req, HttpServletResponse res)
	throws IOException, ServletException {
	handle (req, res);
    }
    protected void doPost(HttpServletRequest req, HttpServletResponse res)
	throws IOException, ServletException {
	handle (req, res);
    }
    protected void doDelete(HttpServletRequest req, HttpServletResponse res)
	throws ServletException, IOException {
	handle (req, res);
    }
    protected void doGet(HttpServletRequest req, HttpServletResponse res)
	throws ServletException, IOException {
	if(Util.logLevel>4) {
	    if (req.getAttribute("javax.servlet.include.request_uri")!=null) log("doGet (included):"+req.getAttribute("javax.servlet.include.request_uri"));
	    log("doGet:"+req.getRequestURI());
	}
	handle (req, res);
    }

}
