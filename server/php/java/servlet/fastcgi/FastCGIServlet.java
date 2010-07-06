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
import php.java.bridge.http.FCGIConnection;
import php.java.bridge.http.FCGIConnectionFactory;
import php.java.bridge.http.FCGIConnectException;
import php.java.bridge.http.FCGIConnectionException;
import php.java.bridge.http.FCGIConnectionPool;
import php.java.bridge.http.ContextServer;
import php.java.bridge.http.FCGIUtil;
import php.java.bridge.http.FCGIInputStream;
import php.java.bridge.http.FCGIOutputStream;
import php.java.bridge.http.HeaderParser;
import php.java.bridge.http.IContextFactory;
import php.java.bridge.http.IFCGIProcess;
import php.java.bridge.http.IFCGIProcessFactory;
import php.java.bridge.http.FCGIIOFactory;
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
public class FastCGIServlet extends HttpServlet implements IFCGIProcessFactory {
    private static final long serialVersionUID = 3545800996174312757L;

    static final String PEAR_DIR = "/WEB-INF/pear";
    static final String CGI_DIR = "/WEB-INF/cgi";
    static final String WEB_INF_DIR = "/WEB-INF";
 
    protected String php = null; 
    protected boolean phpTryOtherLocations = false;
    protected boolean preferSystemPhp = false; // prefer /usr/bin/php-cgi over WEB-INF/cgi/php-cgi?
    
    /** default: true. Switched off when fcgi is not configured */
    protected boolean fcgiIsConfigured;

    protected boolean canStartFCGI = false;
    protected boolean override_hosts = true;

    protected String php_fcgi_connection_pool_size = FCGIUtil.PHP_FCGI_CONNECTION_POOL_SIZE;
    protected String php_fcgi_connection_pool_timeout = FCGIUtil.PHP_FCGI_CONNECTION_POOL_TIMEOUT;
    protected boolean php_include_java;
    protected int php_fcgi_connection_pool_size_number = Integer.parseInt(FCGIUtil.PHP_FCGI_CONNECTION_POOL_SIZE);
    protected long php_fcgi_connection_pool_timeout_number = Long.parseLong(FCGIUtil.PHP_FCGI_CONNECTION_POOL_TIMEOUT);
    protected String php_fcgi_max_requests = FCGIUtil.PHP_FCGI_MAX_REQUESTS;
    protected int php_fcgi_max_requests_number = Integer.parseInt(FCGIUtil.PHP_FCGI_MAX_REQUESTS);

    protected ILogger logger;

    protected boolean promiscuous = true;
    

    private static class Environment {
	public IContextFactory ctx;
	public FCGIConnectionPool connectionPool;
	public String contextPath;
	public String pathInfo;
	public String servletPath;
	public String queryString;
	public String requestUri;
	public HashMap environment;
	public boolean includedJava;
	public ArrayList allHeaders;
    }
    
    /* There may be two connection pools accessing only one FastCGI server. 
     * We reserve 1 connection for the JavaBridge web context, the remaining 4 connections
     * can be used by the GlobalPhpJavaServlet.
     * The FastCGIServer is started in ChannelName#startServer (invoked by createConnectionPool).
     * by opening a dummy URLConnection  from the global- to the JavaBridge web context.
     * The following variables guard the creation of the global and "JavaBridge" connection pools.
     */
    private static final Object globalCtxLock = new Object();
    private static final Object javaBridgeCtxLock = new Object();
    private static FCGIConnectionPool fcgiConnectionPool = null;
    
    private final FCGIIOFactory defaultPoolFactory = new FCGIIOFactory() {
	    public InputStream createInputStream() { return new FCGIInputStream(FastCGIServlet.this); }
	    public OutputStream createOutputStream() { return new FCGIOutputStream(); }
	    public FCGIConnection connect(FCGIConnectionFactory name) throws FCGIConnectException {
		return name.connect();
	    }
	};
    protected FCGIConnectionFactory channelName;

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
		    value = ServletUtil.getRealPath(config.getServletContext(), CGI_DIR)+File.separator+value;
		}
		php = value;
	    }  catch (Throwable t) {Util.printStackTrace(t);}      
	}      
	try {
	    value = config.getServletContext().getInitParameter("use_fast_cgi");
	    if(value==null) try { value = System.getProperty("php.java.bridge.use_fast_cgi"); } catch (Exception e) {/*ignore*/}
	    if("false".equalsIgnoreCase(value) || "off".equalsIgnoreCase(value)) fcgiIsConfigured=false;
	    else {
		value = config.getInitParameter("use_fast_cgi");
		if(value==null) value="auto";
		value=value.trim();
		value = value.toLowerCase();
		boolean autostart = value.startsWith("auto");
		boolean notAvailable = value.equals("false") || value.equals("off");
		if(notAvailable) fcgiIsConfigured=false;
		if(autostart) canStartFCGI = true;
	    }
	}  catch (Throwable t) {Util.printStackTrace(t);}
    }
    private String DOCUMENT_ROOT;
    private String SERVER_SIGNATURE;
    private ContextServer contextServer; // shared with FastCGIServlet

    ServletContext context;
    static final HashMap PROCESS_ENVIRONMENT = getProcessEnvironment();
    
    private static void updateProcessEnvironment(File conf) {
	try {
	    PROCESS_ENVIRONMENT.put("PHP_INI_SCAN_DIR", conf.getCanonicalPath());
	} catch (IOException e) {
	    e.printStackTrace();
	    PROCESS_ENVIRONMENT.put("PHP_INI_SCAN_DIR", conf.getAbsolutePath());
	}
    }
    private static HashMap getProcessEnvironment() {
	HashMap map = new HashMap(Util.COMMON_ENVIRONMENT);
	return map;
    }
    private  boolean useSystemPhp(File f) {
	
	// path hard coded in web.xml
	if (!phpTryOtherLocations) return true;
	
	// no local php exists
	if (!f.exists()) return true;
	
	// local exists
	if(!preferSystemPhp) return false;
	
	// check default locations for preferred system php
	for(int i=0; i<Util.DEFAULT_CGI_LOCATIONS.length; i++) {
	    File location = new File(Util.DEFAULT_CGI_LOCATIONS[i]);
	    if(location.exists()) return true;
	}
	
	return false;
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
     * @see php.java.bridge.http.FCGIConnectionPool
     * @see #destroy()
     */
    public void init(ServletConfig config) throws ServletException {
	String value;
	super.init(config);
	
	context = config.getServletContext();

	try {
	    value = context.getInitParameter("promiscuous");
	    if(value==null) value="";
	    value = value.trim();
	    value = value.toLowerCase();
	    
	    if(value.equals("off") || value.equals("false")) promiscuous=false;
	} catch (Throwable t) {t.printStackTrace();}

	String servletContextName=ServletUtil.getRealPath(context, "");
	if(servletContextName==null) servletContextName="";
	contextServer = ServletUtil.getContextServer(context, promiscuous);

	DOCUMENT_ROOT = ServletUtil.getRealPath(context, "");
	SERVER_SIGNATURE = context.getServerInfo();

	String name = context.getServerInfo();
	if (name != null && (name.startsWith("JBoss")))    isJBoss    = true;

	logger = new Util.Logger(!isJBoss, new Logger());
    	Util.setDefaultLogger(logger);

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
	fcgiIsConfigured = true;
	checkCgiBinary(config);
	
	channelName = FCGIConnectionFactory.createChannelFactory(this, promiscuous);
	channelName.findFreePort(canStartFCGI);

	createPhpFiles ();
    }
    
    private void createPhpFiles () {

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

	    File phpDebuggerFile = new File (javaDir, "PHPDebugger.php");
	    try {
		if (!phpDebuggerFile.exists()) {
		    Field f = Util.PHPDEBUGGER_PHP.getField("bytes");
		    byte[] buf = (byte[]) f.get(Util.PHPDEBUGGER_PHP);
		    OutputStream out = new FileOutputStream (phpDebuggerFile);
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
	String pearDir = ServletUtil.getRealPath(context, PEAR_DIR);
	if (pearDir != null) {
	    File pearDirFile = new File (pearDir);
	    try {
		if (!pearDirFile.exists()) {
		    pearDirFile.mkdir();
		}
	    } catch (Exception e) {
		e.printStackTrace();
	    }
	}
	String cgiDir = ServletUtil.getRealPath(context, CGI_DIR);
	File cgiOsDir = new File(cgiDir, Util.osArch+"-"+Util.osName);
	File conf = new File(cgiOsDir, "conf.d");
	File ext = new File(cgiOsDir, "ext");
	File cgiDirFile = new File (cgiDir);
	try {
	    if (!cgiDirFile.exists()) {
		cgiDirFile.mkdirs();
	    }
	} catch (Exception e) {
	    e.printStackTrace();
	}

	try {
	    if (!conf.exists()) {
		conf.mkdirs ();
	    }
	} catch (Exception e) {
	    e.printStackTrace();
	}

	try {
	    if (!ext.exists()) {
		ext.mkdir();
	    }
	} catch (Exception e) {
	    e.printStackTrace();
	}

	File javaIncFile = new File (cgiOsDir, "launcher.sh");
	if (Util.USE_SH_WRAPPER) {
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
	}
	File javaProxyFile = new File (cgiOsDir, "launcher.exe");
	if (!Util.USE_SH_WRAPPER) {
	    try {
		if (!javaProxyFile.exists()) {
		    Field f =  Util.LAUNCHER_WINDOWS.getField("bytes");
		    Field f2 = Util.LAUNCHER_WINDOWS2.getField("bytes");
		    Field f3 = Util.LAUNCHER_WINDOWS3.getField("bytes");
		    Field f4 = Util.LAUNCHER_WINDOWS4.getField("bytes");
		    byte[] buf =  (byte[]) f.get(Util.LAUNCHER_WINDOWS);
		    byte[] buf2 = (byte[]) f2.get(Util.LAUNCHER_WINDOWS2);
		    byte[] buf3 = (byte[]) f3.get(Util.LAUNCHER_WINDOWS3);
		    byte[] buf4 = (byte[]) f4.get(Util.LAUNCHER_WINDOWS4);
		    OutputStream out = new FileOutputStream (javaProxyFile);
		    out.write(buf);
		    out.write(buf2);
		    out.write(buf3);
		    out.write(buf4);
		    out.close();
		}
	    } catch (Exception e) {
		e.printStackTrace();
	    }
	}
	boolean exeExists = true;
	if (Util.USE_SH_WRAPPER) {
	    try {
		File phpCgi = new File (cgiOsDir, "php-cgi");
		if (!useSystemPhp(phpCgi)) {
		    updateProcessEnvironment(conf);
		    File wrapper = new File(cgiOsDir, "php-cgi.sh");
		    if (!wrapper.exists()) {
			byte[] data = ("#!/bin/sh\nchmod +x ./php-cgi-"+Util.osArch+"-"+Util.osName+"\n"+
			"exec ./php-cgi -c php-cgi.ini \"$@\"").getBytes();
			OutputStream out = new FileOutputStream (wrapper);
			out.write(data);
			out.close();
		    }
		    File ini = new File(cgiOsDir, "php-cgi.ini");
		    if (!ini.exists()) {
			byte[] data = (";; -*- mode: Scheme; tab-width:4 -*-\n;; A simple php.ini\n"+
				";; DO NOT EDIT THIS FILE!\n" +
				";; Add your configuration files to the "+conf+" instead.\n"+
				";; PHP extensions go to "+ext+". Please see phpinfo() for ABI version details.\n"+
				"extension_dir=\""+ext+"\"\n"+
				"include_path=\""+pearDir+":/usr/share/pear:.\"\n").getBytes();
			OutputStream out = new FileOutputStream (ini);
			out.write(data);
			out.close();
		    }
		} else {
		    exeExists = false;
		    File readme = new File(cgiOsDir, "php-cgi.MISSING.README.txt");
		    if (!readme.exists()) {
			byte[] data = ("You can add \"php-cgi\" to this directory and re-deploy your web application.\n").getBytes();
			OutputStream out = new FileOutputStream (readme);
			out.write(data);
			out.close();
		    }
		}
	    } catch (Exception e) {
		e.printStackTrace();
	    }
	} else {
	    try {
		File phpCgi = new File (cgiOsDir, "php-cgi.exe");
		if (!useSystemPhp(phpCgi)) {
		    updateProcessEnvironment(conf);
		    File ini = new File(cgiOsDir, "php.ini");
		    if (!ini.exists()) {
			byte[] data = (";; -*- mode: Scheme; tab-width:4 -*-\r\n;; A simple php.ini\r\n"+
				";; DO NOT EDIT THIS FILE!\r\n" +
				";; Add your configuration files to the "+conf+" instead.\r\n"+
				";; PHP extensions go to "+ext+". Please see phpinfo() for ABI version details.\r\n"+
				"extension_dir=\""+ext+"\"\r\n"+
				"include_path=\""+pearDir+";.\"\r\n").getBytes();
			OutputStream out = new FileOutputStream (ini);
			out.write(data);
			out.close();
		    }
		} else {
		    exeExists = false;
		    File readme = new File(cgiOsDir, "php-cgi.exe.MISSING.README.txt");
		    if (!readme.exists()) {
			byte[] data = ("You can add \"php-cgi.exe\" to this directory and re-deploy your web application.\r\n").getBytes();
			OutputStream out = new FileOutputStream (readme);
			out.write(data);
			out.close();
		    }
		}
	    } catch (Exception e) {
		e.printStackTrace();
	    }
	}

	File tmpl = new File(conf, "mysql.ini");
	if (exeExists && !tmpl.exists()) {
	    String str;
	    if (Util.USE_SH_WRAPPER) {
		str = ";; -*- mode: Scheme; tab-width:4 -*-\n"+
		";; Example extension.ini file: mysql.ini.\n"+
		";; Copy the correct version (see phpinfo()) of the PHP extension \"mysql.so\" to the ./../ext directory and uncomment the following line\n"+
		"; extension = mysql.so\n";
	    } else {
		str = ";; -*- mode: Scheme; tab-width:4 -*-\r\n"+
		";; Example extension.ini file: mysql.ini.\r\n"+
		";; Copy the correct version (see phpinfo()) of the PHP extension \"php_mysql.dll\" to the .\\..\\ext directory and uncomment the following line\r\n"+
		"; extension = php_mysql.dll\r\n";
	    }
	    byte[] data = str.getBytes();
	    try {
		OutputStream out = new FileOutputStream (tmpl);
		out.write(data);
		out.close();
	    } catch (Exception e) {
		e.printStackTrace();
	    }
	}
    }
    
    /**
     * Destroys the FastCGI connection pool, if it exists.
     */
    public void destroy() {
    	if(fcgiConnectionPool!=null) fcgiConnectionPool.destroy();
    	channelName.destroy();
    	super.destroy();
    	fcgiConnectionPool = null;

    	if (contextServer != null) contextServer.destroy();
    }

    protected void setupRequestVariables(HttpServletRequest req, Environment env) {
	env.allHeaders = new ArrayList();
	env.includedJava = php_include_java && PhpJavaServlet.getHeader("X_JAVABRIDGE_INCLUDE", req) == null;

	env.contextPath = (String) req.getAttribute("javax.servlet.include.context_path");
	if (env.contextPath == null) env.contextPath = req.getContextPath();

	env.pathInfo = (String) req.getAttribute("javax.servlet.include.path_info");
	if (env.pathInfo == null) env.pathInfo = req.getPathInfo();

	env.servletPath = (String) req.getAttribute("javax.servlet.include.servlet_path");
	if (env.servletPath == null) env.servletPath = req.getServletPath();

	env.queryString = (String) req.getAttribute("javax.servlet.include.query_string");
	if (env.queryString == null) env.queryString = req.getQueryString();

	env.requestUri = (String) req.getAttribute("javax.servlet.include.request_uri");
	if (env.requestUri == null) env.requestUri = req.getRequestURI();
    }
    
    private FCGIConnectionPool createConnectionPool(HttpServletRequest req, int children, Environment env) throws FCGIConnectException {
	channelName.initialize(env.contextPath);

	// Start the launcher.exe or launcher.sh
	channelName.startServer(logger);
	return new FCGIConnectionPool(channelName, children, php_fcgi_max_requests_number, defaultPoolFactory, php_fcgi_connection_pool_timeout_number);
    }

    /** calculate PATH_INFO, PATH_TRANSLATED and SCRIPT_FILENAME */
    protected void setPathInfo(HttpServletRequest req, HashMap envp, Environment env) {

	String pathInfo = env.pathInfo;
	if (pathInfo!=null) {
	    envp.put("PATH_INFO", pathInfo);
	    envp.put("PATH_TRANSLATED", DOCUMENT_ROOT+pathInfo);
	}

        if (env.includedJava)
	    envp.put("SCRIPT_FILENAME", ServletUtil.getRealPath(context, "java/JavaProxy.php"));
	else
	    envp.put("SCRIPT_FILENAME", ServletUtil.getRealPath(context, env.servletPath));
        
    }

    protected void setupFastCGIServer(HttpServletRequest req, Environment env) throws FCGIConnectException {
	boolean isJavaBridgeWc = ServletUtil.isJavaBridgeWc(env.contextPath);
	Object lockObject = isJavaBridgeWc?javaBridgeCtxLock:globalCtxLock;
	synchronized(lockObject) {
	    if(null == (env.connectionPool=fcgiConnectionPool)) {
		int children = php_fcgi_connection_pool_size_number;
		env.connectionPool = fcgiConnectionPool= createConnectionPool(req, children, env);
	    }
	}

    }
    protected void setupCGIEnvironment(HttpServletRequest req, HttpServletResponse res, Environment env) throws ServletException {
	HashMap envp = (HashMap)PROCESS_ENVIRONMENT.clone();

	envp.put("SERVER_SOFTWARE", "TOMCAT");
	envp.put("SERVER_NAME", ServletUtil.nullsToBlanks(req.getServerName()));
	envp.put("GATEWAY_INTERFACE", "CGI/1.1");
	envp.put("SERVER_PROTOCOL", ServletUtil.nullsToBlanks(req.getProtocol()));
	int port = ServletUtil.getLocalPort(req);
	Integer iPort = (port == 0 ? new Integer(-1) : new Integer(port));
	envp.put("SERVER_PORT", iPort.toString());
	envp.put("REQUEST_METHOD", ServletUtil.nullsToBlanks(req.getMethod()));
	envp.put("SCRIPT_NAME", env.contextPath+env.servletPath);
	envp.put("QUERY_STRING", ServletUtil.nullsToBlanks(env.queryString));
	envp.put("REMOTE_HOST", ServletUtil.nullsToBlanks(req.getRemoteHost()));
	envp.put("REMOTE_ADDR", ServletUtil.nullsToBlanks(req.getRemoteAddr()));
	envp.put("AUTH_TYPE", ServletUtil.nullsToBlanks(req.getAuthType()));
	envp.put("REMOTE_USER", ServletUtil.nullsToBlanks(req.getRemoteUser()));
	envp.put("REMOTE_IDENT", ""); //not necessary for full compliance
	envp.put("CONTENT_TYPE", ServletUtil.nullsToBlanks(req.getContentType()));
	setPathInfo(req, envp, env);

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


	env.environment = envp;

	    
	/* Inform the client that we are a cgi servlet and send the re-direct port */
	String override;
	if(override_hosts) { 
	    try {
		StringBuffer buf = new StringBuffer();
		buf.append(env.environment.get("SERVER_PORT"));
		buf.append("/");
		buf.append(env.contextPath);
		buf.append(env.servletPath);
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
		buf.append(env.environment.get("SERVER_PORT")); 
		buf.append('/');
		buf.append(env.requestUri);
		buf.append(".phpjavabridge");
		override = buf.toString();
	    }
	}
	else 
	    override = "";

	if (env.includedJava) {
	    env.environment.put("X_JAVABRIDGE_INCLUDE_ONLY", "1");
	    env.environment.put("X_JAVABRIDGE_INCLUDE", ServletUtil.getRealPath(getServletContext(), env.servletPath));
	}
	env.environment.put("X_JAVABRIDGE_OVERRIDE_HOSTS", override);
	// same for fastcgi, which already contains X_JAVABRIDGE_OVERRIDE_HOSTS=/ in its environment
	env.environment.put("X_JAVABRIDGE_OVERRIDE_HOSTS_REDIRECT", override); 
	env.environment.put("REDIRECT_STATUS", "200");
	env.environment.put("SERVER_SOFTWARE", Util.EXTENSION_NAME);
	env.environment.put("HTTP_HOST", env.environment.get("SERVER_NAME")+":"+env.environment.get("SERVER_PORT"));
	String remotePort = null;
	try {
	    remotePort = String.valueOf(req.getRemotePort());
	} catch (Throwable t) {
	    remotePort = String.valueOf(t);
	}
	env.environment.put("REMOTE_PORT", remotePort);
	String query = env.queryString;
	if(query!=null)
	    env.environment.put("REQUEST_URI", ServletUtil.nullsToBlanks(env.requestUri + "?" + query));
	else
	    env.environment.put("REQUEST_URI", ServletUtil.nullsToBlanks(env.requestUri));	          
	        
	env.environment.put("SERVER_ADDR", req.getServerName());
	env.environment.put("SERVER_SIGNATURE", SERVER_SIGNATURE);
	env.environment.put("DOCUMENT_ROOT", DOCUMENT_ROOT);
	if(req.isSecure()) env.environment.put("HTTPS", "On");
	        
	        
	/* send the session context now, otherwise the client has to 
	 * call handleRedirectConnection */
	String id = PhpJavaServlet.getHeader("X_JAVABRIDGE_CONTEXT", req);
	if(id==null) {
	    id = (env.ctx=ServletContextFactory.addNew(contextServer, this, getServletContext(), req, req, res)).getId();
		// short path S1: no PUT request
		AbstractChannelName channelName = contextServer.getChannelName(env.ctx);
		if (channelName != null) {
		    env.environment.put("X_JAVABRIDGE_REDIRECT", channelName.getName());
		    env.ctx.getBridge();
		    contextServer.start(channelName, logger);
		}
	}
	env.environment.put("X_JAVABRIDGE_CONTEXT", id);
    }
    /**
     * Optimized run method for FastCGI. Makes use of the large FCGI_BUF_SIZE and the specialized in.read(). 
     * It is a modified copy of the parseBody. 
     * @throws InterruptedException 
     * @see HeaderParser#parseBody(byte[], InputStream, OutputStream, HeaderParser)
     */
    protected void parseBody(HttpServletRequest req, HttpServletResponse res, Environment env) throws FCGIConnectionException, FCGIConnectException, IOException, ServletException {
	final byte[] buf = new byte[FCGIUtil.FCGI_BUF_SIZE];// headers cannot be larger than this value!
	
	InputStream in = null;
	OutputStream out = null;
	
	FCGIInputStream natIn = null;
	FCGIOutputStream natOut = null;

	FCGIConnectionPool.Connection connection = null;
	
	try {
	    connection = env.connectionPool.openConnection();
	    natOut = (FCGIOutputStream) connection.getOutputStream();
	    natIn = (FCGIInputStream) connection.getInputStream();

	    in = req.getInputStream(); // do not close in, otherwise requestDispatcher().include() will receive a closed input stream
	    out = ServletUtil.getServletOutputStream(res);
        
	    // send the FCGI header
	    natOut.writeBegin();
	    natOut.writeParams(env.environment);
		
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
		final FCGIOutputStream natOutputStream = natOut; natOut = null;
		(new Thread() {
			public void run() {
			    int n;
			    try {
				while((n=inputStream.read(buf))!=-1) {
				    natOutputStream.write(FCGIUtil.FCGI_STDIN, buf, n);
				}
				natOutputStream.write(FCGIUtil.FCGI_STDIN, FCGIUtil.FCGI_EMPTY_RECORD);
			    } catch (IOException e) {
				e.printStackTrace();
			    } finally {
				try {natOutputStream.close();} catch (IOException e) {}
			    }
			}
		    }).start();
	    } else {
		// write the post data before reading the response
		while((n=in.read(buf))!=-1) {
		    natOut.write(FCGIUtil.FCGI_STDIN, buf, n);
		}
		natOut.write(FCGIUtil.FCGI_STDIN, FCGIUtil.FCGI_EMPTY_RECORD);
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
			    addHeader(res, line, env);
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
		    if(i<N) out.write(buf, i, N-i);
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
	    if(natIn!=null) try {natIn.close();} catch (IOException e) {}
	    if(natOut!=null) try {natOut.close();} catch (IOException e) {}
	}
    }

    protected void execute(HttpServletRequest req, HttpServletResponse res) throws IOException, ServletException, InterruptedException {

	Environment env = new Environment();
	setupRequestVariables(req, env);
	setupFastCGIServer(req, env);
	setupCGIEnvironment(req, res, env);

	try {
	    parseBody(req, res, env);
	} catch (FCGIConnectException ex) {
	    if(Util.logLevel>1) {
		Util.logDebug("PHP FastCGI server failed: " + ex);
		Util.printStackTrace(ex);
	    }
	    throw new IOException("PHP FastCGI server failed: ", ex);
	} catch (FCGIConnectionException x) {
	    Util.logError("PHP application terminated unexpectedly, have you started php-cgi with the environment setting PHP_FCGI_MAX_REQUESTS=" + php_fcgi_max_requests + "?  Error: " + x);
	    if(Util.logLevel>1) {
		Util.logDebug("PHP FastCGI instance failed: " + x);
		Util.printStackTrace(x);
	    }
	    throw new ServletException("PHP FastCGI instance failed.", x);
	} catch (IOException e) {
	    // ignore client abort exception
	    if (Util.logLevel > 4) Util.printStackTrace(e);
	} finally {
	    if(env.ctx!=null) env.ctx.releaseManaged();
	    env.ctx = null;
	}
	    
    }
    protected void addHeader(HttpServletResponse response, String line, Environment env) {
	try {
	    if (line.startsWith("Status")) {
		line = line.substring(line.indexOf(":") + 1).trim();
		int i = line.indexOf(' ');
		if (i>0)
		    line = line.substring(0,i);

		response.setStatus(Integer.parseInt(line));
	    } else {
		if (!env.allHeaders.contains(line)) {
		    response.addHeader
			(line.substring(0, line.indexOf(":")).trim(),
			 line.substring(line.indexOf(":") + 1).trim());
		    env.allHeaders.add(line);
		}
	    }
	} catch (ArrayIndexOutOfBoundsException e) {/*not a valid header*/}
	catch (StringIndexOutOfBoundsException e){/*not a valid header*/}
    }
    protected void handle(HttpServletRequest req, HttpServletResponse res)
	throws ServletException, IOException {
	try {
	    execute(req, res);
	} catch (IOException e) {
	    try {res.reset();} catch (Exception ex) {/*ignore*/}
	    StringBuffer buf = new StringBuffer("PHP FastCGI server not running. Please see server log for details.");
	    if (channelName!=null && context!=null) {
		 buf.append(" Or start a PHP FastCGI server using the command:\n");
		 buf.append(channelName.getFcgiStartCommand(ServletUtil.getRealPath(context, CGI_DIR), php_fcgi_max_requests));
	    }
	    IOException ex = new IOException(buf.toString());
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
    
    /** required by IFCGIProcessFactory */
    /** {@inheritDoc} */
    public IFCGIProcess createFCGIProcess(String[] args, File home, Map env)
            throws IOException {
	return new FCGIProcess(args, home, env, getCgiDir(), phpTryOtherLocations, preferSystemPhp);
    }
    /** {@inheritDoc} */
    public String getPhpConnectionPoolSize() {
	return php_fcgi_connection_pool_size;
    }
    /** {@inheritDoc} */
    public String getPhpMaxRequests() {
	return php_fcgi_max_requests; 
    }
    /** {@inheritDoc} */
    public String getPhp() {
	return php;
    }
    /** {@inheritDoc} */
    public boolean getPhpIncludeJava() {
	return php_include_java;
    }
    /** {@inheritDoc} */
    public HashMap getEnvironment () {
	return FastCGIServlet.getProcessEnvironment();
    }
    /** {@inheritDoc} */
    public boolean canStartFCGI() {
	return canStartFCGI;
    }
    private String cgiDir;
    /** {@inheritDoc} */
    public String getCgiDir() {
	if (cgiDir != null) return cgiDir;
	return cgiDir = ServletUtil.getRealPath(context, FastCGIServlet.CGI_DIR);
    }
    private String pearDir;
    /** {@inheritDoc} */
    public String getPearDir() {
	if (pearDir != null) return pearDir;
	return pearDir = ServletUtil.getRealPath(context, FastCGIServlet.PEAR_DIR);
    }
    private String webInfDir;
    /** {@inheritDoc} */
   public String getWebInfDir() {
	if (webInfDir != null) return webInfDir;
	return webInfDir = ServletUtil.getRealPath(context, FastCGIServlet.WEB_INF_DIR);
   }
}
