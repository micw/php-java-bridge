/*-*- mode: Java; tab-width:8 -*-*/

package php.java.servlet;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.lang.reflect.Method;
import java.util.Map;

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
import php.java.bridge.http.ContextServer;

/**
 * Handles requests from PHP clients. When apache or IIS are not
 * available, this servlet can start php as a CGI sub-process.<br>
 * However, it is recommended to install php as an Apache module and
 * to share the servlet engine's <code>webapps</code> directory with
 * apache's <code>htdocs</code> directory. This is default since RH
 * Fedora 4.  <br> For frameworks, such as Java Server Faces, it is
 * recommended to forward requests to the backend as follows:<br>
 * <code> &lt;?php<br> function toString() {"return "hello java
 * world!";}<br> java_context()-&gt;call(java_closure())
 * ||header("Location: hello.jsp");<br> ?&gt;<br> </code><br> This
 * allows the framework (jsf, struts, ...) to call php procedures as
 * follows:<br> <code> PhpScriptEngine e = new PhpScriptEngine();<br>
 * e.eval(new URLReader(new URL("http://.../hello.php")));<br>
 * out.println(((Invocable)e).invoke("toString", new Object[]{}));<br>
 * e.release();<br> </code> <br>
 *
 * An alternative would be to install <code>mod_jk</code> to "JkMount"
 * the java "webapps" document root into the "htdocs" document root of
 * the HTTP server and to configure it so that it automatically
 * forwards all .jsp and servlet requests to the servlet engine.
 * 
 */
public class PhpJavaServlet extends FastCGIServlet {

    private static final long serialVersionUID = 3257854259629144372L;

    /**
     * The CGI default port
     */
    public static final int CGI_CHANNEL = 9567;
    private static final Object contextServer = new ContextServer(new ThreadPool("JavaBridgeContextRunner", Integer.parseInt(Util.THREAD_POOL_MAX_SIZE)));

    protected static class Logger extends Util.Logger {
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
    private static Object proc = null;
    protected static synchronized final Process startFcgi(Map env, String php) throws IOException {
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
		env.put("X_JAVABRIDGE_OVERRIDE_HOSTS", "/");
		if(System.getProperty("php.java.bridge.promiscuous", "false").toLowerCase().equals("true")) {
			String[] args = new String[]{php, "-b", port};
			File home = null;
			try { home = ((new File(php)).getParentFile()); } catch (Exception e) {Util.printStackTrace(e);}
		    proc = Util.Process.start(args, home, env);
		} else {
			String[] args = new String[]{null, "-b", port};
		    proc = Util.Process.start(args, null, env);
		}
            return (Process)proc;
    }
    static {
        try {
        Runtime.getRuntime().addShutdownHook(
		 new Thread("JavaBridgeServletShutdown") {
		     public void run() {
		         Class noparm[] = new Class[]{};
		         Object noarg[] = new Object[]{};
		         
		         Method m;
			 if(contextServer!=null) 
			     try {
			         m = contextServer.getClass().getMethod("destroy", noparm);
			         m.setAccessible(true);
			         m.invoke(contextServer, noarg);
			    } catch(Throwable t) {
			        t.printStackTrace();
			        }
			 if(proc!=null) 
			     try {
			     m = proc.getClass().getMethod("destroy", noparm);
			     m.setAccessible(true);
			     m.invoke(proc, noarg);
			     proc = null;
			   } catch(Throwable t) {
			     t.printStackTrace();
			  }
		     }
		 });
        } catch (SecurityException t) {/*ignore*/}
    }

    
    private boolean override_hosts = true;
    private boolean allowHttpTunnel = false;
    public void init(ServletConfig config) throws ServletException {
 	String value;
        try {
	    value = config.getInitParameter("servlet_log_level");
	    if(value!=null && value.trim().length()!=0) Util.logLevel=Integer.parseInt(value.trim());
        } catch (Throwable t) {Util.printStackTrace(t);}      

    	try {
	    value = config.getInitParameter("override_hosts");
	    if(value==null) value="";
	    value = value.trim();
	    value = value.toLowerCase();
	    if(value.equals("off") || value.equals("false")) override_hosts=false;
    	} catch (Throwable t) {Util.printStackTrace(t);}      

    	try {
	    value = config.getInitParameter("allow_http_tunnel");
	    if(value==null) value="";
	    value = value.trim();
	    value = value.toLowerCase();
	    if(value.equals("on") || value.equals("true")) allowHttpTunnel=true;
    	} catch (Throwable t) {Util.printStackTrace(t);}      

    	super.init(config);
       
	Util.setLogger(new Logger(config.getServletContext()));
        DynamicJavaBridgeClassLoader.initClassLoader(Util.DEFAULT_EXTENSION_DIR);

	Util.TCP_SOCKETNAME = String.valueOf(CGI_CHANNEL);
    }

    public void destroy() {
    	super.destroy();
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
	    	if(id==null) id = ContextFactory.addNew(PhpJavaServlet.this.getServletContext(), req, res).getId();
		this.env.put("X_JAVABRIDGE_CONTEXT", id);
	        
	        /* For the request: http://localhost:8080/JavaBridge/test.php the
	         * req.getPathInfo() returns cgi/test.php. But PHP shouldn't know
	         * about this detail.
	         */
	        this.env.remove("PATH_INFO"); 
	    }
	    return ret;
	        	
	}
	protected String[] findCGI(String pathInfo, String webAppRootDir,
				   String contextPath, String servletPath,
				   String cgiPathPrefix) {
	    String[] retval;
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
    
    
    private ContextFactory getContextManager(HttpServletRequest req, HttpServletResponse res) {
    	ContextFactory ctx = null;
    	String id = req.getHeader("X_JAVABRIDGE_CONTEXT");
    	if(id!=null) ctx = (ContextFactory)ContextFactory.get(id);
    	if(ctx==null) ctx = ContextFactory.addNew(getServletContext(), null, res); // no session sharing
    	if(ctx.getBridge()==null) {
	    JavaBridge bridge;
   	    ctx.setBridge(bridge = new JavaBridge(null, null));
    	    bridge.setClassLoader(new JavaBridgeClassLoader(ctx.getBridge(), DynamicJavaBridgeClassLoader.newInstance(Util.getContextClassLoader())));
    	    bridge.setSessionFactory(ctx);
    	    JavaBridge.load++;
	    ctx.getBridge().logDebug("first request (session is new).");
    	} else {
    	    ctx.getBridge().logDebug("cont. session");
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
    protected void handleRedirectConnection(HttpServletRequest req, HttpServletResponse res) 
	throws ServletException, IOException {
	InputStream in; ByteArrayOutputStream out; OutputStream resOut;
	ContextFactory ctx = getContextManager(req, res);
	JavaBridge bridge = ctx.getBridge();
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
	    if(r.init(in, out)) {
		r.handleOneRequest();
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
	ContextFactory ctx = getContextManager(req, res);
	JavaBridge bridge = ctx.getBridge();
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
	    if(r.init(in, out)) {
		r.handleRequests();
	    }
	} catch (Throwable e) {
	    Util.printStackTrace(e);
	}
	res.setContentLength(out.size());
	out.writeTo(res.getOutputStream());
	in.close();
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
	ContextFactory ctx = getContextManager(req, res);
	JavaBridge bridge = ctx.getBridge();
	if(session) ctx.setSession(req);

	bridge.in = sin=req.getInputStream();
	bridge.out = sout = new ByteArrayOutputStream();
	Request r = bridge.request = new Request(bridge);
	ContextServer ctxServer = (ContextServer)contextServer;
	
	try {
	    if(r.init(sin, sout)) {
	        ContextServer.ChannelName channelName = ctxServer.getFallbackChannelName(req.getHeader("X_JAVABRIDGE_CHANNEL"));
	        res.setHeader("X_JAVABRIDGE_REDIRECT", channelName.getName());
	    	r.handleOneRequest();

		// redirect and re-open
	    	ctxServer.schedule();
		res.setContentLength(sout.size());
		resOut = res.getOutputStream();
		sout.writeTo(resOut);
		if(bridge.logLevel>3) bridge.logDebug("re-directing to port# "+ channelName);
	    	sin.close();
	    	resOut.flush();
	    	ctxServer.start(channelName);
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
	}
    }

    private boolean isLocal(HttpServletRequest req) {
        return req.getRemoteAddr().startsWith("127.0.0.1");
    }
    /**
     * Dispatcher for the "http tunnel", "local channel" or "override redirect".
     */
    protected void doPut (HttpServletRequest req, HttpServletResponse res)
	throws ServletException, IOException {
    	short redirect =(short) req.getIntHeader("X_JAVABRIDGE_REDIRECT");
    	ContextServer socketRunner = (ContextServer)contextServer;
    	boolean local = isLocal(req);
    	if(!local && !allowHttpTunnel) throw new SecurityException("Non-local clients not allowed per default. Set allow_http_tunnel in your web.xml.");
    	
	try {
	    if(redirect==1) 
		handleRedirectConnection(req, res); /* override re-direct */
	    else if(local && socketRunner.isAvailable()) 
		handleSocketConnection(req, res, redirect==2); /* re-direct */
	    else
		handleHttpConnection(req, res, redirect==2); /* standard http tunnel */

	} catch (Throwable t) {
	    Util.printStackTrace(t);
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
    	    try {res.reset();} catch (Exception ex) {/*ignore*/}
	    ServletException ex = new ServletException("An IO exception occured. Probably php was not installed as \"/usr/bin/php-cgi\" or \"c:/php5/php-cgi.exe\".\nPlease copy your PHP binary (\""+php+"\", see JavaBridge/WEB-INF/web.xml) into the JavaBridge/WEB-INF/cgi directory.\nSee webapps/JavaBridge/WEB-INF/cgi/README for details.", e);
	    php=null;
	    checkCgiBinary(getServletConfig());
	    throw ex;
    	} catch (SecurityException sec) {
    	    try {res.reset();} catch (Exception ex) {/*ignore*/}
	    ServletException ex = new ServletException("A security exception occured, could not run PHP. Please start Apache or IIS or start a standalone PHP server with the command: X_JAVABRIDGE_OVERRIDE_HOSTS=\"/\" PHP_FCGI_CHILDREN=\"20\" PHP_FCGI_MAX_REQUESTS=\"500\" /usr/bin/php-cgi -dlog_errors=On -ddisplay_errors=Off -b 127.0.0.1:9667", sec);
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
