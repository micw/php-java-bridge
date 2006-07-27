/*-*- mode: Java; tab-width:8 -*-*/

package php.java.bridge;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.PrintStream;
import java.io.UnsupportedEncodingException;
import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Properties;
import java.util.StringTokenizer;
import java.util.Vector;

/**
 * Miscellaneous functions.
 * @author jostb
 *
 */
public final class Util {

    static {
        initGlobals();
    }

    // method to load a CLR assembly
    static Method loadMethod, loadFileMethod;
    static Class CLRAssembly;
    
    private Util() {}
    
    /**
     * A bridge which uses log4j or the default logger.
     *
     */
    public static class Logger implements ILogger {
        protected ChainsawLogger clogger = null;
        protected ILogger logger;
        /**
         * Use chainsaw, if available or a default logger.
         *
         */
        public Logger() {
            logger = new FileLogger(); // log to logStream        
        }
        /**
         * Use chainsaw, if available of the specified logger.
         * @param logger The specified logger.
         */
        public Logger(ILogger logger) {
            try {this.clogger = ChainsawLogger.createChainsawLogger();} catch (Throwable t) {
                this.logger = logger;
            }
        }
        private ILogger getLogger() {
            if(logger==null) return logger=new FileLogger();
            return logger;
        }
	public void printStackTrace(Throwable t) {
	    if (clogger==null) logger.printStackTrace(t);
	    else
		try {
		    clogger.printStackTrace(t);
		} catch (Exception e) {
		    clogger=null;
		    getLogger().printStackTrace(t);
		}
	}

	public void log(int level, String msg) {
	    if(clogger==null) logger.log(level, msg);
	    else
		try {
		    clogger.log(level, msg);
		} catch (Exception e) {
		    clogger=null;
		    getLogger().log(level, msg);
		}
	}

	public void warn(String msg) {
	    if(clogger==null) logger.warn(msg);
	    else
		try {
		    clogger.warn(msg);
		} catch (Exception e) {
		    clogger=null;
		    getLogger().warn(msg);
		}
	}
    }

    /**
     * The default CGI locations: <code>"/usr/bin/php-cgi"</code>, <code>"c:/php/php-cgi.exe</code>
     */
    public static final String DEFAULT_CGI_LOCATIONS[] = new String[] {"/usr/bin/php-cgi", "c:/php/php-cgi.exe"};

    /**
     * The default CGI header parser. The default implementation discards everything.
     */
    public static final HeaderParser DEFAULT_HEADER_PARSER = new HeaderParser();

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
    public static final int BUF_SIZE = 8192;

    /**
     * The default extension directories. If one of the directories 
     * "/usr/share/java/ext", "/usr/java/packages/lib/ext" contains java libraries,
     * the bridge loads these libraries automatically.
     * Useful, if you have non-pure java libraries (=libraries which use the Java Native Interface to load native dll's or shared libraries).
     */
    public static final String DEFAULT_EXT_DIRS[] = { "/usr/share/java/ext", "/usr/java/packages/lib/ext" };
    
    /** 
     * The TCP socket name. Default is 9267.
     * @see System property <code>php.java.bridge.tcp_socketname</code>
     */
    public static String TCP_SOCKETNAME;

    /**
     * The name of the extension, usually "JavaBridge" or "MonoBridge"
     */
    public static String EXTENSION_NAME;

    /**
     * The max. number of threads in the thread pool. Default is 20.
     * @see System property <code>php.java.bridge.threads</code>
     */
    public static String THREAD_POOL_MAX_SIZE;
    
    /**
     * The default log level, java.log_level from php.ini
     * overrides. Default is 3, if started via java -jar
     * JavaBridge.jar or 2, if started as a sub-process of Apache/IIS.
     * @see System property <code>php.java.bridge.default_log_level</code>
     */
    public static int DEFAULT_LOG_LEVEL;

    /**
     * Backlog for TCP and unix domain connections.
      */
    public static final int BACKLOG = 20;

    static final Object[] ZERO_ARG = new Object[0];

    static final Class[] ZERO_PARAM = new Class[0];
    
    /**
     * Set to true, if the Java VM has been started with -Dphp.java.bridge.promiscuous=true;
     */
    public static boolean JAVABRIDGE_PROMISCUOUS;

    /**
     * The default log file. Default is stderr, if started as a
     * sub-process of Apache/IIS or <code>EXTENSION_NAME</code>.log,
     * if started via java -jar JavaBridge.jar.
     * @see System property <code>php.java.bridge.default_log_file</code>
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
    /** Only for internal use */
    public static boolean IS_MONO;

    private static void initGlobals() {
    	try {
    	    JAVABRIDGE_PROMISCUOUS = false;
	    JAVABRIDGE_PROMISCUOUS = System.getProperty("php.java.bridge.promiscuous", "false").toLowerCase().equals("true");	    
	} catch (Exception e) {/*ignore*/}
	try {
    	    IS_MONO=false;
    	    Util.CLRAssembly = Class.forName("cli.System.Reflection.Assembly");
    	    Util.loadFileMethod = Util.CLRAssembly.getMethod("LoadFile", new Class[] {String.class});
    	    Util.loadMethod = Util.CLRAssembly.getMethod("Load", new Class[] {String.class});
    	    IS_MONO=true;
    	} catch (Exception e) {/*ignore*/}
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
	EXTENSION_NAME = getProperty(p, "EXTENSION_DISPLAY_NAME", "JavaBridge");
	try {
	    String s = getProperty(p, "DEFAULT_LOG_LEVEL", "3");
	    DEFAULT_LOG_LEVEL = Integer.parseInt(s);
	    Util.logLevel=Util.DEFAULT_LOG_LEVEL; /* java.log_level in php.ini overrides */
	} catch (NumberFormatException e) {/*ignore*/}
//	try {
//	    String s = getProperty(p, "BACKLOG", "20");
//	    BACKLOG = Integer.parseInt(s);
//	} catch (NumberFormatException e) {/*ignore*/}
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
    static PrintStream logStream;
    
    /**
     * The logger
     */
    private static ILogger logger;
    
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

    /**
     * print a message on a given log level
     * @param level The log level
     * @param msg The message
     */
    public static void println(int level, String msg) {
	getLogger().log(level, msg);
    }
    
    /**
     * Display a warning if logLevel >= 1
     * @param msg The warn message
     */
    public static void warn(String msg) {
	if(logLevel<=0) return;
	getLogger().warn(msg);
    }
    
    /**
     * Display a stack trace if logLevel >= 1
     * @param t The Throwable
     */
    public static void printStackTrace(Throwable t) {
        getLogger().printStackTrace(t);
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
	buf.append(Util.stringValueOf(obj));
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
     * Set the default logger.
     *
     */
    public static synchronized void setDefaultFileLogger() {
        setLogger(new Logger(new FileLogger()));
    }
    /**
     * Set a new logger. Example:<br><br>
     * <code>
     * public class MyServlet extends PhpJavaServlet { <br>
     * &nbsp;&nbsp;public static final String LOG_HOST="192.168.5.99";<br>
     * &nbsp;&nbsp;public void init(ServletConfig config) throws ServletException {<br>
     * &nbsp;&nbsp;&nbsp;&nbsp;super.init(config);<br>
     * &nbsp;&nbsp;&nbsp;&nbsp;Util.setLogger(new php.java.bridge.ChainsawLogger() {public void configure(String host, int port) throws Exception {super.configure(LOG_HOST, port);}}); <br>
     * &nbsp;&nbsp;}<br>
     * }<br>
     * @param logger The logger to set.
     */
    public static synchronized void setLogger(ILogger logger) {
	Util.logger = logger;
    }
    /**
     * @return Returns the logger.
     */
    public static synchronized ILogger getLogger() {
        if(logger == null) setDefaultFileLogger();
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
	    if(JAVABRIDGE_PROMISCUOUS) 
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

    /**
     * Returns s if s starts with "PHP Fatal error:";
     * @param s The error string
     * @return The fatal error or null
     */
    public static String checkError(String s) {
        //TODO: Parse the error code from response header instead of looking for "PHP Fatal error"
        return s.startsWith("PHP Fatal error:") ? s : null;
    }

    /**
     * Starts a CGI process and returns the process handle.
     */
    public static class Process extends java.lang.Process {

        java.lang.Process proc;
	private String[] args;
	private File homeDir;
	private Map env;
	private boolean tryOtherLocations;

	protected String argsToString(String php, String[] args) {
	    StringBuffer buf = new StringBuffer(php);
	    for(int i=1; i<args.length; i++) {
		buf.append(" ");
		buf.append(args[i]);
	    }
	    return buf.toString();
	}
	private static final String PHP_EXEC = System.getProperty("php.java.bridge.php_exec");
	protected void start() throws NullPointerException, IOException {
	    File location;
	    String php = null;
	    Runtime rt = Runtime.getRuntime();
	    if(args==null) args=new String[]{null};
	    php = args[0];
            if(php != null) {
		StringBuffer buf = new StringBuffer(php);
		php = checkCgiBinary(buf);
	    }
            if(PHP_EXEC==null && tryOtherLocations && php==null) {
		for(int i=0; i<DEFAULT_CGI_LOCATIONS.length; i++) {
		    location = new File(DEFAULT_CGI_LOCATIONS[i]);
		    if(location.exists()) {php = location.getAbsolutePath(); break;}
		}
            }
            if(php==null && tryOtherLocations) php=PHP_EXEC;
            if(php==null) php=args[0];
            if(php==null) php="php-cgi";
            if(Util.logLevel>3) Util.logDebug("Using php binary: " + php);

            if(homeDir!=null &&!homeDir.exists()) homeDir = null;
	    String s = argsToString(php, args);
	    proc = rt.exec(s, hashToStringArray(env), homeDir);
	    if(Util.logLevel>3) Util.logDebug("Started "+ s);
        }
	protected Process(String[] args, File homeDir, Map env, boolean tryOtherLocations) {
	    this.args = args;
	    this.homeDir = homeDir;
	    this.env = env;
	    this.tryOtherLocations = tryOtherLocations;
	}
        /**
	 * Starts a CGI process and returns the process handle.
	 * @param args The args array, e.g.: new String[]{null, "-b", ...};. If args is null or if args[0] is null, the function looks for the system property "php.java.bridge.php_exec".
	 * @param homeDir The home directory. If null, the current working directory is used.
	 * @param env The CGI environment. If null, Util.DEFAULT_CGI_ENVIRONMENT is used.
	 * @return The process handle.
         * @throws IOException 
         * @throws NullPointerException 
	 * @throws IOException
	 * @see Util#checkCgiBinary(StringBuffer)
	 */	  
        public static Process start(String[] args, File homeDir, Map env, boolean tryOtherLocations) throws IOException {
            Process proc = new Process(args, homeDir, env, tryOtherLocations);
            proc.start();
            return proc;
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

	protected ProcessWithErrorHandler(String[] args, File homeDir, Map env, boolean tryOtherLocations) throws IOException {
	    super(args, homeDir, env, tryOtherLocations);
	}
	protected void start() throws IOException {
	    super.start();
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
         * @param homeDir The home directory. If null, the current working directory is used.
         * @param env The CGI environment. If null, Util.DEFAULT_CGI_ENVIRONMENT is used.
         * @return The process handle.
         * @throws IOException
         * @see Util#checkCgiBinary(StringBuffer)
         */
        public static Process start(String[] args, File homeDir, Map env, boolean tryOtherLocations) throws IOException {
            Process proc = new ProcessWithErrorHandler(args, homeDir, env, tryOtherLocations);
            proc.start();
            return proc;
        }
    }

    /**
     * @return The thread context class loader.
     */
    public static ClassLoader getContextClassLoader() {
        return JavaBridgeClassLoader.DEFAULT_CLASS_LOADER;
    }
    /** Redirect System.out and System.err to the configured logFile or System.err.
     * System.out is always redirected, either to the logFile or to System.err.
     * This is because System.out is reserved to report the status back to the 
     * container (IIS, Apache, ...) running the JavaBridge back-end.
     * @param redirectOutput this flag is set, if natcJavaBridge has already redirected stdin, stdout, stderr
     * @param logFile the log file
     */
    static void redirectOutput(boolean redirectOutput, String logFile) {
	if(IS_MONO)
	    redirectMonoOutput(redirectOutput, logFile); 
	else
	    redirectJavaOutput(redirectOutput, logFile);
    }
    static void redirectJavaOutput(boolean redirectOutput, String logFile) {
        Util.logStream = System.err;
	if(!redirectOutput) {
	    if(logFile != null && logFile.length()>0) 
		try {
		    Util.logStream=new java.io.PrintStream(new java.io.FileOutputStream(logFile));
		} catch (Exception e) {e.printStackTrace();}
	    try { System.setErr(logStream); } catch (Exception e) {e.printStackTrace(); }
	}
	try { System.setOut(logStream); } catch (Exception e) {e.printStackTrace(); System.exit(9); }
    }
    /** Wrapper for the Mono StreamWriter */ 
    private static class MonoOutputStream extends OutputStream {
	Class StreamWriter;
	Object streamWriter;
	private Method Write, Close, Flush;
	public MonoOutputStream(Class StreamWriter, Object streamWriter) throws SecurityException, NoSuchMethodException {
	    this.streamWriter = StreamWriter;
	    this.streamWriter = streamWriter;
	    Write = StreamWriter.getMethod("Write", new Class[]{String.class});
	    Close = StreamWriter.getMethod("Close", ZERO_PARAM);
	    Flush = StreamWriter.getMethod("Flush", ZERO_PARAM);
	}
	public void write(byte b[], int off, int len) throws IOException {
	    try {
		String s = new String(b, off, len);
		Write.invoke(streamWriter, new Object[]{s});
	    } catch (Exception e) {
		IOException ex = new IOException();
		ex.initCause(e);
		throw ex;
	    }
	}
	public void write(int b) throws IOException {
	    throw new NotImplementedException();
	}
	public void flush() throws IOException {
	    try {
		Flush.invoke(streamWriter, ZERO_ARG);
	    } catch (Exception e) { 
		IOException ex = new IOException();
		ex.initCause(e);
		throw ex;
	    }	    
	}
	public void close() throws IOException {
	    try {
		Close.invoke(streamWriter, ZERO_ARG);
	    } catch (Exception e) { 
		IOException ex = new IOException();
		ex.initCause(e);
		throw ex;
	    }	    
	}
    }
    static void redirectMonoOutput(boolean redirectOutput, String logFile) {
	try {
	    boolean redirected = false;
	    Class Console = Class.forName("cli.System.Console");
	    Class TextWriter = Class.forName("cli.System.IO.TextWriter");
	    Class StreamWriter = Class.forName("cli.System.IO.StreamWriter");
	    Method get_Error = Console.getMethod("get_Error", new Class[]{});
	    Object errorStream = get_Error.invoke(Console, new Object[]{});
	    Method setOut = Console.getMethod("SetOut", new Class[]{TextWriter});
	    if(!redirectOutput && (logFile != null && logFile.length()>0)) {
		// redirect to log file, if possible
		try {
		    Constructor constructor = StreamWriter.getConstructor(new Class[]{String.class});
		    Object streamWriter = constructor.newInstance(new Object[]{logFile});
		    Method set_AutoFlush = StreamWriter.getMethod("set_AutoFlush", new Class[]{Boolean.TYPE});
		    set_AutoFlush.invoke(streamWriter, new Object[]{Boolean.TRUE});
		    Method setErr = Console.getMethod("SetError", new Class[]{TextWriter});
		    Object[] args = new Object[]{streamWriter};
		    setOut.invoke(Console, args);
		    setErr.invoke(Console, args);

		    MonoOutputStream out = new MonoOutputStream(StreamWriter, streamWriter);
		    logStream = new PrintStream(out, true);
		    System.setErr(logStream);
		    System.setOut(logStream);
		    redirected = true;
		} catch (Exception e) {e.printStackTrace();}
	    } 
	    if(!redirected) { 
		// else redirect to mono stderr
		try { 
		    setOut.invoke(Console, new Object[]{errorStream});

		    MonoOutputStream out = new MonoOutputStream(TextWriter, errorStream);
		    logStream = new PrintStream(out, true);
		    System.setErr(logStream);
		    System.setOut(logStream);
		    redirected = true;
		} catch (Exception e) {e.printStackTrace(); }
	    }
	    if(!redirected) {
		// redirect to mono failed, at least do not print anything to stdout, because that's connected
		// with a pipe
		try {System.setOut(System.err); } catch (Exception e) {e.printStackTrace(); System.exit(9); }
	    }
	} catch (Exception ex) {
	    ex.printStackTrace(); 
	}
    }

    private static final Class[] STRING_PARAM = new Class[]{String.class};
    /**
     * A map containing common environment values for JDK <= 1.4:
     * "PATH", "LD_LIBRARY_PATH", "LD_ASSUME_KERNEL", "USER", "TMP", "TEMP", "HOME", "HOMEPATH", "LANG", "TZ", "OS"
     * They can be set with e.g.: <code>java -DPATH="$PATH" -DHOME="$HOME" -jar JavaBridge.jar</code> or
     * <code>java -DPATH="%PATH%" -jar JavaBridge.jar</code>. 
     */
    public static final Map COMMON_ENVIRONMENT = getCommonEnvironment();
    private static HashMap getCommonEnvironment() {
	String entries[] = {
	    "PATH", "LD_LIBRARY_PATH", "LD_ASSUME_KERNEL", "USER", "TMP", "TEMP", "HOME", "HOMEPATH", "LANG", "TZ", "OS"
	};
	HashMap map = new HashMap(entries.length+10);
	String val;
        Method m = null;
        try {m = System.class.getMethod("getenv", STRING_PARAM);} catch (Exception e) {/*ignore*/}
	for(int i=0; i<entries.length; i++) {
	    val = null;
	    if (m!=null) { 
	      try {
	        val = (String) m.invoke(System.class, (Object[])new String[]{entries[i]});
	      } catch (Exception e) {
		 m = null;
	      }
	    }
	    if(val==null) {
	        try { val = System.getProperty(entries[i]); } catch (Exception e) {/*ignore*/}
	    }
	    if(val!=null) map.put(entries[i], val);
	}
	return map;
    }
    /** 
     * This procedure should be used whenever <code>object</code> may be a dynamic proxy: 
     * <code>String.valueOf(object) returns null, if object is a proxy and returns null.</code>
     * 
     * @param object The object or dynamic proxy
     */
    public static String stringValueOf(Object object) {
        String s = String.valueOf(object);
        if(s==null) s = String.valueOf(s);
        return s;
    }
}
