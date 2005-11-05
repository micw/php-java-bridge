/*-*- mode: Java; tab-width:8 -*-*/

package php.java.bridge;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.PrintStream;
import java.io.UnsupportedEncodingException;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.Date;
import java.util.Enumeration;
import java.util.Hashtable;
import java.util.Properties;
import java.util.Vector;

/**
 * Miscellaneous functions.
 * @author jostb
 *
 */
public class Util {

    public static final String ASCII = "ASCII";
    public static final String UTF8 = "UTF-8";
    public static final int	BUF_SIZE = 8152;

    public static String TCP_SOCKETNAME = "9267";
    public static String EXTENSION_DIR = null;
    public static String EXTENSION_NAME = "JavaBridge";
    public static String THREAD_POOL_MAX_SIZE = "20";
    public static int DEFAULT_LOG_LEVEL = 2;
    public static int BACKLOG = 20;
    public static String DEFAULT_LOG_FILE = "";
	
    private static String getProperty(Properties p, String key, String defaultValue) {
	String s = null;
	if(p!=null) s = p.getProperty(key);
	if(s==null) s = System.getProperty("php.java.bridge." + String.valueOf(key).toLowerCase());
	if(s==null) s = defaultValue;
	return s;
    }
    
    private static void initGlobals() {
        Properties p = new Properties();
	try {
	    InputStream in = Util.class.getResourceAsStream("global.properties");
	    p.load(in);
	} catch (Throwable t) {
	    //t.printStackTrace();
	};
	try {
	    THREAD_POOL_MAX_SIZE = getProperty(p, "THREADS", THREAD_POOL_MAX_SIZE);
	} catch (Throwable t) {
	    //t.printStackTrace();
	};
	TCP_SOCKETNAME = getProperty(p, "TCP_SOCKETNAME", TCP_SOCKETNAME);
	EXTENSION_DIR = getProperty(p, "EXTENSION_DIR", EXTENSION_DIR);
	EXTENSION_NAME = getProperty(p, "EXTENSION_DISPLAY_NAME", EXTENSION_NAME);
	try {
	    String s = getProperty(p, "DEFAULT_LOG_LEVEL", String.valueOf(DEFAULT_LOG_LEVEL));
	    DEFAULT_LOG_LEVEL = Integer.parseInt(s);
		Util.logLevel=Util.DEFAULT_LOG_LEVEL; /* java.log_level in php.ini overrides */
	} catch (NumberFormatException e) {/*ignore*/}
	try {
	    String s = getProperty(p, "BACKLOG", String.valueOf(BACKLOG));
	    BACKLOG = Integer.parseInt(s);
	} catch (NumberFormatException e) {/*ignore*/}
	DEFAULT_LOG_FILE = getProperty(p, "DEFAULT_LOG_FILE", Util.EXTENSION_NAME+".log");
	if(DEFAULT_LOG_FILE.length()!=0) {
		try {
			Util.logStream=new java.io.PrintStream(new java.io.FileOutputStream(DEFAULT_LOG_FILE));
		} catch (FileNotFoundException e1) {/*ignore*/}
    }
    }
 
    /**
     * The logStream, defaults to System.err
     */
    public static PrintStream logStream = System.err;
    
    /**
     * A logger class.
     */
    public static class Logger { // hook for servlet
        static boolean haveDateFormat=true;
        private static Object _form;
        protected Logger () {
            initGlobals();
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
         * @param s - The message
         */
   	public void log(String s) {
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
   	 * @param t - The Throwable
   	 */
   	public void printStackTrace(Throwable t) {
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
     * @param level - The log level
     * @param msg - The message
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
     * @param msg - The warn message
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
     * @param t - The Throwable
     */
    public static void printStackTrace(Throwable t) {
	if(logLevel > 0)
	    if ((t instanceof Error) || logLevel > 1) 
	    	logger.printStackTrace(t);
    }
    
    /**
     * Display a debug message
     * @param msg - The message
     */
    public static void logDebug(String msg) {
	if(logLevel>3) println(4, msg);
    }
    
    /**
     * Display a fatal error
     * @param msg - The error
     */
    public static void logFatal(String msg) {
	if(logLevel>0) println(1, msg);
    }
    
    /**
     * Display an error or an exception
     * @param msg - The error or the exception
     */
    public static void logError(String msg) {
	if(logLevel>1) println(2, msg);
    }
    
    /**
     * Display a message
     * @param msg - The message
     */
    public static void logMessage(String msg) {
	if(logLevel>2) println(3, msg);
    }
    
    /**
     * Return the class name
     * @param obj - The object
     * @return The class name
     */
    public static String getClassName(Object obj) {
    	Class c = getClass(obj);
    	if(c!=null) return c.getName();
    	return "null";
    }
    
    /**
     * Return the short class name
     * @param obj - The object
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
     * @param obj - The object
     * @return The class
     */
    public static Class getClass(Object obj) {
	if(obj==null) return null;
	return obj instanceof Class?(Class)obj:obj.getClass();
    }
    
    /**
     * Append an object to a StringBuffer
     * @param obj - The object
     * @param buf - The StringBuffer
     */
    public static void appendObject(Object obj, StringBuffer buf) {
    	if(obj instanceof Class) {
	    if(((Class)obj).isInterface()) 
		buf.append("i:");
	    else
		buf.append("c:");
	    buf.append(getClassName((obj)));
    	} else {
    	    if(obj!=null) {
	        buf.append("o(");
	        buf.append(getShortClassName(obj));
	        buf.append("):");
	    	buf.append("\"");
		buf.append(String.valueOf(obj));
		buf.append("\"");
    	    } else {
    	    	buf.append("null");
    	    }
	}
    }
    
    /**
     * Append a function parameter to a StringBuffer
     * @param obj - The parameter object
     * @param buf - The StringBuffer
     */
    public static void appendParam(Object obj, StringBuffer buf) {
    	buf.append("(");
	buf.append(getShortClassName(obj));
	buf.append(")");
    }
    
    /**
     * Return function arguments and their types as a String
     * @param args - The args
     * @param params - The associated types
     * @return A new string
     */
    public static String argsToString(Object args[], Class[] params) {
	StringBuffer buffer = new StringBuffer("");
	appendArgs(args, params, buffer);
	return buffer.toString();
    }
    
    /**
     * Append function arguments and their types to a StringBuffer
     * @param args - The args
     * @param params - The associated types
     * @param buf - The StringBuffer
     */
    public static void appendArgs(Object args[], Class[] params, StringBuffer buf) {
	if(args!=null) {
	    for(int i=0; i<args.length; i++) {
		if(params!=null) {
		    appendParam(params[i], buf); 
		}
	    	appendObject(args[i], buf);
		
		if(i+1<args.length) buf.append(", ");
	    }
	}
    }
    
    /**
     * Locale-independent getBytes(), uses ASCII encoding
     * @param s - The String
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
     * @param h - The hashtable
     * @return The String
     * @throws NullPointerException
     */
    public static String[] hashToStringArray(Hashtable h)
	throws NullPointerException {
	Vector v = new Vector();
	Enumeration e = h.keys();
	while (e.hasMoreElements()) {
	    String k = e.nextElement().toString();
	    v.add(k + "=" + h.get(k));
	}
	String[] strArr = new String[v.size()];
	v.copyInto(strArr);
	return strArr;
    }

    /**
     * Discard all header fields from a HTTP connection and write the body to the OutputStream
     * @param buf - A buffer, for example new byte[BUF_SIZE]
     * @param natIn - The InputStream
     * @param out - The OutputStream
     * @throws UnsupportedEncodingException
     * @throws IOException
     */
    public static void parseBody(byte[] buf, InputStream natIn, OutputStream out) throws UnsupportedEncodingException, IOException {
	String line = null;
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
		    	//System.err.println(new String(buf, s, i-s-2, ASCII));
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
 * @return
  */
public static String getHostAddress() {
	try {
		return InetAddress.getLocalHost().getHostAddress();
	} catch (UnknownHostException e) {
		return "127.0.0.1";
	}
}
}
