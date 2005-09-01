/*-*- mode: Java; tab-width:8 -*-*/

package php.java.bridge;

import java.io.InputStream;
import java.io.PrintStream;
import java.util.Date;
import java.util.Properties;

public class Util {

    public static String TCP_SOCKETNAME = "9267";
    public static String EXTENSION_DIR = null;
    public static String EXTENSION_NAME = "JavaBridge";
    public static String THREAD_POOL_MAX_SIZE = "20";
    static void initGlobals() {
        Properties p = new Properties();
	try {
	    InputStream in = Util.class.getResourceAsStream("global.properties");
	    p.load(in);
	} catch (Throwable t) {
	    //t.printStackTrace();
	};
	try {
	    THREAD_POOL_MAX_SIZE = System.getProperty("php.java.bridge.threads", THREAD_POOL_MAX_SIZE);
	} catch (Throwable t) {
	    //t.printStackTrace();
	};
	TCP_SOCKETNAME = p.getProperty("TCP_SOCKETNAME", TCP_SOCKETNAME);
	EXTENSION_DIR = p.getProperty("EXTENSION_DIR", EXTENSION_DIR);
	EXTENSION_NAME = p.getProperty("EXTENSION_DISPLAY_NAME", EXTENSION_NAME);
    }

    public static final String UTF8 = "UTF-8";

    public static final int DEFAULT_LOG_LEVEL = 2;
    public static final int BACKLOG = 20;

    public static PrintStream logStream;
    public static class Logger { // hook for servlet
        static boolean haveDateFormat=true;
        private static Object _form;
        public Logger () {
            initGlobals();
        }
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
   	public void printStackTrace(Throwable t) {
   	    t.printStackTrace(logStream);
   	}
    }
    public static Logger logger = new Logger();
    public static int logLevel;

    //
    // Logging
    //

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
    public static void warn(String msg) {
	if(logLevel<=0) return;
    	StringBuffer b = new StringBuffer(logger.now());
	b.append(" "); b.append(Util.EXTENSION_NAME); b.append(" ");
	b.append("WARNING");
	b.append(": ");
	b.append(msg);
	logger.log(b.toString());
    }
    public static void printStackTrace(Throwable t) {
	if(logLevel > 0)
	    if ((t instanceof Error) || logLevel > 1) 
	    	logger.printStackTrace(t);
    }
    public static void logDebug(String msg) {
	if(logLevel>3) println(4, msg);
    }
    public static void logFatal(String msg) {
	if(logLevel>0) println(1, msg);
    }
    public static void logError(String msg) {
	if(logLevel>1) println(2, msg);
    }
    public static void logMessage(String msg) {
	if(logLevel>2) println(3, msg);
    }
    public static String getClassName(Object obj) {
    	Class c = getClass(obj);
    	if(c!=null) return c.getName();
    	return "null";
    }
    public static String getShortClassName(Object obj) {
	String name = getClassName(obj);
	int idx = name.lastIndexOf('.');
	if(idx!=-1) 
	    name = name.substring(idx+1);
	return name;
    }
    //
    public static Class getClass(Object obj) {
	if(obj==null) return null;
	return obj instanceof Class?(Class)obj:obj.getClass();
    }
    public static void appendObject(Object obj, StringBuffer buf) {
    	if(obj instanceof Class) {
	    if(((Class)obj).isInterface()) 
		buf.append("i:");
	    else
		buf.append("c:");
	    buf.append(getClassName((obj)));
    	} else {
	    buf.append("o(");
	    buf.append(getShortClassName(obj));
	    buf.append("):\"");
	    buf.append(String.valueOf(obj));
	    buf.append("\"");
	}
    }
    public static void appendParam(Object obj, StringBuffer buf) {
    	buf.append("(");
	buf.append(Util.getClass(obj).getName());
	buf.append(")");
    }
    public static String argsToString(Object args[], Class[] params) {
	StringBuffer buffer = new StringBuffer("");
	appendArgs(args, params, buffer);
	return buffer.toString();
    }
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
}
