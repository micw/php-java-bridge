/*-*- mode: Java; tab-width:4 -*-*/

package php.java.bridge;
import java.beans.BeanInfo;
import java.beans.Introspector;
import java.beans.PropertyDescriptor;
import java.io.File;
import java.lang.reflect.Array;
import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.Hashtable;
import java.util.Iterator;
import java.util.Map;
import java.util.StringTokenizer;
import java.util.Vector;



public class JavaBridge implements Runnable {

    // class hash
    private static final HashMap classes = new HashMap(); 

    // the list of jar files in which we search for user classes.
    static private Collection sysUrls = null;

    // the list of jar files in which we search for user classes.  can
    // be changed with setLibraryPath
    private Collection urls = null;

	// list of objects in use in the current script
	GlobalRef globalRef;	

	JavaBridgeClassLoader cl=new JavaBridgeClassLoader();
	//
    // Native methods
    //
    static native boolean openLog(String logFile);
    static native void startNative(int logLevel, String sockname);
	static native int swrite(long peer, byte buf[], int nmemb);
	static native int sread(long peer, byte buf[], int nmemb);
	static native int seof(long peer);
	static native void sclose(long peer);

	
    // 
    // Communication with client in a new thread
    //
    private int uid, gid;
	long peer;
    public void run() { 
		(new Request(this)).handleRequests();
		sclose(peer);
	}
    public static void HandleRequests(long peer, int uid, int gid) {  
		JavaBridge bridge = new JavaBridge();
		Thread thread = new Thread(bridge);
		bridge.peer=peer;
		bridge.uid=uid;
		bridge.gid=gid;
		thread.setContextClassLoader(bridge.cl);
		Util.logDebug("Request from client with uid/gid "+uid+"/"+gid);
		thread.start();
    }

    //
    // Return map for the value (PHP 5 only)
    //
    public PhpMap getPhpMap(Object value) { 
		Util.logDebug("returning map for "+ value.getClass());

		if(value.getClass().isArray()) {
			return 
				new PhpMap(value, (Object)null) {
					boolean valid;
					int i;
					long length;
		    
					void init() {
						i=0;
						length = Array.getLength(this.value);
						valid=length>0;
					}
					public Object currentData() {
						if(!valid) return null;
						return Array.get(this.value, i);
					}
					public byte[] currentKey() {
						if(!valid) return null;
						return String.valueOf(i).getBytes();
					}
					public Object moveForward() {
						valid=++i<length;
						return valid?this:null;
					}
					public Object hasMore() {
						return valid?this:null;
					}

					public boolean offsetExists(Object pos) {
						int i = ((Long)pos).intValue();
						return (i>0 && i<length && (Array.get(this.value, i)!=this));
					}
					public Object offsetGet(Object pos) {
						int i = ((Long)pos).intValue();
						Object o = Array.get(this.value, i);
						return o==this ? null : o;
					}
					public void offsetSet(Object pos, Object val) {
						int i = ((Long)pos).intValue();
						Array.set(this.value, i, val);
					}
					public void offsetUnset(Object pos) {
						int i = ((Long)pos).intValue();
						Array.set(this.value, i, this);
					}
				};
		}
		if(value instanceof Collection) {
			return 
				new PhpMap(value, (Object)null) {
					Object currentKey;
					int i;
					Iterator iter;
		    
					void init() {
						iter = ((Collection)(this.value)).iterator();
						i = 0;
						currentKey=null;
						if(iter.hasNext()) {
							currentKey=iter.next();
						}
					}
					public Object currentData() {
						return currentKey;
					}
					public byte[] currentKey() {
						return String.valueOf(i).getBytes();
					}
					public Object moveForward() {
						if(iter.hasNext()) {
							i++;
							currentKey = iter.next();
							return String.valueOf(i).getBytes();
						} else {
							return null;
						}
					}
					public Object hasMore() {
						return currentKey;
					}

					// Should we really care?
					public boolean offsetExists(Object pos) {
						return false;
					}
					public Object offsetGet(Object pos) {
						return null;
					}
					public void offsetSet(Object pos, Object val) {
					}
					public void offsetUnset(Object pos) {
					}
				};
		}
		if(value instanceof Map) {
			return
				new PhpMap(value, value){
					Object currentKey;
					Iterator iter;
		    
					void init() {
						iter = ((Map)(this.value)).keySet().iterator();
						currentKey=null;
						if(iter.hasNext()) {
							currentKey=iter.next();
						}
					}
					public Object currentData() {
						if(currentKey==null) return null;
						return ((Map)(this.value)).get(currentKey);
					}
					public byte[] currentKey() {
						return String.valueOf(currentKey).getBytes();
					}
					public Object moveForward() {
						currentKey = iter.hasNext() ? iter.next() : null;
						return currentKey;
					}
					public Object hasMore() {
						return currentKey;
					}

					public boolean offsetExists(Object pos) {
						return ((Map)(this.value)).containsKey(pos);
					}
					public Object offsetGet(Object pos) {
						return ((Map)(this.value)).get(pos);
					}
					public void offsetSet(Object pos, Object val) {
						((Map)(this.value)).put(pos, val);
					}
					public void offsetUnset(Object pos) {
						((Map)(this.value)).remove(pos);
					}
				};
		}
		return null;
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
						Util.logMessage("added system library: " + url);
						sysUrls.add(url);
					}  catch (MalformedURLException e1) {
						Util.printStackTrace(e1);
					}
				}
			}
		} catch (Exception t) {
			Util.printStackTrace(t);
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
				Util.logFatal("No socket.  You must pass the socket filename, for example /tmp/.report_bridge");
				System.exit(12);
			}
			try {
				if(s.length>1) {
					Util.logLevel=Integer.parseInt(s[1]);
				}
			} catch (Throwable t) {
				Util.printStackTrace(t);
			}

			try {
				if(s.length>2) {
					logFile=s[2];
					if(Util.logLevel>3) System.out.println("Java log         : " + logFile);
					if(logFile==null||logFile.trim().length()==0)
						Util.logStream=System.out;
					else {
						if(!openLog(logFile))
							Util.logStream=new java.io.PrintStream(new java.io.FileOutputStream(logFile));
						else
							Util.logStream=System.out;
					}
				}
			}catch (Throwable t) {
				Util.printStackTrace(t);
			}
			Util.logMessage("Java logFile     : " + logFile);
			Util.logMessage("Java logLevel    : " + Util.logLevel);
			Util.logMessage("Java socket      : " + sockname);
			JavaBridge.startNative(Util.logLevel, sockname);
			System.exit(0);
		} catch (Throwable t) {
			Util.printStackTrace(t);
			System.exit(1);
		}
    }

    public static void main(String s[]) {
		try {
			System.loadLibrary("natcJavaBridge");
		} catch (Throwable t) {
			Util.printStackTrace(t);
			System.exit(9);
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
    static void setResult(Response response, Object value) {
		if (value == null) {
			response.writeObject(null);
		} else if (value instanceof byte[]) {
			response.writeString(new String((byte[])value));
		} else if (value instanceof java.lang.String) {
			response.writeString(((String)value));
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
			if(response.version<=4) {
				// Since PHP 5 this is dead code, setResultFromArray
				// behaves like setResultFromObject and returns
				// false. See PhpMap.
				response.writeCompositeBegin_a();
				for (int i=0; i<length; i++) {
					setResult(response, Array.get(value, i));
				}
				response.writeCompositeEnd();
			}
		} else if (value instanceof java.util.Hashtable) {

			Hashtable ht = (Hashtable) value; 
			if (response.version<=4) {
				// Since PHP 5 this is dead code, setResultFromArray
				// behaves like setResultFromObject and returns
				// false. See PhpMap.
				response.writeCompositeBegin_h();
				for (Enumeration e = ht.keys(); e.hasMoreElements(); ) {
					Object key = e.nextElement();
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
					response.writeCompositeEnd();
				}
			}
		} else {
			response.writeObject(value);
		}
    }
    Throwable lastException = null;

    void lastException(Response response) {
		setResult(response, lastException);
    }

    void clearException() {
		lastException = null;
    }


    void setException(Response response, long peer, Throwable e, String method, Object obj, String name, Object args[]) {
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
			Constructor selected = null;

			if(createInstance) {
				Constructor cons[] = Class.forName(name, true, cl).getConstructors();
				for (int i=0; i<cons.length; i++) {
					if (cons[i].getParameterTypes().length == args.length) {
						matches.addElement(cons[i]);
					}
				}

				selected = (Constructor)select(matches, args);
			}

			if (selected == null) {
				if (args.length > 0) {
					throw new InstantiationException("No matching constructor found. " + "Matches: " + String.valueOf(matches));
				} else {
					// for classes which have no visible constructor, return the class
					// useful for classes like java.lang.System and java.util.Calendar.
					setResult(response, Class.forName(name, true, cl));
					return;
				}
			}

			Object coercedArgs[] = coerce(selected.getParameterTypes(), args);
			response.writeObject(selected.newInstance(coercedArgs));

		} catch (Throwable e) {
			if(e instanceof OutOfMemoryError || 
			   ((e instanceof InvocationTargetException) && 
				((InvocationTargetException)e).getTargetException() instanceof OutOfMemoryError)) {
				Util.logStream.println("FATAL: OutOfMemoryError");
				throw new RuntimeException(); // abort
			}
			Util.printStackTrace(e);
			// Special handling of our connection abort
			// throwable.  We can't use our own (inner)
			// exception class because that would mean we
			// have to deal with a classname that contains
			// a $ sign in its name during the bridge
			// install procedure
			if(e.getMessage()!=null &&
			   e.getMessage().startsWith("child aborted connection during"))
				throw new RuntimeException();

			setException(response, peer, e, createInstance?"CreateInstance":"ReferenceClass", null, name, args);
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
					if (args[i] instanceof java.util.Hashtable) {
						Enumeration enumeration = ((Hashtable)args[i]).elements();
						if(enumeration.hasMoreElements()) {
							Object elem = enumeration.nextElement();
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
    private static Object[] coerce(Class parms[], Object args[]) {
		Object result[] = args;
		Class targetType = null;
		int size = 0;

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
					Util.printStackTrace(n);
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
					size = ht.size();

					// Verify that the keys are Long, and determine maximum
					for (Enumeration e = ht.keys(); e.hasMoreElements(); ) {
						int index = ((Long)e.nextElement()).intValue();
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
					Object coercedArray[] = coerce(tempTarget, tempArray);
        
					// copy the results into the desired array type
					Object array = Array.newInstance(targetType,size);
					for (int j=0; j<size; j++) {
						Array.set(array, j, coercedArray[j]);
					}

					result[i]=array;
				} catch (Exception e) {
					Util.logError("Error: " + String.valueOf(e) + " could not create array of type: " + targetType + ", size: " + size);
					Util.printStackTrace(e);
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
		(Object object, String method, Object args[], Response response)
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
			if (selected == null) throw new NoSuchMethodException(String.valueOf(method) + "(" + Util.argsToString(args) + "). " + "Matches: " + String.valueOf(matches));

			Object coercedArgs[] = coerce(selected.getParameterTypes(), args);
			setResult(response, selected.invoke(object, coercedArgs));

		} catch (Throwable e) {
			if(e instanceof OutOfMemoryError || 
			   ((e instanceof InvocationTargetException) && 
				((InvocationTargetException)e).getTargetException() instanceof OutOfMemoryError)) {
				Util.logStream.println("FATAL: OutOfMemoryError");
				throw new RuntimeException(); // abort
			}
			Util.printStackTrace(e);
			// Special handling of our connection abort
			// throwable.  We can't use our own (inner)
			// exception class because that would mean we
			// have to deal with a classname that contains
			// a $ sign in its name during the bridge
			// install procedure
			if(e.getMessage()!=null &&
			   e.getMessage().startsWith("child aborted connection during"))
				throw new RuntimeException();
			setException(response, peer, e, "Invoke", object, method, args);
		}
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

				// first search for the field *exactly*
				try {
					java.lang.reflect.Field jfields[] = jclass.getFields();
					for (int i=0; i<jfields.length; i++) {
						if (jfields[i].getName().equals(prop)) {
							matches.add(jfields[i].getName());
							if (set) {
								args = coerce(new Class[] {jfields[i].getType()}, args);
								jfields[i].set(object, args[0]);
							} else {
								setResult(response, jfields[i].get(object));
							}
							return;
						}
					}
				} catch (Exception ee) {/* may happen when field is not static */}

				// search for a getter/setter, ignore case
				try {
					BeanInfo beanInfo = Introspector.getBeanInfo(jclass);
					PropertyDescriptor props[] = beanInfo.getPropertyDescriptors();
					for (int i=0; i<props.length; i++) {
						if (props[i].getName().equalsIgnoreCase(prop)) {
							Method method;
							if (set) {
								method=props[i].getWriteMethod();
								args = coerce(method.getParameterTypes(), args);
							} else {
								method=props[i].getReadMethod();
							}
							matches.add(method);
							setResult(response, method.invoke(object, args));
							return;
						}
					}
				} catch (Exception ee) {/* may happen when method is not static */}

				// search for the field, ignore case
				try {
					java.lang.reflect.Field jfields[] = jclass.getFields();
					for (int i=0; i<jfields.length; i++) {
						if (jfields[i].getName().equalsIgnoreCase(prop)) {
							matches.add(prop);
							if (set) {
								args = coerce(new Class[] {jfields[i].getType()}, args);
								jfields[i].set(object, args[0]);
							} else {
								setResult(response, jfields[i].get(object));
							}
							return;
						}
					}
				} catch (Exception ee) {/* may happen when field is not static */}

				// try a second time with the object itself, if it is of type Class
				if (!(object instanceof Class) || (jclass==object)) break;
			}
			throw new NoSuchFieldException(String.valueOf(prop) + " (with args:" + Util.argsToString(args) + "). " + "Matches: " + String.valueOf(matches));

		} catch (Throwable e) {
			if(e instanceof OutOfMemoryError || 
			   ((e instanceof InvocationTargetException) && 
				((InvocationTargetException)e).getTargetException() instanceof OutOfMemoryError)) {
				Util.logStream.println("FATAL: OutOfMemoryError");
				throw new RuntimeException(); // abort
			}
			Util.printStackTrace(e);
			// Special handling of our connection abort
			// throwable.  We can't use our own (inner)
			// exception class because that would mean we
			// have to deal with a classname that contains
			// a $ sign in its name during the bridge
			// install procedure
			if(e.getMessage()!=null &&
			   e.getMessage().startsWith("child aborted connection during"))
				throw new RuntimeException();
			setException(response, peer, e, set?"SetProperty":"GetProperty", object, prop, args);
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
					Util.printStackTrace(e1);
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
				Util.printStackTrace(e);
			}
		}
    }
}
