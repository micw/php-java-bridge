/*-*- mode: Java; tab-width:8 -*-*/

package php.java.servlet;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.Socket;
import java.util.Map;

import javax.servlet.ServletConfig;
import javax.servlet.ServletContext;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import php.java.bridge.Util;
import php.java.bridge.Util.Process;

/**
 * Handles requests from internet clients.  <p> This servlet can handle GET/POST
 * requests directly. These requests invoke the php-cgi machinery from
 * the CGI or FastCGI servlet.  Although the servlet to php-cgi back
 * to servlet path is quite slow and consumes two servlet instances
 * instead of only one (compared to the http frontend/j2ee backend
 * setup), it can be useful as a replacement for a system php
 * installation, see the README in the <code>WEB-INF/cgi</code>
 * folder.  It is currently used for our J2EE test/demo.  </p>
 * @see php.java.bridge.JavaBridge
 *  */
public class PhpCGIServlet extends FastCGIServlet {

    private static final boolean USE_SH_WRAPPER = new File("/bin/sh").exists();
    private boolean fcgiStarted = false;
    private static final long serialVersionUID = 38983388211187962L;
    /**
     * The CGI default port
     */
    public static final int CGI_CHANNEL = 9567;
    
    /**
     * This controls how many child processes the PHP process spawns.
     */
    private static final String PHP_FCGI_CHILDREN = "20";
    
    /**
     * This controls how many requests each child process will handle before
exitting. When one process exits, another will be created. 
     */
    private static final String PHP_FCGI_MAX_REQUESTS = "500";
    
    /**
     * The max. number of concurrent CGI requests. 
     * <p>The value should be less than 1/2 of the servlet engine's thread pool size as this 
     * servlet also consumes an instance of PhpJavaServlet.</p>
     */
    public static final int CGI_MAX_REQUESTS = 50;
    private int cgi_max_requests = CGI_MAX_REQUESTS;
    private String php_fcgi_children = PHP_FCGI_CHILDREN;
    private String php_fcgi_max_requests = PHP_FCGI_MAX_REQUESTS;

    private final void runFcgi(Map env, String php) {
	    int c;
	    byte buf[] = new byte[CGIServlet.BUF_SIZE];
	    try {
	    Process proc = startFcgi(env, php);
	    if(proc==null) return;
	    /// make sure that the wrapper script php-fcgi.sh does not output to stdout
	    proc.getInputStream().close();
	    // proc.OutputStream should be closed in shutdown, see PhpCGIServlet.destroy()
	    InputStream in = proc.getErrorStream();
	    while((c=in.read(buf))!=-1) System.err.write(buf, 0, c);
		if(proc!=null) try {proc.destroy(); proc=null;} catch(Throwable t) {/*ignore*/}
	    } catch (Exception e) {Util.logDebug("Could not start FCGI server: " + e);};
    }

    /* Use the fcgi wrapper on Unix or cygwin */
    private class FCGIProcess extends Util.Process {
        String realPath;
        protected FCGIProcess(String[] args, File homeDir, Map env, String realPath) throws IOException {
             super(args, homeDir, env);
             this.realPath = realPath;
        }
	protected String argsToString(String php, String[] args) {
	    if(USE_SH_WRAPPER) {
		StringBuffer buf = new StringBuffer("/bin/sh ");
		buf.append(realPath);
		buf.append("/php-fcgi.sh ");
		buf.append(super.argsToString(php, args));
		return buf.toString();
	    } 
	    return super.argsToString(php, args);
	}
	public void start() throws NullPointerException, IOException {
	    super.start();
	}
    }
    /*
     * A FCGI server is only started if the admin allows starting FCGI, 
     * the security permission allows execute and bind and if the user has set use_fast_cgi to autostart 
     */
    
    /* The fast CGI Server process on this computer. Switched off per default. */
    private FCGIProcess proc = null;
    
    /* Start a fast CGI Server process on this computer. Switched off per default. */
    private final Process startFcgi(Map env, String php) throws IOException {
        if(proc!=null) return null;
	    String port;
	    if(System.getProperty("php.java.bridge.promiscuous", "false").toLowerCase().equals("true")) 
		port = ":"+String.valueOf(FCGI_CHANNEL);
	    else
		port = "127.0.0.1:"+String.valueOf(FCGI_CHANNEL);

		// Set override hosts so that php does not try to start a VM.
		// The value itself doesn't matter, we'll pass the real value
		// via the (HTTP_)X_JAVABRIDGE_OVERRIDE_HOSTS header field
		// later.
		env.put("X_JAVABRIDGE_OVERRIDE_HOSTS", override_hosts?"/":"");
		String[] args = new String[]{php, "-b", port};
		File home = null;
		try { home = ((new File(php)).getParentFile()); } catch (Exception e) {Util.printStackTrace(e);}
		proc = new FCGIProcess(args, home, env, context.getRealPath(cgiPathPrefix));
		proc.start();
            return (Process)proc;
    }
    
    /**@inheritDoc*/
    public void init(ServletConfig config) throws ServletException {
	String value;
	fcgiStarted = false;
    	super.init(config);
    	try {
	    value = config.getInitParameter("max_requests");
	    if(value!=null) {
	        value = value.trim();
	        cgi_max_requests=Integer.parseInt(value);
	    }
	} catch (Throwable t) {Util.printStackTrace(t);}      

	Util.TCP_SOCKETNAME = String.valueOf(CGI_CHANNEL);

	String val = null;
	try {
	    val = getServletConfig().getInitParameter("PHP_FCGI_CHILDREN");
	    if(val==null) val = System.getProperty("php.java.bridge.php_fcgi_children");
	} catch (Throwable t) {/*ignore*/}
	if(val!=null) php_fcgi_children = val;
	
	val = null;
	try {
	    val = getServletConfig().getInitParameter("PHP_FCGI_MAX_REQUESTS");
	    if(val==null) val = System.getProperty("php.java.bridge.php_fcgi_max_requests");	    
	} catch (Throwable t) {/*ignore*/}
	if(val!=null) php_fcgi_max_requests = val;
    }

    public void destroy() {
      super.destroy();

    	if(proc!=null) 
    	  try {
    	    if(USE_SH_WRAPPER) {
    	        // if called via /bin/sh wrapper
    	      	proc.getOutputStream().close();
    	      	proc.waitFor();
    	    } else {
    	        try { proc.getOutputStream().close(); } catch (Exception ex) {/*ignore*/}
    	        proc.destroy();
    	    }
    	  } catch (Throwable e) {
    	      /*ignore*/
    	  }
    
        proc = null;
	fcgiStarted = false;
    }
    
    /**
     * Adjust the standard tomcat CGI env. CGI only.
     */
    protected class CGIEnvironment extends FastCGIServlet.CGIEnvironment {
    	protected ContextFactory sessionFactory;
    	
	protected CGIEnvironment(HttpServletRequest req, HttpServletResponse res, ServletContext context) {
	    super(req, res, context);
	}

	protected String getCommand() {
	    return php;
	}
	protected boolean setCGIEnvironment(HttpServletRequest req, HttpServletResponse res) {
	    boolean ret = super.setCGIEnvironment(req, res);
	    if(ret) {
	    	/* Inform the client that we are a cgi servlet and send the re-direct port */
		if(override_hosts) { 
		    StringBuffer buf = new StringBuffer("127.0.0.1:");
		    buf.append(this.env.get("SERVER_PORT"));
		    buf.append("/");
		    buf.append(req.getRequestURI());
		    buf.append("javabridge");
		    this.env.put("X_JAVABRIDGE_OVERRIDE_HOSTS", buf.toString());
		}
		else
		    this.env.put("X_JAVABRIDGE_OVERRIDE_HOSTS", "");
		this.env.put("REDIRECT_STATUS", "200");
		this.env.put("SCRIPT_FILENAME", this.env.get("PATH_TRANSLATED"));
	        this.env.put("SERVER_SOFTWARE", Util.EXTENSION_NAME);
	        this.env.put("HTTP_HOST", this.env.get("SERVER_NAME")+":"+this.env.get("SERVER_PORT"));
	        
		/* send the session context now, otherwise the client has to 
		 * call handleRedirectConnection */
	    	String id = req.getHeader("X_JAVABRIDGE_CONTEXT");
	    	if(id==null) id = ContextFactory.addNew(PhpCGIServlet.this.getServletContext(), req, req, res).getId();
		this.env.put("X_JAVABRIDGE_CONTEXT", id);
	        
	        /* For the request:
	         * http://localhost:8080/JavaBridge/test.php the
	         * req.getPathInfo() returns cgi/test.php. But PHP
	         * shouldn't know about this detail.
	         */
	        this.env.remove("PATH_INFO"); 
	    }
	    return ret;
	        	
	}
	
	private boolean canStartFCGI(String contextPath) {
	    return canStartFCGI || (fcgiIsAvailable && (contextPath!=null && contextPath.endsWith("JavaBridge")));
	}
	protected String[] findCGI(String pathInfo, String webAppRootDir,
				   String contextPath, String servletPath,
				   String cgiPathPrefix) {
	    String[] retval;
	    if(!fcgiStarted && canStartFCGI(contextPath)) {
		fcgiStarted = true;
		try {
		    Thread t = (new Thread("JavaBridgeFastCGIRunner") {
			    public void run() {
				Map env = (Map) processEnvironment.clone();
				env.put("PHP_FCGI_CHILDREN", php_fcgi_children);
				env.put("PHP_FCGI_MAX_REQUESTS", php_fcgi_max_requests);
				runFcgi(env, php);
			    }
			});
		    t.setDaemon(true);
		    t.start();
		    long T0 = System.currentTimeMillis();
		    int count = 15;
		    InetAddress addr = InetAddress.getByName("127.0.0.1");
		    while(count-->0) {
			try {
			    Socket s = new Socket(addr, FCGI_CHANNEL);
			    s.close();
			    break;
			} catch (IOException e) {/*ignore*/}
			if(System.currentTimeMillis()-1600>T0) break;
			Thread.sleep(100);
		    }
		} catch (Throwable t) {Util.printStackTrace(t);}
	    } 
	    
	    if((retval=super.findCGI(pathInfo, webAppRootDir, contextPath, servletPath, cgiPathPrefix))!=null) return retval;
	    cgiRunnerFactory = new CGIRunnerFactory();
		
	    StringBuffer cgiDir = new StringBuffer(webAppRootDir);
	    if(!webAppRootDir.endsWith(File.separator)) cgiDir.append(File.separatorChar);
	    cgiDir.append(cgiPathPrefix);

	    // incorrect but reasonable values for display only.
	    String display_cgi="php";
	    this.pathInfo = "/"+display_cgi+servletPath;
	    
	    cgiDir.append(File.separatorChar);
	    cgiDir.append(display_cgi);
	    return new String[] {
		cgiDir.toString(),
		contextPath+servletPath, 
		File.separator+display_cgi, 
		display_cgi};
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

    protected class CGIRunnerFactory extends CGIServlet.CGIRunnerFactory {
        protected CGIServlet.CGIRunner createCGIRunner(CGIServlet.CGIEnvironment cgiEnv) {
            return new CGIRunner(cgiEnv);
	}
    }

    protected static class HeaderParser extends Util.HeaderParser {
    	private CGIRunner runner;
	protected HeaderParser(CGIRunner runner) {
	    this.runner = runner;
    	}
    	protected void parseHeader(String header) {
	    runner.addHeader(header);
    	}
    }
    protected class CGIRunner extends CGIServlet.CGIRunner {
	
	protected CGIRunner(CGIServlet.CGIEnvironment env) {
	    super(env);
	}
        protected void run() throws IOException, ServletException {
	    Process proc = null;
	    InputStream natIn = null;
	    OutputStream natOut = null;
	    InputStream in = null;
	    OutputStream out = null;
    	    try {
        	proc = Util.ProcessWithErrorHandler.start(new String[]{command}, wd, env);

        	byte[] buf = new byte[BUF_SIZE];// headers cannot be larger than this value!

        	// the post variables
        	in = stdin;
    		natOut = proc.getOutputStream();
        	if(in!=null) {
		    int n;
    		    while((n=in.read(buf))!=-1) {
    			natOut.write(buf, 0, n);
    		    }
    		}
        	natOut.flush();
        	
        	// header and body
         	natIn = proc.getInputStream();
    		out = response.getOutputStream();

    		Util.parseBody(buf, natIn, out, new Util.HeaderParser() {protected void parseHeader(String header) {addHeader(header);}});

    		try {
                proc.waitFor();
            } catch (InterruptedException e) {
                Util.printStackTrace(e);
            }
    	    } finally {
    		if(in!=null) try {in.close();} catch (IOException e) {}
    		if(natIn!=null) try {natIn.close();} catch (IOException e) {}
    		if(natOut!=null) try {natOut.close();} catch (IOException e) {}
    		if(proc!=null) proc.destroy();
    	    }
        }
    } //class CGIRunner
    
    static short count = 0;
    private boolean checkPool(HttpServletResponse res) throws ServletException, IOException {
        if(count++>=cgi_max_requests) {
            res.sendError(HttpServletResponse.SC_SERVICE_UNAVAILABLE, "Out of system resources. Try again shortly or use the Apache or IIS frontend instead.");
            Util.logFatal("Out of system resources. Adjust max_requests or set up the Apache or IIS frontend.");
            return false;
        }
        return true;
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
    	    if(!checkPool(res)) return;
 	    super.doGet(req, res);
    	} catch (IOException e) {
    	    try {res.reset();} catch (Exception ex) {/*ignore*/}
	    StringBuffer buf = new StringBuffer(getServletConfig().getServletContext().getRealPath(cgiPathPrefix));
	    buf.append(File.separator);
	    buf.append("php-cgi-");
	    buf.append(Util.osArch);
	    buf.append("-");
	    buf.append(Util.osName);
	    buf.append("[.sh]|[.exe]");
    	    String wrapper = buf.toString();
 	    ServletException ex = new ServletException("An IO exception occured. " +
	    		"Probably php was not installed as \"/usr/bin/php-cgi\" or \"c:/php5/php-cgi.exe\"\n or \""+wrapper+"\".\n" +
	    		"Please see \"php_exec\" in your WEB-INF/web.xml and WEB-INF/cgi/README for details.", e);
	    php=null;
	    checkCgiBinary(getServletConfig());
	    throw ex;
    	} catch (SecurityException sec) {
    	    try {res.reset();} catch (Exception ex) {/*ignore*/}
	    ServletException ex = new ServletException(
"A security exception occured, could not run PHP.\n" + startFcgiMessage());
	    php=null;
	    checkCgiBinary(getServletConfig());
	    throw ex;    	    
    	} catch (ServletException e) {
    	    try {res.reset();} catch (Exception ex) {/*ignore*/}
    	    throw e;
    	}
    	catch (Throwable t) {
    	    try {res.reset();} catch (Exception ex) {/*ignore*/}
	    Util.printStackTrace(t);
    	    throw new ServletException(t);
    	} finally {
    	    count--;
    	}
   }
}
