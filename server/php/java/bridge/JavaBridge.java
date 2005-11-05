/*-*- mode: Java; tab-width:8 -*-*/

package php.java.bridge;
import java.beans.BeanInfo;
import java.beans.Introspector;
import java.beans.PropertyDescriptor;
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
import java.util.HashSet;
import java.util.Hashtable;
import java.util.Iterator;
import java.util.Map;
import java.util.StringTokenizer;
import java.util.Vector;

/**
 * This is the main class for the PHP/Java Bridge. It starts the standalone backend,
 * listenes for protocol requests and handles CreateInstance, GetSetProp and Invoke 
 * requests. Supported protocol modes are INET (listens on all interfaces), INET_LOCAL (loopback only) and 
 * UNIX (uses a local, invisible communication channel, requires natcJavaBridge.so). Furthermore it
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


    // The client's file encoding, for example UTF-8.
    String fileEncoding="UTF-8";

    /**
     * For PHP4's last_exception_get.
     */
    public Throwable lastException = null;

    // list of objects in use in the current script
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
    public static native boolean openLog(String logFile);
    /**
     * Create a local ("Unix domain") socket for sockname and return the handle.
     * If it was possible to obtain the user credentials, setGlobals will be called with
     * the uid and gid.
     * @param bridge The bridge instance.
     * @param logLevel The current log level.
     * @param backlog The current backlog.
     * @param sockname The sockename.
     * @return local socket handle ("Unix domain socket")
     */
    public static native int startNative(int logLevel, int backlog, String sockname);
    public static native int accept(int socket);
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

    // The options from the request header
    byte requestOptions;
    
    private static boolean haveNatcJavaBridge=true;

    static Object loadLock=new Object();
    /** For internal use only. */
    public static short load = 0;

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
		setClassLoader(new JavaBridgeClassLoader(this, null));
	    }
	    request = new Request(this);
          
	    try {
		if(!request.initOptions(in, out)) return;
	    } catch (Throwable e) {
		printStackTrace(e);
		return;
	    }
	    if(logLevel>3) logDebug("Request from client with uid/gid "+uid+"/"+gid);
	    load++;
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
	    load--;
	    globalRef=null;
	    Session.expire(this);
	    Util.logDebug("END: JavaBridge.run()");
	    Util.logStream.flush();
        } catch (Throwable t) {
	    printStackTrace(t);
        }
    }

    /* Disabled for the release 2.0.7.  We'll probably want something more general in the future.
    //
    // add all jars found in the phpConfigDir/lib and /usr/share/java
    // to our classpath
    //
    static void initGlobals(String phpConfigDir) {
    try {

    // FIXME: This doesn't work on FC3 or when running the bridge in a J2EE AS (no file access permissions)
    try {
    Util.logMessage("Trying to open '"+phpConfigDir+File.separator+"PHPJavaBridge.ini'");
    File iniFile = new File(phpConfigDir+File.separator+"PHPJavaBridge.ini");
    Properties props = null;
    if (iniFile.exists()) {
    props = new Properties();
    props.load(new FileInputStream(iniFile));
    } else {
    props = new Properties();
    props.put("classloader","Classic");
    props.put("thread_pool_size","20");
    Util.logMessage("Ini File not found, creating '"+phpConfigDir+File.separator+"PHPJavaBridge.ini'");
    try {
    FileOutputStream fout = new FileOutputStream(phpConfigDir+File.separator+"PHPJavaBridge.ini");
    props.store(fout, "PHPJavaBridge Properties");
    fout.close();
    } catch (IOException ioe) {
    Util.printStackTrace(ioe);
    }
    }
    BridgeThread.poolSize = Integer.parseInt(props.getProperty("thread_pool_size", Util.THREAD_POOL_MAX_SIZE));
    } catch (Exception ex) {
    Util.printStackTrace(ex);
    }
    try {
    if(BridgeThread.poolSize==0) BridgeThread.poolSize= Integer.parseInt(Util.THREAD_POOL_MAX_SIZE);
    } catch (Throwable t) {
    BridgeThread.poolSize = 20;
    }
    DynamicJavaBridgeClassLoader.initClassLoader(phpConfigDir);
    } catch (Exception t) {
    Util.printStackTrace(t);
    }
    }
    */
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
	    Util.logMessage("Local sockets not available:" + e + ". Try TCP sockets instead");
	}
	if(null==socket)
	    socket = TCPServerSocket.create(sockname, Util.BACKLOG);

	if(null==socket)
	    throw new Exception("Could not create socket: " + sockname);

	return socket;
    }
    private static void usage() {
	System.err.println("Usage: java -jar JavaBridge.jar [SOCKETNAME LOGLEVEL LOGFILE]");
	System.err.println("Example: java -jar JavaBridge.jar");
	System.err.println("Example: java -Djava.awt.headless -Dphp.java.bridge.threads=50 -jar JavaBridge.jar INET:0 3 JavaBridge.log");
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
    	    CLRAssembly = Class.forName("cli.System.Reflection.Assembly");
    	    loadMethod = CLRAssembly.getMethod("Load", new Class[] {String.class});
    	} catch (Exception e) {}
	try {
	    if(s.length>0) {
		sockname=s[0];
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
		Util.printStackTrace(t);
	    }

	    try {
		logFile=Util.DEFAULT_LOG_FILE;
		if(s.length>2) {
		    logFile=s[2];
		}
		if(Util.logLevel>3) System.err.println(Util.EXTENSION_NAME+" log: " + logFile);
	    }catch (Throwable t) {
		Util.printStackTrace(t);
	    }
	    boolean redirectOutput = false;
	    try {
	    	redirectOutput = (logFile==null || logFile.length() == 0) || openLog(logFile);
	    } catch (Throwable t) {/*ignore*/}

	    if(!redirectOutput) {
		try {
		    Util.logStream=new java.io.PrintStream(new java.io.FileOutputStream(logFile));
		} catch (Exception e) {
		    Util.logStream=System.err;
		}
	    } else {
		Util.logStream=System.err;
		logFile="<stderr>";
	    }
	    
	    ISocketFactory socket = bind(sockname);
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
            DynamicJavaBridgeClassLoader.initClassLoader(Util.EXTENSION_DIR);
            
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
		    t.setContextClassLoader(DynamicJavaBridgeClassLoader.newInstance());
		    t.start();
		}
	    }

	} catch (Throwable t) {
	    Util.printStackTrace(t);
	    System.exit(1);
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
	} catch (Throwable t) {
	    haveNatcJavaBridge=false;
	    //Util.printStackTrace(t);
	    //System.exit(9);
	}
	try {
	    init(s);
	} catch (Throwable t) {
	    Util.printStackTrace(t);
	    System.exit(9);
	}
    }

    //
    // Helper routines which encapsulate the native methods
    //
    void setResult(Response response, Object value) {
	if (value == null) {
	    response.writeObject(null);
	} else if (value instanceof byte[]) {
	    response.writeString((byte[])value);
	} else if (value instanceof java.lang.String) {
	    response.writeString((String)value);
	} else if (value instanceof java.lang.Number) {

	    if (value instanceof java.lang.Integer ||
		value instanceof java.lang.Short ||
		value instanceof java.lang.Byte) {
		response.writeLong(((Number)value).longValue());
	    } else {
		/* Float, Double, BigDecimal, BigInteger, Double, Long, ... */
		response.writeDouble(((Number)value).doubleValue());
	    }

	} else if (value instanceof java.lang.Boolean) {

	    response.writeBoolean(((Boolean)value).booleanValue());

	} else if (value.getClass().isArray()) {

	    long length = Array.getLength(value);
	    if(response.hook.sendArraysAsValues()) {
		// Since PHP 5 this is dead code, setResultFromArray
		// behaves like setResultFromObject and returns
		// false. See PhpMap.
		response.writeCompositeBegin_a();
		for (int i=0; i<length; i++) {
                    response.writePairBegin();
		    setResult(response, Array.get(value, i));
		    response.writePairEnd();
		}
		response.writeCompositeEnd();
	    } else { //PHP 5
	    	response.writeObject(value);
	    }
	} else if (value instanceof java.util.Map) {
	    Map ht = (Map) value;
	    if (response.hook.sendArraysAsValues()) {
		// Since PHP 5 this is dead code, setResultFromArray
		// behaves like setResultFromObject and returns
		// false. See PhpMap.
		response.writeCompositeBegin_h();
		for (Iterator e = ht.keySet().iterator(); e.hasNext(); ) {
		    Object key = e.next();
		    long slot;
		    if (key instanceof Number &&
			!(key instanceof Double || key instanceof Float)) {
			response.writePairBegin_n(((Number)key).intValue());
			setResult(response, ht.get(key));
		    }
		    else {
			response.writePairBegin_s(String.valueOf(key));
			setResult(response, ht.get(key));
		    }
		    response.writePairEnd();
		}
		response.writeCompositeEnd();
	    } else { //PHP 5
	    	response.writeObject(value);
	    }
	} else {
	    response.writeObject(value);
	}
    }

    /**
     * Print a stack trace to the log file.
     * @param t the throwable
     */
    public void printStackTrace(Throwable t) {
	if(logLevel > 0)
	    if ((t instanceof Error) || logLevel > 1)
	    	Util.getLogger().printStackTrace(t);
    }
    private String getId() {
    	return "@"+Integer.toHexString(System.identityHashCode(this));
    }
    public void logDebug(String msg) {
	if(logLevel>3) Util.println(4, getId() + " " + msg);
    }
    public void logFatal(String msg) {
	if(logLevel>0) Util.println(1, getId() + " " + msg);
    }
    public void logError(String msg) {
	if(logLevel>1) Util.println(2, getId() + " " + msg);
    }
    public void logMessage(String msg) {
	if(logLevel>2) Util.println(3, getId() + " " + msg);
    }
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
	    Util.appendObject(obj, buf);
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
	response.writeException(lastException, lastException.toString());
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
		    	if(!response.extJavaCompatibility()) new InstantiationException("No matching constructor found. " + "Candidates: " + String.valueOf(candidates));
		    }
		    response.writeObject(clazz);
		    return;
		}
	    }

	    Object coercedArgs[] = coerce(params=selected.getParameterTypes(), args, response);
	    if (this.logLevel>4) logInvoke(clazz, name, coercedArgs); // If we have a logLevel of 5 or above, do very detailed invocation logging
    	    response.writeObject(selected.newInstance(coercedArgs));

	} catch (Throwable e) {
	    if(e instanceof OutOfMemoryError ||
	       ((e instanceof InvocationTargetException) &&
		((InvocationTargetException)e).getTargetException() instanceof OutOfMemoryError)) {
		Util.logStream.println("FATAL: OutOfMemoryError");
		throw new OutOfMemoryError(); // abort
	    }
	    if(e instanceof NoClassDefFoundError ||
	       ((e instanceof InvocationTargetException) &&
		((InvocationTargetException)e).getTargetException() instanceof NoClassDefFoundError)) {
		if(e instanceof InvocationTargetException) e = ((InvocationTargetException)e).getTargetException();
		getClassLoader().clearCaches();
		e = new ClassNotFoundException("Unresolved external reference: "+e+". -- Unable to call the constructor because it or one of its parameters refer to the mentioned external class which is not available in the current \"java_require(<path>)\" url path. Remember that all interconnected classes must be loaded with a single java_require() call and that a class must not appear in more than one java_require() call. Please check the Java Bridge log file for details.", e);
	    }
	    printStackTrace(e);
	    setException(response, e, createInstance?"CreateInstance":"ReferenceClass", null, name, args, params);
	}
    }

    //
    // Select the best match from a list of methods
    //
    private static Object select(Vector methods, Object args[]) {
	if (methods.size() == 1) return methods.firstElement();

	Object selected = null;
	int best = Integer.MAX_VALUE;

	for (Enumeration e = methods.elements(); e.hasMoreElements(); ) {
	    Object element = e.nextElement();
	    int weight=0;

	    Class parms[] = (element instanceof Method) ?
		((Method)element).getParameterTypes() :
		((Constructor)element).getParameterTypes();

	    for (int i=0; i<parms.length; i++) {
	    	Object arg = args[i];
	    	if(Util.getClass(arg) == PhpProcedureProxy.class) {
		    PhpProcedureProxy proxy = ((PhpProcedureProxy)arg);
		    if(proxy.suppliedInterfaces==null) {
			if(!parms[i].isInterface()) weight+=9999;	    			
		    } else {
			arg = proxy.getProxy(null);
		    }
	    	}
		if (parms[i].isInstance(arg)) {
		    for (Class c=arg.getClass(); (c=c.getSuperclass()) != null; ) {
			if (!parms[i].isAssignableFrom(c)) {
			    if (arg instanceof byte[]) { //special case: when arg is a byte array we always prefer a String parameter (if it exists).
				weight+=1;
			    }
			    break;
			}
			weight+=256; // prefer more specific arg, for
				     // example AbstractMap hashMap
				     // over Object hashMap.
		    }
		} else if (parms[i].isAssignableFrom(java.lang.String.class)) {
		    if (!(arg instanceof byte[]) && !(arg instanceof String))
			weight+=9999;
		} else if (parms[i].isArray()) {
		    if ((arg != null) && arg.getClass() == Request.PhpArray.class) { 
			Iterator iterator = ((Map)arg).values().iterator();
			if(iterator.hasNext()) {
			    Object elem = iterator.next();
			    Class c=parms[i].getComponentType();
			    if (elem instanceof Number) {
				if(elem instanceof Double) {
				    if (c==Float.TYPE) weight+=11;
				    else if (c==Double.TYPE) weight+=10;
				    else weight += 256;
				} else {
				    if (c==Boolean.TYPE) weight+=15;
				    else if (c==Character.TYPE) weight+=14;
				    else if (c==Byte.TYPE) weight+=13;
				    else if (c==Short.TYPE) weight+=12;
				    else if (c==Integer.TYPE) weight+=11;
				    else if (c==Long.TYPE) weight+=10;
				    else weight += 256;
				}
			    } else if (elem instanceof Boolean) {
				if (c!=Boolean.TYPE) weight+=256;
			    } else if (elem instanceof Character) {
				if (c!=Character.TYPE) weight+=256;
			    } else
				weight += 256;
			} else
			    weight+=256;
		    } else
			weight+=9999;
		} else if ((java.util.Collection.class).isAssignableFrom(parms[i])) {
		    if (!(arg instanceof Map))
			weight+=9999;
		} else if (parms[i].isPrimitive()) {
		    Class c=parms[i];
		    if (arg instanceof Number) {
			if(arg instanceof Double) {
			    if (c==Float.TYPE) weight++;
			    else if (c==Double.TYPE) weight+=0;
			    else weight += 256;
			} else {
			    if (c==Boolean.TYPE) weight+=5;
			    else if (c==Character.TYPE) weight+=4;
			    else if (c==Byte.TYPE) weight+=3;
			    else if (c==Short.TYPE) weight+=2;
			    else if (c==Integer.TYPE) weight++;
			    else if (c==Long.TYPE) weight+=0;
			    else weight += 256;
			}
		    } else if (arg instanceof Boolean) {
			if (c!=Boolean.TYPE) weight+=9999;
		    } else if (arg instanceof Character) {
			if (c!=Character.TYPE) weight+=9999;
		    } else if (arg instanceof String) {
			if (c== Character.TYPE || ((String)arg).length()>0)
			    weight+=((String)arg).length();
			else
			    weight+=64;
		    } else {
			weight+=9999;
		    }
		} else {
		    weight+=9999;
		}
	    }
	    if (weight < best) {
		if (weight == 0) return element;
		best = weight;
		selected = element;
	    }
	}

	return selected;
    }

    //
    // Coerce arguments when possible to conform to the argument list.
    // Java's reflection will automatically do widening conversions,
    // unfortunately PHP only supports wide formats, so to be practical
    // some (possibly lossy) conversions are required.
    //
    Object[] coerce(Class parms[], Object args[], Response response) {
	Object result[] = args;
	Class targetType = null;
	int size = 0;

	for (int i=0; i<args.length; i++) {
	    if (Util.getClass(args[i]) == PhpProcedureProxy.class && parms[i] != PhpProcedureProxy.class) {
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
	    if (args[i] instanceof byte[] && !parms[i].isArray()) {
		Class c = parms[i];
		String s = response.newString((byte[])args[i]);
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
	    } else if (args[i] instanceof Number) {
	    	if (parms[i].isPrimitive()) {
		    if (result==args) result=(Object[])result.clone();
		    Class c = parms[i];
		    Number n = (Number)args[i];
		    if (c == Boolean.TYPE) result[i]=new Boolean(0.0!=n.floatValue());
		    if (c == Byte.TYPE)    result[i]=new Byte(n.byteValue());
		    if (c == Short.TYPE)   result[i]=new Short(n.shortValue());
		    if (c == Integer.TYPE) result[i]=new Integer(n.intValue());
		    if (c == Float.TYPE)   result[i]=new Float(n.floatValue());
		    if (c == Long.TYPE && !(n instanceof Long))
			result[i]=new Long(n.longValue());
	    	} else {
		    if(args[i].getClass()==Request.PhpNumber.class) {
			if (result==args) result=(Object[])result.clone();
	    		if(!response.extJavaCompatibility()) {
			Class c = parms[i];
			if(c.isAssignableFrom(Integer.class)) {
		    		result[i] = new Integer(((Number)args[i]).intValue());
			} else {
		    		result[i] = new Long(((Number)args[i]).longValue());				
			}
		    } else {
	    		result[i] = new Long(((Number)args[i]).longValue());
		    }
	    	}
	    	}
	    } else if ((args[i] != null) && args[i].getClass() == Request.PhpArray.class) {
	    	if(parms[i].isArray()) {
		    try {
			Map ht = (Map)args[i];
			size = ht.size();

			// Verify that the keys are Long, and determine maximum
			for (Iterator e = ht.keySet().iterator(); e.hasNext(); ) {
			    int index = ((Number)e.next()).intValue();
			    if (index >= size) size = index+1;
			}

			Object tempArray[] = new Object[size];
			Class tempTarget[] = new Class[size];
			targetType = parms[i].getComponentType();

			// flatten the hash table into an array
			for (int j=0; j<size; j++) {
			    tempArray[j] = ht.get(new Long(j));
			    if (tempArray[j] == null && targetType.isPrimitive())
				throw new Exception("bail");
			    tempTarget[j] = targetType;
			}

			// coerce individual elements into the target type
			Object coercedArray[] = coerce(tempTarget, tempArray, response);

			// copy the results into the desired array type
			Object array = Array.newInstance(targetType,size);
			for (int j=0; j<size; j++) {
			    Array.set(array, j, coercedArray[j]);
			}

			result[i]=array;
		    } catch (Exception e) {
			logError("Error: " + String.valueOf(e) + ". Could not create array of type: " + targetType + ", size: " + size);
			printStackTrace(e);
			// leave result[i] alone...
		    }
		} else if ((java.util.Collection.class).isAssignableFrom(parms[i])) {
		    try {
			Map ht = (Map)args[i];
			Collection res;
			try {
			    res = (Collection) parms[i].newInstance();
			} catch (java.lang.InstantiationException ex) {
			    res = (Collection) new HashSet();
			}
			for (Iterator e = ht.keySet().iterator(); e.hasNext(); ) {
			    Object key = e.next();
			    Object val = ht.get(key);
			    int index = ((Number)key).intValue();		    // Verify that the keys are Long

			    if(val instanceof byte[]) val = response.newString((byte[])val); // always prefer strings over byte[]
			    res.add(val);
			}

			result[i]=res;
		    } catch (Exception e) {
			logError("Error: " +  String.valueOf(e) + ". Could not create Collection from Map.  You have probably passed a hashtable instead of an array. Please check that the keys are long.");
			printStackTrace(e);
			// leave result[i] alone...
		    }
		} else if ((java.util.Hashtable.class).isAssignableFrom(parms[i])) {
		    try {
			Map ht = (Map)args[i];
			Hashtable res;
			res = (Hashtable)parms[i].newInstance();
			for (Iterator e = ht.keySet().iterator(); e.hasNext(); ) {
			    Object key = e.next();
			    Object val = ht.get(key);

			    if(key instanceof byte[]) key = response.newString((byte[])key); // always prefer strings over byte[]
			    if(val instanceof byte[]) val = response.newString((byte[])val); // always prefer strings over byte[]
			    res.put(key, val);
			}

			result[i]=res;
		    } catch (Exception e) {
			logError("Error: " +  String.valueOf(e) + ". Could not create Hashtable from Map.");
			printStackTrace(e);
			// leave result[i] alone...
		    }
		} else if ((java.util.Map.class).isAssignableFrom(parms[i])) {
		    try {
			Map ht = (Map)args[i];
			Map res;
			try {
			    res = (Map)parms[i].newInstance();
			} catch (java.lang.InstantiationException ex) {
			    res = (Map) new HashMap();
			}
			for (Iterator e = ht.keySet().iterator(); e.hasNext(); ) {
			    Object key = e.next();
			    Object val = ht.get(key);

			    if(key instanceof byte[]) key = response.newString((byte[])key); // always prefer strings over byte[]
			    if(val instanceof byte[]) val = response.newString((byte[])val); // always prefer strings over byte[]
			    res.put(key, val);
			}

			result[i]=res;
		    } catch (Exception e) {
			logError("Error: " +  String.valueOf(e) + ". Could not create Map from Map.");
			printStackTrace(e);
			// leave result[i] alone...
		    }
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
			    if (methods[i].getName().equalsIgnoreCase(method)) {
				candidates.addElement(methods[i]);
				if(methods[i].getParameterTypes().length == args.length) {
				    matches.addElement(methods[i]);
				}
			    }
			}
		    }
		    selected = (Method)select(matches, args);
		    if (selected == null) 
                  	throw new NoSuchMethodException(String.valueOf(method) + "(" + Util.argsToString(args, params) + "). " + "Candidates: " + String.valueOf(candidates));
		    methodCache.put(entry, selected);
		    if(!iter.checkAccessible(selected)) {
			logMessage("Security restriction: Cannot use setAccessible(), reverting to interface searching.");
			canModifySecurityPermission=false;
			candidates.clear(); matches.clear();
			again=true;
		    }
		}
		coercedArgs = coerce(params=selected.getParameterTypes(), args, response);
	    } while(again);
	    if (this.logLevel>4) logInvoke(object, method, coercedArgs); // If we have a logLevel of 5 or above, do very detailed invocation logging
	    setResult(response, selected.invoke(object, coercedArgs));
	} catch (Throwable e) {
	    if(e instanceof OutOfMemoryError ||
	       ((e instanceof InvocationTargetException) &&
		((InvocationTargetException)e).getTargetException() instanceof OutOfMemoryError)) {
		Util.logStream.println("FATAL: OutOfMemoryError");
		throw new OutOfMemoryError(); // abort
	    }
	    if(e instanceof NoClassDefFoundError ||
	       ((e instanceof InvocationTargetException) &&
		((InvocationTargetException)e).getTargetException() instanceof NoClassDefFoundError)) {
		if(e instanceof InvocationTargetException) e = ((InvocationTargetException)e).getTargetException();
		getClassLoader().clearCaches();
		e = new ClassNotFoundException("Unresolved external reference: "+ e+ ". -- Unable to call the method because it or one of its parameters refer to the mentioned external class which is not available in the current \"java_require(<path>)\" url path. Remember that all interconnected classes must be loaded with a single java_require() call and that a class must not appear in more than one java_require() call. Please check the Java Bridge log file for details.", e);
	    }
	    
	    printStackTrace(e);
            if (e instanceof IllegalArgumentException) {
                if (this.logLevel>3) {
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

    public static String objectDebugDescription(Object obj) {
    	if(obj==null) return "[Object null]";
	return "[Object "+System.identityHashCode(obj)+" - Class: "+ classDebugDescription(obj.getClass())+ "]";
    }

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
			if (jfields[i].getName().equals(prop)) {
			    matches.add(jfields[i].getName());
			    Object res=null;
			    if(!(iter.checkAccessible(jfields[i]))) {
				logMessage("Security restriction: Cannot use setAccessible(), reverting to interface searching.");
				canModifySecurityPermission=false;
			        matches.clear();
				break again2;
			    }
			    if (set) {
				args = coerce(params=new Class[] {jfields[i].getType()}, args, response);
				jfields[i].set(object, args[0]);
			    } else {
			    	res=jfields[i].get(object);
			    }
			    setResult(response, res);
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
				logMessage("Security restriction: Cannot use setAccessible(), reverting to interface searching.");
				canModifySecurityPermission=false;
				matches.clear();
				break again1;
			    }
			    setResult(response, method.invoke(object, args));
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
		    java.lang.reflect.Field jfields[] = jclass.getFields();
		    for (int i=0; i<jfields.length; i++) {
			if (jfields[i].getName().equalsIgnoreCase(prop)) {
			    matches.add(prop);
			    Object res=null;
			    if(!(iter.checkAccessible(jfields[i]))) {
				logMessage("Security restriction: Cannot use setAccessible(), reverting to interface searching.");
				canModifySecurityPermission=false;
				matches.clear();
				break again0;
			    }
			    if (set) {
				args = coerce(params=new Class[] {jfields[i].getType()}, args, response);
				jfields[i].set(object, args[0]);
			    } else {
				res = jfields[i].get(object);
			    }
			    setResult(response, res);
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
		Util.logStream.println("FATAL: OutOfMemoryError");
		throw new OutOfMemoryError(); // abort
	    }
	    if(e instanceof NoClassDefFoundError ||
	       ((e instanceof InvocationTargetException) &&
		((InvocationTargetException)e).getTargetException() instanceof NoClassDefFoundError)) {
		if(e instanceof InvocationTargetException) e = ((InvocationTargetException)e).getTargetException();
		getClassLoader().clearCaches();
		e = new ClassNotFoundException("Unresolved external reference: "+e+ ". -- Unable to invoke a property because it or one of its parameters refer to the mentioned external class which is not available in the current \"java_require(<path>)\" url path. Remember that all interconnected classes must be loaded with a single java_require() call and that a class must not appear in more than one java_require() call. Please check the Java Bridge log file for details.", e);
	    }
	    printStackTrace(e);
	    setException(response, e, set?"SetProperty":"GetProperty", object, prop, args, params);
	}
    }

    private static class ForceValuesHook extends Response.ValuesHook {
        public ForceValuesHook(Response res) {
	    super(res);
	}
	public boolean sendArraysAsValues() {
     	    return true;
        }
    }

    /**
     * for PHP5: convert Map or Collection into a PHP array,
     * sends the entire Map or Collection to the client. This
     * is much more efficient than generating round-trips when
     * iterating through a Map or Collection.
     */
    public Object getValues(Object ob) {
    	Response res = request.response;
	res.hook=new ForceValuesHook(res);
	return ob;
    }

    /**
     * Only for internal use. 
     * @param in
     * @param out
     * @see php.java.servlet.PhpJavaServlet
     * @see php.java.bridge.JavaBridgeRunner
    * @see php.java.bridge.main(String[])
      */
    public JavaBridge(InputStream in, OutputStream out) {
	this.in = in;
	this.out = out;
    }

    /**
 * Only for internal use.
    * @see php.java.servlet.PhpJavaServlet.getContext(HttpServletRequest, HttpServletResponse)
     * @see php.java.bridge.main(String[])
    * @see php.java.bridge.JavaBridgeRunner
 */
public JavaBridge() {
	this(null, null);
}
/**
     * Return map for the value (PHP 5 only)
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
     */
    public void setLibraryPath(String _path, String extensionDir) {
        if(_path==null || _path.length()<2) return;

        // add a token separator if first char is alnum
	char c=_path.charAt(0);
	if((c>='A' && c<='Z') || (c>='a' && c<='z') ||
	   (c>='0' && c<='9') || (c!='.' || c!='/'))
	    _path = ";" + _path;

	String path = _path.substring(1);
	StringTokenizer st = new StringTokenizer(path, _path.substring(0, 1));
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
        Vector fields = new Vector(), methods = new Vector();
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
	return buf.toString();
    }
    public void setFileEncoding(String fileEncoding) {
	this.fileEncoding=fileEncoding;
    }

    public static boolean InstanceOf(Object ob, Object c) {
	Class clazz = Util.getClass(c);
	return clazz.isInstance(ob);
    }

    public static String ObjectToString(Object ob) {
	StringBuffer buf = new StringBuffer("[");
	Util.appendObject(ob, buf);
	buf.append("]");
	return buf.toString();
    }
    public Object getContext() {
    	return sessionFactory.getContext();
    }
    /**
     * Return a session handle shared among all JavaBridge
     * instances. If it is a HTTP session, the session is shared with
     * the servlet or jsp.
     * @see ISession
     */
    public ISession getSession(String name, boolean clientIsNew, int timeout){
    	try {
	    ISession session= sessionFactory.getSession(name, clientIsNew, timeout);
	    if(session==null) throw new NullPointerException("Isession is null");
	    return session;
    	} catch (Throwable t) {
	    printStackTrace(t);
	    return null;
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
     * @returnthe proxy
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
     * Reset the global caches of the bridge.
     * Currently this is the classloader and the session.
     */
    public void reset() {
	warn("Your PHP script has called the privileged procedure \"reset()\", which resets the backend to its initial state. Therefore all session variables and all caches are now gone.");
	Session.reset(this);
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
     * Deserialize serialID
     * @param serialID
     * @param timeout
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
     * Serialize the object obj and return the serial id.
     * @param obj
     * @param timeout
     * @return the serialID
     */
    public String serialize(Object obj, int timeout) {
    	ISession session = defaultSessionFactory.getSession(null, false, timeout);
    	String id = Integer.toHexString(getSerialID());
    	session.put(id, obj);
    	return id;
    }
/**
 * @param cl The cl to set.
 */
public void setClassLoader(JavaBridgeClassLoader cl) {
	this.cl = cl;
}
/**
 * @return Returns the cl.
 */
public JavaBridgeClassLoader getClassLoader() {
	return cl;
}
}
