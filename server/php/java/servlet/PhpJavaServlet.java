/*-*- mode: Java; tab-width:8 -*-*/
package php.java.servlet;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;

import javax.servlet.ServletConfig;
import javax.servlet.ServletContext;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;

import php.java.bridge.DynamicJavaBridgeClassLoader;
import php.java.bridge.JavaBridge;
import php.java.bridge.JavaBridgeClassLoader;
import php.java.bridge.Request;
import php.java.bridge.Session;
import php.java.bridge.Util;
import php.java.servlet.CGIServlet.CGIEnvironment;


public class PhpJavaServlet extends CGIServlet {
    public static class Logger extends Util.Logger {
	private ServletContext ctx;
	public Logger(ServletContext ctx) {
	    this.ctx = ctx;
	}
	public void log(String s) { ctx.log(s); }
	public String now() { return ""; }
	public void printStackTrace(Throwable t) {
	    ctx.log(Util.EXTENSION_NAME + " Exception: ", t);
	}
    }

    /*
     * The name of the php executable.
     */
    static protected String php = "php"; 
    static protected File phpFile = new File(php);
    static protected boolean override_hosts = true;

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

                value = getServletConfig().getInitParameter("override_php_ini");
                if(value!=null && value.trim().equalsIgnoreCase("off")) override_hosts=false;
        } catch (Throwable t) {}
	Util.logger=new Logger(config.getServletContext());
        DynamicJavaBridgeClassLoader.initClassLoader(Util.EXTENSION_DIR);
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
		
		
		
		cgi_bin=php;
		String cgiDir=webAppRootDir+  cgiPathPrefix;
		if(!phpFile.isAbsolute()) {
			File currentLocation=null;
				try {
				
					if((currentLocation=new File(cgiDir, php)).isFile()||
					(currentLocation=new File(Util.EXTENSION_DIR+"/../../../../bin",php)).isFile() ||
					(currentLocation=new File("/usr/bin",php)).isFile()||
					(currentLocation=new File("c:/php5",php)).isFile()) 
					cgi_bin=currentLocation.getCanonicalPath();
				} catch (IOException e) {
					// currentLocation cannot be null.
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


    public void doPut (HttpServletRequest req, HttpServletResponse res)
	throws ServletException, IOException {
	InputStream in; ByteArrayOutputStream out;
        try {
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
	} catch (Throwable t) {
	    Util.printStackTrace(t);
	    res.getOutputStream().close();
	    req.getInputStream().close();
	}
    }
}
