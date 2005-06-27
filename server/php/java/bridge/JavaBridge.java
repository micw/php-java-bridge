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
import java.util.Hashtable;
import java.util.Iterator;
import java.util.Map;
import java.util.Vector;


public class JavaBridge implements Runnable {


    // The client's file encoding, for example UTF-8.
    String fileEncoding="UTF-8";
    // For PHP4's last_exception_get.
    public Throwable lastException = null;

    // list of objects in use in the current script
    GlobalRef globalRef=new GlobalRef(this);

    public JavaBridgeClassLoader cl = null;

    static HashMap sessionHash = new HashMap();

    public InputStream in; public OutputStream out;

    int logLevel = Util.logLevel;
    // the current request (if any)
    Request request;

    boolean canModifySecurityPermission = true;


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

    private static boolean haveNatcJavaBridge=true;

    static Object loadLock=new Object();
    static short load = 0;
    public static short getLoad() {
	synchronized(loadLock) {
	    return load;
	}
    }




    //
    // Communication with client in a new thread
    //
    public void run() {
        try {
          logDebug("START: JavaBridge.run()");
          try {
            cl = new JavaBridgeClassLoader(this, (DynamicJavaBridgeClassLoader) Thread.currentThread().getContextClassLoader());
          } catch (ClassCastException ex) {
            cl = new JavaBridgeClassLoader(this, null);
          }
          Util.logStream.flush();
          request = new Request(this);
          try {
              Util.logStream.flush();
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

    //
    // init
    //
    public static void init(String s[]) {
	String logFile=null;
	String sockname=null;
	ISocketFactory socket = null;
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
	    } catch (Throwable t) {
		Util.printStackTrace(t);
	    }

	    try {
		logFile=Util.EXTENSION_NAME+".log";
		if(s.length>2) {
		    logFile=s[2];
		}
		if(Util.logLevel>3) System.out.println(Util.EXTENSION_NAME+" log: " + logFile);
	    }catch (Throwable t) {
		Util.printStackTrace(t);
	    }
	    boolean redirectOutput = false;
	    try {
	    	redirectOutput = openLog(logFile);
	    } catch (Throwable t) {
	    }

	    if(!redirectOutput) {
		try {
		    Util.logStream=new java.io.PrintStream(new java.io.FileOutputStream(logFile));
		} catch (Exception e) {
		    Util.logStream=System.out;
		}
	    } else {
		Util.logStream=System.out;
		logFile="<stdout>";
	    }
	    try {
		socket = LocalServerSocket.create(sockname, Util.BACKLOG);
	    } catch (Throwable e) {
		Util.logMessage("Local sockets not available:" + e + ". Try TCP sockets instead");
	    }
	    if(null==socket)
		socket = TCPServerSocket.create(sockname, Util.BACKLOG);

	    if(null==socket)
		throw new Exception("Could not create socket: " + sockname);

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
            DynamicJavaBridgeClassLoader.initClassLoader("/usr/share/java");
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

    public void printStackTrace(Throwable t) {
	if(logLevel > 0)
	    if ((t instanceof Error) || logLevel > 1)
	    	Util.logger.printStackTrace(t);
    }
    public void logDebug(String msg) {
	if(logLevel>3) Util.println(4, this + " " + msg);
    }
    public void logFatal(String msg) {
	if(logLevel>0) Util.println(1, this + " " + msg);
    }
    public void logError(String msg) {
	if(logLevel>1) Util.println(2, this + " " + msg);
    }
    public void logMessage(String msg) {
	if(logLevel>2) Util.println(3, this + " " + msg);
    }

    void setException(Response response, Throwable e, String method, Object obj, String name, Object args[]) {
	if (e instanceof InvocationTargetException) {
	    Throwable t = ((InvocationTargetException)e).getTargetException();
	    if (t!=null) e=t;
	}

	StringBuffer buf=new StringBuffer(method);
	buf.append(" failed: ");
	if(obj!=null) {
	    buf.append("[");
	    buf.append(String.valueOf(obj));
	    buf.append("]->");
	} else {
	    buf.append("new ");
	}
	buf.append(name);
	String arguments = Util.argsToString(args);
	if(arguments.length()>0) {
	    buf.append("(");
	    buf.append(arguments);
	    buf.append(")");
	}
	buf.append(".");
	buf.append(" Cause: ");
	buf.append(String.valueOf(e));

	lastException = new Exception(buf.toString(), e);
	response.writeException(lastException, lastException.toString());
    }

    //
    // Create an new instance of a given class
    //
    public void CreateObject(String name, boolean createInstance,
			     Object args[], Response response) {
	try {
	    Vector matches = new Vector();
	    Vector candidates = new Vector();
	    Constructor selected = null;
	    if(createInstance) {
		Constructor cons[] = cl.getClassLoader().loadClass(name).getConstructors();
		for (int i=0; i<cons.length; i++) {
		    candidates.addElement(cons[i]);
		    if (cons[i].getParameterTypes().length == args.length) {
			matches.addElement(cons[i]);
		    }
		}

		selected = (Constructor)select(matches, args);
	    }

	    if (selected == null) {
		if (args.length > 0) {
		    throw new InstantiationException("No matching constructor found. " + "Candidates: " + String.valueOf(candidates));
		} else {
		    // for classes which have no visible constructor, return the class
		    // useful for classes like java.lang.System and java.util.Calendar.
		    response.writeObject(cl.getClassLoader().loadClass(name));
		    return;
		}
	    }

	    Object coercedArgs[] = coerce(selected.getParameterTypes(), args, response);
	    try {
	    	response.writeObject(selected.newInstance(coercedArgs));
	    } catch (NoClassDefFoundError xerr) {
	    	logError("Error: Could not invoke constructor. A class referenced in the constructor method could not be found: " + xerr + ". Please correct this error or use \"new JavaClass()\" to avoid calling the constructor.");
	    	cl.reset();
	    	response.writeObject(cl.getClassLoader().loadClass(name));
	    }

	} catch (Throwable e) {
	    if(e instanceof OutOfMemoryError ||
	       ((e instanceof InvocationTargetException) &&
		((InvocationTargetException)e).getTargetException() instanceof OutOfMemoryError)) {
		Util.logStream.println("FATAL: OutOfMemoryError");
		throw new RuntimeException(); // abort
	    }
	    printStackTrace(e);
	    setException(response, e, createInstance?"CreateInstance":"ReferenceClass", null, name, args);
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
		if (parms[i].isInstance(args[i])) {
		    for (Class c=args[i].getClass(); (c=c.getSuperclass()) != null; ) {
			if (!parms[i].isAssignableFrom(c)) {
			    if (args[i] instanceof byte[]) { //special case: when arg is a byte array we always prefer a String parameter (if it exists).
				weight+=1;
			    }
			    break;
			}
			weight+=256; // prefer more specific arg, for
				     // example AbstractMap hashMap
				     // over Object hashMap.
		    }
		} else if (parms[i].isAssignableFrom(java.lang.String.class)) {
		    if (!(args[i] instanceof byte[]) && !(args[i] instanceof String))
			weight+=9999;
		} else if (parms[i].isArray()) {
		    if (args[i] instanceof java.util.Map) {
			Iterator iterator = ((Map)args[i]).values().iterator();
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
			    } else
				weight += 256;
			} else
			    weight+=256;
		    } else
			weight+=9999;
		} else if ((java.util.Collection.class).isAssignableFrom(parms[i])) {
		    if (!(args[i] instanceof Map))
			weight+=9999;
		} else if (parms[i].isPrimitive()) {
		    Class c=parms[i];
		    if (args[i] instanceof Number) {
			if(args[i] instanceof Double) {
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
		    } else if (args[i] instanceof Boolean) {
			if (c!=Boolean.TYPE) weight+=9999;
		    } else if (args[i] instanceof String) {
			if (c== Character.TYPE || ((String)args[i]).length()>0)
			    weight+=((String)args[i]).length();
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
    private Object[] coerce(Class parms[], Object args[], Response response) {
	Object result[] = args;
	Class targetType = null;
	int size = 0;

	for (int i=0; i<args.length; i++) {
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
	    } else if (args[i] instanceof Number && parms[i].isPrimitive()) {
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
	    } else if (args[i] instanceof Map) {
	    	if(parms[i].isArray()) {
		    try {
			Map ht = (Map)args[i];
			size = ht.size();

			// Verify that the keys are Long, and determine maximum
			for (Iterator e = ht.keySet().iterator(); e.hasNext(); ) {
			    int index = ((Long)e.next()).intValue();
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
			logError("Error: " + String.valueOf(e) + " could not create array of type: " + targetType + ", size: " + size);
			printStackTrace(e);
			// leave result[i] alone...
		    }
		} else if ((java.util.Collection.class).isAssignableFrom(parms[i])) {
		    try {
			Map ht = (Map)args[i];
			Collection res = (Collection) parms[i].newInstance();
			for (Iterator e = ht.keySet().iterator(); e.hasNext(); ) {
			    Object key = e.next();
			    Object val = ht.get(key);
			    int index = ((Long)key).intValue();		    // Verify that the keys are Long

			    if(val instanceof byte[]) val = response.newString((byte[])val); // always prefer strings over byte[]
			    res.add(val);
			}

			result[i]=res;
		    } catch (Exception e) {
			logError("Error: " +  String.valueOf(e) + " Could not create java.util.Map.  You have probably passed a hashtable instead of an array. Please check that the keys are long.");
			printStackTrace(e);
			// leave result[i] alone...
		    }
		} else if ((java.util.Hashtable.class).isAssignableFrom(parms[i])) {
		    try {
			Map ht = (Map)args[i];
			Hashtable res = (Hashtable)parms[i].newInstance();
			for (Iterator e = ht.keySet().iterator(); e.hasNext(); ) {
			    Object key = e.next();
			    Object val = ht.get(key);

			    if(key instanceof byte[]) key = response.newString((byte[])key); // always prefer strings over byte[]
			    if(val instanceof byte[]) val = response.newString((byte[])val); // always prefer strings over byte[]
			    res.put(key, val);
			}

			result[i]=res;
		    } catch (Exception e) {
			logError("Error: " +  String.valueOf(e) + " Could not create java.util.Hashtable.");
			printStackTrace(e);
			// leave result[i] alone...
		    }
		} else if ((java.util.Map.class).isAssignableFrom(parms[i])) {
		    try {
			Map ht = (Map)args[i];
			Map res = (Map)parms[i].newInstance();
			for (Iterator e = ht.keySet().iterator(); e.hasNext(); ) {
			    Object key = e.next();
			    Object val = ht.get(key);

			    if(key instanceof byte[]) key = response.newString((byte[])key); // always prefer strings over byte[]
			    if(val instanceof byte[]) val = response.newString((byte[])val); // always prefer strings over byte[]
			    res.put(key, val);
			}

			result[i]=res;
		    } catch (Exception e) {
			logError("Error: " +  String.valueOf(e) + " Could not create java.util.Hashtable.");
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

    //
    // Invoke a method on a given object
    //
    public void Invoke
	(Object object, String method, Object args[], Response response)
    {
       Vector matches = new Vector();
       Vector candidates = new Vector();
       Class jclass;
       boolean again;
       Object coercedArgs[] = null;
       Class paramClasses[] = new Class[args.length];
       for (int p=0;p<args.length;p++) {
          paramClasses[p] = args[p].getClass();
       }
       Method selected = null;
	try {
            try {
              selected = object.getClass().getMethod(method, paramClasses); // Let's try to find the recommended method first
              if (this.logLevel>4) logInvoke(object, method, args); // If we have a logLevel of 5 or above, do very detailed invocation logging
              setResult(response, selected.invoke(object, args));
	    } catch (NoSuchMethodException nsme) {
            }
            if (selected==null) {
              // gather
              do {
                  again = false;
                  ClassIterator iter;
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
                  if (selected == null) throw new NoSuchMethodException(String.valueOf(method) + "(" + Util.argsToString(args) + "). " + "Candidates: " + String.valueOf(candidates));
                  coercedArgs = coerce(selected.getParameterTypes(), args, response);
                  if(!iter.checkAccessible(selected)) {
                      logMessage("Security restriction: Cannot use setAccessible(), reverting to interface searching.");
                      canModifySecurityPermission=false;
                      candidates.clear(); matches.clear();
                      again=true;
                  }
              } while(again);
              if (this.logLevel>4) logInvoke(object, method, coercedArgs); // If we have a logLevel of 5 or above, do very detailed invocation logging
              setResult(response, selected.invoke(object, coercedArgs));
            }
	} catch (Throwable e) {
	    if(e instanceof OutOfMemoryError ||
	       ((e instanceof InvocationTargetException) &&
		((InvocationTargetException)e).getTargetException() instanceof OutOfMemoryError)) {
		Util.logStream.println("FATAL: OutOfMemoryError");
		throw new RuntimeException(); // abort
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
	    setException(response, e, "Invoke", object, method, args);
	}
    }

    public static String objectDebugDescription(Object obj) {
      return "[Object "+System.identityHashCode(obj)+" - Class: "+ classDebugDescription(obj.getClass())+ "]";
    }

    public static String classDebugDescription(Class cls) {
      return cls.getName() + ":ID" + System.identityHashCode(cls) + ":LOADER-ID"+System.identityHashCode(cls.getClassLoader());
    }

    //
    // Get or Set a property
    //
    public void GetSetProp
	(Object object, String prop, Object args[], Response response)
    {
	boolean set = (args!=null && args.length>0);
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
				args = coerce(new Class[] {jfields[i].getType()}, args, response);
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
				args = coerce(method.getParameterTypes(), args, response);
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
				args = coerce(new Class[] {jfields[i].getType()}, args, response);
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
	    throw new NoSuchFieldException(String.valueOf(prop) + " (with args:" + Util.argsToString(args) + "). " + "Candidates: " + String.valueOf(matches));

	} catch (Throwable e) {
	    if(e instanceof OutOfMemoryError ||
	       ((e instanceof InvocationTargetException) &&
		((InvocationTargetException)e).getTargetException() instanceof OutOfMemoryError)) {
		Util.logStream.println("FATAL: OutOfMemoryError");
		throw new RuntimeException(); // abort
	    }
	    printStackTrace(e);
	    setException(response, e, set?"SetProperty":"GetProperty", object, prop, args);
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

    /*
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

    public JavaBridge(InputStream in, OutputStream out) {
      this.in = in;
      this.out = out;
    }

    //
    // Return map for the value (PHP 5 only)
    //
    public PhpMap getPhpMap(Object value) {
	return PhpMap.getPhpMap(value, this);
    }

    // Set the library path for the java bridge. Examples:
    // setJarLibPath(";file:///tmp/test.jar;file:///tmp/my.jar");
    // setJarLibPath("|file:c:/t.jar|http://.../a.jar|jar:file:///tmp/x.jar!/");
    // The first char must be the token separator.
    public void setJarLibraryPath(String _path) {
    	cl.updateJarLibraryPath(_path);
    }

    public void setFileEncoding(String fileEncoding) {
	this.fileEncoding=fileEncoding;
    }

    public static boolean InstanceOf(Object ob, Object c) {
	Class clazz = Util.GetClass(c);
	return clazz.isInstance(ob);
    }

    public static String ObjectToString(Object ob) {
	StringBuffer buf = new StringBuffer("[");
	buf.append(String.valueOf(ob.getClass()));
	buf.append(": ");
	buf.append(String.valueOf(ob));
	buf.append("]");
	return buf.toString();
    }

    public Session getSession(String name, boolean clientIsNew, int timeout) {
    	synchronized(JavaBridge.sessionHash) {
	    Session ref = null;
	    if(!JavaBridge.sessionHash.containsKey(name)) {
		ref = new Session(name);
		ref.setTimeout(1000*(long)timeout);
	    } else {
		ref = (Session) JavaBridge.sessionHash.get(name);
		if(clientIsNew) { // client side gc'ed, destroy server ref now!
		    ref.destroy();
		    ref = new Session(name);
		    ref.setTimeout(1000*(long)timeout);
		} else {
		    ref.isNew=false;
		}
	    }

	    JavaBridge.sessionHash.put(name, ref);
	    return ref;
    	}
    }


}
