package php.java.servlet;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.Socket;
import java.net.SocketException;
import java.util.Iterator;
import java.util.Map;

import javax.servlet.ServletConfig;
import javax.servlet.ServletContext;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import php.java.bridge.NotImplementedException;
import php.java.bridge.Util;
import php.java.servlet.ConnectionPool.ConnectionException;

/**
 * A CGI Servlet which connects to a FastCGI server. If allowed by the
 * administrator and if a fast cgi binary is installed in the JavaBridge web application or
 * DEFAULT_CGI_LOCATIONS, the bridge can automatically start one FCGI
 * server on the computer. Default is Autostart.  
 * The admin may start a FCGI
 * server for all users with the command:<br><code> cd /tmp<br>
 * X_JAVABRIDGE_OVERRIDE_HOSTS="/" PHP_FCGI_CHILDREN="20"
 * PHP_FCGI_MAX_REQUESTS="5000" /usr/bin/php-cgi -b 127.0.0.1:9667<br>
 * </code>
 * 
 * @see php.java.bridge.Util#DEFAULT_CGI_LOCATIONS
 * @author jostb
 */
public class FastCGIServlet extends CGIServlet {
    /**
     * 
     */
    private static final long serialVersionUID = 3545800996174312757L;

    // IO buffer size
    private static final int FCGI_BUF_SIZE = 65535;

    private static final int FCGI_HEADER_LEN = 8;
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
    public static final int FCGI_CHANNEL = 9667;
    /**
     * This controls how many child processes the PHP process spawns.
     */
    private static final String PHP_FCGI_CHILDREN = "20";
    
    /**
     * This controls how many requests each child process will handle before
     * exitting. When one process exits, another will be created. 
     */
    private static final String PHP_FCGI_MAX_REQUESTS = "5000";
    
    protected String php = null; 
    protected boolean phpTryOtherLocations = false;
    
    /** default: true, switched off, if fcgi is not configured */
    protected boolean fcgiIsConfigured;
    /** default: true, switched off, if fcgi is not configured or if fcgi server failed */
    private boolean fcgiIsAvailable;

    protected boolean canStartFCGI = false;
    protected boolean override_hosts = true;

    protected String php_fcgi_children = PHP_FCGI_CHILDREN;
    protected int php_fcgi_children_number = Integer.parseInt(PHP_FCGI_CHILDREN);
    protected String php_fcgi_max_requests = PHP_FCGI_MAX_REQUESTS;

    private final Object lockObject = new Object();
    private ConnectionPool fcgiConnectionPool = null;
    private final ConnectionPool.Factory defaultPoolFactory = new ConnectionPool.Factory() {
      public Socket connect(String host, int port) throws ConnectionException, SocketException { 
	  Socket s = super.connect(host, port);
	  s.setTcpNoDelay(true);
	  return s; 
      }
      public InputStream getInputStream() { return new FastCGIInputStream(); }
      public OutputStream getOutputStream() { return new FastCGIOutputStream(); }
    };
    private final CGIRunnerFactory defaultCGIRunnerFactory = new CGIRunnerFactory();

    protected String startFcgiMessage() {
	String base = context.getRealPath(cgiPathPrefix);
	StringBuffer buf = new StringBuffer(".");
	buf.append(File.separator);
	buf.append("php-cgi-");
	buf.append(Util.osArch);
	buf.append("-");
	buf.append(Util.osName);
	String wrapper = buf.toString();
	String msg =
"Please start Apache or IIS or start a standalone PHP server.\n"+
"For example with the commands: \n\n" +
"cd " + base + "\n" + 
"chmod +x " + wrapper + "\n" + 
"X_JAVABRIDGE_OVERRIDE_HOSTS=\"/\" PHP_FCGI_CHILDREN=\"20\" PHP_FCGI_MAX_REQUESTS=\"5000\" "+wrapper+" -c "+wrapper+".ini -b 127.0.0.1:9667\n\n";
        return msg;
    }
    
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
		      value = config.getServletContext().getRealPath(cgiPathPrefix)+File.separator+value;
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
     * If the JavaBridge context exists and the JavaBridge context can start
     * a FastCGI server and the current context is configured to connect to a FastCGI 
     * server, the current context connects to the JavaBridge context to start the server
     * and then uses this server for all subsequent requests, until the server is 
     * stopped. When FastCGI is not available (anymore), the parent CGI servlet is 
     * used instead.
     * @see php.java.servlet.ConnectionPool
     * @see #destroy()
     */
    public void init(ServletConfig config) throws ServletException {
	String value;
	super.init(config);
    	try {
	    value = config.getInitParameter("override_hosts");
	    if(value==null) value="";
	    value = value.trim();
	    value = value.toLowerCase();
	    if(value.equals("off") || value.equals("false")) override_hosts=false;
	} catch (Throwable t) {Util.printStackTrace(t);}      
	String val = null;
	try {
	    val = getServletConfig().getInitParameter("PHP_FCGI_CHILDREN");
	    if(val==null) val = System.getProperty("php.java.bridge.php_fcgi_children");
	    if(val!=null) php_fcgi_children_number = Integer.parseInt(val);
	} catch (Throwable t) {/*ignore*/}
	if(val!=null) php_fcgi_children = val;
	
	val = null;
	try {
	    val = getServletConfig().getInitParameter("PHP_FCGI_MAX_REQUESTS");
	    if(val==null) val = System.getProperty("php.java.bridge.php_fcgi_max_requests");	    
	} catch (Throwable t) {/*ignore*/}
	if(val!=null) php_fcgi_max_requests = val;
	fcgiIsConfigured = fcgiIsAvailable = true;
	checkCgiBinary(config);
    }
    /**
     * Destroys the FastCGI connection pool, if it exists.
     */
    public void destroy() {
    	if(fcgiConnectionPool!=null) fcgiConnectionPool.destroy();
        super.destroy();
    }

    protected StringBuffer getCgiDir() {
       	String webAppRootDir = getServletContext().getRealPath("/");
        StringBuffer cgiDir = new StringBuffer(webAppRootDir);
        if(!webAppRootDir.endsWith(File.separator)) cgiDir.append(File.separatorChar);
        cgiDir.append(cgiPathPrefix);
        return cgiDir;
    }

    protected class CGIEnvironment extends CGIServlet.CGIEnvironment {
	ConnectionPool connectionPool;

	protected CGIEnvironment(HttpServletRequest req, HttpServletResponse res, ServletContext context) {
	    super(req, res, context);
	}
	protected static final String empty_string = "";
	protected String[] findCGI(String pathInfo, String webAppRootDir,
				   String contextPath, String servletPath,
				   String cgiPathPrefix) {

	    if(!fcgiIsAvailable) return null;
	    synchronized(lockObject) {
		if(null == (connectionPool=fcgiConnectionPool))
		    try {
			fcgiConnectionPool=connectionPool = new ConnectionPool("127.0.0.1", FCGI_CHANNEL, php_fcgi_children_number, defaultPoolFactory);
		    } catch (Exception e) {
			Util.logDebug(e+": FastCGI channel not available, switching off fast cgi. " + startFcgiMessage());
			
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

    private final class FastCGIOutputStream extends ConnectionPool.DefaultOutputStream {
	private void write(int type, byte buf[]) throws IOException {
	    write(type, buf, buf.length);
	}
	public void write(byte buf[], int buflength) throws IOException {
	    write(FCGI_STDIN, buf, buflength);
	}
	public void write(int type, byte buf[], int buflength) throws IOException {
	    int requestId = 1;
	    byte[] header = new byte[] {
		1, (byte)type, 
        	(byte)((requestId >> 8) & 0xff), (byte)((requestId) & 0xff),
        	(byte)((FCGI_BUF_SIZE >> 8) & 0xff), (byte)((FCGI_BUF_SIZE) & 0xff),
		0, //padding
		0};
	    int contentLength = buflength;
	    int pos=0;
	    while(pos + FCGI_BUF_SIZE <= contentLength) {
        	write(header);
        	write(buf, pos, FCGI_BUF_SIZE);
        	pos += FCGI_BUF_SIZE;
	    }
	    contentLength = buflength % FCGI_BUF_SIZE;
	    header[4] = (byte)((contentLength >> 8) & 0xff);
	    header[5] = (byte)((contentLength) & 0xff);
	    write(header);
	    write(buf, pos, contentLength);
	}

	public void writeBegin() throws IOException {
	    int role = FCGI_RESPONDER;
	    byte[] body = new byte[] {
		(byte)((role >> 8) & 0xff), (byte)((role) & 0xff),
		FCGI_KEEP_CONN,
		0,0,0,0,0};
	        
	    write(FCGI_BEGIN_REQUEST, body);
	}
	public void writeLength(ByteArrayOutputStream out, int keyLen) throws IOException {
	    if (keyLen < 0x80) {
		out.write((byte)keyLen);
	    }else {
		byte[] b = new byte[] {
		    (byte)(((keyLen >> 24) | 0x80) & 0xff),
		    (byte)((keyLen >> 16) & 0xff),
		    (byte)((keyLen >> 8) & 0xff),
		    (byte)keyLen};
		out.write(b);
	    }
	}
	public void writeParams(Map props) throws IOException {
	    ByteArrayOutputStream out = new ByteArrayOutputStream();
	    for(Iterator ii = props.keySet().iterator(); ii.hasNext();) {
		Object k = ii.next();
		Object v = props.get(k);
		String key = String.valueOf(k);
		String val = String.valueOf(v);
		int keyLen = key.length();
		int valLen = val.length();
		if(keyLen==0 || valLen==0) continue;
			
		writeLength(out, keyLen);
		writeLength(out, valLen);
		out.write(key.getBytes(Util.ASCII)); 	
		out.write(val.getBytes(Util.ASCII)); 	
	    }
	    write(FCGI_PARAMS, out.toByteArray());
	    write(FCGI_PARAMS, FCGI_EMPTY_RECORD);
	}

	/* (non-Javadoc)
	 * @see java.io.OutputStream#write(int)
	 */
	public void write(int b) {
	    throw new NotImplementedException();
	}

    }
    private final class FastCGIInputStream extends ConnectionPool.DefaultInputStream {
	private String error;
	public String getError() {
	    return error;
	}
	public int read(byte buf[]) throws ConnectionPool.ConnectionException {
	    try {
	      return doRead(buf);
	    } catch (IOException e) {
	        throw new ConnectionPool.ConnectionException(e);
	    }
	}
        public int doRead(byte buf[]) throws IOException {
	    if(buf.length!=FCGI_BUF_SIZE) throw new IOException("Invalid block size");
	    byte header[] = new byte[FCGI_HEADER_LEN];
	    if(FCGI_HEADER_LEN!=read(header, 0, header.length)) throw new IOException ("Protocol error");
	    int type = header[1] & 0xFF;
	    int contentLength = ((header[4] & 0xFF) << 8) | (header[5] & 0xFF);
	    int paddingLength = header[6] & 0xFF;
	    switch(type) {
	    case FCGI_STDERR: 
	    case FCGI_STDOUT: {
		int n = read(buf, 0, contentLength);
		if(n!=contentLength) throw new IOException("Protocol error while reading FCGI data");
		if(type==FCGI_STDERR) { 
		    String s = new String(buf, 0, n, Util.ASCII);
		    log(s); 
		    contentLength = 0;
		    if(error==null)
		      error = Util.checkError(s);
		}
		if(paddingLength>0) {
		    byte b[] = new byte[paddingLength];
		    n = read(b, 0, b.length);
		    if(n!=paddingLength) throw new IOException("Protocol error while reading FCGI padding");
		}
		return contentLength;
	    }
	    case FCGI_END_REQUEST: {
		int n = read(buf, 0, contentLength);
		if(n!=contentLength) throw new IOException("Protocol error while reading EOF data");
		if(paddingLength>0) {
		    n = super.read(buf, 0, paddingLength);		
		    if(n!=paddingLength) throw new IOException("Protocol error while reading EOF padding");
		}
		return -1;
	    }
	    }
	    throw new IOException("Received unknown type");
	}
    }	
    protected class CGIRunnerFactory extends CGIServlet.CGIRunnerFactory {
        protected CGIServlet.CGIRunner createCGIRunner(CGIServlet.CGIEnvironment cgiEnv) {
            return new CGIRunner(cgiEnv);
	}
    }

    protected class CGIRunner extends CGIServlet.CGIRunner {

	ConnectionPool connectionPool;
	
	/**
	 * @param command
	 * @param env
	 * @param wd
	 */
	protected CGIRunner(CGIServlet.CGIEnvironment env) {
	    super(env);
	    connectionPool = ((CGIEnvironment)env).connectionPool;
	}

	protected void doRun() throws IOException, ServletException {
	    try {
	        parseBody();
	    } catch (ConnectionPool.ConnectionException ex) {
	        Util.logDebug("PHP application terminated (see PHP_FCGI_MAX_REQUESTS), trying again using a new application: " + ex);
	        parseBody();
	    }
	}
	protected void run() throws IOException, ServletException {
	    try {
	        doRun();
	    } catch (ConnectionPool.ConnectionException ex) {
	        fcgiIsAvailable = false;
	        Util.logDebug("PHP FastCGI server failed, switching off FastCGI SAPI: " + ex);
	        throw new ServletException("PHP FastCGI server failed, switching off FastCGI SAPI: " +ex);
	    } catch (IOException e) {
	      if(Util.logLevel>4) {
		  Util.logDebug("IOException caused by internet browser: " + e);
		  Util.printStackTrace(e);
	      }	      
	      throw e;
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
		// NOTE: unlike cgi, fcgi headers must be semt in _one_ packet
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
			if(out!=null) out.write(buf, i, N-i);
			i=0;
		    }
		}
		natIn.close(); String phpFatalError = natIn.getError(); 
		natIn = null; connection = null;
		if(phpFatalError!=null) throw new RuntimeException(phpFatalError);
	    } catch (InterruptedException e) {
	      throw new ServletException(e);
	    } finally {
	        // Destroy physical connection, if exception occured, so that the PHP side doesn't keep unsent data
	        // A more elegant approach would be to use the FCGI ABORT request.
		if(connection!=null) connection.setIsClosed(); 
	        if(in!=null) try {in.close();} catch (IOException e) {}
		if(natIn!=null) try {natIn.close();} catch (IOException e) {}
		if(natOut!=null) try {natOut.close();} catch (IOException e) {}
	    }
	}
    } //class CGIRunner
}
