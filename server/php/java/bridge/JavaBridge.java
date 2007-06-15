/*-*- mode: Java; tab-width:8 -*-*/

package php.java.bridge;

/*
 * Copyright (C) 2003-2007 Jost Boekemeier and others.
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

import java.beans.BeanInfo;
import java.beans.Introspector;
import java.beans.PropertyDescriptor;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.UnsupportedEncodingException;
import java.lang.reflect.AccessibleObject;
import java.lang.reflect.Array;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.net.Socket;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.Hashtable;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.StringTokenizer;
import java.util.Map.Entry;

/**
 * This is the main interface of the PHP/Java Bridge. It
 * contains utility methods which can be used by clients.
 * @author Sam Ruby (methods coerce and select)
 * @author Kai Londenberg
 * @author Jost Boekemeier
 * @see php.java.bridge.Standalone
 * @see php.java.servlet.PhpJavaServlet
 */
public class JavaBridge implements Runnable {

    /**
     * For PHP4's last_exception_get.
     */
    public Throwable lastException = null;
    protected Throwable lastAsyncException; // reported by end_document()

    // array of objects in use in the current script
    GlobalRef globalRef=new GlobalRef();

    /**
     * For internal use only. The classloader. 
     */
    private SimpleJavaBridgeClassLoader cl = null;

    static HashMap sessionHash = new HashMap();

    /**
     * For internal use only. The input stream for the current channel. 
     */
    public InputStream in; 
    /**
     * For internal use only. The output stream for the current channel. 
     */
    public OutputStream out;

    /**
     * For internal use only. The request log level.
     */
    public int logLevel = Util.logLevel;
  
    /**
     * Return the log level:
     * <br>
     * 0: log off <br>
     * 1: log fatal <br>
     * 2: log messages/exceptions <br>
     * 3: log verbose <br>
     * 4: log debug <br>
     * 5: log method invocations
     * @return The request log level.
     */
    public int getLogLevel() {
	return logLevel;
    }
    
    /**
     * For internal use only. The current request (if any)
     * 
     */
    public Request request;

    // false if we detect that setAccessible is not possible
    boolean canModifySecurityPermission = true;

    // native accept fills these (if available)
    int uid =-1, gid =-1;

    /**
     * Open a system log file with the correct (Unix) permissions.
     * @param logFile The log file or "" for standard out
     * @return true if it was possible to re-direct stdout and stderr.
     * If yes, we can simply print to standard out.
     */
    static native boolean openLog(String logFile);
    /**
     * Create a local ("Unix domain") socket for sockname and return the handle.
     * If it was possible to obtain the user credentials, setGlobals will be called with
     * the uid and gid.
     * @param logLevel The current log level.
     * @param backlog The current backlog.
     * @param sockname The sockename.
     * @return local socket handle ("Unix domain socket")
     */
    static native int startNative(int logLevel, int backlog, String sockname);

    /**
     * Accept a connection from the "unix domain" socket.
     * @param socket The socket number
     * @return The socket number.
     */
    static native int accept(int socket);
    /**
     * Write bytes to a local socket.
     * @param peer The socket handle
     * @param buf the byte buffer.
     * @param nmemb number of bytes
     * @return number of bytes written.
     */
    static native int swrite(int peer, byte buf[], int nmemb);
    /**
     * Read bytes from a local socket.
     * @param peer The socket handle
     * @param buf the byte buffer.
     * @param nmemb number of bytes
     * @return number of bytes written.
     */
    static native int sread(int peer, byte buf[], int nmemb);

    /**
     * Close a local socket.
     * @param peer The socket handle
     */
    public static native void sclose(int peer);

    private MethodCache methodCache = new MethodCache();
    private ConstructorCache constructorCache = new ConstructorCache();
    StringCache stringCache = new StringCache(this);

    /** For internal use only. */
    private SessionFactory sessionFactory;
    
    /** 
     * For internal use only.
     */
    static final SessionFactory defaultSessionFactory = new SessionFactory();

    public SessionFactory getSessionFactory() {
	if(sessionFactory==null) return sessionFactory = defaultSessionFactory;
	return sessionFactory;
    }
    
    Options options;
    
    /**
     * Returns the connection options
     * @return The Options.
     */
    public Options getOptions() {
        return options;
    }

    
    /**
     * Communication with client in a new thread
     */
    public void run() {
        try {
	    logDebug("START: JavaBridge.run()");
	    request = new Request(this);
          
	    try {
		if(!request.init(in, out)) return;
	    } catch (Throwable e) {
		printStackTrace(e);
		return;
	    }
	    if(logLevel>3) logDebug("Request from client with uid/gid "+uid+"/"+gid);
	    try {
		request.handleRequests();
	    } catch (Exception e) {
		printStackTrace(e);
	    }

	    globalRef=null;
	    logDebug("END: JavaBridge.run()");

        } catch (Throwable t) {
	    printStackTrace(t);
        } finally {
	    try {in.close();} catch (IOException e1) {printStackTrace(e1);}
	    try {out.close();} catch (IOException e2) {printStackTrace(e2);}
	}
    }

    /**
     * Create a new server socket and return it.
     * @param sockname the socket name
     * @return the server socket
     * @throws IOException 
     */
    public static ISocketFactory bind(String sockname) throws IOException  {
        return Standalone.bind(Util.logLevel, sockname);
    }
    /**
     * parse java.log_file=@HOST:PORT
     * @param logFile The log file from the PHP .ini file
     * @return true, if we can use the log4j logger, false otherwise.
     */
    private static boolean setChainsawLogger(String logFile) {
        try {
	  return doSetChainsawLogger(logFile);
	} catch (Exception e) {
	  e.printStackTrace();
	  Util.setLogger(new FileLogger());
	}
	return true;
    }
    private static final class Logger extends ChainsawLogger {
        private String host;
	private int port;
	private Logger(String host, int port) {
	    super();
	    this.host=host;
	    this.port=port;
        }
        public static Logger createLogger(String host, int port) throws Exception {
            Logger logger = new Logger(host, port);
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
    private static boolean doSetChainsawLogger(String logFile) throws Exception {
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
	    Util.setLogger(Logger.createLogger(host, port));
	    return true;
	}
	return false;
    }
    /**
     * Global init. Redirects System.out and System.err to the server
     * log file(s) or to System.err and creates and opens the
     * communcation channel. Note: Do not write anything to
     * System.out, this stream is connected with a pipe which waits
     * for the channel name.
     * @param s an array of [socketname, level, logFile]
     */
    public static void init(String s[]) {
        (new Standalone()).init(s);
    }
    // called by Standalone.init()
    static void initLog(String socket, int logLevel, String s[]) {
	String logFile=null, rawLogFile=null;
	
	if(logLevel==-1) logLevel = Util.DEFAULT_LOG_LEVEL;
	Util.logLevel = logLevel;

	try {
	    try {
		rawLogFile=logFile=s.length>0?"":Util.DEFAULT_LOG_FILE;
		if(s.length>2) {
		    rawLogFile=logFile=s[2];
		    if(setChainsawLogger(logFile))
		        logFile=null; // when log4j is used, System.out and System.err are not redirected
		    else
		        Util.setLogger(new FileLogger()); // use specified log file
		    if(Util.logLevel>3) System.err.println(Util.EXTENSION_NAME+" log: " + rawLogFile);
		}
	    }catch (Throwable t) {
		t.printStackTrace();
	    }
	    boolean redirectOutput = false;
	    try {
	    	redirectOutput = logFile==null || openLog(logFile);
	    } catch (Throwable t) {/*ignore*/}

	    Util.redirectOutput(redirectOutput, logFile);
	    Util.logMessage("VM                  : " + Util.VM_NAME);
	    if(Util.VERSION != null)
		Util.logMessage(Util.EXTENSION_NAME+  " version             : " + Util.VERSION);
	    Util.logMessage("logFile             : " + rawLogFile);
	    Util.logMessage("default logLevel    : " + Util.logLevel);
	    Util.logMessage("socket              : " + socket);
	    Util.logMessage("java.ext.dirs       : " + System.getProperty("java.ext.dirs"));
	    Util.logMessage("php.java.bridge.base: " + Util.JAVABRIDGE_BASE);
	    Util.logMessage("extra library dir   : " + Util.JAVABRIDGE_LIB);
	    Util.logMessage("thread pool size    : " + Util.THREAD_POOL_MAX_SIZE);
	} catch (Throwable t) {
	    throw new RuntimeException(t);
	}
    }
    // called by Standalone.init()
    static void init(ISocketFactory socket, int logLevel, String s[]) {	    
	try {
	    ThreadPool pool = Util.createThreadPool(Util.EXTENSION_NAME+"ThreadPool");
	    try {
	        String policy = System.getProperty("java.security.policy");
	        String base = Util.JAVABRIDGE_BASE;
	        if(policy!=null && base!=null) {
	            SecurityManager manager = new php.java.bridge.JavaBridgeSecurityManager();
	            System.setSecurityManager(manager);
		    Util.logMessage(Util.EXTENSION_NAME + " policy base     : " + base);
		    Util.logMessage(Util.EXTENSION_NAME + " security policy : " + policy);
	        }
	    } catch(Exception e) {
	        Util.logMessage("Cannot install security manager: " + e);
	    }

	    Util.logDebug("Starting to accept Socket connections");
            
	    while(true) {
		Socket sock = socket.accept();
                Util.logDebug("Socket connection accepted");
		JavaBridge bridge = new JavaBridge(sock.getInputStream(), sock.getOutputStream());
		if(pool!=null) {
                    Util.logDebug("Starting bridge thread from thread pool");
		    pool.start(bridge); // Uses thread pool
		} else {
                    Util.logDebug("Starting new bridge thread");
		    Thread t = new Util.Thread(bridge);
		    t.start();
		}
	    }

	} catch (Throwable t) {
	    throw new RuntimeException(t);
	}
    }

    /**
     * Start the PHP/Java Bridge. <br>
     * Example:<br>
     * <code>java -Djava.awt.headless=true -jar JavaBridge.jar INET:9656 5 /var/log/php-java-bridge.log</code><br>
     * Note: Do not write anything to System.out, this
     * stream is connected with a pipe which waits for the channel name.
     * @param s an array of [socketname, level, logFile]
     * @deprecated Use Standalone.main()
     * @see php.java.bridge.Standalone#main(String[])
     */
    public static void main(String s[]) {
        Standalone.main(s);
    }

    //
    // Helper routines which encapsulate the native methods
    //
    void setResult(Response response, Object value, Class type) {
    	response.setResult(value, type);
    }

    /**
     * Print a stack trace to the log file.
     * @param t the throwable
     */
    public void printStackTrace(Throwable t) {
	if(logLevel > 0)
	    if ((t instanceof Error) || logLevel > 1) {
	    	Util.getLogger().printStackTrace(t);
	    }
    }
    private String getId() {
    	return Integer.toHexString(System.identityHashCode(this))+"@"+Integer.toHexString(System.identityHashCode(Thread.currentThread()));
    }
    /**
     * Write a debug message
     * @param msg The message
     */
    public void logDebug(String msg) {
	if(logLevel>3) Util.println(4, getId() + " " + msg);
    }
    /**
     * Write a fatal message
     * @param msg The message
     */
    public void logFatal(String msg) {
	if(logLevel>0) Util.println(1, getId() + " " + msg);
    }
    /**
     * Write an error message.
     * @param msg The message
     */
    public void logError(String msg) {
	if(logLevel>1) Util.println(2, getId() + " " + msg);
    }
    /**
     * Write a notice.
     * @param msg The message
     */
    public void logMessage(String msg) {
	if(logLevel>2) Util.println(3, getId() + " " + msg);
    }
    /**
     * Write a warning.
     * @param msg The warning.
     */
    public void warn(String msg) {
	if(logLevel>0) Util.warn(getId() + " " + msg);
    }
    
    void setException(Response response, Throwable e, String method, Object obj, String name, Object args[], Class params[]) {
	if(logLevel>2) printStackTrace(e);

	if (e instanceof InvocationTargetException) {
	    Throwable t = ((InvocationTargetException)e).getTargetException();
	    if (t!=null) e=t;
	}

	StringBuffer buf=new StringBuffer(method);
	buf.append(" failed: ");
	if(obj!=null) {
	    buf.append("[");
	    Util.appendShortObject(obj, buf);
	    buf.append("]->");
	} else {
	    buf.append("new ");
	}
	buf.append(name);
	String arguments = Util.argsToString(args, params);
	if(arguments.length()>0) {
	    buf.append("(");
	    Util.appendArgs(args, params, buf);
	    buf.append(")");
	}
	buf.append(".");
	buf.append(" Cause: ");
	buf.append(String.valueOf(e));
	buf.append(" Responsible VM: ");
	buf.append(Util.VM_NAME);
	
	lastException = new Exception(buf.toString(), e);
	StackTraceElement[] trace = e.getStackTrace();
	if(trace!=null) lastException.setStackTrace(trace);
	response.setResultException(lastException, lastException.toString());
    }

    private Exception getUnresolvedExternalReferenceException(Throwable e, String what) {
        return 	new ClassNotFoundException("Unresolved external reference: "+ e+ ". -- " +
        		"Unable to "+what+", see the README section \"Java platform issues\" " +
        				"for details.", e);
    }
    
    /**
     * Create an new instance of a given class
     */
    public void CreateObject(String name, boolean createInstance,
			     Object args[], Response response) {
	Class params[] = null;
	LinkedList candidates = new LinkedList();
	LinkedList matches = new LinkedList();

	try {
	    Constructor selected = null;
	    ConstructorCache.Entry entry = null;
	    
	    Class clazz = getClassLoader().forName(name);
	    if(createInstance) {
		entry = constructorCache.getEntry(name, args);
		selected = constructorCache.get(entry);
		if(selected==null) {
		    Constructor cons[] = clazz.getConstructors();
		    for (int i=0; i<cons.length; i++) {
			candidates.add(cons[i]);
			if (cons[i].getParameterTypes().length == args.length) {
			    matches.add(cons[i]);
			}
		    }
		    
		    selected = (Constructor)select(matches, args);
		    if(selected!=null) constructorCache.put(entry, selected);
		}
	    }

	    if (selected == null) {
		if (args.length > 0) {
		    throw createInstance? 
			    (Exception)new InstantiationException("No matching constructor found. " + "Candidates: " + String.valueOf(candidates)):
				(Exception)new JavaBridgeIllegalArgumentException("ReferenceClass must be called w/o arguments; either write new JavaClass(\""+name+"\") or new Java(\""+name+"\", args...).");
		} else {
		    // for classes which have no visible constructor, return the class
		    // useful for classes like java.lang.System and java.util.Calendar.
		    if(createInstance && logLevel>2) {
		    	logMessage("No visible constructor found in: "+ name +", returning the class instead of an instance; this may not be what you want. Please correct this error or please use new JavaClass(\""+name+"\") instead.");
		    }
		    response.setResultClass(clazz);
		    return;
		}
	    }

	    Object coercedArgs[] = coerce(params=entry.getParameterTypes(selected), args, response);
	    // If we have a logLevel of 5 or above, do very detailed invocation logging
	    if (this.logLevel>4) {
	        Object result = selected.newInstance(coercedArgs);
	        logInvoke(result, name, coercedArgs);
    	    	response.setResultObject(result);
	    } else {
  	    	response.setResultObject(selected.newInstance(coercedArgs));
  	    }
	} catch (Throwable e) {
	    if(e instanceof InvocationTargetException) e = ((InvocationTargetException)e).getTargetException();
	    if(e instanceof Request.AbortException) {throw (Request.AbortException)e;}
	    if(e instanceof OutOfMemoryError) {
		Util.logFatal("OutOfMemoryError");
		throw (OutOfMemoryError)e; // abort
	    }
	    if(e instanceof NoClassDefFoundError) {
		getClassLoader().clearCaches();
		e = getUnresolvedExternalReferenceException(e, "call constructor");
	    }
	    setException(response, e, createInstance?"CreateInstance":"ReferenceClass", null, name, args, params);
	}
    }

    //
    // Select the best match from a list of methods
    //
    private int weight(Class param, Object arg) {
	int w = 0;
	if(arg!=null) {
	 if (param.isInstance(arg)) {
 	    for (Class c=arg.getClass(); (c=c.getSuperclass()) != null; ) {
		if (!param.isAssignableFrom(c)) {
		    break;
		}
		w+=16;		// prefer more specific arg, for
				// example AbstractMap hashMap
				// over Object hashMap.
	    }
	} else if (param == java.lang.String.class) {
	    if (!(arg instanceof String) && !(arg instanceof PhpString))
	        if(arg instanceof byte[])
	            w+=32;
	        else
	            w+=8000; // conversion to string is always possible
	} else if (param.isArray()) {
	        if(arg instanceof PhpString) {
	            Class c=param.getComponentType();
	            if(c == byte.class) 
	                w+=32;
	            else
	                w+=9999;
	        } else if(arg.getClass() == PhpArray.class) {
		    Iterator iterator = ((Map)arg).values().iterator();
		    if(iterator.hasNext()) {
			Object elem = iterator.next();
			Class c=param.getComponentType();
			w+=weight(c, elem);
		    }
		} else if(arg.getClass().isArray()) {
		    int length = Array.getLength(arg);
		    if(length>0) {
			w+=weight(param.getComponentType(), Array.get(arg,0));
		    }
		}
		else w+=9999;
	    } else if ((java.util.Collection.class).isAssignableFrom(param)) {
	    if (!(arg instanceof PhpArray))
		w+=9999;
	} else if (param.isPrimitive()) {
	    Class c=param;
	    if (arg instanceof Number) {
		if(arg instanceof Double) {
		    if (c==Float.TYPE) w+=1;
		    else if (c==Double.TYPE) w+=0;
		    else w+=256;
		} else {
		    if (c==Boolean.TYPE) w+=5;
		    else if (c==Character.TYPE) w+=4;
		    else if (c==Byte.TYPE) w+=3;
		    else if (c==Short.TYPE) w+=2;
		    else if (c==Integer.TYPE) w+=1;
		    else if (c==Long.TYPE) w+=0;
		    else w+=256;
		}
	    } else if (arg instanceof Boolean) {
		if (c!=Boolean.TYPE) w+=9999;
	    } else if (arg instanceof Character) {
		if (c!=Character.TYPE) w+=9999;
	    } else if ((arg instanceof String)||(arg instanceof PhpString)) {
		    w+=64;
	    } else {
		w+=9999;
	    }
	} else if(Number.class.isAssignableFrom(param)) {
	    if(param==Float.class || param==Double.class) {
		if(!(arg instanceof Double)) w+=9999;
	    } else if(!(arg instanceof PhpExactNumber)) w+=9999;
	} else {
	    w+=9999;
	}
	}
	if(logLevel>4) logDebug("weight " + param + " " + Util.getClass(arg) + ": " +w);
	return w;
    }

    private Object select(LinkedList methods, Object args[]) {
	if (methods.size() == 1) return methods.getFirst();
	Object similar = null, selected = null;
	int best = Integer.MAX_VALUE;
	int n = 0;
	
	for (Iterator e = methods.iterator(); e.hasNext(); n++) {
	    Object element = e.next();
	    int w=0;

	    Class parms[] = (element instanceof Method) ?
		((Method)element).getParameterTypes() :
		((Constructor)element).getParameterTypes();

	    for (int i=0; i<parms.length; i++) {
		Object arg = args[i];
		if(arg instanceof PhpProcedureProxy) {
		    PhpProcedureProxy proxy = ((PhpProcedureProxy)arg);
		    if(proxy.suppliedInterfaces==null) {
		        continue; // exact match
		    } else {
			arg = args[i] = proxy.getProxy(null);        
		    }
		}
		
		w+=weight(parms[i], arg);
	    }
	    if (w < best) {
		if (w == 0) {
		    if(logLevel>4) logDebug("Selected: " + element + " " + w);
		    return element;
		}
		best = w;
		selected = element;
		if(logLevel>2) { 
		    similar = null;
		    if(logLevel>4) logDebug("best: " + selected + " " + w);
		}
	    } else {
		if(logLevel>2) {
			if(w==best) similar = element;
	        	if(logLevel>4) logDebug("skip: " + element + " " + w);
		}
	    }
	}
	if(logLevel>2 && similar!=null) {
	    StringBuffer buf = new StringBuffer();
	    for(int i=0; i<args.length; i++) {
		Util.appendParam(args[i], buf);
	    }
	    logMessage("Portability warning: " + selected + " and " + similar + " both match " + buf.toString());
	}
	if(logLevel>4) logDebug("Selected: " + selected + " " + best);
	return selected;
    }

    private Object o[] = new Object[1];
    private Class c[] = new Class[1];
    Object coerce(Class param, Object arg, Response response) {
        o[0]=arg; c[0]=param;
        return coerce(c, o, response)[0];
    }
    //
    // Coerce arguments when possible to conform to the argument list.
    // Java's reflection will automatically do widening conversions,
    // unfortunately PHP only supports wide formats, so to be practical
    // some (possibly lossy) conversions are required.
    //
    Object[] coerce(Class parms[], Object args[], Response response) {
        Object arg;
	Object result[] = args;
	int size = 0;

	for (int i=0; i<args.length; i++) {
	    if (args[i] instanceof PhpProcedureProxy && parms[i] != PhpProcedureProxy.class) {
		Class param = parms[i];
		if(!param.isInterface()) {
		    if(Util.CLRAssembly!=null) // CLR uses an inner method class
			try {
			    args[i] = ((PhpProcedureProxy)args[i]).getProxy(new Class[] {getClassLoader().forName(param.getName() + "$Method")});
			} catch (ClassNotFoundException e) { 
			    logDebug("Could not find CLR interface for: " + param);
			    args[i] = ((PhpProcedureProxy)args[i]).getProxy(param.getInterfaces());
			}
		    else
			args[i] = ((PhpProcedureProxy)args[i]).getProxy(param.getInterfaces());
		} else
		    args[i] = ((PhpProcedureProxy)args[i]).getProxy(new Class[] {param});
	    }
	    if((arg=args[i]) == null) continue;
	    
	    if(parms[i]==String.class) {
	    	if (arg instanceof PhpString)
		    result[i] = ((PhpString)arg).getString();
	    	else 
		    result[i] = arg.toString();
	    } else if (arg instanceof PhpString || arg instanceof String) {
	        if(!parms[i].isArray()) {
		Class c = parms[i];
		String s = (arg instanceof String) ? (String) arg : ((PhpString)arg).getString();
		result[i] = s;
		try {
		    if (c == Boolean.TYPE) result[i]=new Boolean(s);
		    if (c == Byte.TYPE)    result[i]=new Byte(s);
		    if (c == Short.TYPE)   result[i]=new Short(s);
		    if (c == Integer.TYPE) result[i]=new Integer(s);
		    if (c == Float.TYPE)   result[i]=new Float(s);
		    if (c == Long.TYPE)    result[i]=new Long(s);
		    if (c == Character.TYPE && s.length()>0)
			result[i]=new Character(s.charAt(0));
		} catch (NumberFormatException n) {
		    printStackTrace(n);
		    // oh well, we tried!
		}
	        } else {
	            result[i]=((PhpString)arg).getBytes();
	        }
	    } else if (arg instanceof Number) {
	    	if (parms[i].isPrimitive()) {
		    Class c = parms[i];
		    Number n = (Number)arg;
		    if (c == Boolean.TYPE) result[i]=new Boolean(0.0!=n.floatValue());
		    else if (c == Byte.TYPE)    result[i]=new Byte(n.byteValue());
		    else if (c == Short.TYPE)   result[i]=new Short(n.shortValue());
		    else if (c == Integer.TYPE) result[i]=new Integer(n.intValue());
		    else if (c == Float.TYPE)   result[i]=new Float(n.floatValue());
		    else if (c == Long.TYPE && !(n instanceof Long))
			result[i]=new Long(n.longValue());
	    	} else {
		    if(arg.getClass()==PhpExactNumber.class) {
	    		{
			    Class c = parms[i];
			    if(c.isAssignableFrom(Integer.class)) {
		    		result[i] = new Integer(((Number)arg).intValue());
			    } else {
		    		result[i] = new Long(((Number)arg).longValue());				
			    }
			}
		    }
	    	}
	    } else if (arg instanceof PhpArray) {
	    	if(parms[i].isArray()) {
	    	    Map.Entry e = null;
	    	    Object tempArray = null;
	    	    PhpArray ht = null;
	    	    Class targetType = parms[i].getComponentType();
		    try {
			ht = (PhpArray)arg;
			size = ht.arraySize();
			
			// flatten hash into an array
			targetType = parms[i].getComponentType();
			tempArray = Array.newInstance(targetType, size);
		    } catch (Exception ex) {
			//logError("Could not create array from Map: " + objectDebugDescription(arg) + ". Cause: " + ex);
			throw new JavaBridgeIllegalArgumentException("Could not create array from Map: " + firstChars(arg), ex);
		    }
		    try {
		        for (Iterator ii = ht.entrySet().iterator(); ii.hasNext(); ) {
			    e = (Entry) ii.next();
			    Array.set(tempArray, ((Number)(e.getKey())).intValue(), coerce(targetType, e.getValue(), response));
			}
			result[i]=tempArray;
		    } catch (Exception ex) {
			//logError("Could not create array of type: " + targetType + ", size: " + size + ", " + " failed entry at: " + e + ", from Map: " + objectDebugDescription(arg) + ". Cause: " + ex);
			throw new JavaBridgeIllegalArgumentException("Could not create array of type: " + targetType + ", size: " + size + ", " + " failed entry at: " + e, ex);
		    }
		} else if ((java.util.Collection.class).isAssignableFrom(parms[i])) {
		    try {
			Map m = (Map)arg;
			Collection c = m.values();
			if(!parms[i].isInstance(c))
			  try { // could be a concrete class, for example LinkedList.
			    Collection collection = (Collection) parms[i].newInstance();
			    collection.addAll(c);
			    c=collection;
			  } catch (Exception e) { // it was an interface, try some concrete class
			      try { c = new ArrayList(c); } catch (Exception ex) {/*we've tried*/}
			  }
			result[i]=c;
		    } catch (Exception ex) {
			//logError("Could not create Collection from Map: " +objectDebugDescription(arg) + ". Cause: " + ex);
			throw new JavaBridgeIllegalArgumentException("Could not create Collection from Map: " + firstChars(arg), ex);
		    }
		} else if ((java.util.Hashtable.class).isAssignableFrom(parms[i])) {
		    try {
			Map ht = (Map)arg;
			Hashtable res;
			res = (Hashtable)parms[i].newInstance();
			res.putAll(ht);

			result[i]=res;
		    } catch (Exception ex) {
			logError("Could not create Hashtable from Map: " +objectDebugDescription(arg) + ". Cause: " + ex);
			throw new JavaBridgeIllegalArgumentException("Could not create Hashtable from Map: " + firstChars(arg), ex);
		    }
		} else if ((java.util.Map.class).isAssignableFrom(parms[i])) {
		    result[i]=arg;
		} else if(arg instanceof PhpString) {
		    result[i] = ((PhpString)arg).getString(); // always prefer strings over byte[]
		} 
		}
	}
	return result;
    }

    static abstract class FindMatchingInterface {
	JavaBridge bridge;
	String name;
	Object args[];
	boolean ignoreCase;
	public FindMatchingInterface (JavaBridge bridge, String name, Object args[], boolean ignoreCase) {
	    this.bridge=bridge;
	    this.name=name;
	    this.args=args;
	    this.ignoreCase=ignoreCase;
	}
	abstract Class findMatchingInterface(Class jclass);
	public boolean checkAccessible(AccessibleObject o) {return true;}
    }


    static final FindMatchingInterfaceVoid MATCH_VOID_ICASE = new FindMatchingInterfaceVoid(true);
    static final FindMatchingInterfaceVoid MATCH_VOID_CASE = new FindMatchingInterfaceVoid(false);
    static class FindMatchingInterfaceVoid extends FindMatchingInterface {
	public FindMatchingInterfaceVoid(boolean b) { super(null, null, null, b); }
	Class findMatchingInterface(Class jclass) {
	    return jclass;
	}
	public boolean checkAccessible(AccessibleObject o) {
	    if(!o.isAccessible()) {
		try {
		    o.setAccessible(true);
		} catch (java.lang.SecurityException ex) {
		    return false;
		}
	    }
	    return true;
	}
    }

    static class FindMatchingInterfaceForInvoke extends FindMatchingInterface {
	protected FindMatchingInterfaceForInvoke(JavaBridge bridge, String name, Object args[], boolean ignoreCase) {
	    super(bridge, name, args, ignoreCase);
	}
	public static FindMatchingInterface getInstance(JavaBridge bridge, String name, Object args[], boolean ignoreCase, boolean canModifySecurityPermission) {
	    if(canModifySecurityPermission) return ignoreCase?MATCH_VOID_ICASE : MATCH_VOID_CASE;
	    else return new FindMatchingInterfaceForInvoke(bridge, name, args, ignoreCase);
	}
	Class findMatchingInterface(Class jclass) {
	    if(jclass==null) return jclass;
	    if(bridge.logLevel>3)
	    	if(bridge.logLevel>3)bridge.logDebug("searching for matching interface for Invoke for class " + jclass);
	    while (!Modifier.isPublic(jclass.getModifiers())) {
		// OK, some joker gave us an instance of a non-public class
		// This often occurs in the case of enumerators
		// Substitute the matching first public interface in its place,
		// and barring that, try the superclass
		Class interfaces[] = jclass.getInterfaces();
		Class superclass = jclass.getSuperclass();
		for (int i=interfaces.length; i-->0;) {
		    if (Modifier.isPublic(interfaces[i].getModifiers())) {
			jclass=interfaces[i];
			Method methods[] = jclass.getMethods();
			for (int j=0; j<methods.length; j++) {
			    String nm = methods[j].getName();
			    boolean eq = ignoreCase ? nm.equalsIgnoreCase(name) : nm.equals(name);
			    if (eq && (methods[j].getParameterTypes().length == args.length)) {
			    	if(bridge.logLevel>3) bridge.logDebug("matching interface for Invoke: " + jclass);
				return jclass;
			    }
			}
		    }
		}
		jclass = superclass;
	    }
	    if(bridge.logLevel>3) bridge.logDebug("interface for Invoke: " + jclass);
	    return jclass;
	}
    }

    static class FindMatchingInterfaceForGetSetProp extends FindMatchingInterface {
	protected FindMatchingInterfaceForGetSetProp(JavaBridge bridge, String name, Object args[], boolean ignoreCase) {
	    super(bridge, name, args, ignoreCase);
	}
	public static FindMatchingInterface getInstance(JavaBridge bridge, String name, Object args[], boolean ignoreCase, boolean canModifySecurityPermission) {
	    if(canModifySecurityPermission) return ignoreCase?MATCH_VOID_ICASE : MATCH_VOID_CASE;
	    else return new FindMatchingInterfaceForGetSetProp(bridge, name, args, ignoreCase);
	}

	Class findMatchingInterface(Class jclass) {
	    if(jclass==null) return jclass;
	    if(bridge.logLevel>3)
	    	if(bridge.logLevel>3)bridge.logDebug("searching for matching interface for GetSetProp for class "+ jclass);
	    while (!Modifier.isPublic(jclass.getModifiers())) {
		// OK, some joker gave us an instance of a non-public class
		// This often occurs in the case of enumerators
		// Substitute the matching first public interface in its place,
		// and barring that, try the superclass
		Class interfaces[] = jclass.getInterfaces();
		Class superclass = jclass.getSuperclass();
		for (int i=interfaces.length; i-->0;) {
		    if (Modifier.isPublic(interfaces[i].getModifiers())) {
			jclass=interfaces[i];
			Field jfields[] = jclass.getFields();
			for (int j=0; j<jfields.length; j++) {
			    String nm = jfields[j].getName();
			    boolean eq = ignoreCase ? nm.equalsIgnoreCase(name) : nm.equals(name);
			    if (eq) {
			    	if(bridge.logLevel>3) bridge.logDebug("matching interface for GetSetProp: "+ jclass);
				return jclass;
			    }
			}
		    }
		}
		jclass = superclass;
	    }
	    if(bridge.logLevel>3) bridge.logDebug("interface for GetSetProp: "+ jclass);
	    return jclass;
	}
    }
    private static ClassIterator getClassClassIterator(Class clazz) {
	if(clazz==Class.class) return new MetaClassIterator();
	return new ClassClassIterator();
    }
    private static abstract class ClassIterator {
	Object object;
	Class current;
	FindMatchingInterface match;

	public static ClassIterator getInstance(Object object, FindMatchingInterface match) {
	    ClassIterator c;
	    if(object instanceof Class)
		c = getClassClassIterator((Class)object);
	    else
		c = new ObjectClassIterator();

	    c.match = match;
	    c.object = object;
	    c.current = null;
	    return c;
	}

	public abstract Class getNext();
	public abstract boolean checkAccessible(AccessibleObject o);
	public abstract boolean isVisible(int modifier);
    }

    static class ObjectClassIterator extends ClassIterator {
	private Class next() {
	    if (current == null) return current = object.getClass();
	    return null;
	}
	public Class getNext() {
	    return match.findMatchingInterface(next());
	}
	public boolean checkAccessible(AccessibleObject o) {
	    return match.checkAccessible(o);
	}
	public boolean isVisible(int modifier) { return true; }
    }

    static class ClassClassIterator extends ClassIterator {
        boolean hasNext=false;
	private Class next() {
	    // check the class first, then the class class.
	    if(current == null) { hasNext = true; return current = (Class)object;}
	    if(hasNext) { hasNext = false; return object.getClass();}
	    return null;
	}
	public Class getNext() {
	    return next();
	}
	public boolean checkAccessible(AccessibleObject o) {
	    return true;
	}
	public boolean isVisible(int modifier) {
	    // all members of the class class or only static members of the class
	    return !hasNext || ((modifier&Modifier.STATIC)!=0);  
	}
    }
    static class MetaClassIterator extends ClassIterator {
 	private Class next() {
	    // The ClassClass has the ClassClass as its class
	    if(current == null) { return current = (Class)object;}
	    return null;
	}
	public Class getNext() {
	    return next();
	}
	public boolean checkAccessible(AccessibleObject o) {
	    return true;
	}
	public boolean isVisible(int modifier) {
	    return true;
	}
    }

    public static void logInvoke(Object obj, String method, Object args[]) {
	String dmsg = "\nInvoking "+objectDebugDescription(obj)+"."+method+"(";
	for (int t =0;t<args.length;t++) {
	    if (t>0) dmsg +=",";
	    dmsg += objectDebugDescription(args[t]);

	}
	dmsg += ");\n";
	Util.logDebug(dmsg);
  }
    public static void logResult(Object obj) {
	String dmsg = "\nResult "+objectDebugDescription(obj) + "\n";
	Util.logDebug(dmsg);
  }

    /**
     * Invoke a method on a given object
     */
    public void Invoke
	(Object object, String method, Object args[], Response response)
    {
	Class jclass;
	boolean again;
	Object coercedArgs[] = null;
	Class params[] = null;
	LinkedList candidates = new LinkedList();
	LinkedList matches = new LinkedList();
	Method selected = null;
	try {
	    if(object==null) {object = Request.PHPNULL;throw new NullPointerException("call object is null, check the server log file(s).");}
	    /* PR1616498: Do not use Util.getClass(): if object is a class, we must pass the class class.  
	     * All VM, including gcc >= 3.3.3, return the class class for class.getClass(), not null. This is okay for the cache implementation. */
	    MethodCache.Entry entry = methodCache.getEntry(method, object, args);
	    selected = (Method) methodCache.get(entry);
	    
	    // gather
	    do {
		again = false;
		ClassIterator iter;
		if (selected==null) {
		    for (iter = ClassIterator.getInstance(object, FindMatchingInterfaceForInvoke.getInstance(this, method, args, true, canModifySecurityPermission)); (jclass=iter.getNext())!=null;) {
			Method methods[] = jclass.getMethods();
			for (int i=0; i<methods.length; i++) {
			    Method meth = methods[i];
			    if (meth.getName().equalsIgnoreCase(method)&&iter.isVisible(meth.getModifiers())) {
				candidates.add(meth);
				if(meth.getParameterTypes().length == args.length) {
				    matches.add(meth);
				}
			    }
			}
		    }
		    selected = (Method)select(matches, args);
		    if (selected == null) 
                  	throw new NoSuchMethodException(String.valueOf(method) + "(" + Util.argsToString(args, params) + "). " + "Candidates: " + String.valueOf(candidates));
		    methodCache.put(entry, selected);
		    if(!iter.checkAccessible(selected)) {
			logDebug("Security restriction: Cannot use setAccessible(), reverting to interface searching.");
			canModifySecurityPermission=false;
			candidates.clear(); matches.clear();
			again=true;
		    }
		}
		coercedArgs = coerce(params=entry.getParameterTypes(selected), args, response);
	    } while(again);
	    // If we have a logLevel of 5 or above, do very detailed invocation logging
	    if (this.logLevel>4) {
	        logInvoke(object, method, coercedArgs); 
	        Object result = selected.invoke(object, coercedArgs);
	        logResult(result);
	        setResult(response, result, selected.getReturnType());
	    } else {
	        setResult(response, selected.invoke(object, coercedArgs), selected.getReturnType());	      
	    }
	} catch (Throwable e) {
	    if(e instanceof InvocationTargetException) e = ((InvocationTargetException)e).getTargetException();
	    if(e instanceof Request.AbortException) {throw (Request.AbortException)e;}
	    if(e instanceof OutOfMemoryError) {
		Util.logFatal("OutOfMemoryError");
		throw (OutOfMemoryError)e; // abort
	    }
	    if(e instanceof NoClassDefFoundError) {
		getClassLoader().clearCaches();
		e = getUnresolvedExternalReferenceException(e, "call the method");
	    }
	    
            if (selected != null && e instanceof IllegalArgumentException) {
                if (this.logLevel>1) {
                    String errMsg = "\nInvoked "+method + " on "+objectDebugDescription(object)+"\n";
                    errMsg += " Expected Arguments for this Method:\n";
                    Class paramTypes[] = selected.getParameterTypes();
                    for (int k=0;k<paramTypes.length;k++) {
                        errMsg += "   ("+k+") "+classDebugDescription(paramTypes[k])+"\n";
                    }
                    errMsg +=" Plain Arguments for this Method:\n";
                    for (int k=0;k<args.length;k++) {
                        errMsg += "   ("+k+") "+objectDebugDescription(args[k])+"\n";
                    }
                    if (coercedArgs!=null) {
			errMsg +=" Coerced Arguments for this Method:\n";
			for (int k=0;k<coercedArgs.length;k++) {
			    errMsg += "   ("+k+") "+objectDebugDescription(coercedArgs[k])+"\n";
			}
                    }
                    this.logDebug(errMsg);
                }
            }
	    setException(response, e, "Invoke", object, method, args, params);
	}
    }

    static private final int DISPLAY_MAX_ELEMENTS = 10;
    static private final int DISPLAY_MAX_CHARS = 80;
    private static String firstChars(Object o) {
        String append="";
        String s = o instanceof java.lang.reflect.Proxy? o.getClass().getName(): String.valueOf(o);
        int len = s.length();
        if(len>DISPLAY_MAX_CHARS) {append="..."; len=DISPLAY_MAX_CHARS;}
        s = s.substring(0,len)+append;
        return s;
    }
    /**
     * Only for internal use
     * @param ob The object
     * @return A debug description.
     */
    public static String objectDebugDescription(Object ob) {
        return objectDebugDescription(ob, 0);
    }
    /**
     * Only for internal use
     * @param obj The object
     * @return A debug description.
     */
    private static String objectDebugDescription(Object ob, int level) {
    	if(ob==null) return "[Object null]";
    	Object obj = ob;
    	if (obj instanceof Collection) obj = ((Collection)obj).toArray();
    	else if(obj instanceof List) obj = ((List)obj).toArray();
    	else if(obj instanceof Map) obj = ((Map)obj).values().toArray();
    	if (level<DISPLAY_MAX_ELEMENTS && obj.getClass().isArray()) {
    	    StringBuffer buf = new StringBuffer("[Object "+System.identityHashCode(ob)+" - Class: "+ classDebugDescription(ob.getClass())+ "]: ");
    	    buf.append("{\n");
    	    int length = Array.getLength(obj);
    	    for(int i=0; i<length; i++) {
    	        buf.append(objectDebugDescription(Array.get(obj, i), level+1));
    	        if(i>=DISPLAY_MAX_ELEMENTS) { buf.append("...\n"); break; }
    	        buf.append("\n");
    	    }
    	    buf.append("}");
    	    return buf.toString();
    	} else {
    	    return "[Object "+System.identityHashCode(obj)+" - Class: "+ classDebugDescription(obj.getClass())+"]";
    	}
    }

    /**
     * Only for internal use
     * @param cls The class
     * @return A debug description.
     */
    public static String classDebugDescription(Class cls) {
	return cls.getName() + ":ID" + System.identityHashCode(cls) + ":LOADER-ID"+System.identityHashCode(cls.getClassLoader());
    }

    /**
     * Get or Set a property
     */
    public void GetSetProp
	(Object object, String prop, Object args[], Response response)
    {
    	LinkedList matches = new LinkedList();
	boolean set = (args!=null && args.length>0);
	Class params[] = null;

	try {
	    Class jclass;
	    if(object==null) {object=Request.PHPNULL; throw new NullPointerException("call object is null, check the server log file(s).");}
	    
	    // first search for the field *exactly*
	    again2:		// because of security exception
	    for (ClassIterator iter = ClassIterator.getInstance(object, FindMatchingInterfaceForGetSetProp.getInstance(this, prop, args, false, canModifySecurityPermission)); (jclass=iter.getNext())!=null;) {
		try {
		    Field jfields[] = jclass.getFields();
		    for (int i=0; i<jfields.length; i++) {
			Field fld = jfields[i];
			if (fld.getName().equals(prop)&&iter.isVisible(fld.getModifiers())) {
			    matches.add(fld.getName());
			    Object res=null;
			    if(!(iter.checkAccessible(fld))) {
				logDebug("Security restriction: Cannot use setAccessible(), reverting to interface searching.");
				canModifySecurityPermission=false;
			        matches.clear();
				break again2;
			    }
			    Class ctype = fld.getType();
			    if (set) {
				args = coerce(params=new Class[] {ctype}, args, response);
				fld.set(object, args[0]);
			    } else {
			    	res=fld.get(object);
			    }
			    setResult(response, res, ctype);
			    return;
			}
		    }
		} catch (Exception ee) {/* may happen when field is not static */}
	    }
	    matches.clear();

	    // search for a getter/setter, ignore case
	    again1:		// because of security exception
	    for (ClassIterator iter = ClassIterator.getInstance(object, FindMatchingInterfaceForInvoke.getInstance(this, prop, args, true, canModifySecurityPermission)); (jclass=iter.getNext())!=null;) {
		try {
		    BeanInfo beanInfo = Introspector.getBeanInfo(jclass);
		    PropertyDescriptor props[] = beanInfo.getPropertyDescriptors();
		    for (int i=0; i<props.length; i++) {
			if (props[i].getName().equalsIgnoreCase(prop)) {
			    Method method;
			    if (set) {
				method=props[i].getWriteMethod();
				args = coerce(params=method.getParameterTypes(), args, response);
			    } else {
				method=props[i].getReadMethod();
			    }
			    matches.add(method);
			    if(!iter.checkAccessible(method)) {
				logDebug("Security restriction: Cannot use setAccessible(), reverting to interface searching.");
				canModifySecurityPermission=false;
				matches.clear();
				break again1;
			    }
			    setResult(response, method.invoke(object, args), method.getReturnType());
			    return;
			}
		    }
		} catch (Exception ee) {/* may happen when method is not static */}
	    }
	    matches.clear();

	    // search for the field, ignore case
	    again0:		// because of security exception
	    for (ClassIterator iter = ClassIterator.getInstance(object, FindMatchingInterfaceForGetSetProp.getInstance(this, prop, args, true, canModifySecurityPermission)); (jclass=iter.getNext())!=null;) {
		try {
		    Field jfields[] = jclass.getFields();
		    for (int i=0; i<jfields.length; i++) {
			Field fld = jfields[i];
			if (fld.getName().equalsIgnoreCase(prop)&&iter.isVisible(fld.getModifiers())) {
			    matches.add(prop);
			    Object res=null;
			    if(!(iter.checkAccessible(fld))) {
				logDebug("Security restriction: Cannot use setAccessible(), reverting to interface searching.");
				canModifySecurityPermission=false;
				matches.clear();
				break again0;
			    }
			    Class ctype = fld.getType();
			    if (set) {
				args = coerce(params=new Class[] {ctype}, args, response);
				fld.set(object, args[0]);
			    } else {
				res = fld.get(object);
			    }
			    setResult(response, res, ctype);
			    return;
			}
		    }
		} catch (Exception ee) {/* may happen when field is not static */}
	    }
	    throw new NoSuchFieldException(String.valueOf(prop) + " (with args:" + Util.argsToString(args, params) + "). " + "Candidates: " + String.valueOf(matches));

	} catch (Throwable e) {
	    if(e instanceof InvocationTargetException) e = ((InvocationTargetException)e).getTargetException();
	    if(e instanceof Request.AbortException) {throw (Request.AbortException)e;}
	    if(e instanceof OutOfMemoryError) {
		Util.logFatal("OutOfMemoryError");
		throw (OutOfMemoryError)e; // abort
	    }
	    if(e instanceof NoClassDefFoundError) {
		getClassLoader().clearCaches();
		e = getUnresolvedExternalReferenceException(e, "invoke a property");
	    }
	    setException(response, e, set?"SetProperty":"GetProperty", object, prop, args, params);
	}
    }


    /**
     * Convert Map or Collection into a PHP array,
     * sends the entire array, Map or Collection to the client. This
     * is much more efficient than generating round-trips while
     * iterating over the values of an array, Map or Collection.
     * @param ob - The object to expand
     * @return The passed <code>ob</code>, will be expanded by the appropriate writer.
     */
    public Object getValues(Object ob) {
    	Response res = request.response;
    	res.setArrayValuesWriter();
	return ob;
    }
 
    /**
     * Cast a object to a type
     * @param ob - The object to cast
     * @param type - The target type
     * @return The passed <code>ob</code>, will be coerced by the appropriate writer.
     */
    public Object cast(Object ob, Class type) {
	Response res = request.response;
	res.setCoerceWriter().setType(type);
    	return ob;
    }
 
    /**
     * Cast an object to a string
     * @param ob - The object to cast
     * @return The passed <code>ob</code>, will be coerced by the appropriate writer.
     */
    public Object castToString(Object ob) {
	return cast(ob, String.class);
    }

    /**
     * Cast a throwable to a string
     * @param throwable The throwable to cast
     * @param trace The PHP stack trace 
     * @return The result String object, will be coerced by the appropriate writer.
     */
    public Object castToString(Exception throwable, String trace) {
	StringBuffer buf = new StringBuffer();
	Util.appendObject(throwable, buf);
	Util.appendTrace(throwable, trace, buf);
	return castToString(buf);
    }
            
    /**
     * Cast an object to an exact number
     * @param ob - The object to cast
     * @return The passed <code>ob</code>, will be coerced by the appropriate writer.
     */
    public Object castToExact(Object ob) {
	return cast(ob, Long.TYPE);
    }
    /**
     * Cast an object to a boolean value
     * @param ob - The object to cast
     * @return The passed <code>ob</code>, will be coerced by the appropriate writer.
     */
    public Object castToBoolean(Object ob) {
	return cast(ob, Boolean.TYPE);
    }
    /**
     * Cast an object to a inexact value
     * @param ob - The object to cast
     * @return The passed <code>ob</code>, will be coerced by the appropriate writer.
     */
    public Object castToInexact(Object ob) {
	return cast(ob, Double.TYPE);
    }
    /**
     * Cast an object to an array
     * @param ob - The object to cast
     * @return The passed <code>ob</code>, will be coerced by the appropriate writer.
     */
    public Object castToArray(Object ob) {
	Response res = request.response;
	res.setArrayValueWriter();
    	return ob;
    }

    /**
     * Only for internal use. 
     * @param in the InputStream
     * @param out the OutputStream
     * @see php.java.bridge.JavaBridgeRunner
     * @see php.java.bridge.JavaBridge#main(String[])
    */
    protected JavaBridge(InputStream in, OutputStream out) {
	this.in = in;
	this.out = out;
	this.setClassLoader(this.getSessionFactory().getJavaBridgeClassLoader());
    }
 
    /**
     * Only for internal use.
     * @see php.java.bridge.JavaBridge#main(String[])
     * @see php.java.bridge.JavaBridgeRunner
     */
    protected JavaBridge() {
	this(null, null);
    }
    
    public JavaBridge(SimpleJavaBridgeClassLoader loader) {
	setClassLoader(loader);
    }
    /**
     * Return map for the value (PHP 5 only)
     * @param value - The value which must be an array or implement Map or Collection.
     * @return The PHP map.
     * @see php.java.bridge.PhpMap
     */
    public PhpMap getPhpMap(Object value) {
	return PhpMap.getPhpMap(value, this);
    }

    /**
     * @deprecated
     * @see #updateJarLibraryPath(String, String)
     */
    public void setJarLibraryPath(String path, String extensionDir) throws IOException {
        updateJarLibraryPath(path, extensionDir);
    }
    /**
     * Append the path to the current library path<br>
     * Examples:<br>
     * setJarLibPath(";file:///tmp/test.jar;file:///tmp/my.jar");<br>
     * setJarLibPath("|file:c:/t.jar|http://.../a.jar|jar:file:///tmp/x.jar!/");<br>
     * @param path A file or url list, usually separated by ';'
     * @param extensionDir The php extension directory. 
     * @throws IOException 
     */
    public void updateJarLibraryPath(String path, String extensionDir) throws IOException {
    	getClassLoader().updateJarLibraryPath(path, extensionDir.intern());
    }

    /**
     * @deprecated
     * @see #updateLibraryPath(String, String)
     */
    public void setLibraryPath(String rawPath, String extensionDir) {
        updateLibraryPath(rawPath, extensionDir);
    }
    /**
     * Update the library path for ECMA dll's
     * @param rawPath A file or url list, usually separated by ';'
     * @param extensionDir The php extension directory. 
     */
    public void updateLibraryPath(String rawPath, String extensionDir) {
        if(rawPath==null || rawPath.length()<2) return;
        String contextDir = new File(extensionDir, "lib").getAbsolutePath();
        // add a token separator if first char is alnum
	char c=rawPath.charAt(0);
	if((c>='A' && c<='Z') || (c>='a' && c<='z') ||
	   (c>='0' && c<='9') || (c!='.' || c!='/'))
	    rawPath = ";" + rawPath;

	String path = rawPath.substring(1);
	StringTokenizer st = new StringTokenizer(path, rawPath.substring(0, 1));
	while (st.hasMoreTokens()) {
	    String s = st.nextToken();
	    try {
		File f=null;
		StringBuffer buf= new StringBuffer();
		if((f=new File(s)).isFile() || f.isAbsolute()) {
		    buf.append(s);
		    Util.loadFileMethod.invoke(Util.CLRAssembly, new Object[] {buf.toString()} );
		} else if ((f=new File(contextDir, s)).isFile()) {
		    buf.append(f.getAbsolutePath());
		    Util.loadFileMethod.invoke(Util.CLRAssembly, new Object[] {buf.toString()} );
		} else {
		    buf.append(s);
		    Util.loadMethod.invoke(Util.CLRAssembly, new Object[] {buf.toString()} );
		}
	    }  catch (Exception e1) {
		logError("Could not load cli."+ s +".");
	    }
	}
    }
    /**
     * For internal use only.
     * @param object The java object
     * @return A list of all visible constructors, methods, fields and inner classes.
     */
    public String inspect(Object object) {
        Class jclass;
    	ClassIterator iter;
	StringBuffer buf = new StringBuffer("[");
	buf.append(String.valueOf(Util.getClass(object)));
	buf.append(":\nConstructors:\n");
        for (iter = ClassIterator.getInstance(object, MATCH_VOID_CASE); (jclass=iter.getNext())!=null;) {
	    Constructor[] constructors = jclass.getConstructors();
	    for(int i=0; i<constructors.length; i++) { buf.append(String.valueOf(constructors[i])); buf.append("\n"); }
        }
        buf.append("\nFields:\n");
        for (iter = ClassIterator.getInstance(object, MATCH_VOID_CASE); (jclass=iter.getNext())!=null;) {
	    Field jfields[] = jclass.getFields();
	    for(int i=0; i<jfields.length; i++) { buf.append(String.valueOf(jfields[i])); buf.append("\n"); }
        }
        buf.append("\nMethods:\n");
        for (iter = ClassIterator.getInstance(object, MATCH_VOID_CASE); (jclass=iter.getNext())!=null;) {
	    Method jmethods[] = jclass.getMethods();
	    for(int i=0; i<jmethods.length; i++) { buf.append(String.valueOf(jmethods[i])); buf.append("\n"); }
        }
        buf.append("\nClasses:\n");
        for (iter = ClassIterator.getInstance(object, MATCH_VOID_CASE); (jclass=iter.getNext())!=null;) {
	    Class jclasses[] = jclass.getClasses();
	    for(int i=0; i<jclasses.length; i++) { buf.append(String.valueOf(jclasses[i].getName())); buf.append("\n"); }
        }
	buf.append("]");
	return (String)castToString(buf.toString());
    }
    /**
     * Set a new file encoding, used to code and decode strings.
     * Example: setFileEncoding("UTF-8")
     * @param fileEncoding The file encoding.
     */
    public void setFileEncoding(String fileEncoding) {
	options.setEncoding(fileEncoding.intern());
    }

    /**
     * Check if object is an instance of class.
     * @param ob The object
     * @param claz The class or an instance of a class
     * @return true if ob is an instance of class, false otherwise.
     */
    public static boolean InstanceOf(Object ob, Object claz) {
	Class clazz = Util.getClass(claz);
	return clazz.isInstance(ob);
    }

    /**
     * Returns a string representation of the object
     * @param ob The object
     * @return A string representation.
     */
    public String ObjectToString(Object ob) {
	    StringBuffer buf = new StringBuffer("[");
	try {
	    Util.appendObject(ob, buf);
	} catch (Request.AbortException sub) {
	    throw sub;
	} catch (Exception t) {
		    Util.printStackTrace(t);
		    buf.append("[Exception in toString(): ");
		    buf.append(t);
		    if(t.getCause()!=null) {
		      buf.append(", Cause: ");
		      buf.append(t.getCause());
		    }
		    buf.append("]");
		}
	    buf.append("]");
	    return (String)castToString(buf.toString());
    }

    /**
     * Returns a string representation of the object
     * @param ob The object
     * @return A string representation.
     */
    public String ObjectToString(String ob) {
	    return (String)castToString(ob);
    }

    /**
     * Returns a string representation of the object
     * @param ob The Throwable
     * @param trace The stack trace
     * @return A string representation.
     */
    public String ObjectToString(Throwable ob, String trace) {
	    StringBuffer buf = new StringBuffer("[");
	try {
	    Util.appendObject(ob, buf);
	    Util.appendTrace(ob, trace, buf);
	} catch (Request.AbortException sub) {
	    throw sub;
	} catch (Exception t) {
		    Util.printStackTrace(t);
		    buf.append("[Exception in toString(): ");
		    buf.append(t);
		    if(t.getCause()!=null) {
		      buf.append(", Cause: ");
		      buf.append(t.getCause());
		    }
		    buf.append("]");
		}
	    buf.append("]");
	    return (String)castToString(buf.toString());
    }
    
    private Object contextCache = null;
    /**
     * Returns the JSR223 context. 
     * @return The JSR223 context.
     */
    public Object getContext() {
	if(contextCache!=null) return contextCache;
    	return contextCache = sessionFactory.getContext();
    }
    /**
     * Returns the JSR223 context when using persistent connections.
     * @return The JSR223 context.
     * @param id The fresh context id, as specified by the jsr223 client, will be aliased to the current id
     * @return The JSR223 context.
     * @see #recycle()
     * @see #getContext()
     */
    public Object getContext(String id) {
	recycleContext(id);
    	return getContext();
    }
    private ISession sessionCache = null;
    /**
     * Return a session handle shared among all JavaBridge
     * instances. If it is a HTTP session, the session is shared with
     * the servlet or jsp.
     * @param name The session name, if any
     * @param clientIsNew true, if the client wants a new session
     * @param timeout session timeout in seconds
     * @return The session context.
     * @throws Exception 
     * @see php.java.bridge.ISession
     */
    public ISession getSession(String name, boolean clientIsNew, int timeout) throws Exception {
	if(sessionCache!=null) return sessionCache;
	try {
	ISession session= sessionFactory.getSession(name, clientIsNew, timeout);
	if(session==null) throw new NullPointerException("session is null");
	return sessionCache = session;
	} catch (Exception t) {
	  printStackTrace(t);
	  throw t;
	}
    }
    /**
     * Return a session handle when using persistent connections.
     * @throws Exception 
     * @param id The fresh context id, as specified by the jsr223 client, will be aliased to the current id
     * @param name The session name, if any
     * @param clientIsNew true, if the client wants a new session
     * @param timeout session timeout in seconds
     * @return The session context.
     * @see #recycle()
     * @see #getSession(String, boolean, int)
     */
    public ISession getSession(String id, String name, boolean clientIsNew, int timeout) throws Exception {
	recycleContext(id);
	return getSession(name, clientIsNew, timeout);
    }
    
    /**
     * Create a dynamic proxy proxy for calling PHP code.<br>
     * Example: <br>
     * java_closure($this, $map);<br>
     * 
     * @param object the PHP environment (the php "class")
     * @param names maps java to php names
     * @return the proxy
     */
    public Object makeClosure(long object, Map names) {
	if(names==null) return makeClosure(object);
    	return new PhpProcedureProxy(this, names, null, object);
    }
    /**
     * Create a dynamic proxy proxy for calling PHP code.<br>
     * Example: <br>
     * java_closure($this, $map, $interfaces);<br>
     * 
     * @param object the PHP environment (the php "class")
     * @param names maps java to php names
     * @param interfaces list of interfaces which the PHP environment must implement
     * @return the proxy
     */
    public Object makeClosure(long object, Map names, Class interfaces[]) {
	if(names==null) names=emptyMap;
    	return new PhpProcedureProxy(this, names, interfaces, object);
    }
    /**
     * Create a dynamic proxy proxy for calling PHP code.<br>
     * Example: <br>
     * java_closure($this, $map, $interfaces);<br>
     * 
     * @param object the PHP environment (the php "class")
     * @param names maps java to php names
     * @param iface interface which the PHP environment must implement
     * @return the proxy
     */
    public Object makeClosure(long object, Map names, Class iface) {
	Class[] interfaces = iface==null ? null : new Class[] {iface};
	return makeClosure(object, names, interfaces);
    }
    /**
     * Create a dynamic proxy proxy for calling PHP code.<br>
     * Example: <br>
     * java_closure($this, "clickMe");<br>
     * 
     * @param object  the PHP environment (the php "class")
     * @param name maps all java names to this php name
     * @return the proxy
     */
    public Object makeClosure(long object, String name) {
	if(name==null) return makeClosure(object);
    	return new PhpProcedureProxy(this, name, null, object);
    }
    /**
     * Create a dynamic proxy proxy for calling PHP code.<br>
     * Example: <br>
     * java_closure($this, "clickMe", $interfaces);<br>
     * 
     * @param object the PHP environment (the php "class")
     * @param name maps all java names to this php name
     * @param interfaces  list of interfaces which the PHP environment must implement
     * @return the proxy
     */
    public Object makeClosure(long object, String name, Class interfaces[]) {
	if(name==null) return makeClosure(object, emptyMap, interfaces);
    	return new PhpProcedureProxy(this, name, interfaces, object);
    }
    private static final HashMap emptyMap = new HashMap();

    /**
     * Create a dynamic proxy proxy for calling PHP code.<br>
     * Example: <br>
     * java_closure();<br>
     * java_closure($this);<br>
     * 
     * @param object the PHP environment (the php "class")
     * @return the proxy
     */
    public Object makeClosure(long object) {
    	return new PhpProcedureProxy(this, emptyMap, null, object);
    }

    /**
     * Reset the global caches of the bridge.  Currently this is the
     * classloader. This is a no-op when the backend
     * is running in a servlet engine or application server.
     * @see php.java.bridge.Session#reset()
     */
    public void reset() {
	if(logLevel>3) warn("Your PHP script has called the privileged procedure \"reset()\", which resets the backend to its initial state. Therefore all session variables and all caches are now gone.");
	getClassLoader().reset();
    }
    /**
     * This method sets a new session factory. Used by the servlet to
     * implement session sharing.
     * @param sessionFactory The sessionFactory to set.
     */
    public void setSessionFactory(SessionFactory sessionFactory) {
        this.sessionFactory = sessionFactory;
    }

    protected static final String PHPSESSION = "PHPSESSION";
    protected static final String INTERNAL_PHPSESSION = "INTERNAL_PHPSESSION";

    /**
     * Load the object from the session store.
     * The C code requires that this method is called "deserialize" even though it doesn't deserialize anything.
     * See the JSessionAdapter in the php_java_lib folder. For real serialization/deserialization see the JPersistenceAdapter in the php_java_lib folder.
     * @param serialID The key
     * @param timeout The timeout, usually 1400 seconds.
     * @return the new object identity.
     * @throws IllegalArgumentException if serialID does not exist anymore.
     */
    public int deserialize(String serialID, int timeout) {
	ISession session = defaultSessionFactory.getSessionInternal(false, timeout);
	Object obj = session.get(serialID);
	if(obj==null) throw new IllegalArgumentException("Session serialID " +  serialID + " expired.");
	return globalRef.append(obj);
    }
    private static int counter=0;
    private static synchronized int getSerialID() {
    	return counter++;
    }
    /**
     * Store the object in the session store and return the serial id.
     * The C code requires that this method is called "serialize" even though it doesn't serialize anything.
     * See the JSessionAdapter in the php_java_lib folder. For real serialization/deserialization see the JPersistenceAdapter in the php_java_lib folder.
     * @param obj The object
     * @param timeout The timeout, usually 1400 seconds
     * @return the serialID
     */
    public String serialize(Object obj, int timeout) {
    	ISession session = defaultSessionFactory.getSessionInternal(false, timeout);
    	String id = Integer.toHexString(getSerialID());
    	session.put(id, obj);
    	return (String)castToString(id);
    }
    /**
     * Set a new ClassLoader
     * @param cl The ClassLoader
     * 
     */
    public void setClassLoader(SimpleJavaBridgeClassLoader cl) {
	this.cl = cl;
    }
    /**
     * Return the current ClassLoader
     * @return The ClassLoader.
     */
    public SimpleJavaBridgeClassLoader getClassLoader() {
	return cl;
    }
    /**
     * Checks if a given position exists.
     * @param value The map.
     * @param pos The position
     * @return true if an element exists at this position, false otherwise.
     */
    private boolean offsetExists(Map value, Object pos) {
	return value.containsKey(pos);
    }
    /**
     * Returns the object at the posisition.
     * @param value The map.
     * @param pos The position.
     * @return The object at the given position.
     */    
    private Object offsetGet(Map value, Object pos) {
	return value.get(pos);
    }
    /**
     * Set an object at position. 
     * @param value The map.
     * @param pos The position.
     * @param val The object.
     */
    private void offsetSet(Map value, Object pos, Object val) {
        Class type = value.getClass().getComponentType();
        if(type!=null) val = coerce(type, val, request.response);
	value.put(pos, val);
    }
    /**
     * Remove an object from the position.
     * @param value The map.
     * @param pos The position.
      */
    private void offsetUnset(Map value, Object pos) {
	offsetSet(value, pos, null);
    }
    /**
     * Checks if a given position exists.
     * @param value The list.
     * @param pos The position
     * @return true if an element exists at this position, false otherwise.
     */
    private boolean offsetExists(List value, int pos) {
	try {
	    offsetGet(value, pos);
	    return true;
	} catch (IndexOutOfBoundsException ex) {
	    return false;
	}
    }
    /**
     * Returns the object at the posisition.
     * @param value The list.
     * @param pos The position.
     * @return The object at the given position.
     */    
    private Object offsetGet(List value, int pos) {
	return value.get(pos);
    }
    /**
     * Set an object at position. 
     * @param value The list.
     * @param pos The position.
     * @param val The object.
     */
    private void offsetSet(List value, int pos, Object val) {
	value.set(pos, val);
    }
    /**
     * Remove an object from the position.
     * @param value The list.
     * @param pos The position.
      */
    private void offsetUnset(List value, int pos) {
	offsetSet(value, pos, null);
    }
    boolean offsetExists(int length, int pos) {
	int i = pos;
	return (i>0 && i<length);
    }
    /**
     * Checks if a given position exists.
     * @param value The array.
     * @param pos The position
     * @return true if an element exists at this position, false otherwise.
     */
    private boolean offsetExists(Object value, int pos) {
        return offsetExists(Array.getLength(value), pos);
    }
    
    /**
     * Checks if a given position exists.
     * @param table The table.
     * @param off The offset
     * @return true if an element exists at this position, false otherwise.
     */
    public boolean offsetExists(Object table, Object off) {
	if(table.getClass().isArray()) return offsetExists(table, ((Number)off).intValue());
	if(table instanceof List) return offsetExists((List)table, ((Number)off).intValue());
	return offsetExists((Map)table, off);
    }
    /**
     * Returns the object at the posisition.
     * @param value The array.
     * @param pos The position.
     * @return The object at the given position.
     */    
    private Object offsetGet(Object value, int pos) {
	int i = pos;
	Object o = Array.get(value, i);
	return o==this ? null : o;
   }
    /**
     * Returns the object at the posisition.
     * @param table The table.
     * @param off The offset.
     * @return The object at the given position.
     */    
    public Object offsetGet(Object table, Object off) {
	if(table.getClass().isArray()) return offsetGet(table, ((Number)off).intValue());
	if(table instanceof List) return offsetGet((List)table, ((Number)off).intValue());
	return offsetGet((Map)table, off);	
    }
    /**
     * Selects the asynchronuous protocol mode.
     */
    public void beginDocument() {
	Response res = request.response;
	res.setAsyncWriter();
	lastAsyncException = null;
    }
    /**
     * Back to synchronuous protocol mode
     */
    public void endDocument() throws Throwable {
	Response res = request.response;
	res.setDefaultWriter();
	if(lastAsyncException!=null) throw lastAsyncException;
    }

    /**
     * Set an object at position. 
     * @param value The array.
     * @param pos The position.
     * @param val The object.
     */
    private void offsetSet(Object value, int pos, Object val) {
	Array.set(value, pos, coerce(value.getClass().getComponentType(), val, request.response));
    }
    /**
     * Set an object at position. 
     * @param table The table
     * @param off The offset.
     * @param val The value
     */
    public void offsetSet(Object table, Object off, Object val) {
	if(table.getClass().isArray()) offsetSet(table, ((Number)off).intValue(), val);
	else if(table instanceof List) offsetSet((List)table, ((Number)off).intValue(), val);
	else offsetSet((Map)table, off, val);	
    }
    /**
     * Remove an object from the position.
     * @param value The array.
     * @param pos The position.
      */
    private void offsetUnset(Object value, int pos) {
	int i = pos;
	Array.set(value, i, null);
    }
    /**
     * Remove an object from the position.
     * @param table The table.
     * @param off The offset.
      */
    public void offsetUnset(Object table, Object off) {
	if(table.getClass().isArray()) offsetUnset(table, ((Number)off).intValue());
	else if(table instanceof List) offsetUnset((List)table, ((Number)off).intValue());
	else offsetUnset((Map)table, off);	
    }
    /** 
     * Re-initialize the current bridge for keep-alive
     * See php.ini option <code>java.persistent_connections</code>
     */
    public void recycle() {
	this.contextCache = null;
	this.sessionCache = null;
        globalRef = new GlobalRef();
        lastException = lastAsyncException = null;
	cl.recycle();
        options.recycle();
        request.recycle();
        sessionFactory.recycle();
        
        // TODO: recycle common entries such as bridge.require(), etc.
        methodCache.clear();
        constructorCache.clear();
        stringCache.clear();
    }
    private void recycleContext(String id) {
   }
    
    /**
     * Return a new string using the current file encoding (see java_set_file_encoding()).
     * @param b The byte array
     * @param start The start index
     * @param length The number of bytes to encode.
     * @return The encoded string.
     */    
    public String getString(byte[] b, int start, int length) {
//      return stringCache.getString(b, start, length, options.getEncoding());
      try {
	    return new String(b, start, length, options.getEncoding());
	} catch (UnsupportedEncodingException e) {
	    printStackTrace(e);
	    return new String(b, start, length);
	}
    }
    /**
     * Return a cached string using the current file encoding (see java_set_file_encoding()).
     * @param b The byte array
     * @param start The start index
     * @param length The number of bytes to encode.
     * @return The encoded string.
     */    
    public String getCachedString(byte[] b, int start, int length) {
        return stringCache.getString(b, start, length, options.getEncoding());
    }
    /**
     * Create a response object for this bridge, according to options.
     * @return The response object
     */
    Response createResponse() {
	if(options.passContext()) return new ClassicResponse(this);
	return new Response(this);
    }
}
