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
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import javax.servlet.ServletConfig;
import javax.servlet.ServletContext;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import php.java.bridge.Util;
import php.java.bridge.http.IContextFactory;
import php.java.servlet.CGIServlet;
import php.java.servlet.PhpCGIServlet;

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
public abstract class FastCGIServlet extends CGIServlet {
    /**
     * 
     */
    private static final long serialVersionUID = 3545800996174312757L;

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
    public static final String PHP_FCGI_CHILDREN = "5"; // should be less than Util.THREAD_POOL_MAX_SIZE;
    
    /**
     * This controls how many requests each child process will handle before
     * exitting. When one process exits, another will be created. Default is 5000.
     */
    public static final String PHP_FCGI_MAX_REQUESTS = "5000";

    public static final String FCGI_PIPE = NPChannelName.PREFIX +"JavaBridge@9667";
    
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

    protected String php_fcgi_children = PHP_FCGI_CHILDREN;
    protected int php_fcgi_children_number = Integer.parseInt(PHP_FCGI_CHILDREN);
    protected String php_fcgi_max_requests = PHP_FCGI_MAX_REQUESTS;
    protected int php_fcgi_max_requests_number = Integer.parseInt(PHP_FCGI_MAX_REQUESTS);

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
    
    private final Factory defaultPoolFactory = new Factory() {
      public InputStream createInputStream() { return new FastCGIInputStream(FastCGIServlet.this); }
      public OutputStream createOutputStream() { return new FastCGIOutputStream(); }
      public Channel connect(ChannelName name) throws ConnectException {
	  return name.connect();
    }
    };
    private final CGIRunnerFactory defaultCGIRunnerFactory = new CGIRunnerFactory();
    protected ChannelName channelName;

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
		      value = getRealPath(config.getServletContext(), cgiPathPrefix)+File.separator+value;
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
     * @see php.java.servlet.fastcgi.ConnectionPool
     * @see #destroy()
     */
    public void init(ServletConfig config) throws ServletException {
	String value;
	super.init(config);
    	try {
	    value = config.getInitParameter("override_hosts");
	    if (value==null) value = context.getInitParameter("override_hosts");
	    if(value==null) value="";
	    value = value.trim();
	    value = value.toLowerCase();
	    if(value.equals("off") || value.equals("false")) override_hosts=false;
	} catch (Throwable t) {Util.printStackTrace(t);}
    	try {
	    value = config.getInitParameter("prefer_system_php_exec");
	    if(value==null) value="";
	    value = value.trim();
	    value = value.toLowerCase();
	    if(value.equals("on") || value.equals("true")) preferSystemPhp=true;
	} catch (Throwable t) {Util.printStackTrace(t);}	
	String val = null;
	try {
	    val = getServletConfig().getInitParameter("php_fcgi_children");
	    if(val==null) val = getServletConfig().getInitParameter("PHP_FCGI_CHILDREN");
	    if(val==null) val = System.getProperty("php.java.bridge.php_fcgi_children");
	    if(val!=null) php_fcgi_children_number = Integer.parseInt(val);
	} catch (Throwable t) {/*ignore*/}
	if(val!=null) php_fcgi_children = val;
	
	val = null;
	try {
	    val = getServletConfig().getInitParameter("php_fcgi_max_requests");
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
	channelName = ChannelName.getChannelName();
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

    protected StringBuffer getCgiDir() {
       	String webAppRootDir = getRealPath(getServletContext(), "/");
        StringBuffer cgiDir = new StringBuffer(webAppRootDir);
        if(!webAppRootDir.endsWith(File.separator)) cgiDir.append(File.separatorChar);
        cgiDir.append(cgiPathPrefix);
        return cgiDir;
    }

    protected class CGIEnvironment extends CGIServlet.CGIEnvironment {
	private ConnectionPool connectionPool;
    	public IContextFactory ctx;

	protected CGIEnvironment(HttpServletRequest req, HttpServletResponse res, ServletContext context) {
	    super(req, res, context);
	}
	protected static final String empty_string = "";
	// in a shared environment the PhpCGIServlet JavaBridge
	// context reserves 1 php-cgi instances for activation and for
	// handling JavaBridge/foo.php requests.  The rest is
	// available to the GlobalPhpCGIServlet
	private static final int JAVABRIDGE_RESERVE = 1;
	protected String[] findCGI(String pathInfo, String webAppRootDir,
				   String contextPath, String servletPath,
				   String cgiPathPrefix) {

	    
	    boolean isJavaBridgeWc = isJavaBridgeWc(contextPath);
	    Object lockObject = isJavaBridgeWc?javaBridgeCtxLock:globalCtxLock;
	    synchronized(lockObject) {
		if(!fcgiIsAvailable) return null;
		if(null == (connectionPool=fcgiConnectionPool))
		    try {
			int children = php_fcgi_children_number;
			if(delegateToJavaBridgeContext) { 
			    // NOTE: the shared_fast_cgi_pool options
			    // from the GlobalPhpCGIServlet and from
			    // the PhpCGIServlet must match.
			    children = isJavaBridgeWc ? JAVABRIDGE_RESERVE : php_fcgi_children_number-JAVABRIDGE_RESERVE;
			}
			fcgiConnectionPool=connectionPool = createConnectionPool(children);
		    } catch (Exception e) {
			String base = CGIServlet.getRealPath(context, cgiPathPrefix);
			Util.logDebug(e+": FastCGI channel not available, switching off fast cgi. " + 
				channelName.getFcgiStartCommand(base, php_fcgi_max_requests));
			
			fcgiIsAvailable=false;
			return null;
		    }
	    }
	    cgiRunnerFactory = defaultCGIRunnerFactory;
			
	    // Needed by CGIServlet
	    return new String[] {
		php, // sCGIFullPath, the full path of the PHP executable: used by getCommand(), X_TOMCAT_SCRIPT_PATH and getWorkingDirectory()
		contextPath+servletPath,  		// sCGIScriptName: the php file relative to webappRootDir, e.g.: /index.php 
		empty_string,       	// sCGIFullName: not used (used in setPathInfo, which we don't use)
		empty_string};      	// sCGIName: not used anywhere
	}
	private ConnectionPool createConnectionPool(int children) throws ConnectException {
	    channelName.initialize((PhpCGIServlet)FastCGIServlet.this, (PhpCGIServlet.CGIEnvironment)this, contextPath);

	    // Start the launcher.exe or launcher.sh
	    fcgiIsAvailable = channelName.startServer();
	    return new ConnectionPool(channelName, children, php_fcgi_max_requests_number, defaultPoolFactory);
	}
    }
    /**
     * Create a cgi environment. Used by cgi only.
     * @param req The request
     * @param servletContext The servlet context
     * @return The new cgi environment.
     */
    protected CGIServlet.CGIEnvironment createCGIEnvironment(HttpServletRequest req, HttpServletResponse res, ServletContext servletContext) {
	CGIEnvironment env = new CGIEnvironment(req, res, servletContext);
	env.init(req, res);
	return env;
    }

    public static boolean isJavaBridgeWc(String contextPath) {
        return (contextPath!=null && contextPath.endsWith("JavaBridge"));
    }

    protected class CGIRunnerFactory extends CGIServlet.CGIRunnerFactory {
        protected CGIServlet.CGIRunner createCGIRunner(CGIServlet.CGIEnvironment cgiEnv) {
            return new CGIRunner(cgiEnv);
	}
    }

    protected class CGIRunner extends CGIServlet.CGIRunner {

	private ConnectionPool connectionPool;
	protected IContextFactory ctx;
	
	/**
	 * @param command
	 * @param env
	 * @param wd
	 */
	protected CGIRunner(CGIServlet.CGIEnvironment env) {
	    super(env);
	    connectionPool = ((CGIEnvironment)env).connectionPool;
	    ctx = ((CGIEnvironment)env).ctx;
	}

	protected void doExecute() throws IOException, ServletException {
	    try {
	        parseBody();
	    } catch (ConnectionException ex) {
	        Util.logError("PHP application terminated unexpectedly, have you started php-cgi with the environment setting PHP_FCGI_MAX_REQUESTS=" + php_fcgi_max_requests + "? Trying again using a new connection: " + ex);
	        parseBody();
	    }
	}
	protected void execute() throws IOException, ServletException {
	    try {
	        doExecute();
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
	      if(Util.logLevel>4) {
		  Util.logDebug("IOException caused by internet browser: " + e);
		  Util.printStackTrace(e);
	      }	      
	      //throw new ServletException("IOException caused by internet browser", e);
	    } finally {
    		if(ctx!=null) ctx.release();
    		ctx = null;
	    }
	    
	}
	/**
	 * Optimized run method for FastCGI. Makes use of the large FCGI_BUF_SIZE and the specialized in.read(). 
	 * It is a modified copy of the parseBody. 
	 * @throws InterruptedException 
	 * @see Util#parseBody(byte[], InputStream, OutputStream, HeaderParser)
	 */
        protected void parseBody() throws IOException, ServletException {
	    InputStream in = null;
            OutputStream out = null;
	    FastCGIInputStream natIn = null;
	    FastCGIOutputStream natOut = null;
	    ConnectionPool.Connection connection = null;
	    try {
	        connection = connectionPool.openConnection();
		natOut = (FastCGIOutputStream) connection.getOutputStream();
		natIn = (FastCGIInputStream) connection.getInputStream();

		in = stdin;
		out = response.getOutputStream();
        
		// send the FCGI header
		natOut.writeBegin();
		natOut.writeParams(env);
		
		String line = null;
		byte[] buf = new byte[FCGI_BUF_SIZE];// headers cannot be larger than this value!
		int i=0, n, s=0;
		boolean eoh=false;

		// the post variables
		if(in!=null) {
		    while((n=in.read(buf))!=-1) {
			natOut.write(buf, n);
		    }
		    in.close(); in = null;
		} else {
		    natOut.write(FCGI_EMPTY_RECORD);
		}
		natOut.close(); natOut = null;
		
		// the header and content
		// NOTE: unlike cgi, fcgi headers must be sent in _one_ packet
		// leading or trailing zero length packets are allowed.
		while((n = natIn.read(buf)) !=-1) {
		    int N = i + n;
		    // header
		    while(!eoh && i<N) {
			switch(buf[i++]) {
			
			case '\n':
			    if(s+2==i && buf[s]=='\r') {
				eoh=true;
			    } else {
				line = new String(buf, s, i-s-2, Util.ASCII);
				addHeader(line);
				s=i;
			    }
			}
		    }
		    // body
		    if(eoh) {
			if(out!=null && i<N) out.write(buf, i, N-i);
			i=0;
		    }
		}
		natIn.close(); 
		String phpFatalError = natIn.checkError();
		StringBuffer phpError = natIn.getError();
		if (phpError!=null) Util.logMessage(phpError.toString());
		natIn = null; connection = null;
		
		if(phpFatalError!=null) throw new RuntimeException(phpFatalError);
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
    } //class CGIRunner
}
