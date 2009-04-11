/*-*- mode: Java; tab-width:8 -*-*/

package php.java.servlet;

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

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URI;
import java.util.HashMap;

import javax.servlet.ServletConfig;
import javax.servlet.ServletContext;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import php.java.bridge.Util;
import php.java.bridge.Util.Process;
import php.java.bridge.http.AbstractChannelName;
import php.java.bridge.http.ContextFactory;
import php.java.bridge.http.ContextServer;
import php.java.bridge.http.IContextFactory;
import php.java.servlet.fastcgi.FastCGIServlet;

/**
 * Handles requests from internet clients.  <p> This servlet can handle GET/POST
 * requests directly. These requests invoke the php-cgi machinery from
 * the CGI or FastCGI servlet.  Although the servlet to php-cgi back
 * to servlet path is quite slow (compared with the http front end/j2ee back end
 * setup) and consumes two servlet instances
 * instead of only one, it can be useful as a replacement for a system php
 * installation, see the README in the <code>WEB-INF/cgi</code>
 * folder.  It is currently used for our J2EE test/demo.  </p>
 * @see php.java.bridge.JavaBridge
 *  */
public class PhpCGIServlet extends FastCGIServlet {

    /** True if /bin/sh exists, false otherwise */
    public static final boolean USE_SH_WRAPPER = new File("/bin/sh").exists();
    private static final long serialVersionUID = 38983388211187962L;

    /**
     * The max. number of concurrent CGI requests. 
     * <p>The value should be less than 1/2 of the servlet engine's thread pool size as this 
     * servlet also consumes an instance of PhpJavaServlet.</p>
     */
    private static final int CGI_MAX_REQUESTS = Integer.parseInt(Util.THREAD_POOL_MAX_SIZE);
    private static int servletPoolSize = CGI_MAX_REQUESTS;
    private final CGIRunnerFactory defaultCgiRunnerFactory = new CGIRunnerFactory();
    
    private String DOCUMENT_ROOT;
    private String SERVER_SIGNATURE;
    private ContextServer contextServer; // shared with PhpJavaServlet, PhpCGIServlet
    private boolean php_include_java;
    
    /**@inheritDoc*/
    public void init(ServletConfig config) throws ServletException {
    	super.init(config);

    	ServletContext context = config.getServletContext();
	String servletContextName=CGIServlet.getRealPath(context, "");
	if(servletContextName==null) servletContextName="";
	contextServer = PhpJavaServlet.getContextServer(context);

	DOCUMENT_ROOT = getRealPath(context, "");
	SERVER_SIGNATURE = context.getServerInfo();

	String val = null;
	php_include_java = true;
	try {
	    val  = config.getInitParameter("php_include_java");
	    if(val==null) val = config.getInitParameter("PHP_INCLUDE_JAVA");
	    if(val==null) val = System.getProperty("php.java.bridge.php_include_java");
	    if(val!=null && (val.equalsIgnoreCase("off") ||  val.equalsIgnoreCase("false")))
		php_include_java = false;
	} catch (Throwable t) {/*ignore*/}
    }
    
    private static final Object lockObject = new Object();   
    private static boolean servletPoolSizeDetermined = false;
    /** Return the servlet pool size 
     * @return The  pool size*/
    public static int getServletPoolSize () {
	synchronized (lockObject) {
	    if (servletPoolSizeDetermined) return servletPoolSize;
	    servletPoolSizeDetermined = true;

	    // tomcat
	    int size = Util.getMBeanProperty("*:type=ThreadPool,name=http*", "maxThreads");
	    // jetty
	    if (size < 2) size = Util.getMBeanProperty("*:ServiceModule=*,J2EEServer=*,name=JettyWebConnector,j2eeType=*", "maxThreads");
	    // sun 
	    if (size < 2) size = Util.getMBeanProperty("*:type=protocolHandler,className=*HttpProtocol", "maxThreads");
	    // fail
	    if (size > 2) servletPoolSize = size;
	
	    return servletPoolSize;
	}
    }
    
    /**{@inheritDoc}*/
    public void destroy() {
      super.destroy();
    }
    
    /**
     * Adjust the standard tomcat CGI env. CGI only.
     */
    public class CGIEnvironment extends FastCGIServlet.CGIEnvironment {
    	protected SimpleServletContextFactory sessionFactory;
	/** Only for internal use */
    	public HttpServletRequest req;
    	private boolean included_java;
    	
	protected CGIEnvironment(HttpServletRequest req, HttpServletResponse res, ServletContext context) {
	    super(req, res, context);
	    this.req = req;
	    this.included_java = php_include_java && req.getHeader("X_JAVABRIDGE_INCLUDE") == null;
	}

	/** PATH_INFO and PATH_TRANSLATED not needed for PHP, SCRIPT_FILENAME is enough */
        protected void setPathInfo(HttpServletRequest req, HashMap envp, String sCGIFullName) {
            if (included_java) 
        	envp.put("SCRIPT_FILENAME", nullsToBlanks(getRealPath(context, "java/JavaProxy.php")));
            else
                envp.put("SCRIPT_FILENAME", nullsToBlanks(getRealPath(context, servletPath)));
        }
	protected boolean setCGIEnvironment(HttpServletRequest req, HttpServletResponse res) {
	    boolean ret = super.setCGIEnvironment(req, res);
	    if(ret) {
	    	/* Inform the client that we are a cgi servlet and send the re-direct port */
	      String override;
	      if(override_hosts) { 
                    try {
                	StringBuffer buf = new StringBuffer();
                	buf.append(this.environment.get("SERVER_PORT"));
                	buf.append("/");
                	buf.append(req.getContextPath());
                	buf.append(req.getServletPath());
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
                	buf.append(req.getRequestURI());
                	buf.append(".phpjavabridge");
                	override = buf.toString();
                    }
	        }
		else 
		    override = "";

	        if (included_java) {
	            this.environment.put("X_JAVABRIDGE_INCLUDE_ONLY", "1");
	            this.environment.put("X_JAVABRIDGE_INCLUDE", CGIServlet.getRealPath(PhpCGIServlet.this.getServletContext(), req.getServletPath()));
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
	        String query = req.getQueryString();
	        if(query!=null)
	            this.environment.put("REQUEST_URI", nullsToBlanks(req.getRequestURI() + "?" + query));
	        else
	            this.environment.put("REQUEST_URI", nullsToBlanks(req.getRequestURI()));	          
	        
	        this.environment.put("SERVER_ADDR", req.getServerName());
	        this.environment.put("SERVER_SIGNATURE", SERVER_SIGNATURE);
	        this.environment.put("DOCUMENT_ROOT", DOCUMENT_ROOT);
	        if(req.isSecure()) this.environment.put("HTTPS", "On");
	        
	        
		/* send the session context now, otherwise the client has to 
		 * call handleRedirectConnection */
	    	String id = req.getHeader("X_JAVABRIDGE_CONTEXT");
	    	if(id==null)
	    	    id = (ctx=ServletContextFactory.addNew(PhpCGIServlet.this, PhpCGIServlet.this.getServletContext(), req, req, res)).getId();
	    	else
	    	    ctx = ContextFactory.peek(id);
	    	// short path S1: no PUT request
	    	AbstractChannelName channelName = contextServer.getFallbackChannelName(null, ctx);
	    	if (channelName != null) {
	    	    this.environment.put("X_JAVABRIDGE_REDIRECT", channelName.getName());
	    	    ctx.getBridge();
	    	    contextServer.start(channelName);
	    	}
	    	this.environment.put("X_JAVABRIDGE_CONTEXT", id);
	    }
	    return ret;
	        	
	}
	protected String[] findCGI(String pathInfo, String webAppRootDir,
				   String contextPath, String servletPath,
				   String cgiPathPrefix) {
	    String[] retval;
	    /*
	     * Now that FCGI is started (or failed to start), connect to the FCGI server 
	     */
	    if((retval=super.findCGI(pathInfo, webAppRootDir, contextPath, servletPath, cgiPathPrefix))!=null) return retval;
	    cgiRunnerFactory = defaultCgiRunnerFactory;
	
	    // Needed by CGIServlet
	    return new String[] {
		php, // sCGIFullPath, the full path of the PHP executable: used by getCommand(), X_TOMCAT_SCRIPT_PATH and getWorkingDirectory()
		contextPath+servletPath,  		// sCGIScriptName: the php file relative to webappRootDir, e.g.: /index.php 
		empty_string,       	// sCGIFullName: not used (used in setPathInfo, which we don't use)
		empty_string};      	// sCGIName: not used anywhere
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
    	public void parseHeader(String header) {
	    runner.addHeader(header);
    	}
    }
    protected class CGIRunner extends CGIServlet.CGIRunner {
	protected IContextFactory ctx;
	
	protected CGIRunner(CGIServlet.CGIEnvironment env) {
	    super(env);
	    ctx = ((CGIEnvironment)env).ctx;
	}
        protected void execute() throws IOException, ServletException {
	    Process proc = null;
	    
	    InputStream natIn = null;
	    OutputStream natOut = null;
	    ByteArrayOutputStream natErr = new ByteArrayOutputStream();
	    
	    InputStream in = null;
	    OutputStream out = null;

	    try {
        	proc = Util.ProcessWithErrorHandler.start(Util.getPhpArgs(new String[]{php}), wd, env, phpTryOtherLocations, preferSystemPhp, natErr);

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

    		Util.parseBody(buf, natIn, out, new Util.HeaderParser() {public void parseHeader(String header) {addHeader(header);}});

    		try {
     		    proc.waitFor();
     		} catch (InterruptedException e) {
    		    /*ignore*/
    		}
    	    } finally {
    		if(in!=null) try {in.close();} catch (IOException e) {/*ignore*/}
    		if(natIn!=null) try {natIn.close();} catch (IOException e) {/*ignore*/}
    		if(natOut!=null) try {natOut.close();} catch (IOException e) {/*ignore*/}
    		if(proc!=null) try {proc.destroy();} catch (Exception e) {/*ignore*/}
    		
    		if (ctx!=null) ctx.release();
    		ctx = null;
    	    }
    	    
    	    if (proc!=null)
    	    {
    	        if(natErr.size()>0) Util.logMessage(natErr.toString());
    	        try {proc.checkError(); } catch (Util.Process.PhpException e) {throw new ServletException(e);}
    	    }
    	    
        }
    } //class CGIRunner
        
    /**
     * Used when running as a cgi binary only.
     * 
     *  (non-Javadoc)
     * @see javax.servlet.http.HttpServlet#doGet(javax.servlet.http.HttpServletRequest, javax.servlet.http.HttpServletResponse)
     */
    protected void handle(HttpServletRequest req, HttpServletResponse res, boolean handleInput)
	throws ServletException, IOException {
    	try {
 	    super.handle(req, res, handleInput);
    	} catch (IOException e) {
    	    try {res.reset();} catch (Exception ex) {/*ignore*/}
	    StringBuffer buf = new StringBuffer(getRealPath(getServletConfig().getServletContext(), cgiPathPrefix));
	    buf.append(File.separator);
	    buf.append("php-cgi-");
	    buf.append(Util.osArch);
	    buf.append("-");
	    buf.append(Util.osName);
	    buf.append("[.sh]|[.exe]");
    	    String wrapper = buf.toString();
 	    ServletException ex = new ServletException("An IO exception occured. " +
	    		"Probably php was not installed as \"/usr/bin/php-cgi\" or \"c:/Program Files/PHP/php-cgi.exe\"\n or \""+wrapper+"\".\n" +
	    		"Please see \"php_exec\" in your WEB-INF/web.xml and WEB-INF/cgi/README for details.", e);
	    php=null;
	    checkCgiBinary(getServletConfig());
	    throw ex;
    	} catch (SecurityException sec) {
    	    try {res.reset();} catch (Exception ex) {/*ignore*/}
    	    String base = CGIServlet.getRealPath(context, cgiPathPrefix);
    	    
	    ServletException ex = new ServletException(
		    "A security exception occured, could not run PHP.\n" + channelName.getFcgiStartCommand(base, php_fcgi_max_requests));
	    fcgiIsAvailable=fcgiIsConfigured;
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
    	}
   }
}
