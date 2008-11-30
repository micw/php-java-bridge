/*-*- mode: Java; tab-width:8 -*-*/

package php.java.bridge;

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
import java.io.PrintStream;
import java.io.UnsupportedEncodingException;
import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.text.DateFormat;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.Locale;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.StringTokenizer;
import java.util.TimeZone;
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
    
    /** Wait for the second Java statement of a script (in ms). Default is to wait for one minute. 
     * See also system property <code>php.java.bridge.max_wait</code>
     */
    public static int MAX_WAIT;
    
    private Util() {}
    
    /**
     * Only for internal use. Use Util.getLogger() instread.
     * 
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
         * Use chainsaw, if available.
         * @param logger The specified logger.
         */
        public Logger(ILogger logger) {
            try {this.clogger = ChainsawLogger.createChainsawLogger();} catch (Throwable t) {
        	if(Util.logLevel>5) t.printStackTrace();
                this.logger = logger;
            }
        }
        private ILogger getLogger() {
            if(logger==null) return logger=new FileLogger();
            return logger;
        }
        /**{@inheritDoc}*/
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

        /**{@inheritDoc}*/
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

        /**{@inheritDoc}*/
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
     * The default PHP arguments
     */
    public static final String PHP_ARGS[] = new String[] {"-d", "allow_url_include=On", "-d", "display_errors=Off", "-d", "log_errors=On"};
    
    /**
     * The default CGI locations: <code>"/usr/bin/php-cgi"</code>, <code>"c:/Program Files/PHP/php-cgi.exe</code>
     */
    public static final String DEFAULT_CGI_LOCATIONS[] = new String[] {"/usr/bin/php-cgi", "c:/Program Files/PHP/php-cgi.exe"};

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
     * DEFAULT currently UTF-8, will be changed when most OS support and use UTF-16.
     */
    public static final String DEFAULT_ENCODING = "UTF-8";

    /**
     * The default buffer size
     */
    public static final int BUF_SIZE = 8192;

    /**
     * The default extension directories. If one of the directories
     * "/usr/share/java/ext", "/usr/java/packages/lib/ext" contains
     * java libraries, the bridge loads these libraries automatically.
     * Useful if you have non-pure java libraries (=libraries which
     * use the Java Native Interface to load native dll's or shared
     * libraries).
     */
    public static final String DEFAULT_EXT_DIRS[] = { "/usr/share/java/ext", "/usr/java/packages/lib/ext" };
    
    
    /** Set to true if the VM is gcj, false otherwise */
    public static final boolean IS_GNU_JAVA = checkVM();

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

    /** The name of the VM, for example "1.4.2@http://java.sun.com/" or "1.4.2@http://gcc.gnu.org/java/".*/
    public static String VM_NAME;
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

    /** The base directory of the PHP/Java Bridge. Usually /usr/php/modules/ or $HOME  */
    public static String JAVABRIDGE_BASE;
    
    /** The library directory of the PHP/Java Bridge. Usually /usr/php/modules/lib or $HOME/lib */
    public static String JAVABRIDGE_LIB;
    
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
    /** Only for internal use */
    public static String PHP_EXEC;
    /** Only for internal use */
    public static boolean EXT_JAVA_COMPATIBILITY;
    /** Only for internal use */
    public static File HOME_DIR;

    private static void initGlobals() {
	try {
	    MAX_WAIT = Integer.parseInt(System.getProperty("php.java.bridge.max_wait", "15000"));
	} catch (Exception e) {
	    MAX_WAIT = 15000;
	}
	try {
	    HOME_DIR = new File(System.getProperty("user.home"));
	} catch (Exception e) {
	    HOME_DIR = null;
	}
	try {
	    JAVABRIDGE_BASE = System.getProperty("php.java.bridge.base",  System.getProperty("user.home"));
	    JAVABRIDGE_LIB =  JAVABRIDGE_BASE + File.separator +"lib";
	} catch (Exception e) {
	    JAVABRIDGE_BASE=".";
	    JAVABRIDGE_LIB=".";	    
	}
	try {
    	    VM_NAME = "unknown";
	    VM_NAME = System.getProperty("java.version")+"@" + System.getProperty("java.vendor.url");	    
	} catch (Exception e) {/*ignore*/}
    	try {
    	    JAVABRIDGE_PROMISCUOUS = false;
	    JAVABRIDGE_PROMISCUOUS = System.getProperty("php.java.bridge.promiscuous", "false").toLowerCase().equals("true");	    
	} catch (Exception e) {/*ignore*/}

	IS_MONO = Standalone.checkMono();

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
	EXTENSION_NAME = getProperty(p, "EXTENSION_DISPLAY_NAME", "JavaBridge");
	PHP_EXEC = getProperty(p, "PHP_EXEC", null);
	try {
	    String s = getProperty(p, "DEFAULT_LOG_LEVEL", "3");
	    DEFAULT_LOG_LEVEL = Integer.parseInt(s);
	    Util.logLevel=Util.DEFAULT_LOG_LEVEL; /* java.log_level in php.ini overrides */
	} catch (NumberFormatException e) {/*ignore*/}
	DEFAULT_LOG_FILE = getProperty(p, "DEFAULT_LOG_FILE", Util.EXTENSION_NAME+".log");
	EXT_JAVA_COMPATIBILITY = "true".equals(getProperty(p, "EXT_JAVA_COMPATIBILITY", "false"));
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
     * Display a warning if logLevel &gt;= 1
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
        if(name.startsWith("[")) name = "array_of_"+name.substring(1);
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
     * Return the short class name
     * @param clazz The class
     * @return The class name
     */
    public static String getShortName(Class clazz) {
	String name = clazz.getName();
        if(name.startsWith("[")) name = "array_of_"+name.substring(1);
	int idx = name.lastIndexOf('.');
	if(idx!=-1) 
	    name = name.substring(idx+1);
	return name;
    }
    
    /**
     * Return the class or the object, if obj is already a class.
     * @param obj The object
     * @return Either obj or the class of obj.
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
		buf.append("[i:");
	    else
		buf.append("[c:");
    	} else {
	    buf.append("[o:");
	}
        buf.append(getShortClassName(obj));
	buf.append("]:");
	buf.append("\"");
	buf.append(Util.stringValueOf(obj));
	buf.append("\"");
    }
    /**
     * Append a stack trace to buf.
     * @param throwable The throwable object
     * @param trace The trace from PHP
     * @param buf The current buffer.
     */
    public static void appendTrace(Throwable throwable, String trace, StringBuffer buf) {
	    buf.append(" at:\n");
	    StackTraceElement stack[] = throwable.getStackTrace();
	    int top=stack.length;
	    int count = 0;
	    for(int i=0; i<top; i++) {
		buf.append("#-");
		buf.append(top-i);
		buf.append(" ");
		buf.append(stack[i].toString());
		buf.append("\n");
		if (++count==3) break;
	    }
	    buf.append(trace);
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
		buf.append("[i:");
	    else
		buf.append("[c:");
    	} else {
	    buf.append("[o:");
	}
        buf.append(getShortClassName(obj));
	buf.append("]");
    }
    
    /**
     * Append a function parameter to a StringBuffer
     * @param c The parameter 
     * @param buf The StringBuffer
     */
    public static void appendParam(Class c, StringBuffer buf) {
	if(c.isInterface()) 
	    buf.append("(i:");
	else if (c==java.lang.Class.class)
	    buf.append("(c:");
	else
	    buf.append("(o:");
	buf.append(getShortClassName(c));
	buf.append(")");
    }
    
    /**
     * Append a function parameter to a StringBuffer
     * @param obj The parameter object
     * @param buf The StringBuffer
     */
    public static void appendParam(Object obj, StringBuffer buf) {
	if(obj instanceof Class) {
	    Class c = (Class)obj;
	    if(c.isInterface()) 
		buf.append("(i:");
	    else
		buf.append("(c:");
	}
	else
	    buf.append("(o:");
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
    public static class HeaderParser {
      /**
       * @param header The header string to parse
       */
      public void parseHeader(String header) {/*template*/}
    }
    /**
     * Discards all header fields from a HTTP connection and write the body to the OutputStream
     * @param buf A buffer, for example new byte[BUF_SIZE]
     * @param natIn The InputStream
     * @param out The OutputStream
     * @param parser The header parser
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
		if(out!=null && i<N) out.write(buf, i, N-i); 
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
    public static String[] checkCgiBinary(StringBuffer buf) {
    	File location;
 
    	buf.append("-");
	buf.append(osArch);
	buf.append("-");
	buf.append(osName);

	location = new File(buf.toString() + ".sh");
	if(Util.logLevel>3) Util.logDebug("trying: " + location);
	if(location.exists()) return new String[] {"/bin/sh", location.getAbsolutePath()};
		
	location = new File(buf.toString() + ".exe");
	if(Util.logLevel>3) Util.logDebug("trying: " + location);
	if(location.exists()) return new String[] {location.getAbsolutePath()};

	location = new File(buf.toString());
	if(Util.logLevel>3) Util.logDebug("trying: " + location);
	if(location.exists()) return new String[] {location.getAbsolutePath()};
	
	return null;
    }

    /**
     * Returns s if s contains "PHP Fatal error:";
     * @param s The error string
     * @return The fatal error or null
     */
    public static String checkError(String s) {
        // Is there a better way to check for a fatal error?
        return s.indexOf("PHP Fatal error:")>-1 || s.indexOf("PHP Parse error:")>-1 ? s : null;
    }

    /** 
     * Convenience daemon thread class
      */
    public static class Thread extends java.lang.Thread {
	/**Create a new thread */
	public Thread() {
	    super();
	    initThread();
	}
	/**Create a new thread 
	 * @param name */
	public Thread(String name) {
	    super(name);
	    initThread();
	}
	/**Create a new thread 
	 * @param target */
	public Thread(Runnable target) {
	    super(target);
	    initThread();
	}
	/**Create a new thread 
	 * @param group 
	 * @param target */
	public Thread(ThreadGroup group, Runnable target) {
	    super(group, target);
	    initThread();
	}
	/**Create a new thread 
	 * @param group 
	 * @param name */
	public Thread(ThreadGroup group, String name) {
	    super(group, name);
	    initThread();
	}
	/**Create a new thread 
	 * @param target 
	 * @param name */
	public Thread(Runnable target, String name) {
	    super(target, name);
	    initThread();
	}
	/**Create a new thread 
	 * @param group 
	 * @param target 
	 * @param name */
	public Thread(ThreadGroup group, Runnable target, String name) {
	    super(group, target, name);
	    initThread();
	}
	/**Create a new thread 
	 * @param group 
	 * @param target 
	 * @param name 
	 * @param stackSize */
	public Thread(ThreadGroup group, Runnable target, String name, long stackSize) {
	    super(group, target, name, stackSize);
	    initThread();
	}
	private void initThread() {
	    setDaemon(true);
	}
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
	private boolean preferSystemPhp;
	
	protected String[] getArgumentArray(String[] php, String[] args) {
	    LinkedList buf = new LinkedList();
	    buf.addAll(java.util.Arrays.asList(php));
	    for(int i=1; i<args.length; i++) {
		buf.add(args[i]);
	    }
	    return  (String[]) buf.toArray(new String[buf.size()]);
	}
	protected void start() throws NullPointerException, IOException {
	    File location;
	    Runtime rt = Runtime.getRuntime();

	    /*
	     * Extract the php executable from args[0] ...
	     */
	    String[] php = new String[] {null};
	    if(args==null) args=new String[]{null};
	    String phpExec = args[0];
	    String[] cgiBinary = null;
	    if(PHP_EXEC==null) {
	      if(!preferSystemPhp) {
		if(phpExec != null && 
				((cgiBinary=checkCgiBinary(new StringBuffer(phpExec))) != null)) php = cgiBinary;
		/*
		 * ... resolve it ..
		 */            
		if(tryOtherLocations && php[0]==null) {
		    for(int i=0; i<DEFAULT_CGI_LOCATIONS.length; i++) {
			location = new File(DEFAULT_CGI_LOCATIONS[i]);
			if(location.exists()) {php[0] = location.getAbsolutePath(); break;}
		    }
		}
	      } else {
		/*
		 * ... resolve it ..
		 */            
		if(tryOtherLocations && php[0]==null) {
		    for(int i=0; i<DEFAULT_CGI_LOCATIONS.length; i++) {
			location = new File(DEFAULT_CGI_LOCATIONS[i]);
			if(location.exists()) {
			    php[0] = location.getAbsolutePath(); 
			    break;
			}
		    }
		}
		if(phpExec != null && 
				(php[0]==null &&  (cgiBinary=checkCgiBinary(new StringBuffer(phpExec))) != null)) php = cgiBinary;
	      }
	    }
            if(php[0]==null && tryOtherLocations) php[0]=PHP_EXEC;
            if(php[0]==null && phpExec!=null && (new File(phpExec).exists())) php[0]=phpExec;
            if(php[0]==null) php[0]="php-cgi";
            if(Util.logLevel>3) Util.logDebug("Using php binary: " + java.util.Arrays.asList(php));

            /*
             * ... and construct a new argument array for this specific process.
             */
            if(homeDir!=null && cgiBinary ==null)
        	homeDir = HOME_DIR; // system PHP executables are always executed in the user's HOME dir
            
            if(homeDir!=null &&!homeDir.exists()) homeDir = null;
	    String[] s = getArgumentArray(php, args);
	    
	    proc = rt.exec(s, hashToStringArray(env), homeDir);
	    if(Util.logLevel>3) Util.logDebug("Started "+ java.util.Arrays.asList(s));
        }
	protected Process(String[] args, File homeDir, Map env, boolean tryOtherLocations, boolean preferSystemPhp) {
	    this.args = args;
	    this.homeDir = homeDir;
	    this.env = env;
	    this.tryOtherLocations = tryOtherLocations;
	    this.preferSystemPhp = preferSystemPhp;
	}
        /**
	 * Starts a CGI process and returns the process handle.
	 * @param args The args array, e.g.: new String[]{null, "-b", ...};. If args is null or if args[0] is null, the function looks for the system property "php.java.bridge.php_exec".
	 * @param homeDir The home directory. If null, the current working directory is used.
	 * @param env The CGI environment. If null, Util.DEFAULT_CGI_ENVIRONMENT is used.
         * @param tryOtherLocations true if we should check the DEFAULT_CGI_LOCATIONS first
         * @param preferSystemPhp 
         * @param err 
	 * @return The process handle.
         * @throws IOException 
         * @throws NullPointerException 
	 * @throws IOException
	 * @see Util#checkCgiBinary(StringBuffer)
	 */	  
        public static Process start(String[] args, File homeDir, Map env, boolean tryOtherLocations, boolean preferSystemPhp, OutputStream err) throws IOException {
            Process proc = new Process(args, homeDir, env, tryOtherLocations, preferSystemPhp);
            proc.start();
            return proc;
        }

        /** A generic PHP exception */
        public static class PhpException extends Exception {
	    private static final long serialVersionUID = 767047598257671018L;
	    private String errorString;
	    /** 
	     * Create a PHP exception 
	     * @param errorString the PHP error string 
	     */
	    public PhpException(String errorString) {
		super(errorString);
		this.errorString = errorString;
	    }
	    /** 
	     * Return the error string
	     * @return the PHP error string
	     */
	    public String getError() {
		return errorString;
	    }
	};

	/**
	 * Check for a PHP fatal error and throw a PHP exception if necessary.
	 * @throws PhpException
	 */
	public void checkError() throws PhpException {}

	/**{@inheritDoc}*/
        public OutputStream getOutputStream() {
            return proc.getOutputStream();
        }

	/**{@inheritDoc}*/
        public InputStream getInputStream() {
            return proc.getInputStream();
        }

	/**{@inheritDoc}*/
        public InputStream getErrorStream() {
            return proc.getErrorStream();
        }

	/**{@inheritDoc}*/
        public int waitFor() throws InterruptedException {
            return proc.waitFor();
        }

	/**{@inheritDoc}*/
        public int exitValue() {
            return proc.exitValue();
        }

	/**{@inheritDoc}*/
        public void destroy() {
            proc.destroy();
        }
        
    }

    /**
     * Starts a CGI process with an error handler attached and returns the process handle.
     */
    public static class ProcessWithErrorHandler extends Process {
	StringBuffer error = null;
	InputStream in = null;
	OutputStream err = null;

	protected ProcessWithErrorHandler(String[] args, File homeDir, Map env, boolean tryOtherLocations, boolean preferSystemPhp, OutputStream err) throws IOException {
	    super(args, homeDir, env, tryOtherLocations, preferSystemPhp);
	    this.err = err;
	}
	protected void start() throws IOException {
	    super.start();
	    (new Util.Thread("CGIErrorReader") {public void run() {readErrorStream();}}).start();
	}
	/**{@inheritDoc}*/
	public void checkError() throws PhpException {
	    String errorString = error==null?null:Util.checkError(error.toString());
	    if(errorString!=null) throw new PhpException(errorString);
	}
	/**{@inheritDoc}*/
	public void destroy() {
	    proc.destroy();
	}
	private synchronized void readErrorStream() {
	    byte[] buf = new byte[BUF_SIZE];
	    int c;
	    try { 
		in =  proc.getErrorStream();
		while((c=in.read(buf))!=-1) {
			err.write(buf, 0, c);
			String s = new String(buf, 0, c, ASCII); 
			if(Util.logLevel>4) Util.logError(s);
			if(error==null) error = new StringBuffer(s);
			else error.append(s);
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
	/**{@inheritDoc}*/
	public synchronized int waitFor() throws InterruptedException {
	    if(in==null) wait();
	    return super.waitFor();
	}

	/**
         * Starts a CGI process and returns the process handle.
         * @param args The args array, e.g.: new String[]{null, "-b", ...};. If args is null or if args[0] is null, the function looks for the system property "php.java.bridge.php_exec".
         * @param homeDir The home directory. If null, the current working directory is used.
         * @param env The CGI environment. If null, Util.DEFAULT_CGI_ENVIRONMENT is used.
	 * @param tryOtherLocations true if the should check DEFAULT_CGI_LOCATIONS 
	 * @param preferSystemPhp true if the should check DEFAULT_CGI_LOCATIONS first
	 * @param err The error stream
         * @return The process handle.
         * @throws IOException
         * @see Util#checkCgiBinary(StringBuffer)
         */
        public static Process start(String[] args, File homeDir, Map env, boolean tryOtherLocations, boolean preferSystemPhp, OutputStream err) throws IOException {
            Process proc = new ProcessWithErrorHandler(args, homeDir, env, tryOtherLocations, preferSystemPhp, err);
            proc.start();
            return proc;
        }
    }

    /**
     * @return The thread context class loader.
      */
    public static ClassLoader getContextClassLoader() {
          return JavaBridgeClassLoader.getContextClassLoader();
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
     * @return The string representation of object
     */
    public static String stringValueOf(Object object) {
        String s = String.valueOf(object);
        if(s==null) s = String.valueOf(s);
        return s;
    }

    /**
     * Create a new AppThreadPool.
     * @param name The pool name
     * @return A new AppThreadPool for up to {@link #THREAD_POOL_MAX_SIZE} runnables
     */
    public static AppThreadPool createThreadPool(String name) {
        AppThreadPool pool = null;
        int maxSize = 20;
        try {
        	maxSize = Integer.parseInt(Util.THREAD_POOL_MAX_SIZE);
        } catch (Throwable t) {
        	Util.printStackTrace(t);
        }
        if(maxSize>0) {
            pool = new AppThreadPool(name, maxSize);
	}
        return pool;
    }
    
    
    /**
     * parse java.log_file=@HOST:PORT
     * @param logFile The log file from the PHP .ini file
     * @return true, if we can use the log4j logger, false otherwise.
     */
    public static boolean setConfiguredLogger(String logFile) {
        try {
	  return tryConfiguredChainsawLogger(logFile);
	} catch (Exception e) {
	  printStackTrace(e);
	  Util.setLogger(new FileLogger());
	}
	return true;
    }
    private static final class ConfiguredChainsawLogger extends ChainsawLogger {
        private String host;
	private int port;
	private ConfiguredChainsawLogger(String host, int port) {
	    super();
	    this.host=host;
	    this.port=port;
        }
        public static ConfiguredChainsawLogger createLogger(String host, int port) throws Exception {
            ConfiguredChainsawLogger logger = new ConfiguredChainsawLogger(host, port);
            logger.init();
	    return logger;
        }
        public void configure(String host, int port) throws Exception {
            host = this.host!=null ? this.host : host;
            port = this.port > 0 ? this.port : port;
            super.configure(host, port);
        }
    }
    /**
     * parse java.log_file=@HOST:PORT
     * @param logFile The log file from the PHP .ini file
     * @return true, if we can use the log4j logger, false otherwise.
     * @throws Exception
     */
    private static boolean tryConfiguredChainsawLogger(String logFile) throws Exception {
	if(logFile!=null && logFile.length()>0 && logFile.charAt(0)=='@') {
	    logFile=logFile.substring(1, logFile.length());
	    int idx = logFile.indexOf(':');
	    int port = -1;
	    String host = null;
	    if(idx!=-1) {
		String p = logFile.substring(idx+1, logFile.length());
		if(p.length()>0) port = Integer.parseInt(p);
		host = logFile.substring(0, idx);
	    } else {
		if(logFile.length()>0) host = logFile;
	    }
	    Util.setLogger(ConfiguredChainsawLogger.createLogger(host, port));
	    return true;
	}
	return false;
    }

    /**
     * Return the time in GMT
     * @param ms the time in milliseconds
     * @return The formatted date string
     */
    public static String formatDateTime(long ms) {
	java.sql.Timestamp t = new java.sql.Timestamp(ms);
	DateFormat formatter = DateFormat.getDateTimeInstance(DateFormat.FULL, DateFormat.LONG, Locale.ENGLISH);
	formatter.setTimeZone(TimeZone.getTimeZone("GMT"));
	String str =  formatter.format(t);
	return str;
    }
    /**destroy the logger */
    public static void destroy () {
	    Util.logLevel = 0;
    }
    /**
     * Return an mbean property.
     * Example: <code>Util.getMBeanProperty("*:type=ThreadPool,name=http*", "maxThreads")</code> or 
     * <code>Util.getMBeanProperty("*:ServiceModule=*,J2EEServer=*,name=JettyWebConnector,j2eeType=*", "maxThreads");</code>
     * @param pattern the pattern string 
     * @param property the property key
     * @return the property value
     */
    public static int getMBeanProperty(String pattern, String property) {
            try {
             Class objectNameClazz = Class.forName("javax.management.ObjectName");
             Constructor constructor = objectNameClazz.getConstructor(new Class[]{String.class});
             Object objectName = constructor.newInstance(new Object[]{pattern});
             
             
             Class clazz = Class.forName("javax.management.MBeanServerFactory");
             Method method = clazz.getMethod("findMBeanServer", new Class[]{String.class});
             ArrayList servers = (ArrayList)method.invoke(clazz, new Object[]{null});
             Object server = servers.get(0);
             
             Class mBeanServerClazz = Class.forName("javax.management.MBeanServer");
             clazz = Class.forName("javax.management.QueryExp");
             method = mBeanServerClazz.getMethod("queryMBeans", new Class[]{objectNameClazz, clazz});
             
             Set s = (Set)method.invoke(server, new Object[]{objectName, null});
             Iterator ii = s.iterator(); 
             
             if (ii.hasNext()) {
        	     clazz = Class.forName("javax.management.ObjectInstance");
             method = clazz.getMethod("getObjectName", Util.ZERO_PARAM);
             objectName = method.invoke(ii.next(), Util.ZERO_ARG);
             
             method = mBeanServerClazz.getMethod("getAttribute", new Class[]{objectNameClazz, String.class});
        	     Object result = method.invoke(server, new Object[]{objectName, property});
        	     return Integer.parseInt(String.valueOf(result));
             }
	} catch (Exception t) {
		if (Util.logLevel>5) Util.printStackTrace(t);
	}
	return 0;
   }

    static final boolean checkVM() {
	try {
	    return "libgcj".equals(System.getProperty("gnu.classpath.vm.shortname"));
	} catch (Throwable t) {
	    return false;
	}
    }
    /**
     * Return args + PHP_ARGS
     * @param args The prefix
     * @return args with PHP_ARGS appended
     */
    public static final String[] getPhpArgs(String[] args) {
	String[] allArgs = new String[args.length+PHP_ARGS.length];
	int i=0;
	for(i=0; i<args.length; i++) {
	    allArgs[i]=args[i];
	}
	for(int j=0; j<PHP_ARGS.length; j++) {
	    allArgs[i++]=PHP_ARGS[j];
	}
	return allArgs;
    }
}
