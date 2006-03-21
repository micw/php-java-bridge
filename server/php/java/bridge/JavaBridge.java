/*-*- mode: Java; tab-width:8 -*-*/

package php.java.bridge;
import java.beans.BeanInfo;
import java.beans.Introspector;
import java.beans.PropertyDescriptor;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
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
import java.util.Enumeration;
import java.util.HashMap;
import java.util.Hashtable;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.StringTokenizer;
import java.util.Vector;
import java.util.Map.Entry;

import php.java.bridge.Request.PhpArray;

/**
 * This is the main class of the PHP/Java Bridge. It starts the standalone backend,
 * listenes for protocol requests and handles CreateInstance, GetSetProp and Invoke 
 * requests. Supported protocol modes are INET (listens on all interfaces), INET_LOCAL (loopback only) and 
 * LOCAL (uses a local, invisible communication channel, requires natcJavaBridge.so). Furthermore it
 * contains utility methods which can be used by clients.
 * <p>
 * Example:<br>
 * <code>
 * java -Djava.awt.headless=true -jar JavaBridge.jar INET_LOCAL:9676 5 bridge.log &<br>
 * telnet localhost 9676<br>
 * &lt;CreateInstance value="java.lang.Long" predicate="Instance" id="0"&gt;<br> 
 *     &lt;Long value="6"/&gt; <br>
 *   &lt;/CreateInstance&gt;<br>
 * &lt;Invoke value="1" method="toString" predicate="Invoke" id="0"/&gt;<br>
 * </code>
 *
 * @author Sam Ruby (methods coerce and select)
 * @author Kai Londenberg
 * @author Jost Boekemeier
 */

public class JavaBridge implements Runnable {

    /**
     * For PHP4's last_exception_get.
     */
    public Throwable lastException = null;
    protected Throwable lastAsyncException; // reported by end_document()

    // array of objects in use in the current script
    GlobalRef globalRef=new GlobalRef(this);

    /**
     * For internal use only. The classloader. 
     */
    private JavaBridgeClassLoader cl = null;

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
     * For internal use only. The current request (if any)
     * 
     */
    public Request request;

    // false if we detect that setAccessible is not possible
    boolean canModifySecurityPermission = true;

    // method to load a CLR assembly
    private static Method loadMethod = null;
    private static Class CLRAssembly = null;

    //
    // Native methods, only called  when loadLibrary succeeds.
    // These are necessary to deal with local sockets ("Unix domain sockets")
    // which are much faster and more secure than standard TCP sockets.

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

    // native accept fills these
    int uid =-1, gid =-1;

    //static Object loadLock=new Object();
    /** For internal use only. */
    //public static short load = 0;

    /*
      public static short getLoad() {
      synchronized(loadLock) {
      return load;
      }
      }*/

    private MethodCache methodCache = new MethodCache();
    private ConstructorCache constructorCache = new ConstructorCache();

    private SessionFactory sessionFactory;
    /** 
     * For internal use only.
     */
    static final SessionFactory defaultSessionFactory = new SessionFactory();

    Options options = new Options();
    
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
	    setSessionFactory(defaultSessionFactory);
	    try {
		setClassLoader(new JavaBridgeClassLoader(this, (DynamicJavaBridgeClassLoader) Thread.currentThread().getContextClassLoader()));
	    } catch (ClassCastException ex) {
	         // should never happen
		setClassLoader(new JavaBridgeClassLoader(this, null));
	    }
	    request = new Request(this);
          
	    try {
		if(!request.init(in, out)) return;
	    } catch (Throwable e) {
		printStackTrace(e);
		return;
	    }
	    if(logLevel>3) logDebug("Request from client with uid/gid "+uid+"/"+gid);
	    //load++;
	    try {
		request.handleRequests();
	    } catch (Throwable e) {
		printStackTrace(e);
	    }

	    try {
		in.close();
	    } catch (IOException e1) {
		printStackTrace(e1);
	    }
	    try {
		out.close();
	    } catch (IOException e2) {
		printStackTrace(e2);
	    }
	    //load--;
	    globalRef=null;
	    getClassLoader().clear();
	    Session.expire(this);
	    Util.logDebug("END: JavaBridge.run()");
        } catch (Throwable t) {
	    printStackTrace(t);
        }
    }

    /**
     * Create a new server socket and return it.
     * @param sockname the socket name
     * @return the server socket
     */
    public static ISocketFactory bind(String sockname) throws Exception {
	ISocketFactory socket = null;
	try {
	    socket = LocalServerSocket.create(sockname, Util.BACKLOG);
	} catch (Throwable e) {
	    Util.logMessage("Local sockets not available:" + e + ". Try(ing) TCP sockets instead");
	}
	if(null==socket)
	    socket = TCPServerSocket.create(sockname, Util.BACKLOG);

	if(null==socket)
	    throw new Exception("Could not create socket: " + sockname);

	return socket;
    }
    private static void usage() {
	System.err.println("PHP/Java Bridge version "+Util.VERSION);
        System.err.println("Usage: java -jar JavaBridge.jar [SOCKETNAME LOGLEVEL LOGFILE]");
	System.err.println("Example: java -jar JavaBridge.jar");
	System.err.println("Example: java -Djava.awt.headless=\"true\" -Dphp.java.bridge.threads=50 -jar JavaBridge.jar INET:0 3 JavaBridge.log");
	System.exit(1);
    }

    /**
     * Global init
     * @param s an array of [socketname, level, logFile]
     */
    public static void init(String s[]) {
	String logFile=null;
	String sockname=null;
	if(s.length>3) usage();
	try {
	    StringBuffer buf = new StringBuffer();
	    String ext = System.getProperty("java.ext.dirs");
	    if(ext!=null) buf.append(ext);
	    buf.append(File.pathSeparator);
	    buf.append("/usr/share/java/ext");
	    buf.append(File.pathSeparator);
	    buf.append("/usr/java/packages/lib/ext");
	    System.setProperty("java.ext.dirs", buf.toString());
	} catch (Throwable t) {/*ignore*/}
    	try {
    	    CLRAssembly = Class.forName("cli.System.Reflection.Assembly");
    	    loadMethod = CLRAssembly.getMethod("Load", new Class[] {String.class});
    	} catch (Exception e) {}
	try {
	    if(s.length>0) {
		sockname=s[0];
		if(sockname.startsWith("-")) usage();
	    }
	    try {
		if(s.length>1) {
		    Util.logLevel=Integer.parseInt(s[1]);
		} else {
		    Util.logLevel=Util.DEFAULT_LOG_LEVEL;
		}
	    } catch (NumberFormatException e) {
		usage();
	    } catch (Throwable t) {
		t.printStackTrace();
	    }

	    try {
		logFile=s.length>0?"":Util.DEFAULT_LOG_FILE;
		if(s.length>2) {
		    logFile=s[2];
		}
		if(Util.logLevel>3) System.err.println(Util.EXTENSION_NAME+" log: " + logFile);
	    }catch (Throwable t) {
		t.printStackTrace();
	    }
	    boolean redirectOutput = false;
	    try {
	    	redirectOutput = logFile==null || openLog(logFile);
	    } catch (Throwable t) {/*ignore*/}

	    Util.redirectOutput(redirectOutput, logFile);
	    ISocketFactory socket = bind(sockname);
	    if(Util.VERSION != null)
		Util.logMessage(Util.EXTENSION_NAME+  " version         : " + Util.VERSION);
	    Util.logMessage(Util.EXTENSION_NAME + " logFile         : " + logFile);
	    Util.logMessage(Util.EXTENSION_NAME + " default logLevel: " + Util.logLevel);
	    Util.logMessage(Util.EXTENSION_NAME + " socket          : " + socket);
	    

	    int maxSize = 20;
	    try {
	    	maxSize = Integer.parseInt(Util.THREAD_POOL_MAX_SIZE);
	    } catch (Throwable t) {
	    	Util.printStackTrace(t);
	    }
	    
	    ThreadPool pool = null;
	    if(maxSize>0) pool = new ThreadPool(Util.EXTENSION_NAME, maxSize);
            Util.logDebug("Starting to accept Socket connections");
            
	    while(true) {
		Socket sock = socket.accept();
                Util.logDebug("Socket connection accepted");
		JavaBridge bridge = new JavaBridge(sock.getInputStream(), sock.getOutputStream());
                if(maxSize>0) {
                    Util.logDebug("Starting bridge from Thread Pool");
		    pool.start(bridge); // Uses thread pool
		} else {
                    Util.logDebug("Starting bridge from new Thread");
		    Thread t = new Thread(bridge);
		    t.setContextClassLoader(DynamicJavaBridgeClassLoader.newInstance(Util.getContextClassLoader()));
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
     * 
     * @param s an array of [socketname, level, logFile]
     */
    public static void main(String s[]) {
	try {
	    System.loadLibrary("natcJavaBridge");
	} catch (Throwable t) {/*ignore*/}
	try {
	    init(s);
	} catch (Throwable t) {
	    t.printStackTrace();
	    System.exit(9);
	}
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
		Util.println(2, "Exception occured");
	    	Util.getLogger().printStackTrace(t);
	    }
    }
    private String getId() {
    	return "@"+Integer.toHexString(System.identityHashCode(Thread.currentThread()));
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

	lastException = new Exception(buf.toString(), e);
	StackTraceElement[] trace = e.getStackTrace();
	if(trace!=null) lastException.setStackTrace(trace);
	response.setResultException(lastException, lastException.toString());
    }

    private Exception getUnresolvedExternalReferenceException(Throwable e, String what) {
        return 	new ClassNotFoundException("Unresolved external reference: "+ e+ ". -- Unable to "+what+" because it or one of its parameters refer to the mentioned external class which is not available in the current \"java_require()\" url path. Remember that all interconnected classes must be loaded with a single java_require() call, i.e. use java_require(\"foo.jar;bar.jar\") instead of java_require(\"foo.jar\"); java_require(\"bar.jar\"). Please check the Java Bridge log file for details.", e);
    }
    /**
     * Create an new instance of a given class
     */
    public void CreateObject(String name, boolean createInstance,
			     Object args[], Response response) {
	Class params[] = null;
	try {
	    Vector matches = new Vector();
	    Vector candidates = new Vector();
	    Constructor selected = null;
	    
	    Class clazz = getClassLoader().forName(name);
	    if(createInstance) {
		ConstructorCache.Entry entry = constructorCache.getEntry(name, args);
		selected = constructorCache.get(entry);
		if(selected==null) {
		    Constructor cons[] = clazz.getConstructors();
		    for (int i=0; i<cons.length; i++) {
			candidates.addElement(cons[i]);
			if (cons[i].getParameterTypes().length == args.length) {
			    matches.addElement(cons[i]);
			}
		    }
		    
		    selected = (Constructor)select(matches, args);
		    if(selected!=null) constructorCache.put(entry, selected);
		}
	    }

	    if (selected == null) {
		if (args.length > 0) {
		    throw new InstantiationException("No matching constructor found. " + "Candidates: " + String.valueOf(candidates));
		} else {
		    // for classes which have no visible constructor, return the class
		    // useful for classes like java.lang.System and java.util.Calendar.
		    if(createInstance && logLevel>0) {
		    	logMessage("No visible constructor found in: "+ name +", returning the class instead of an instance; this may not be what you want. Please correct this error or please use new JavaClass("+name+") instead.");
		    }
		    response.setResultClass(clazz);
		    return;
		}
	    }

	    Object coercedArgs[] = coerce(params=selected.getParameterTypes(), args, response);
	    if (this.logLevel>4) logInvoke(clazz, name, coercedArgs); // If we have a logLevel of 5 or above, do very detailed invocation logging
    	    response.setResultObject(selected.newInstance(coercedArgs));

	} catch (Throwable e) {
	    if(e instanceof OutOfMemoryError ||
	       ((e instanceof InvocationTargetException) &&
		((InvocationTargetException)e).getTargetException() instanceof OutOfMemoryError)) {
		Util.logFatal("OutOfMemoryError");
		throw new OutOfMemoryError(); // abort
	    }
	    if(e instanceof NoClassDefFoundError ||
	       ((e instanceof InvocationTargetException) &&
		((InvocationTargetException)e).getTargetException() instanceof NoClassDefFoundError)) {
		if(e instanceof InvocationTargetException) e = ((InvocationTargetException)e).getTargetException();
		getClassLoader().clearCaches();
		e = getUnresolvedExternalReferenceException(e, "call constructor");
	    }
	    printStackTrace(e);
	    setException(response, e, createInstance?"CreateInstance":"ReferenceClass", null, name, args, params);
	}
    }

    //
    // Select the best match from a list of methods
    //
    private int weight(Class param, Object arg) {
	int w = 0;
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
	    if ((arg != null) && !(arg instanceof String) && !(arg instanceof Request.PhpString))
	        if(arg instanceof byte[])
	            w+=32;
	        else
	            w+=8000; // conversion to string is always possible
	} else if (param.isArray()) {
	    if (arg != null) {
	        if(arg instanceof Request.PhpString) {
	            Class c=param.getComponentType();
	            if(c == byte.class) 
	                w+=32;
	            else
	                w+=9999;
	        } else if(arg.getClass() == Request.PhpArray.class) {
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
	    }
	} else if ((java.util.Collection.class).isAssignableFrom(param)) {
	    if ((arg != null) && !(arg instanceof Request.PhpArray))
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
	    } else if ((arg instanceof String)||(arg instanceof Request.PhpString)) {
		    w+=64;
	    } else {
		w+=9999;
	    }
	} else {
	    w+=9999;
	}
	
	if(logLevel>4) logDebug("weight " + param + " " + Util.getClass(arg) + ": " +w);
	return w;
    }

    private Object select(Vector methods, Object args[]) {
	if (methods.size() == 1) return methods.firstElement();
	Object selected = null;
	int best = Integer.MAX_VALUE;
	int n = 0;
	
	for (Enumeration e = methods.elements(); e.hasMoreElements(); n++) {
	    Object element = e.nextElement();
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
		if(logLevel>4) logDebug("best: " + selected + " " + w);
	    } else {
	        if(logLevel>4) logDebug("skip: " + element + " " + w);
	    }
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
		    if(CLRAssembly!=null) // CLR uses an inner method class
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
	    	if (arg instanceof Request.PhpString)
		    result[i] = ((Request.PhpString)arg).getString();
	    	else 
		    result[i] = arg.toString();
	    } else if (arg instanceof Request.PhpString || arg instanceof String) {
	        if(!parms[i].isArray()) {
		Class c = parms[i];
		String s = (arg instanceof String) ? (String) arg : ((Request.PhpString)arg).getString();
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
	            result[i]=((Request.PhpString)arg).getBytes();
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
		    if(arg.getClass()==Request.PhpNumber.class) {
	    		if(!options.extJavaCompatibility()) {
			    Class c = parms[i];
			    if(c.isAssignableFrom(Integer.class)) {
		    		result[i] = new Integer(((Number)arg).intValue());
			    } else {
		    		result[i] = new Long(((Number)arg).longValue());				
			    }
			} else {
			    result[i] = new Long(((Number)arg).longValue());
			}
		    }
	    	}
	    } else if (arg instanceof Request.PhpArray) {
	    	if(parms[i].isArray()) {
	    	    Class targetType = parms[i].getComponentType();
		    try {
			PhpArray ht = (PhpArray)arg;
			size = ht.arraySize();
			
			// flatten hash into an array
			targetType = parms[i].getComponentType();
			Object tempArray = Array.newInstance(targetType, size);
			
			for (Iterator ii = ht.entrySet().iterator(); ii.hasNext(); ) {
			    Map.Entry e = (Entry) ii.next();
			    Array.set(tempArray, ((Number)(e.getKey())).intValue(), coerce(targetType, e.getValue(), response));
			}
			result[i]=tempArray;
		    } catch (Exception e) {
			logError("Error: " + String.valueOf(e) + ". Could not create array of type: " + targetType + ", size: " + size);
			printStackTrace(e);
			// leave result[i] alone...
		    }
		} else if ((java.util.Collection.class).isAssignableFrom(parms[i])) {
		    try {
			Map m = (Map)arg;
			Collection c = m.values();
			result[i]=c;
		    } catch (Exception e) {
			logError("Error: " +  String.valueOf(e) + ". Could not create Collection from Map.");
			printStackTrace(e);
			// leave result[i] alone...
		    }
		} else if ((java.util.Hashtable.class).isAssignableFrom(parms[i])) {
		    try {
			Map ht = (Map)arg;
			Hashtable res;
			res = (Hashtable)parms[i].newInstance();
			res.putAll(ht);

			result[i]=res;
		    } catch (Exception e) {
			logError("Error: " +  String.valueOf(e) + ". Could not create Hashtable from Map.");
			printStackTrace(e);
			// leave result[i] alone...
		    }
		} else if ((java.util.Map.class).isAssignableFrom(parms[i])) {
		    result[i]=arg;
		} else if(arg instanceof Request.PhpString) {
		    result[i] = ((Request.PhpString)arg).getString(); // always prefer strings over byte[]
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
			    	if(bridge.logLevel>3) bridge.logDebug("smatching interface for GetSetProp: "+ jclass);
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

    private static abstract class ClassIterator {
	Object object;
	Class current;
	FindMatchingInterface match;

	public static ClassIterator getInstance(Object object, FindMatchingInterface match) {
	    ClassIterator c;
	    if(object instanceof Class)
		c = new ClassClassIterator();
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

    public static void logInvoke(Object obj, String method, Object args[]) {
	String dmsg = "\nInvoking "+objectDebugDescription(obj)+"."+method+"(";
	for (int t =0;t<args.length;t++) {
	    if (t>0) dmsg +=",";
	    dmsg += objectDebugDescription(args[t]);

	}
	dmsg += ");\n";
	Util.logDebug(dmsg);
    }

    /**
     * Invoke a method on a given object
     */
    public void Invoke
	(Object object, String method, Object args[], Response response)
    {
	Vector matches = new Vector();
	Vector candidates = new Vector();
	Class jclass;
	boolean again;
	Object coercedArgs[] = null;
	Class params[] = null;
	
	MethodCache.Entry entry = methodCache.getEntry(method, Util.getClass(object), args);
	Method selected = (Method) methodCache.get(entry);
	try {
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
				candidates.addElement(meth);
				if(meth.getParameterTypes().length == args.length) {
				    matches.addElement(meth);
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
		coercedArgs = coerce(params=selected.getParameterTypes(), args, response);
	    } while(again);
	    if (this.logLevel>4) logInvoke(object, method, coercedArgs); // If we have a logLevel of 5 or above, do very detailed invocation logging
	    setResult(response, selected.invoke(object, coercedArgs), selected.getReturnType());
	} catch (Throwable e) {
	    if(e instanceof OutOfMemoryError ||
	       ((e instanceof InvocationTargetException) &&
		((InvocationTargetException)e).getTargetException() instanceof OutOfMemoryError)) {
		Util.logFatal("OutOfMemoryError");
		throw new OutOfMemoryError(); // abort
	    }
	    if(e instanceof NoClassDefFoundError ||
	       ((e instanceof InvocationTargetException) &&
		((InvocationTargetException)e).getTargetException() instanceof NoClassDefFoundError)) {
		if(e instanceof InvocationTargetException) e = ((InvocationTargetException)e).getTargetException();
		getClassLoader().clearCaches();
		e = getUnresolvedExternalReferenceException(e, "call the method");
	    }
	    
	    printStackTrace(e);
            if (e instanceof IllegalArgumentException) {
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

    /**
     * Only for internal use
     * @param obj The object
     * @return A debug description.
     */
    public static String objectDebugDescription(Object obj) {
    	if(obj==null) return "[Object null]";
	return "[Object "+System.identityHashCode(obj)+" - Class: "+ classDebugDescription(obj.getClass())+ "]";
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
	boolean set = (args!=null && args.length>0);
	Class params[] = null;
	try {
	    ArrayList matches = new ArrayList();
	    Class jclass;
	    
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
	    if(e instanceof OutOfMemoryError ||
	       ((e instanceof InvocationTargetException) &&
		((InvocationTargetException)e).getTargetException() instanceof OutOfMemoryError)) {
		Util.logFatal("OutOfMemoryError");
		throw new OutOfMemoryError(); // abort
	    }
	    if(e instanceof NoClassDefFoundError ||
	       ((e instanceof InvocationTargetException) &&
		((InvocationTargetException)e).getTargetException() instanceof NoClassDefFoundError)) {
		if(e instanceof InvocationTargetException) e = ((InvocationTargetException)e).getTargetException();
		getClassLoader().clearCaches();
		e = getUnresolvedExternalReferenceException(e, "invoke a property");
	    }
	    printStackTrace(e);
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
     * @param in
     * @param out
     * @see php.java.bridge.JavaBridgeRunner
     * @see php.java.bridge.JavaBridge#main(String[])
     */
    public JavaBridge(InputStream in, OutputStream out) {
	this.in = in;
	this.out = out;
    }

    /**
     * Only for internal use.
     * @see php.java.bridge.JavaBridge#main(String[])
     * @see php.java.bridge.JavaBridgeRunner
     */
    public JavaBridge() {
	this(null, null);
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
     * Append the path to the current library path<br>
     * Examples:<br>
     * setJarLibPath(";file:///tmp/test.jar;file:///tmp/my.jar");<br>
     * setJarLibPath("|file:c:/t.jar|http://.../a.jar|jar:file:///tmp/x.jar!/");<br>
     * @param path A file or url list, usually separated by ';'
     * @param extensionDir The php extension directory. 
     */
    public void setJarLibraryPath(String path, String extensionDir) {
    	getClassLoader().updateJarLibraryPath(path, extensionDir);
    }

    /**
     * 
     * Set the library path for ECMA dll's
     * @param rawPath A file or url list, usually separated by ';'
     * @param extensionDir The php extension directory. 
     */
    public void setLibraryPath(String rawPath, String extensionDir) {
        if(rawPath==null || rawPath.length()<2) return;

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
		loadMethod.invoke(CLRAssembly, new Object[] {s} );
	    } catch (Exception e) {
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
	this.options.encoding=fileEncoding;
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
    private ISession sessionCache = null;
    /**
     * Return a session handle shared among all JavaBridge
     * instances. If it is a HTTP session, the session is shared with
     * the servlet or jsp.
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
     * Create a dynamic proxy proxy for calling PHP code.<br>
     * Example: <br>
     * java_closure($this, $map);<br>
     * 
     * @param object the PHP environment (the php "class")
     * @param names maps java to php names
     * @return the proxy
     */
    public Object makeClosure(long object, Map names) {
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
    	return new PhpProcedureProxy(this, names, interfaces, object);
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
	warn("Your PHP script has called the privileged procedure \"reset()\", which resets the backend to its initial state. Therefore all session variables and all caches are now gone.");
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

    static final String PHPSESSION = "PHPSESSION";
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
	ISession session = defaultSessionFactory.getSession(null, false, timeout);
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
    	ISession session = defaultSessionFactory.getSession(null, false, timeout);
    	String id = Integer.toHexString(getSerialID());
    	session.put(id, obj);
    	return (String)castToString(id);
    }
    /**
     * Set a new ClassLoader
     * @param cl The ClassLoader
     * 
     */
    public void setClassLoader(JavaBridgeClassLoader cl) {
	this.cl = cl;
    }
    /**
     * Return the current ClassLoader
     * @return The ClassLoader.
     */
    public JavaBridgeClassLoader getClassLoader() {
	return cl;
    }
    /**
     * Checks if a given position exists.
     * @param value The map.
     * @param pos The position
     * @return true if an element exists at this position, false otherwise.
     */
    public boolean offsetExists(Map value, Object pos) {
	return value.containsKey(pos);
    }
    /**
     * Returns the object at the posisition.
     * @param value The map.
     * @param pos The position.
     * @return The object at the given position.
     */    
    public Object offsetGet(Map value, Object pos) {
	return value.get(pos);
    }
    /**
     * Set an object at position. 
     * @param value The map.
     * @param pos The position.
     * @param val The object.
     */
    public void offsetSet(Map value, Object pos, Object val) {
        Class type = value.getClass().getComponentType();
        if(type!=null) val = coerce(type, val, request.response);
	value.put(pos, val);
    }
    /**
     * Remove an object from the position.
     * @param value The map.
     * @param pos The position.
      */
    public void offsetUnset(Map value, Object pos) {
	offsetSet(value, pos, null);
    }
    /**
     * Checks if a given position exists.
     * @param value The list.
     * @param pos The position
     * @return true if an element exists at this position, false otherwise.
     */
    public boolean offsetExists(List value, Number pos) {
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
    public Object offsetGet(List value, Number pos) {
	return value.get(pos.intValue());
    }
    /**
     * Set an object at position. 
     * @param value The list.
     * @param pos The position.
     * @param val The object.
     */
    public void offsetSet(List value, Number pos, Object val) {
	value.set(pos.intValue(), val);
    }
    /**
     * Remove an object from the position.
     * @param value The list.
     * @param pos The position.
      */
    public void offsetUnset(List value, Number pos) {
	offsetSet(value, pos, null);
    }
    boolean offsetExists(int length, Object pos) {
	int i = ((Number)pos).intValue();
	return (i>0 && i<length);
    }
    /**
     * Checks if a given position exists.
     * @param value The array.
     * @param pos The position
     * @return true if an element exists at this position, false otherwise.
     */
    public boolean offsetExists(Object value, Object pos) {
        return offsetExists(Array.getLength(value), pos);
    }
    
    /**
     * Returns the object at the posisition.
     * @param value The array.
     * @param pos The position.
     * @return The object at the given position.
     */    
    public Object offsetGet(Object value, Object pos) {
	int i = ((Number)pos).intValue();
	Object o = Array.get(value, i);
	return o==this ? null : o;
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
    public void offsetSet(Object value, Object pos, Object val) {
	int i = ((Number)pos).intValue();
	Array.set(value, i, coerce(value.getClass().getComponentType(), val, request.response));
    }
    /**
     * Remove an object from the position.
     * @param value The array.
     * @param pos The position.
      */
    public void offsetUnset(Object value, Object pos) {
	int i = ((Number)pos).intValue();
	Array.set(value, i, null);
    }

}
