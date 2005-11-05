package php.java.servlet;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.Socket;
import java.util.Hashtable;
import java.util.Iterator;

import javax.servlet.ServletConfig;
import javax.servlet.ServletContext;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import php.java.bridge.NotImplementedException;
import php.java.bridge.Util;


/**
 * @author jostb
 */
public class FastCGIServlet extends CGIServlet {
    // IO buffer size
    static final int BUF_SIZE = 65535;

    public static final int FCGI_HEADER_LEN = 8;
    /*
     * Values for type component of FCGI_Header
     */
    public static final int FCGI_BEGIN_REQUEST =      1;
    public static final int FCGI_ABORT_REQUEST =      2;
    public static final int FCGI_END_REQUEST   =      3;
    public static final int FCGI_PARAMS        =      4;
    public static final int FCGI_STDIN         =      5;
    public static final int FCGI_STDOUT        =      6;
    public static final int FCGI_STDERR        =      7;
    public static final int FCGI_DATA          =      8;
    public static final int FCGI_GET_VALUES    =      9;
    public static final int FCGI_GET_VALUES_RESULT = 10;
    public static final int FCGI_UNKNOWN_TYPE      = 11;
    public static final byte[] FCGI_EMPTY_RECORD = new byte[0];

    /*
     * Mask for flags component of FCGI_BeginRequestBody
     */
    public static final int FCGI_KEEP_CONN  = 1;

    /*
     * Values for role component of FCGI_BeginRequestBody
     */
    public static final int FCGI_RESPONDER  = 1;
    public static final int FCGI_AUTHORIZER = 2;
    public static final int FCGI_FILTER     = 3;

    // fast cgi channel
    static int FCGI_CHANNEL = 9667;

    static File unixLocation=null, unixLocation2=null, windowsLocation=null;
    static protected String php = "php-cgi"; 
    static protected File phpFile = new File(php);
    static private Process proc = null;
    
    public void init(ServletConfig config) throws ServletException {
	super.init(config);

	unixLocation = new File("/usr/bin/php-cgi");
	if(!unixLocation.exists()) unixLocation=null;
	unixLocation2 = new File("/usr/bin/php");
	if(!unixLocation2.exists()) unixLocation2=null;
	windowsLocation = new File("c:/php5/php-cgi.exe");
	if(!windowsLocation.exists()) windowsLocation=null;

	String value;
	try {
	    value = getServletConfig().getInitParameter("fastcgi_socket");
	    if(value!=null && value.trim().length()!=0) FCGI_CHANNEL = Integer.parseInt(value);
	} catch (Throwable t) {Util.printStackTrace(t);}      
	try {
	    value = getServletConfig().getInitParameter("php_exec");
	    if(value!=null && value.trim().length()!=0) {
		php=value;
		phpFile=new File(php);
		setCGIBinary();
		Runtime rt = Runtime.getRuntime();
		if(FCGI_CHANNEL>0) {
		    (new Thread("JavaBridgeFastCGIRunner") {
			    public void run() {
				int c;
				byte buf[] = new byte[CGIServlet.BUF_SIZE];
				try {
				    proc = Runtime.getRuntime().exec(php + " -b:" +String.valueOf(FCGI_CHANNEL), hashToStringArray(defaultEnv), new File(getCgiDir().toString()));
				    proc.getOutputStream().close();
				    proc.getInputStream().close();
				    InputStream in = proc.getErrorStream();
				    while((c=in.read(buf))!=-1) 
					Util.logError("JavaBridgeFastCGIRunner: " +new String(buf, 0, c));
	    	    				
				} catch (IOException e) {
				    Util.printStackTrace(e);
				}
			    }
			}).start();
		    try {
			Thread.sleep(100);
		    } catch (InterruptedException e) {/*ignore*/}

		}
	    }
	} catch (Throwable t) {Util.printStackTrace(t);}

    }
    public void destroy() {
    	super.destroy();
    	if(proc!=null) { 
	    proc.destroy();
	    proc = null;
    	}
    }

    protected StringBuffer getCgiDir() {
       	String webAppRootDir = getServletContext().getRealPath("/");
        StringBuffer cgiDir = new StringBuffer(webAppRootDir);
        if(!webAppRootDir.endsWith(File.separator)) cgiDir.append(File.separatorChar);
        cgiDir.append(getServletConfig().getInitParameter("cgiPathPrefix"));
        return cgiDir;
    }
    protected  void setCGIBinary() {
    	if(phpFile.isAbsolute()) return;
    	File currentLocation=null;
    	String cgi_bin = null;
    	try {
    				
    	    if((currentLocation=new File(getCgiDir().toString(), php)).isFile()||
    	       (currentLocation=new File(Util.EXTENSION_DIR+"/../../../../bin",php)).isFile() ||
    	       ((currentLocation=unixLocation)!=null)||
    	       ((currentLocation=unixLocation2)!=null)||
    	       (currentLocation=windowsLocation)!=null) 
    		cgi_bin = currentLocation.getCanonicalPath();
    	} catch (IOException e) {
    	    if(currentLocation!=null)
    		cgi_bin=currentLocation.getAbsolutePath();
    	}
    	if(cgi_bin!=null) phpFile = new File(php=cgi_bin);
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
			
	    return super.setCGIEnvironment(req, res);
	}
	protected String[] findCGI(String pathInfo, String webAppRootDir,
				   String contextPath, String servletPath,
				   String cgiPathPrefix) {

	    try {
		fastCGISocket = new Socket(InetAddress.getByName("127.0.0.1"), FCGI_CHANNEL);
		cgiRunnerFactory = new CGIRunnerFactory();
	    } catch (Exception e) {
		Util.printStackTrace(e);
	    }
	    if(cgiRunnerFactory==null) return null;
			
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
	public void write(byte buf[]) throws IOException {
	    write(FCGI_STDIN, buf);
	}
	public void write(int type, byte buf[]) throws IOException {
	    int requestId = 1;
	    byte[] header = new byte[] {
		1, (byte)type, 
        	(byte)((requestId >> 8) & 0xff), (byte)((requestId) & 0xff),
        	(byte)((BUF_SIZE >> 8) & 0xff), (byte)((BUF_SIZE) & 0xff),
		0, //padding
		0};
	    int contentLength = buf.length;
	    int pos=0;
	    while(pos + BUF_SIZE < contentLength) {
        	natOut.write(header);
        	natOut.write(buf, pos, BUF_SIZE);
        	pos += BUF_SIZE;
	    }
	    contentLength = buf.length % BUF_SIZE;
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
	public void writeParams(Hashtable props) throws IOException {
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
		out.write(key.getBytes(ASCII)); 	
		out.write(val.getBytes(ASCII)); 	
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
	    if(buf.length!=BUF_SIZE) throw new IOException("Invalid block size");
	    byte header[] = new byte[FCGI_HEADER_LEN];
	    if(FCGI_HEADER_LEN!=natIn.read(header)) throw new IOException ("Protocol error");
	    int version = header[0] & 0xFF;
	    int type = header[1] & 0xFF;
	    int id = ((header[2] & 0xFF) << 8) | (header[3] & 0xFF);
	    int contentLength = ((header[4] & 0xFF) << 8) | (header[5] & 0xFF);
	    int paddingLength = header[6] & 0xFF;
	    switch(type) {
	    case FCGI_STDERR: 
	    case FCGI_STDOUT: {
		int n = natIn.read(buf, 0, contentLength);
		if(n!=contentLength) throw new IOException("Protocol error while reading FCGI data");
		if(type==FCGI_STDERR) log(new String(buf));
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
		byte[] buf = new byte[BUF_SIZE];// headers cannot be larger than this value!
		int i=0, n, s=0;
		boolean eoh=false;

		// the post variables
		if(in!=null) {
		    while((n=in.read(buf))!=-1) {
			natOut.write(buf);
		    }
		} else {
		    natOut.write(FCGI_EMPTY_RECORD);
		}
		
		// the header and content
		while((n = natIn.read(buf)) !=-1) {
		    int N = i + n;
		    // header
		    while(!eoh && i<N) {
			switch(buf[i++]) {
			
			case '\n':
			    if(s+2==i && buf[s]=='\r') {
				eoh=true;
			    } else {
				line = new String(buf, s, i-s-2, ASCII);
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
	    } catch(IOException t) {throw t;} catch (Throwable t) { throw new ServletException(t); } finally {
		if(out!=null) try {out.close();} catch (IOException e) {}
		if(in!=null) try {in.close();} catch (IOException e) {}
		if(natIn!=null) try {natIn.close();} catch (IOException e) {}
		if(natOut!=null) try {natOut.close();} catch (IOException e) {}
	    }
	}
    } //class CGIUtil
}
