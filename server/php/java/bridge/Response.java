/*-*- mode: Java; tab-width:8 -*-*/

package php.java.bridge;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.lang.reflect.Array;
import java.util.Iterator;
import java.util.Map;

public class Response {

    public static final int VALUES_WRITER = 1;
    public static final int COERCE_WRITER = 2;

    // used in getFirstBytes() only
    static final byte append_for_OutBuf_getFirstBytes[] = new byte[] {'.', '.', '.' }; 
    static final byte append_none_for_OutBuf_getFirstBytes[] = new byte[0]; 

    private class OutBuf extends ByteArrayOutputStream {
	
    	/*
	 * Return up to 256 bytes. Useful for logging.
	 */
	byte[] getFirstBytes() {
	    int c = super.count;
	    byte[] append = (c>256) ? append_for_OutBuf_getFirstBytes : append_none_for_OutBuf_getFirstBytes;
	    if(c>256) c=256;
	    byte[] ret = new byte[c+append.length];
	    System.arraycopy(super.buf, 0, ret, 0, c);
	    System.arraycopy(append, 0, ret, ret.length-append.length, append.length);
	    return ret;
	}

    	void append(byte[] s) {
	    try {
		write(s);
	    } catch (IOException e) {/*not possible*/}
	}
    	void append(String s) {
	    try {
		this.write(s.getBytes()); //used for objects only, not for UTF8 strings
	    } catch (IOException e) {/*not possible*/}
	}
    	void appendQuoted(byte[] s) {
	    for(int i=0; i<s.length; i++) {
		byte ch;
		switch(ch=s[i]) {
		case '&':
		    append(amp);
		    break;
		case '\"':
		    append(quote);
		    break;
		default:
		    write(ch);
		}
	    }
    	}
    	void appendQuoted(String s) {
	    appendQuoted(getBytes(s));
    	}
    }
    OutBuf buf;
    long result, peer;
    byte options;
    private String encoding;
    private JavaBridge bridge;

 
    protected abstract class DelegateWriter {
    	protected Class type;

	public abstract boolean setResult(Object value);

	/**
	 * @param type - The result type
	 */
	public void setType(Class type) {
	    this.type = type;
	}
    }
    
    protected abstract class Writer extends DelegateWriter {
	protected DelegateWriter delegate;

	public void setResult(Object value, Class type) {
	    setType(type);
	    setResult(value);
	}
	public void setType(Class type) {
	    super.setType(type);
	    delegate.setType(type);
	}
    }
    protected class ArrayWriter extends DelegateWriter {
	public boolean setResult(Object value) {
	    if (value.getClass().isArray()) {
		writeObject(value);
	    } else if (value instanceof java.util.Map) {
		writeObject(value);
	    } else {
		return false;
	    }
	    return true;
        }
    }
    protected class ArrayValuesWriter extends DelegateWriter {
	public boolean setResult(Object value) {
	    if (value.getClass().isArray()) {
		long length = Array.getLength(value);
		writeCompositeBegin_a();
		for (int i=0; i<length; i++) {
		    writePairBegin();
		    setResult(Array.get(value, i));
		    writePairEnd();
		}
		writeCompositeEnd();
	    } else if (value instanceof java.util.Map) {
		Map ht = (Map) value;
		writeCompositeBegin_h();
		for (Iterator e = ht.keySet().iterator(); e.hasNext(); ) {
		    Object key = e.next();
		    long slot;
		    if (key instanceof Number &&
			!(key instanceof Double || key instanceof Float)) {
			writePairBegin_n(((Number)key).intValue());
			setResult(ht.get(key));
		    }
		    else {
			writePairBegin_s(String.valueOf(key));
			setResult(ht.get(key));
		    }
		    writePairEnd();
		}
		writeCompositeEnd();
	    } else {
		return false;
	    }
	    return true;
        }
    }
    protected class ClassicWriter extends Writer {
 
	public boolean setResult(Object value) {
	    if (value == null) {
		writeObject(null);
	    } else if (value instanceof byte[]) {
		writeString((byte[])value);
	    } else if (value instanceof java.lang.String) {
		writeString((String)value);
	    } else if (value instanceof java.lang.Number) {

		if (value instanceof java.lang.Integer ||
		    value instanceof java.lang.Short ||
		    value instanceof java.lang.Byte) {
		    writeLong(((Number)value).longValue());
		} else {
		    /* Float, Double, BigDecimal, BigInteger, Double, Long, ... */
		    writeDouble(((Number)value).doubleValue());
		}

	    } else if (value instanceof java.lang.Boolean) {
		writeBoolean(((Boolean)value).booleanValue());

	    } else if(!delegate.setResult(value))
		writeObject(value);
	    return true;
	}
    }
    protected class DefaultWriter extends Writer {

	public boolean setResult(Object value) {
	    if(type.isPrimitive()) {
   		if(type == Boolean.TYPE)
		    writeBoolean(((Boolean) value).booleanValue());
   		else if(type == Byte.TYPE || type == Short.TYPE || type == Integer.TYPE || type == Long.TYPE)
		    writeLong(((Number)value).longValue());
   		else if(type == Float.TYPE || type == Double.TYPE) 
		    writeDouble(((Number)value).doubleValue());
   		else if(type == Character.TYPE) 
		    writeString(String.valueOf(value));
   		else { Util.logFatal("Unknown type"); writeObject(value); }
	    } else if(!delegate.setResult(value))
		writeObject(value);
	    return true;
        }        	     	
    }
    protected class CoerceDelegate extends DelegateWriter {

	public boolean setResult(Object value) {
	    if(type == String.class) {
		writeString(String.valueOf(value));
		return true;
	    }
	    return false;
	}        	     	
    }
    protected class CoerceWriter extends DefaultWriter {
	public void setResult(Object value, Class resultType) {
	    // ignore resultType and use the coerce type
	    setResult(value);
	}
    }
    DelegateWriter getDefaultDelegate() {
	if(sendArraysAsValues())
	    return new ArrayValuesWriter();
	else
	    return new ArrayWriter();
    }
	
    Writer newWriter(DelegateWriter delegate) {
     	Writer writer;
    	if(extJavaCompatibility())
	    writer = new ClassicWriter();
    	else 
	    writer = new DefaultWriter();
    	writer.delegate = delegate;
	return writer;
    }
     
    private Writer writer, defaultWriter;

    boolean sendArraysAsValues() {
        return (options & 2)==2;
    }

    boolean extJavaCompatibility() {
    	return (this.options & 1) == 1;
    }

    public Response(JavaBridge bridge) {
	buf=new OutBuf();
	this.bridge=bridge;
	defaultWriter = writer = newWriter(getDefaultDelegate());
    }
     
    public void setResult(Object value, Class type) {
     	writer.setResult(value, type);
    }

    public Writer selectWriter(int writerType) {
     	switch(writerType) {
    	case VALUES_WRITER: writer = newWriter(new ArrayValuesWriter()); break;
    	case COERCE_WRITER: writer = new CoerceWriter(); writer.delegate=new CoerceDelegate(); break;
    	default: throw new IllegalArgumentException(String.valueOf(writerType));
    	}
     	return writer;
    }
    void setResult(long id, byte options) {
    	this.result=id;
	this.encoding=this.bridge.fileEncoding;
    	this.options=options;
    }
    
    String getEncoding() {
    	return encoding;
    }

    byte[] getBytes(String s) { 
        try { 
	    return s.getBytes(getEncoding());
        } catch (java.io.UnsupportedEncodingException e) { 
	    bridge.printStackTrace(e);
	    return s.getBytes();
	}
    }
    String newString(byte[] b) {
        try { 
	    return new String(b, getEncoding());
        } catch (java.io.UnsupportedEncodingException e) { 
	    bridge.printStackTrace(e);
	    return new String(b);
	}
    }
    
    static final byte[] e="\"/>".getBytes();
    static final byte[] c="\">".getBytes();
    static final byte[] I="\" i=\"".getBytes();
    static final byte[] S="<S v=\"".getBytes();
    static final byte[] B="<B v=\"".getBytes();
    static final byte[] L="<L v=\"".getBytes();
    static final byte[] D="<D v=\"".getBytes();
    static final byte[] E="<E v=\"".getBytes();
    static final byte[] O="<O v=\"".getBytes();
    static final byte[] m="\" m=\"".getBytes();
    static final byte[] n="\" n=\"".getBytes();
    static final byte[] p="\" p=\"".getBytes();
    static final byte[] Xa="<X t=\"A".getBytes();
    static final byte[] Xh="<X t=\"H".getBytes();
    static final byte[] Xe="</X>".getBytes();
    static final byte[] A="<A v=\"".getBytes();
    static final byte[] Ae="</A>".getBytes();
    static final byte[] P="<P>".getBytes();
    static final byte[] Pn="<P t=\"N\" v=\"".getBytes();
    static final byte[] Ps="<P t=\"S\" v=\"".getBytes();
    static final byte[] Pe="</P>".getBytes();
    static final byte[] quote="&quot;".getBytes();
    static final byte[] amp="&amp;".getBytes();
    void writeString(byte s[]) {

	buf.append(S);
	buf.appendQuoted(s);
	buf.append(I); buf.append(String.valueOf(result));
	buf.append(e);
    }
    void writeString(String s) {
	writeString(getBytes(s));
    }
    void writeBoolean(boolean b) {
	buf.append(B); buf.write(b==true?'T':'F');
	buf.append(I); buf.append(String.valueOf(result));
	buf.append(e);
    }
    void writeLong(long l) {
	buf.append(L);
	buf.append(String.valueOf(l));
	buf.append(I); buf.append(String.valueOf(result));
	buf.append(e);
    }
    void writeDouble(double d) {
	buf.append(D); buf.append(String.valueOf(d));
	buf.append(I); buf.append(String.valueOf(result));
	buf.append(e);
    }
    void writeObject(Object o) {
	buf.append(O); buf.append(o==null?"":
				  String.valueOf(this.bridge.globalRef.append(o)));
	buf.append(I); buf.append(String.valueOf(result));
	buf.append(e);
    }
    void writeException(Object o, String str) {
	buf.append(E); buf.append(String.valueOf(this.bridge.globalRef.append(o)));
	buf.append(m); buf.appendQuoted(String.valueOf(str));
	buf.append(I); buf.append(String.valueOf(result));
	buf.append(e);
    }
    void writeCompositeBegin_a() {
	buf.append(Xa);
	buf.append(I); buf.append(String.valueOf(result));
	buf.append(c);
    }
    void writeCompositeBegin_h() {
	buf.append(Xh);
	buf.append(I); buf.append(String.valueOf(result));
	buf.append(c);
    }
    void writeCompositeEnd() {
	buf.append(Xe);
    }
    void writePairBegin_s(String key) {
	buf.append(Ps);	buf.appendQuoted(key);
	buf.append(c);
    }
    void writePairBegin_n(int key) {
	buf.append(Pn); buf.append(String.valueOf(key));
	buf.append(c);
    }
    void writePairBegin() {
	buf.append(P);
    }
    void writePairEnd() {
	buf.append(Pe);
    }
    void writeApplyBegin(long object, String pos, String str, int argCount) {
 	buf.append(A); buf.append(String.valueOf(object));
 	buf.append(p); buf.appendQuoted(String.valueOf(pos));
 	buf.append(m); buf.appendQuoted(String.valueOf(str));
 	buf.append(n); buf.append(String.valueOf(argCount));
 	buf.append(I); buf.append(String.valueOf(result));
 	buf.append(c);
    }
    void writeApplyEnd() {
	buf.append(Ae);
    }
    void flush() throws IOException {
 	if(bridge.logLevel>=4) {
	    bridge.logDebug(" <-- " +newString(buf.getFirstBytes()));
	}
	buf.writeTo(bridge.out);
	reset();
    }
    
    /**
     * Called at the end of each packed.
     *
     */
    void reset() {
    	writer = defaultWriter;
    	buf.reset();
    }
    public String toString() {
    	return newString(buf.getFirstBytes());
    }


}
