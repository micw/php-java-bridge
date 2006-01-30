/*-*- mode: Java; tab-width:8 -*-*/

package php.java.bridge;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.PrintStream;
import java.io.UnsupportedEncodingException;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.Date;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Properties;
import java.util.Vector;
import java.util.StringTokenizer;

/**
 * Miscellaneous functions.
 * @author jostb
 *
 */
public class Util {

    static {
        initGlobals();
    }

    /**
     * The default CGI locations: <code>"/usr/bin/php-cgi"</code>, <code>"/usr/bin/php"</code>, <code>"c:/php5/php-cgi.exe</code>
     */
    public static final String DEFAULT_CGI_LOCATIONS[] = new String[] {"/usr/bin/php-cgi", "/usr/bin/php", "c:/php5/php-cgi.exe"};

    /**
     * The default CGI header parser. The default implementation discards everything.
     */
    public static final HeaderParser DEFAULT_HEADER_PARSER = new HeaderParser();

    /**
     * A default CGI environment which contains a "SystemRoot" entry. Some versions of windows require it
     * in order to function properly.
     * @author jostb
     *
     */
    public static class CgiEnvironment extends HashMap {

	private static final File winnt = new File("c:/winnt");
	private static final File windows = new File("c:/windows");
	/**
	 * 
	 */
	private static final long serialVersionUID = 4121409609719429173L;

	protected CgiEnvironment() {
	    String val = null;
	    //			 Bug in WINNT and WINXP.
	    //			 If SystemRoot is missing, php cannot access winsock.
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
	    if(val!=null) put("SystemRoot", val);			
	}
    }
    /**
     * The default CGI environment
     */
    public static final HashMap DEFAULT_CGI_ENVIRONMENT = new CgiEnvironment();

    /**
     * ASCII encoding
     */
    public static final String ASCII = "ASCII";

    /**
     * UTF8 encoding
     */
    public static final String UTF8 = "UTF-8";

    /**
     * The default buffer size
     */
    public static final int BUF_SIZE = 8152;

    
    /** 
     * The TCP socket name
     */
    public static String TCP_SOCKETNAME;

    /**
     * The default extension directory.
     * Each php instance can pass its extension dir via a call to <code>setJarLibraryPath</code> or <code>setLibraryPath</code>.
     * @see php.java.bridge.JavaBridge#setJarLibraryPath(String, String)
     * @see php.java.bridge.JavaBridge#setLibraryPath(String, String)
     */
    public static String DEFAULT_EXTENSION_DIR;

    /**
     * The name of the extension, usually "JavaBridge" or "MonoBridge"
     */
    public static String EXTENSION_NAME;

    /**
     * The number of threads in the thread pool.
     * @see System property <code>php.java.bridge.threads</code>
     */
    public static String THREAD_POOL_MAX_SIZE;
    
    /**
     * The default log level, java.log_level from php.ini overrides.
     */
    public static int DEFAULT_LOG_LEVEL;

    /**
     * Backlog for TCP and unix domain connections
     */
    public static int BACKLOG;

    /**
     * The default log file.
     */
    public static String DEFAULT_LOG_FILE;
	
    private static String getProperty(Properties p, String key, String defaultValue) {
	String s = null;
	if(p!=null) s = p.getProperty(key);
	if(s==null) s = System.getProperty("php.java.bridge." + String.valueOf(key).toLowerCase());
	if(s==null) s = defaultValue;
	return s;
    }
    /** Only for internal use */
    public static String VERSION;
    /** Only for internal use */
    public static String osArch;
    /** Only for internal use */
    public static String osName;

    private static void initGlobals() {
        Properties p = new Properties();
	try {
	    InputStream in = Util.class.getResourceAsStream("global.properties");
	    p.load(in);
	    VERSION = p.getProperty("BACKEND_VERSION");
	} catch (Throwable t) {
	    //t.printStackTrace();
	};
	try {
	    THREAD_POOL_MAX_SIZE = getProperty(p, "THREADS", "20");
	} catch (Throwable t) {
	    //t.printStackTrace();
	};
	TCP_SOCKETNAME = getProperty(p, "TCP_SOCKETNAME", "9267");
	DEFAULT_EXTENSION_DIR = getProperty(p, "DEFAULT_EXTENSION_DIR", null);
	EXTENSION_NAME = getProperty(p, "EXTENSION_DISPLAY_NAME", "JavaBridge");
	try {
	    String s = getProperty(p, "DEFAULT_LOG_LEVEL", "2");
	    DEFAULT_LOG_LEVEL = Integer.parseInt(s);
	    Util.logLevel=Util.DEFAULT_LOG_LEVEL; /* java.log_level in php.ini overrides */
	} catch (NumberFormatException e) {/*ignore*/}
	try {
	    String s = getProperty(p, "BACKLOG", "20");
	    BACKLOG = Integer.parseInt(s);
	} catch (NumberFormatException e) {/*ignore*/}
	DEFAULT_LOG_FILE = getProperty(p, "DEFAULT_LOG_FILE", Util.EXTENSION_NAME+".log");
	String separator = "/-+.,;: ";
	try {
	    String val = System.getProperty("os.arch").toLowerCase();
	    StringTokenizer t = new StringTokenizer(val, separator);
	    osArch = t.nextToken();
	} catch (Throwable t) {/*ignore*/}
	if(osArch==null) osArch="unknown";
	try {
	    String val = System.getProperty("os.name").toLowerCase();
	    StringTokenizer t = new StringTokenizer(val, separator);
	    osName = t.nextToken();
	} catch (Throwable t) {/*ignore*/}
	if(osName==null) osName="unknown";
    }
 
    /**
     * The logStream, defaults to System.err
     */
    public static PrintStream logStream;
    
    /**
     * A logger class.
     */
    public static class Logger { // hook for servlet
        static boolean haveDateFormat=true;
        private static Object _form;
        private boolean isInit = false;
        private void init() {
	    if(Util.logStream==null) {
	      if(DEFAULT_LOG_FILE.trim().length()==0)  Util.logStream = System.err;
	      else 
		try {
		    Util.logStream=new java.io.PrintStream(new java.io.FileOutputStream(DEFAULT_LOG_FILE));
		} catch (FileNotFoundException e1) {Util.logStream=System.err;}
	    }
	    isInit = true;
        }
        /**
         * Create a String containing the current date/time.
         * @return The date/time as a String
         */
        public String now() {
	    if(!haveDateFormat) return String.valueOf(System.currentTimeMillis());
	    try {
		if(_form==null)
		    _form = new java.text.SimpleDateFormat("MMM dd HH:mm:ss");
		return ((java.text.SimpleDateFormat)_form).format(new Date());
	    } catch (Throwable t) {
		haveDateFormat=false;
		return now();
	    }
	}
        /**
         * Log a message
         * @param s  The message
         */
   	public void log(String s) {
   	    if(!isInit) init();
	    byte[] bytes = null;
	    try {
		bytes = s.getBytes(UTF8);
	    } catch (java.io.UnsupportedEncodingException e) {
		Util.printStackTrace(e);
		bytes = s.getBytes();
	    }
	    logStream.write(bytes, 0, bytes.length);
	    logStream.println("");
	    logStream.flush();
    	}
   	
   	/**
   	 * Log a stack trace
   	 * @param t The Throwable
   	 */
   	public void printStackTrace(Throwable t) {
   	    if(!isInit) init();
   	    t.printStackTrace(logStream);
   	}
    }
    private static Logger logger = new Logger();
    
    /**
     * The loglevel:<br>
     * 0: log off <br>
     * 1: log fatal <br>
     * 2: log messages/exceptions <br>
     * 3: log verbose <br>
     * 4: log debug <br>
     * 5: log method invocations
     */
    public static int logLevel;

    //
    // Logging
    //

    /**
     * print a message on a given log level
     * @param level The log level
     * @param msg The message
     */
    public static void println(int level, String msg) {
	StringBuffer b = new StringBuffer(logger.now());
	b.append(" "); b.append(Util.EXTENSION_NAME); b.append(" ");
	switch(level) {
	case 1: b.append("FATAL"); break;
	case 2: b.append("ERROR"); break;
	case 3: b.append("INFO "); break;
	case 4: b.append("DEBUG"); break;
	default: b.append(level); break;
	}
	b.append(": ");
	b.append(msg);
	logger.log(b.toString());
    }
    
    /**
     * Display a warning if logLevel >= 1
     * @param msg The warn message
     */
    public static void warn(String msg) {
	if(logLevel<=0) return;
    	StringBuffer b = new StringBuffer(logger.now());
	b.append(" "); b.append(Util.EXTENSION_NAME); b.append(" ");
	b.append("WARNING");
	b.append(": ");
	b.append(msg);
	logger.log(b.toString());
    }
    
    /**
     * Display a stack trace if logLevel >= 1
     * @param t The Throwable
     */
    public static void printStackTrace(Throwable t) {
	if(logLevel > 0)
	    if ((t instanceof Error) || logLevel > 1) 
	    	logger.printStackTrace(t);
    }
    
    /**
     * Display a debug message
     * @param msg The message
     */
    public static void logDebug(String msg) {
	if(logLevel>3) println(4, msg);
    }
    
    /**
     * Display a fatal error
     * @param msg The error
     */
    public static void logFatal(String msg) {
	if(logLevel>0) println(1, msg);
    }
    
    /**
     * Display an error or an exception
     * @param msg The error or the exception
     */
    public static void logError(String msg) {
	if(logLevel>1) println(2, msg);
    }
    
    /**
     * Display a message
     * @param msg The message
     */
    public static void logMessage(String msg) {
	if(logLevel>2) println(3, msg);
    }
    
    /**
     * Return the class name
     * @param obj The object
     * @return The class name
     */
    public static String getClassName(Object obj) {
        if(obj==null) return "null";
        Class c = getClass(obj);
        String name = c.getName();
        if(name.startsWith("[")) name = "array_of-"+name.substring(1);
        return name;
    }
    
    /**
     * Return the short class name
     * @param obj The object
     * @return The class name
     */
    public static String getShortClassName(Object obj) {
	String name = getClassName(obj);
	int idx = name.lastIndexOf('.');
	if(idx!=-1) 
	    name = name.substring(idx+1);
	return name;
    }
    
    /**
     * Return the class.
     * @param obj The object
     * @return The class
     */
    public static Class getClass(Object obj) {
	if(obj==null) return null;
	return obj instanceof Class?(Class)obj:obj.getClass();
    }
    
    /**
     * Append an object to a StringBuffer
     * @param obj The object
     * @param buf The StringBuffer
     */
    public static void appendObject(Object obj, StringBuffer buf) {
	if(obj==null) { buf.append("null"); return; }

    	if(obj instanceof Class) {
	    if(((Class)obj).isInterface()) 
		buf.append("i(");
	    else
		buf.append("c(");
    	} else {
	    buf.append("o(");
	}
        buf.append(getShortClassName(obj));
	buf.append("):");
	buf.append("\"");
	buf.append(String.valueOf(obj));
	buf.append("\"");
    }
    /**
     * Append a parameter object to a StringBuffer
     * @param obj The object
     * @param buf The StringBuffer
     */
    public static void appendShortObject(Object obj, StringBuffer buf) {
	if(obj==null) { buf.append("null"); return; }

    	if(obj instanceof Class) {
	    if(((Class)obj).isInterface()) 
		buf.append("i(");
	    else
		buf.append("c(");
    	} else {
	    buf.append("o(");
	}
        buf.append(getShortClassName(obj));
	buf.append(")");
    }
    
    /**
     * Append a function parameter to a StringBuffer
     * @param obj The parameter object
     * @param buf The StringBuffer
     */
    public static void appendParam(Object obj, StringBuffer buf) {
    	buf.append("(");
	buf.append(getShortClassName(obj));
	buf.append(")");
    }
    
    /**
     * Return function arguments and their types as a String
     * @param args The args
     * @param params The associated types
     * @return A new string
     */
    public static String argsToString(Object args[], Class[] params) {
	StringBuffer buffer = new StringBuffer("");
	appendArgs(args, params, buffer);
	return buffer.toString();
    }
    
    /**
     * Append function arguments and their types to a StringBuffer
     * @param args The args
     * @param params The associated types
     * @param buf The StringBuffer
     */
    public static void appendArgs(Object args[], Class[] params, StringBuffer buf) {
	if(args!=null) {
	    for(int i=0; i<args.length; i++) {
		if(params!=null) {
		    appendParam(params[i], buf); 
		}
	    	appendShortObject(args[i], buf);
		
		if(i+1<args.length) buf.append(", ");
	    }
	}
    }
    
    /**
     * Locale-independent getBytes(), uses ASCII encoding
     * @param s The String
     * @return The ASCII encoded bytes
     */
    public static byte[] toBytes(String s) {
	try {
	    return s.getBytes(ASCII);
	} catch (UnsupportedEncodingException e) {
	    e.printStackTrace();
	    return s.getBytes();
	}
    }
    
    /**
     * Create a string array from a hashtable.
     * @param h The hashtable
     * @return The String
     * @throws NullPointerException
     */
    public static String[] hashToStringArray(Map h)
	throws NullPointerException {
	Vector v = new Vector();
	Iterator e = h.keySet().iterator();
	while (e.hasNext()) {
	    String k = e.next().toString();
	    v.add(k + "=" + h.get(k));
	}
	String[] strArr = new String[v.size()];
	v.copyInto(strArr);
	return strArr;
    }

    /**
     * A procedure class which can be used to capture the HTTP header strings.
     * Example:<br>
     * <code>
     * Util.parseBody(buf, natIn, out, new Util.HeaderParser() {protected void parseHeader(String header) {System.out.println(header);}});<br>
     * </code>
     * @author jostb
     * @see Util#parseBody(byte[], InputStream, OutputStream, HeaderParser)
     */
    public static class HeaderParser {protected void parseHeader(String header) {/*template*/}}
    /**
     * Discards all header fields from a HTTP connection and write the body to the OutputStream
     * @param buf A buffer, for example new byte[BUF_SIZE]
     * @param natIn The InputStream
     * @param out The OutputStream
     * @throws UnsupportedEncodingException
     * @throws IOException
     */
    public static void parseBody(byte[] buf, InputStream natIn, OutputStream out, HeaderParser parser) throws UnsupportedEncodingException, IOException {
	int i=0, n, s=0;
	boolean eoh=false;
	// the header and content
	while((n = natIn.read(buf, i, buf.length-i)) !=-1 ) {
	    int N = i + n;
	    // header
	    while(!eoh && i<N) {
		switch(buf[i++]) {
		
		case '\n':
		    if(s+2==i && buf[s]=='\r') {
			eoh=true;
		    } else {
		    	parser.parseHeader(new String(buf, s, i-s-2, ASCII));
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
    /**
     * @param logger The logger to set.
     */
    public static void setLogger(Logger logger) {
	Util.logger = logger;
    }
    /**
     * @return Returns the logger.
     */
    public static Logger getLogger() {
	return logger;
    }

    /**
     * Returns the string "127.0.0.1". If the system property "php.java.bridge.promiscuous" is "true", 
     * the real host address is returned.
     * @return The host address as a string.
     */
    public static String getHostAddress() {
	String addr = "127.0.0.1";
	try {
	    if(System.getProperty("php.java.bridge.promiscuous", "false").toLowerCase().equals("true")) 
		addr = InetAddress.getLocalHost().getHostAddress();
	} catch (UnknownHostException e) {/*ignore*/}
	return addr;
    }
	
    /**
     * Checks if the cgi binary buf-&lt;os.arch&gt;-&lt;os.name&gt;.sh or buf-&lt;os.arch&gt;-&lt;os.name&gt;.exe or buf-&lt;os.arch&gt;-&lt;os.name&gt; exists.
     * @param buf The base name, e.g.: /opt/tomcat/webapps/JavaBridge/WEB-INF/cgi/php-cgi
     * @return The full name or null.
     */
    public static String checkCgiBinary(StringBuffer buf) {
    	File location;
 
    	buf.append("-");
	buf.append(osArch);
	buf.append("-");
	buf.append(osName);

	location = new File(buf.toString() + ".sh");
	if(Util.logLevel>3) Util.logDebug("trying: " + location);
	if(location.exists()) return "/bin/sh " + location.getAbsolutePath();
		
	location = new File(buf.toString() + ".exe");
	if(Util.logLevel>3) Util.logDebug("trying: " + location);
	if(location.exists()) return location.getAbsolutePath();

	location = new File(buf.toString());
	if(Util.logLevel>3) Util.logDebug("trying: " + location);
	if(location.exists()) return location.getAbsolutePath();
	
	return null;
    }

 
    /* used by startProcess only */
    private static String argsToString(String php, String[] args) {
	StringBuffer buf = new StringBuffer(php);
	for(int i=1; i<args.length; i++) {
	    buf.append(" ");
	    buf.append(args[i]);
	}
	return buf.toString();
    }

    /**
     * Returns s if s starts with "PHP Fatal error:";
     * @param s The error string
     * @return The fatal error or null
     */
    public static String checkError(String s) {
        return s.startsWith("PHP Fatal error:") ? s : null;
    }

    /**
	 * Starts a CGI process and returns the process handle.
	 */
    public static class Process extends java.lang.Process {

        java.lang.Process proc;

        protected Process(String[] args, File homeDir, Map env) throws IOException {
	    File location;
	    Runtime rt = Runtime.getRuntime();
	    if(args==null) args=new String[]{null};
	    String php = args[0];
	    if(php == null) php = System.getProperty("php.java.bridge.php_exec");
            if(php==null) {
		for(int i=0; i<DEFAULT_CGI_LOCATIONS.length; i++) {
		    location = new File(DEFAULT_CGI_LOCATIONS[i]);
		    if(location.exists()) {php = location.getAbsolutePath(); break;}
		}
	    } else {
		StringBuffer buf = new StringBuffer(php);
		String val = checkCgiBinary(buf);
		if(val!=null) php = val;
	    }
            if(Util.logLevel>3) Util.logDebug("Using php binary: " + php);
            if(php==null) php="php-cgi";

	    String home = System.getProperty("user.home");
	    if(homeDir==null) homeDir = new File(home);
             
	    String s = argsToString(php, args);
	    proc = rt.exec(s, hashToStringArray(env), homeDir);
	    if(Util.logLevel>3) Util.logDebug("Started "+ s);
        }

        /**
	 * Starts a CGI process and returns the process handle.
	 * @param args The args array, e.g.: new String[]{null, "-b", ...};. If args is null or if args[0] is null, the function looks for the system property "php.java.bridge.php_exec".
	 * @param homeDir The home directory. If null, the system property "user.home" is used.
	 * @param env The CGI environment. If null, Util.DEFAULT_CGI_ENVIRONMENT is used.
	 * @return The process handle.
	 * @throws IOException
	 * @see Util#checkCgiBinary(StringBuffer)
	 */
        public static Process start(String[] args, File homeDir, Map env) throws IOException {
            return new Process(args, homeDir, env);
        }


        /* (non-Javadoc)
         * @see java.lang.Process#getOutputStream()
         */
        public OutputStream getOutputStream() {
            return proc.getOutputStream();
        }

        /* (non-Javadoc)
         * @see java.lang.Process#getInputStream()
         */
        public InputStream getInputStream() {
            return proc.getInputStream();
        }

        /* (non-Javadoc)
         * @see java.lang.Process#getErrorStream()
         */
        public InputStream getErrorStream() {
            return proc.getErrorStream();
        }

        /* (non-Javadoc)
         * @see java.lang.Process#waitFor()
         */
        public int waitFor() throws InterruptedException {
            return proc.waitFor();
        }

        /* (non-Javadoc)
         * @see java.lang.Process#exitValue()
         */
        public int exitValue() {
            return proc.exitValue();
        }

        /* (non-Javadoc)
         * @see java.lang.Process#destroy()
         */
        public void destroy() {
            proc.destroy();
        }
        
    }

    /**
	 * Starts a CGI process with an error handler attached and returns the process handle.
	 */
    public static class ProcessWithErrorHandler extends Process {
	String error = null;
	InputStream in = null;
        protected ProcessWithErrorHandler(String[] args, File homeDir, Map env) throws IOException {
	    super(args, homeDir, env);
	    (new Thread("CGIErrorReader") {public void run() {readErrorStream();}}).start();
	}
	public void destroy() {
	    try {proc.destroy();} catch (Exception e) {}
	    if(error!=null) throw new RuntimeException(error);
	}
	private synchronized void readErrorStream() {
	    byte[] buf = new byte[BUF_SIZE];
	    int c;
	    try { 
		in =  proc.getErrorStream();
		while((c=in.read(buf))!=-1) {
		    String s = new String(buf, 0, c, ASCII); 
		    Util.logError(s);
		    error = Util.checkError(s);
		}
	    } catch (IOException e) {
		e.printStackTrace();
	    }
	    finally {
		if(in!=null) 
		    try { in.close();} catch (IOException e1) {
			e1.printStackTrace();
		    }
		notify();
	    }
	}
	public synchronized int waitFor() throws InterruptedException {
	    if(in==null) wait();
	    return 0;
	}

        /**
         * Starts a CGI process and returns the process handle.
         * @param args The args array, e.g.: new String[]{null, "-b", ...};. If args is null or if args[0] is null, the function looks for the system property "php.java.bridge.php_exec".
         * @param homeDir The home directory. If null, the system property "user.home" is used.
         * @param env The CGI environment. If null, Util.DEFAULT_CGI_ENVIRONMENT is used.
         * @return The process handle.
         * @throws IOException
         * @see Util#checkCgiBinary(StringBuffer)
         */
        public static Process start(String[] args, File homeDir, Map env) throws IOException {
            return new ProcessWithErrorHandler(args, homeDir, env);
	}
    }

    /**
     * @return The thread context class loader.
     */
    public static ClassLoader getContextClassLoader() {
        ClassLoader loader = Thread.currentThread().getContextClassLoader();
        return loader;
    }
}
