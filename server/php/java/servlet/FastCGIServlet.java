package php.java.servlet;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.Socket;
import java.util.Iterator;
import java.util.Map;

import javax.servlet.ServletConfig;
import javax.servlet.ServletContext;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import php.java.bridge.NotImplementedException;
import php.java.bridge.Util;

/**
 * A CGI Servlet which connects to a FastCGI server. If allowed by the
 * administrator and if a fast cgi binary is installed in
 * DEFAULT_CGI_LOCATIONS the bridge can automatically start one FCGI
 * server on the computer. Default is off.  The admin may start a FCGI
 * server for all users with the command:<br><code> cd /tmp<br>
 * X_JAVABRIDGE_OVERRIDE_HOSTS="/" PHP_FCGI_CHILDREN="20"
 * PHP_FCGI_MAX_REQUESTS="500" /usr/bin/php-cgi -b 127.0.0.1:9667<br>
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

    /**
     * This controls how many child processes the PHP process spawns.
     */
    private static final String PHP_FCGI_CHILDREN = "20";
    
    /**
     * This controls how many requests each child process will handle before
exitting. When one process exits, another will be created. 
     */
    private static final String PHP_FCGI_MAX_REQUESTS = "500";
    
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

    protected String php = null; 
    private boolean fcgiIsAvailable;
    private boolean canStartFCGI = false;
    private String php_fcgi_children = PHP_FCGI_CHILDREN;
    private String php_fcgi_max_requests = PHP_FCGI_MAX_REQUESTS;
    
    protected String startFcgiMessage() {
	String base = getServletConfig().getServletContext().getRealPath(cgiPathPrefix);
	StringBuffer buf = new StringBuffer("./");
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
"X_JAVABRIDGE_OVERRIDE_HOSTS=\"/\" PHP_FCGI_CHILDREN=\"20\" PHP_FCGI_MAX_REQUESTS=\"500\" "+wrapper+" -c "+wrapper+".ini -b 127.0.0.1:9667\n\n";
        return msg;
    }
    private boolean fcgiIsAvailable() {
        return fcgiIsAvailable;
    }
    private static final void runFcgi(Map env, String php) {
	    int c;
	    byte buf[] = new byte[CGIServlet.BUF_SIZE];
	    try {
	    Process proc = PhpJavaServlet.startFcgi(env, php);
        if(proc==null) return;
	    proc.getOutputStream().close();
	    proc.getInputStream().close();
	    InputStream in = proc.getErrorStream();
	    while((c=in.read(buf))!=-1) System.err.write(buf, 0, c);
		if(proc!=null) try {proc.destroy(); proc=null;} catch(Throwable t) {/*ignore*/}
	    } catch (Exception e) {Util.logDebug("Could not start FCGI server: " + e);};
    }
    protected void checkCgiBinary(ServletConfig config) {
	String value;
	    try {
		    value = config.getServletContext().getRealPath(cgiPathPrefix)+File.separator+"php-cgi";
		    if(value!=null && value.trim().length()!=0) {
		    	if(Util.checkCgiBinary(new StringBuffer(value.trim())) != null) {
		    	    php = value;
		    	    canStartFCGI = false;
		    	}
		    }
		}  catch (Throwable t) {Util.printStackTrace(t);}    
	if (php==null) {
		try {
		    value = config.getInitParameter("php_exec");
		    if(value!=null && value.trim().length()!=0) {
		        File f = new File(value.trim());
		        if(!f.isAbsolute()) value = config.getServletContext().getRealPath(cgiPathPrefix)+File.separator+value;
		        php = value; 
		    }
		}  catch (Throwable t) {Util.printStackTrace(t);}      
	}      
	try {
	    fcgiIsAvailable = true;
	    value = config.getServletContext().getInitParameter("use_fast_cgi");
	    if(value==null) try { value = System.getProperty("php.java.bridge.use_fast_cgi"); } catch (Exception e) {/*ignore*/}
	    if("false".equalsIgnoreCase(value) || "off".equalsIgnoreCase(value)) fcgiIsAvailable = false;
	    else {
		value = config.getInitParameter("use_fast_cgi");
		if(value==null) value="";
		value=value.trim();
		value = value.toLowerCase();
		boolean autostart = value.startsWith("auto");
		boolean notAvailable = value.equals("false") || value.equals("off");
		if(notAvailable) fcgiIsAvailable = false;
		if(autostart) canStartFCGI = true;
	    }
	}  catch (Throwable t) {Util.printStackTrace(t);}      
    }
    public void init(ServletConfig config) throws ServletException {
	super.init(config);

	checkCgiBinary(config);
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
	
	try {
	    if(canStartFCGI){
		Thread t = (new Thread("JavaBridgeFastCGIRunner") {
			public void run() {
			    Map env = (Map) defaultEnv.clone();
			    env.put("PHP_FCGI_CHILDREN", php_fcgi_children);
			    env.put("PHP_FCGI_MAX_REQUESTS", php_fcgi_max_requests);
			    runFcgi(env, php);
			}
		    });
		t.setDaemon(true);
		t.start();
		    Thread.sleep(500);
	    }
	} catch (Throwable t) {Util.printStackTrace(t);}

    }
    public void destroy() {
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
	Socket fastCGISocket;

	protected CGIEnvironment(HttpServletRequest req, HttpServletResponse res, ServletContext context) {
	    super(req, res, context);
	}

	protected String getCommand() {
	    return "";
	}
	protected boolean setCGIEnvironment(HttpServletRequest req, HttpServletResponse res) {
			
	    boolean success = super.setCGIEnvironment(req, res);
	    if(success) {
	        // Same as X_JAVABRIDGE_OVERRIDE_HOSTS but stupid php cannot read it
	        // because it is shadowed by the dummy X_JAVABRIDGE_OVERRIDE_HOSTS in
	        // the global environment.
		    StringBuffer buf = new StringBuffer("127.0.0.1:");
		    buf.append(this.env.get("SERVER_PORT"));
		    buf.append("/");
		    buf.append(req.getRequestURI());
		    buf.append("javabridge");
		    this.env.put("X_JAVABRIDGE_OVERRIDE_HOSTS_REDIRECT", buf.toString());
	    }
	    return success;
	}
	protected String[] findCGI(String pathInfo, String webAppRootDir,
				   String contextPath, String servletPath,
				   String cgiPathPrefix) {

	    if(!fcgiIsAvailable()) return null;
	    
	    try {
		fastCGISocket = new Socket(InetAddress.getByName("127.0.0.1"), FCGI_CHANNEL);
		cgiRunnerFactory = new CGIRunnerFactory();
	    } catch (Exception e) {
		Util.logDebug(e+": FastCGI channel not available, switching off fast cgi. " + startFcgiMessage());
		
		fcgiIsAvailable = false;
		return null;
	    }
			
	    // FIXME: Wieviel von dem Mist wird noch gebraucht?
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
    private class FastCGIOutputStream extends OutputStream {

	OutputStream natOut;
	/**
	 * @param outputStream
	 */
	public FastCGIOutputStream(OutputStream outputStream) {
	    natOut = outputStream;
	}
	public void close() throws IOException {
	    natOut.close();
	}
	public void flush() throws IOException {
	    natOut.flush();
	}
	public void write(byte buf[]) throws IOException {
	    write(buf, buf.length);
	}
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
        	natOut.write(header);
        	natOut.write(buf, pos, FCGI_BUF_SIZE);
        	pos += FCGI_BUF_SIZE;
	    }
	    contentLength = buflength % FCGI_BUF_SIZE;
	    header[4] = (byte)((contentLength >> 8) & 0xff);
	    header[5] = (byte)((contentLength) & 0xff);
	    natOut.write(header);
	    natOut.write(buf, pos, contentLength);
	}

	public void writeBegin() throws IOException {
	    int role = FCGI_RESPONDER;
	    byte[] body = new byte[] {
		(byte)((role >> 8) & 0xff), (byte)((role) & 0xff),
		0,//FCGI_KEEP_CONN,
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
	public void write(int b) throws IOException {
	    throw new NotImplementedException();
	}

    }
    private class FastCGIInputStream extends InputStream {

	InputStream natIn;
	/**
	 * @param inputStream
	 */
	public FastCGIInputStream(InputStream inputStream) {
	    natIn = inputStream;
	}
	
	public void close() throws IOException {
	    natIn.close();
	}
	public int read(byte buf[]) throws IOException {
	    if(buf.length!=FCGI_BUF_SIZE) throw new IOException("Invalid block size");
	    byte header[] = new byte[FCGI_HEADER_LEN];
	    if(FCGI_HEADER_LEN!=natIn.read(header)) throw new IOException ("Protocol error");
	    int type = header[1] & 0xFF;
	    int contentLength = ((header[4] & 0xFF) << 8) | (header[5] & 0xFF);
	    int paddingLength = header[6] & 0xFF;
	    switch(type) {
	    case FCGI_STDERR: 
	    case FCGI_STDOUT: {
		int n = natIn.read(buf, 0, contentLength);
		if(n!=contentLength) throw new IOException("Protocol error while reading FCGI data");
		if(type==FCGI_STDERR) { 
		    String s = new String(buf, 0, n, Util.ASCII);
		    log(s); 
		    contentLength = 0;
		    String error = Util.checkError(s);
		    if(error!=null) throw new RuntimeException(error);
		} 
		byte b[] = new byte[paddingLength];
		n = natIn.read(b);
		if(n!=paddingLength) throw new IOException("Protocol error while reading FCGI padding");
		return contentLength;
	    }
	    case FCGI_END_REQUEST:
		return -1;
	    }
	    throw new IOException("Received unknown type");
	}
	

    /* (non-Javadoc)
	 * @see java.io.InputStream#read()
	 */
	public int read() throws IOException {
	    throw new NotImplementedException();
	}
    }	
    protected class CGIRunnerFactory extends CGIServlet.CGIRunnerFactory {
        protected CGIServlet.CGIRunner createCGIRunner(CGIServlet.CGIEnvironment cgiEnv) {
            return new CGIRunner(cgiEnv);
	}
    }

    protected class CGIRunner extends CGIServlet.CGIRunner {

	Socket fastCGISocket;
	
	/**
	 * @param command
	 * @param env
	 * @param wd
	 */
	protected CGIRunner(CGIServlet.CGIEnvironment env) {
	    super(env);
	    fastCGISocket = ((CGIEnvironment)env).fastCGISocket;
	}


	/**
	 * Optimized run method for FastCGI. Makes use of the large FCGI_BUF_SIZE and the specialized in.read(). 
	 * It is a modified copy of the parseBody. 
	 * @see Util#parseBody(byte[], InputStream, OutputStream, HeaderParser)
	 */
        protected void run() throws IOException, ServletException {
	    InputStream in = null;
            OutputStream out = null;
	    FastCGIInputStream natIn = null;
	    FastCGIOutputStream natOut = null;
	    try {
		natOut = new FastCGIOutputStream(fastCGISocket.getOutputStream());
		natIn = new FastCGIInputStream(fastCGISocket.getInputStream());

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
		} else {
		    natOut.write(FCGI_EMPTY_RECORD);
		}
		natOut.flush();
		
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
	    }  
	    catch (Exception e) {
	        throw new ServletException(e);
	    }
	    finally {
		if(in!=null) try {in.close();} catch (IOException e) {}
		if(natIn!=null) try {natIn.close();} catch (IOException e) {}
		if(natOut!=null) try {natOut.close();} catch (IOException e) {}
	    }
	}
    } //class CGIRunner
}
