import java.beans.BeanInfo;
import java.beans.Introspector;
import java.beans.PropertyDescriptor;
import java.io.IOException;
import java.lang.reflect.Array;
import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.net.Socket;
import java.lang.ref.WeakReference;
import java.util.*;
import java.io.*;
import java.net.*;

public class JavaBridge implements Runnable {

    static PrintStream logStream;
    static int logLevel;

    // class hash
    private static final HashMap classes = new HashMap(); 

    // the list of jar files in which we search for user classes.
    static private Collection sysUrls = null;

    // the list of jar files in which we search for user classes.  can
    // be changed with setLibraryPath
    private Collection urls = null;

    // We now *must* use an inner class and handle all those x$y.class
    // garbage during installation because the IBM 1.4.1 JVM crashes if
    // JavaBridge implements both Runnable and extends ClassLoader:
    // SIGSEGV received in clRemoveClassesFromJIT at 0x4010b8ea in
    // [...]/classic/libjvm.so. Processing terminated. Bleh!
    ClassLoader cl = new ClassLoader() {
	    // Read the class from input stream and return bytes or null
	    private byte[] read (InputStream in, int length) throws java.io.IOException {
		int c, pos;
		byte[] b = new byte[length];
		    
		for(pos=0; (c=in.read(b, pos, b.length-pos))<length-pos; pos+=c) {
		    if(c<0) { in.close(); return null; }
		}
	
		in.close();
		return b;
	    }

	    // Read the class in 8K chunks until EOF, return bytes or null
	    private byte[] readChunks(InputStream in) throws java.io.IOException {
		int c, pos;
		int len = 8192;
		byte[] b = new byte[len];

		Collection buffers = new ArrayList();
		while(true) {
		    for(pos=0; (c=in.read(b, pos, len-pos))<len-pos; pos+=c) {
			if(c<0) break;
		    }
		    if(c<0) break;
		    buffers.add(b);
		    b=new byte[len];
		}
		byte[] result = new byte[buffers.size() * len + pos];
		int p=0;
		for (Iterator i=buffers.iterator(); i.hasNext(); p+=len){
		    byte[] _b = (byte[])i.next();
		    System.arraycopy(_b, 0, result, p, len);
		}
		System.arraycopy(b, 0, result, p, pos);
		in.close();
		return result;
	    }	

	    byte[] load(URL u, String name) {
		logMessage("try to load class " + name + " from " + u);
		try {
		    byte b[]=null;
		    int pt;
		    String p, h, f;
		    
		    p = u.getProtocol(); h = u.getHost(); 
		    pt = u.getPort(); f = u.getFile();
		    URL url = new URL(p ,h , pt, f+name.replace('.','/')+".class");
		    
		    URLConnection con = url.openConnection();
		    con.connect();
		    int length = con.getContentLength();
		    InputStream in = con.getInputStream();

		    if(length > 0) 
			b = read(in, length);
		    else if(length < 0) // bug in gcj
			b = readChunks(in);

		    return b;

		} catch (Exception e) {
		    return null;
		}
	    }
	    public Class findClass(String name) throws ClassNotFoundException {
		Class c = null;
		byte[] b = null;

		synchronized(classes) {
		    Object o = classes.get(name);
		    if(o!=null) o = ((WeakReference)o).get();
		    if(o!=null) c = (Class)o;
		    if(c!=null) return c;
		    try {
			return ClassLoader.getSystemClassLoader().loadClass(name);
		    } catch (ClassNotFoundException e) {};
		    
		    Collection[] allUrls = {urls, sysUrls};
		    for(int n=0; b==null && n<allUrls.length; n++) {
			Collection urls = allUrls[n];
			if(urls!=null) 
			for (Iterator i=urls.iterator(); i.hasNext(); ) 
			    if ((b=load((URL)i.next(), name))!=null) break;
		    }
		    if (b==null) throw new ClassNotFoundException(name + " neither found in path: " + String.valueOf(urls) + " nor in the system path: "+ String.valueOf(sysUrls));

		    if((c = this.defineClass(name, b, 0, b.length)) != null) classes.put(name, new WeakReference(c));
		}
		return c;
	    }
	};

    //
    // Native methods
    //
    static native void startNative(int logLevel, String sockname);
    static native void setResultFromString(long result, long peer, byte value[]);
    static native void setResultFromLong(long result, long peer, long value);
    static native void setResultFromDouble(long result, long peer, double value);
    static native void setResultFromBoolean(long result, long peer, boolean value);
    static native void setResultFromObject(long result, long peer, Object value);
    static native void setResultFromArray(long result, long peer);
    static native long nextElement(long array, long peer);
    static native long hashUpdate(long array, long peer, byte key[]);
    static native long hashIndexUpdate(long array, long peer, long key);
    static native void setException(long result, long peer, Throwable value, byte strValue[]);
    native void handleRequests(long peer);
	
    //
    // Helper routines for the C implementation
    //
    public Object MakeArg(boolean b) { return new Boolean(b); }
    public Object MakeArg(long l)    { return new Long(l); }
    public Object MakeArg(double d)  { return new Double(d); }

    // 
    // Communication with client in a new thread
    //
    private long peer;
    public void run() { handleRequests(peer); }
    public static void HandleRequests(long peer) {  
	JavaBridge bridge = new JavaBridge();
	Thread thread = new Thread(bridge);
	bridge.peer=peer;
	thread.setContextClassLoader(bridge.cl);
	thread.start();
    }

    //
    // add all jars found in the phpConfigDir/lib and /usr/share/java
    // to our classpath
    //
    static void addSystemLibraries(String phpConfigDir) {
	try {
	    String[] paths = {phpConfigDir+"/lib", "/usr/share/java"};
	    for(int i=0; i<paths.length; i++) {
		File d = new File(paths[i]);
		String[] files=d.list();
		if(files==null) continue;
		for(int j=0; j<files.length; j++) {
		    String file = files[j];
		    int len = file.length();
		    if(len<4) continue;
		    if(!file.endsWith(".jar")) continue;
		    try {
			URL url;
			file = "jar:file:" + d.getAbsolutePath() + File.separator + file + "!/";
			url = new URL(file);
			if(sysUrls==null) sysUrls=new ArrayList();
			logMessage("added system library: " + url);
			sysUrls.add(url);
		    }  catch (MalformedURLException e1) {
			printStackTrace(e1);
		    }
		}
	    }
	} catch (Exception t) {
	    printStackTrace(t);
	}
    }
    
		
    //
    // init
    //
    static void init(String s[]) {
	String logFile=null;
	String sockname=null;
	try {
	    if(s.length>0) {
		sockname=s[0];
	    } else {
		JavaBridge.logError("No socket.  You must pass the socket filename, for example /tmp/.report_bridge");
		System.exit(12);
	    }
	    try {
		if(s.length>1) {
		    JavaBridge.logLevel=Integer.parseInt(s[1]);
		}
	    } catch (Throwable t) {
		t.printStackTrace();
	    }
	    try {
		if(s.length>2) {
		    logFile=s[2];
		    if(logFile==null||logFile.trim().length()==0)
			JavaBridge.logStream=System.out;
		    else
			JavaBridge.logStream=new java.io.PrintStream(new java.io.FileOutputStream(logFile));
		}
	    }catch (Throwable t) {
		t.printStackTrace();
	    }
	    if(JavaBridge.logLevel>3) System.out.println("Java log         :" + logFile);
	    JavaBridge.logMessage("Java logFile     : " + logFile);
	    JavaBridge.logMessage("Java logLevel    : " + JavaBridge.logLevel);
	    JavaBridge.logMessage("Java socket      : " + sockname);
	    JavaBridge.startNative(JavaBridge.logLevel, sockname);
	    System.exit(0);
	} catch (Throwable t) {
	    printStackTrace(t);
	    System.exit(1);
	}
    }



    public static void printStackTrace(Throwable t) {
	if(logLevel>0) t.printStackTrace(logStream);
    }
    public static void logDebug(String msg) {
	if(logLevel>3) logStream.println(msg);
    }
    public static void logError(String msg) {
	if(logLevel>0) logStream.println(msg);
    }
    public static void logMessage(String msg) {
	if(logLevel>2) logStream.println(msg);
    }

    public static void main(String s[]) {
	try {
	    System.loadLibrary("natcJavaBridge");
	} catch (Throwable t) {
	    t.printStackTrace();
	    System.exit(9);
	}
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
    static void setResult(long result, long peer, Object value) {
	if (value == null) return;

	if (value instanceof byte[]) {
	    JavaBridge.setResultFromString(result, peer, (byte[])value);
	} else if (value instanceof java.lang.String) {
	    JavaBridge.setResultFromString(result, peer, ((String)value).getBytes());
	} else if (value instanceof java.lang.Number) {

	    if (value instanceof java.lang.Integer ||
		value instanceof java.lang.Short ||
		value instanceof java.lang.Byte) {
		JavaBridge.setResultFromLong(result, peer, ((Number)value).longValue());
	    } else {
		/* Float, Double, BigDecimal, BigInteger, Double, Long, ... */
		JavaBridge.setResultFromDouble(result, peer, ((Number)value).doubleValue());
	    }

	} else if (value instanceof java.lang.Boolean) {

	    JavaBridge.setResultFromBoolean(result, peer, ((Boolean)value).booleanValue());

	} else if (value.getClass().isArray()) {

	    long length = Array.getLength(value);
	    JavaBridge.setResultFromArray(result, peer);
	    for (int i=0; i<length; i++) {
		setResult(JavaBridge.nextElement(result, peer), peer, Array.get(value, i));
	    }

	} else if (value instanceof java.util.Hashtable) {

	    Hashtable ht = (Hashtable) value; 
	    JavaBridge.setResultFromArray(result, peer);
	    for (Enumeration e = ht.keys(); e.hasMoreElements(); ) {
		Object key = e.nextElement();
		long slot;
		if (key instanceof Number && 
		    !(key instanceof Double || key instanceof Float))
		    slot = JavaBridge.hashIndexUpdate(result, peer, ((Number)key).longValue());
		else
		    slot = JavaBridge.hashUpdate(result, peer, key.toString().getBytes());
		setResult(slot, peer, ht.get(key));
	    }

	} else {

	    JavaBridge.setResultFromObject(result, peer, value);

	}
    }
    Throwable lastException = null;

    void lastException(long result, long peer) {
	setResult(result, peer, lastException);
    }

    void clearException() {
	lastException = null;
    }


    void setException(long result, long peer, Throwable e) {
	if (e instanceof InvocationTargetException) {
	    Throwable t = ((InvocationTargetException)e).getTargetException();
	    if (t!=null) e=t;
	}

	lastException = e;
	JavaBridge.setException(result, peer, e, e.toString().getBytes());
    }

    //
    // Create an new instance of a given class
    //
    public void CreateObject(String name, Object args[], long result, long peer) {
	try {
	    Vector matches = new Vector();

	    Constructor cons[] = Class.forName(name, true, cl).getConstructors();
	    for (int i=0; i<cons.length; i++) {
		if (cons[i].getParameterTypes().length == args.length) {
		    matches.addElement(cons[i]);
		}
	    }

	    Constructor selected = (Constructor)select(matches, args);

	    if (selected == null) {
		if (args.length > 0) {
		    throw new InstantiationException("No matching constructor found");
		} else {
		    // for classes which have no visible constructor, return the class
		    // useful for classes like java.lang.System and java.util.Calendar.
		    setResult(result, peer, Class.forName(name, true, cl));
		    return;
		}
	    }

	    Object coercedArgs[] = coerce(selected.getParameterTypes(), args);
	    JavaBridge.setResultFromObject(result, peer, selected.newInstance(coercedArgs));

	} catch (Throwable e) {
	    printStackTrace(e);
	    // Special handling of our connection abort
	    // throwable.  We can't use our own (inner)
	    // exception class because that would mean we
	    // have to deal with a classname that contains
	    // a $ sign in its name during the bridge
	    // install procedure
	    if(e.getMessage()!=null &&
	       e.getMessage().startsWith("child aborted connection during"))
		throw new RuntimeException();

	    setException(result, peer, e);
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
		    for (Class c=parms[i]; (c=c.getSuperclass()) != null; ) {
			if (!c.isInstance(args[i])) break;
			weight++;
		    }
		} else if (parms[i].isAssignableFrom(java.lang.String.class)) {
		    if (!(args[i] instanceof byte[]) && !(args[i] instanceof String))
			weight+=9999;
		} else if (parms[i].isArray()) {
		    if (args[i] instanceof java.util.Hashtable)
			weight+=256;
		    else
			weight+=9999;
		} else if (parms[i].isPrimitive()) {
		    Class c=parms[i];
		    if (args[i] instanceof Number) {
			if (c==Boolean.TYPE) weight+=5;
			if (c==Character.TYPE) weight+=4;
			if (c==Byte.TYPE) weight+=3;
			if (c==Short.TYPE) weight+=2;
			if (c==Integer.TYPE) weight++;
			if (c==Float.TYPE) weight++;
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
    private static Object[] coerce(Class parms[], Object args[]) {
	Object result[] = args;
	for (int i=0; i<args.length; i++) {
	    if (args[i] instanceof byte[] && !parms[i].isArray()) {
		Class c = parms[i];
		String s = new String((byte[])args[i]);
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
	    } else if (args[i] instanceof Hashtable && parms[i].isArray()) {
		try {
		    Hashtable ht = (Hashtable)args[i];
		    int size = ht.size();

		    // Verify that the keys are Long, and determine maximum
		    for (Enumeration e = ht.keys(); e.hasMoreElements(); ) {
			int index = ((Long)e.nextElement()).intValue();
			if (index >= size) size = index+1;
		    }

		    Object tempArray[] = new Object[size];
		    Class tempTarget[] = new Class[size];
		    Class targetType = parms[i].getComponentType();

		    // flatten the hash table into an array
		    for (int j=0; j<size; j++) {
			tempArray[j] = ht.get(new Long(j));
			if (tempArray[j] == null && targetType.isPrimitive()) 
			    throw new Exception("bail");
			tempTarget[j] = targetType;
		    }

		    // coerce individual elements into the target type
		    Object coercedArray[] = coerce(tempTarget, tempArray);
        
		    // copy the results into the desired array type
		    Object array = Array.newInstance(targetType,size);
		    for (int j=0; j<size; j++) {
			Array.set(array, j, coercedArray[j]);
		    }

		    result[i]=array;
		} catch (Exception e) {
		    printStackTrace(e);
		    // leave result[i] alone...
		}
	    }
	}
	return result;
    }

    //
    // Invoke a method on a given object
    //
    public void Invoke
	(Object object, String method, Object args[], long result, long peer)
    {
	try {
	    Vector matches = new Vector();

	    // gather
	    for (Class jclass = object.getClass();;jclass=(Class)object) {
		while (!Modifier.isPublic(jclass.getModifiers())) {
		    // OK, some joker gave us an instance of a non-public class
		    // This often occurs in the case of enumerators
		    // Substitute the first public interface in its place,
		    // and barring that, try the superclass
		    Class interfaces[] = jclass.getInterfaces();
		    jclass=jclass.getSuperclass();
		    for (int i=interfaces.length; i-->0;) {
			if (Modifier.isPublic(interfaces[i].getModifiers())) {
			    jclass=interfaces[i];
			}
		    }
		}
		Method methods[] = jclass.getMethods();
		for (int i=0; i<methods.length; i++) {
		    if (methods[i].getName().equalsIgnoreCase(method) &&
			methods[i].getParameterTypes().length == args.length) {
			matches.addElement(methods[i]);
		    }
		}

		// try a second time with the object itself, if it is of type Class
		if (!(object instanceof Class) || (jclass==object)) break;
	    }
	    Method selected = (Method)select(matches, args);
	    if (selected == null) throw new NoSuchMethodException(method);

	    Object coercedArgs[] = coerce(selected.getParameterTypes(), args);
	    setResult(result, peer, selected.invoke(object, coercedArgs));

	} catch (Throwable e) {
	    printStackTrace(e);
	    // Special handling of our connection abort
	    // throwable.  We can't use our own (inner)
	    // exception class because that would mean we
	    // have to deal with a classname that contains
	    // a $ sign in its name during the bridge
	    // install procedure
	    if(e.getMessage()!=null &&
	       e.getMessage().startsWith("child aborted connection during"))
		throw new RuntimeException();
	    setException(result, peer, e);
	}
    }

    //
    // Get or Set a property
    //
    public void GetSetProp
	(Object object, String prop, Object args[], long result, long peer)
    {
	try {

	    for (Class jclass = object.getClass();;jclass=(Class)object) {
		while (!Modifier.isPublic(jclass.getModifiers())) {
		    // OK, some joker gave us an instance of a non-public class
		    // Substitute the first public interface in its place,
		    // and barring that, try the superclass
		    Class interfaces[] = jclass.getInterfaces();
		    jclass=jclass.getSuperclass();
		    for (int i=interfaces.length; i-->0;) {
			if (Modifier.isPublic(interfaces[i].getModifiers())) {
			    jclass=interfaces[i];
			}
		    }
		}
		BeanInfo beanInfo = Introspector.getBeanInfo(jclass);
		PropertyDescriptor props[] = beanInfo.getPropertyDescriptors();
		for (int i=0; i<props.length; i++) {
		    if (props[i].getName().equalsIgnoreCase(prop)) {
			Method method;
			if (args!=null && args.length>0) {
			    method=props[i].getWriteMethod();
			    args = coerce(method.getParameterTypes(), args);
			} else {
			    method=props[i].getReadMethod();
			}
			setResult(result, peer, method.invoke(object, args));
			return;
		    }
		}

		java.lang.reflect.Field jfields[] = jclass.getFields();
		for (int i=0; i<jfields.length; i++) {
		    if (jfields[i].getName().equalsIgnoreCase(prop)) {
			if (args!=null && args.length>0) {
			    args = coerce(new Class[] {jfields[i].getType()}, args);
			    jfields[i].set(object, args[0]);
			} else {
			    setResult(result, peer, jfields[i].get(object));
			}
			return;
		    }
		}

		// try a second time with the object itself, if it is of type Class
		if (!(object instanceof Class) || (jclass==object)) break;
	    }

	} catch (Throwable e) {
	    printStackTrace(e);
	    // Special handling of our connection abort
	    // throwable.  We can't use our own (inner)
	    // exception class because that would mean we
	    // have to deal with a classname that contains
	    // a $ sign in its name during the bridge
	    // install procedure
	    if(e.getMessage()!=null &&
	       e.getMessage().startsWith("child aborted connection during"))
		throw new RuntimeException();
	    setException(result, peer, e);
	}
    }

    // Set the library path for the java bridge. Examples:
    // setJarLibPath(";file:///tmp/test.jar;file:///tmp/my.jar");
    // setJarLibPath("|file:c:/t.jar|http://.../a.jar|jar:file:///tmp/x.jar!/");
    // The first char must be the token separator.
    public void setJarLibraryPath(String _path) {
	urls = new ArrayList();
	if(_path==null || _path.length()<2) return;

	// add a token separator if first char is alnum
	char c=_path.charAt(0);
	if((c>='A' && c<='Z') || (c>='a' && c<='z') ||
	   (c>='0' && c<='9') || (c!='.' || c!='/'))
	    _path = ";" + _path;

	String path = _path.substring(1);
	StringTokenizer st = new StringTokenizer(path, _path.substring(0, 1));
	while (st.hasMoreTokens()) {
	    URL url;
	    String p, s;
	    s = st.nextToken();

	    try {
		url = new URL(s);
		p = url.getProtocol(); 
	    } catch (MalformedURLException e) {
		try {
		    s = "file:" + s;
		    url = new URL(s);
		    p = url.getProtocol();
		}  catch (MalformedURLException e1) {
		    printStackTrace(e1);
		    continue;
		}
	    }
   
	    if(p.equals("jar")) {
		urls.add(url);
		continue;
	    }
	    try {
		urls.add(new URL("jar:"+s+"!/"));
	    } catch (MalformedURLException e) {
		printStackTrace(e);
	    }
	}
    }
}
