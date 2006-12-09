/*-*- mode: Java; tab-width:8 -*-*/

package php.java.bridge;

/*
 * Copyright (C) 2006 Jost Boekemeier
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

import java.io.IOException;
import java.lang.reflect.Array;
import java.util.Collection;
import java.util.Iterator;
import java.util.List;
import java.util.ListIterator;
import java.util.Map;

/**
 * This class is used to write the response to the front-end.
 * 
 * @author jostb
 *
 */
public class Response {

    /**
     * A specialized writer which writes arrays as values.
     * Used by getValues() and in php 4.
     * @see JavaBridge#getValues(Object)
     */
    public static final int VALUES_WRITER = 1;
    
    /**
     * A specialized writer which casts the value.
     * Used by cast().
     * @see JavaBridge#cast(Object, Class)
     */
    public static final int COERCE_WRITER = 2;

    // used in getFirstBytes() only
    static final byte append_for_OutBuf_getFirstBytes[] = new byte[] {'.', '.', '.' }; 
    static final byte append_none_for_OutBuf_getFirstBytes[] = new byte[0];
    static final byte digits[] = {'0', '1', '2', '3', '4', '5', '6', '7', '8', '9', 'a', 'b', 'c', 'd', 'e', 'f'}; 

    private class Base64OutputBuffer extends Base64EncodingOutputBuffer {
	Base64OutputBuffer(JavaBridge bridge) {
	    super(bridge);
	}
	protected void appendQuoted(byte s[]) {
	    appendBase64(s);
	}	
    }
    protected HexOutputBuffer buf;
    long peer;
    protected JavaBridge bridge;

 
    protected abstract class DelegateWriter {
    	protected Class staticType;

	public abstract boolean setResult(Object value);

	/**
	 * @param type - The result type
	 */
	public void setType(Class type) {
	    this.staticType = type;
	}
    }
    protected abstract class Writer extends DelegateWriter {
	public void setResultProcedure(long object, String cname, String name, Object[] args) {
	    int argsLength = args==null?0:args.length;
	    writeApplyBegin(object, cname, name, argsLength);
	    for (int i=0; i<argsLength; i++) {
		writePairBegin();
		setResult(args[i], args[i].getClass());
		writePairEnd();
	    }
	    writeApplyEnd();
	}
	public void setResultException(Throwable o, String str) {
	    writeException(o, str);
	}
	public void setResultObject(Object value) {
	    writeObject(value);
	}
	public void setResultClass(Class value) {
	    writeClass(value);
	}
	public void setResult(Object value, Class type) {
	    setType(type);
	    setResult(value);
	}
	public void setFinish(boolean keepAlive) {
	    writeFinish(keepAlive);
	}
	public void reset() {
	    writer = currentWriter;
	    buf.reset();	    
	}
	/**
	 * Called at the end of each packed.
	 */
	public void flush() throws IOException {
       	    if(bridge.logLevel>=4) {
      	     bridge.logDebug(" <-- " +newString(buf.getFirstBytes()));
       	    }
       	    buf.writeTo(bridge.out);
       	    reset();
	}
    }
    protected abstract class WriterWithDelegate extends Writer {
	protected DelegateWriter delegate;

	public void setType(Class type) {
	    super.setType(type);
	    delegate.setType(type);
	}
    }
    protected class ArrayWriter extends DelegateWriter {
	public boolean setResult(Object value) {
		return false;
	}
    }
    protected class ArrayValuesWriter extends DelegateWriter {
	public boolean setResult(Object value) {
	    if (value.getClass().isArray()) {
		long length = Array.getLength(value);
		writeCompositeBegin_a();
		for (int i=0; i<length; i++) {
		    writePairBegin();
		    writer.setResult(Array.get(value, i));
		    writePairEnd();
		}
		writeCompositeEnd();
	    } else if (value instanceof java.util.Map) {
		Map ht = (Map) value;
		writeCompositeBegin_h();
		for (Iterator e = ht.entrySet().iterator(); e.hasNext(); ) {
		    Map.Entry entry = (Map.Entry)e.next();
		    Object key = entry.getKey();
		    Object val = entry.getValue();
		    if (key instanceof Number &&
			!(key instanceof Double || key instanceof Float)) {
			writePairBegin_n(((Number)key).intValue());
			writer.setResult(val);
		    }
		    else {
			writePairBegin_s(String.valueOf(key));
			writer.setResult(ht.get(key));
		    }
		    writePairEnd();
		}
		writeCompositeEnd();
	    } else if (value instanceof java.util.List) {
		List ht = (List) value;
		writeCompositeBegin_h();
		for (ListIterator e = ht.listIterator(); e.hasNext(); ) {
		    int key = e.nextIndex();
		    Object val = e.next();
		    writePairBegin_n(key);
		    writer.setResult(val);
		    writePairEnd();
		}
		writeCompositeEnd();
	    } else {
		return false;
	    }
	    return true;
        }
    }
    protected class ClassicArrayValuesWriter extends DelegateWriter {
	public boolean setResult(Object value) {
	    if (value.getClass().isArray()) {
		long length = Array.getLength(value);
		writeCompositeBegin_a();
		for (int i=0; i<length; i++) {
		    writePairBegin();
		    writer.setResult(Array.get(value, i));
		    writePairEnd();
		}
		writeCompositeEnd();
	    } else if (value instanceof java.util.Map) {
		Map ht = (Map) value;
		writeCompositeBegin_h();
		for (Iterator e = ht.entrySet().iterator(); e.hasNext(); ) {
		    Map.Entry entry = (Map.Entry)e.next();
		    Object key = entry.getKey();
		    Object val = entry.getValue();
		    if (key instanceof Number &&
			!(key instanceof Double || key instanceof Float)) {
			writePairBegin_n(((Number)key).intValue());
			writer.setResult(val);
		    }
		    else {
			writePairBegin_s(String.valueOf(key));
			writer.setResult(ht.get(key));
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
    protected abstract class IncompleteClassicWriter extends WriterWithDelegate {
      
	public boolean setResult(Object value) {
	    if (value == null) {
		writeNull();
	    } else if (value instanceof byte[]) {
		writeString((byte[])value);
	    } else if (value instanceof java.lang.String) {
		writeString((String)value);
	    } else if (value instanceof PhpString) {
	        writeString(((PhpString)value).getBytes());
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

	    } else {
	        return false;
	    }
	    return true;
	}
  }

    protected class ClassicWriter extends IncompleteClassicWriter {
 
	public boolean setResult(Object value) {
	    if(super.setResult(value)) return true;
	    if(!delegate.setResult(value))
		writeObject(value);
	    return true;
	}
    }
    protected class DefaultWriter extends WriterWithDelegate {

	public boolean setResult(Object value) {
	    if(value==null) {writeNull(); return true;}
	    if(staticType.isPrimitive()) {
   		if(staticType == Boolean.TYPE)
		    writeBoolean(((Boolean) value).booleanValue());
   		else if(staticType == Byte.TYPE || staticType == Short.TYPE || staticType == Integer.TYPE)
		    writeLong(((Number)value).longValue());
   		else if(staticType == Long.TYPE)
   		    writeDouble(((Number)value).doubleValue());
   		else if(staticType == Float.TYPE || staticType == Double.TYPE) 
		    writeDouble(((Number)value).doubleValue());
   		else if(staticType == Character.TYPE) 
		    writeString(Util.stringValueOf(value));
   		else { Util.logFatal("Unknown type"); writeObject(value); }
	    } else if(value instanceof PhpString) //  No need to check for Request.PhpNumber, this cannot happen.
	        writeString(((PhpString)value).getBytes());
	    else if(!delegate.setResult(value))
		writeObject(value);
	    return true;
        }        	     	
    }

    protected class ArrayValueWriter extends IncompleteClassicWriter {
	public void setResult(Object value, Class type) {
	    if(!delegate.setResult(value)) setResultArray(value);
	}
	public boolean setResult(Object value) {
	    if(!super.setResult(value)) writeString(Util.stringValueOf(value));
	    return true;
	}
	private boolean setResultArray(Object value) {
	    writeCompositeBegin_a();
	    writePairBegin();
	    setResult(value);
	    writePairEnd();
	    writeCompositeEnd();
	    return true;
	}
    }
  	
    protected final class AsyncWriter extends Writer {
	public void setResultProcedure(long object, String cname, String name, Object[] args) {
	    throw new IllegalStateException("Cannot call "+name+": callbacks not allowed in stream mode");
	}
	public void setResultException(Throwable o, String str) {
	    setResultObject(bridge.lastAsyncException = o);
	}
	public void setResultObject(Object value) {
 	    if(value==null) value=Request.PHPNULL;
 	    if(bridge.logLevel>=4) {
		writeObject(value);
	    } else {  
		bridge.globalRef.append(value);
	    }
	}
	public void setResultClass(Class value) {
	    if(value==null) value=Request.PhpNull.class;
	    if(bridge.logLevel>=4) {
		writeClass(value);
	    } else {
		bridge.globalRef.append(value);
	    }
	}
	public boolean setResult(Object value) { 
	    setResultObject(value);
	    return true;
	}
	public void reset() {
	    buf.reset();
	}
	/**
	 * Called at the end of each packed.
	 */
	public void flush() throws IOException {
     	    if(bridge.logLevel>=4) {
       	     bridge.logDebug(" |<- " +newString(buf.getFirstBytes()));
            }
     	    reset();
	}
    }
  	
    protected class CoerceWriter extends Writer {
	public void setResult(Object value, Class resultType) {
	    // ignore resultType and use the coerce type
	    setResult(value);
	}

	public boolean setResult(Object value) {
		 if(value instanceof PhpString) 
		     value = ((PhpString)value).getString();

		 if(staticType.isPrimitive()) {
		if(staticType == Boolean.TYPE) {
		    if(value instanceof Boolean)
		        writeBoolean(((Boolean) value).booleanValue());
		    else 
		        writeBoolean(value!=null);
		} else if(staticType == Byte.TYPE || staticType == Short.TYPE || staticType == Integer.TYPE || staticType == Long.TYPE) {
		    if(value instanceof Number) 
		        writeLong(((Number)value).longValue());
		    else {
		        try { writeLong(new Long(Util.stringValueOf(value)).longValue()); } catch (NumberFormatException n) { writeLong(0); }
		    }
		} else if(staticType == Float.TYPE || staticType == Double.TYPE) {
		    if(value instanceof Number) 
		        writeDouble(((Number)value).doubleValue());
		    else {
		        try { writeDouble(new Double(Util.stringValueOf(value)).doubleValue()); } catch (NumberFormatException n) { writeDouble(0.0); }
		    }
		} else if(staticType == Character.TYPE) {
		    writeString(Util.stringValueOf(value));
		} else { Util.logFatal("Unknown type"); writeObject(value); }
	    } else if(staticType == String.class) {
		 if (value instanceof byte[])
		     writeString((byte[])value);
		 else
		     writeString(Util.stringValueOf(value));
	    } else {
		writeObject(value);
	    }
	    return true;
	}
    }
    DelegateWriter getDefaultDelegate() {
	if(bridge.options.sendArraysAsValues())
	    return new ClassicArrayValuesWriter();
	else
	    return new ArrayWriter();
    }
	
    WriterWithDelegate newWriter(DelegateWriter delegate) {
     	WriterWithDelegate writer;
    	if(bridge.options.preferValues())
	    writer = new ClassicWriter();
    	else 
	    writer = new DefaultWriter();
    	writer.delegate = delegate;
	return writer;
    }
     
    private WriterWithDelegate defaultWriter;
    private Writer writer, currentWriter, arrayValuesWriter=null, arrayValueWriter=null, coerceWriter=null, asyncWriter=null;

    protected HexOutputBuffer createBase64OutputBuffer() {
	return new Base64OutputBuffer(bridge);
    }
    protected HexOutputBuffer createOutputBuffer() {
        if(bridge.options.base64Data())
            return createBase64OutputBuffer();
        else
            return new HexOutputBuffer(bridge);
    }
    /**
     * Creates a new response object. The object is re-used for each packed.
     * @param bridge The bridge.
     */
    public Response(JavaBridge bridge) {
	this.bridge=bridge;
	buf=createOutputBuffer();
	currentWriter = writer = defaultWriter = newWriter(getDefaultDelegate());
    }

    protected Response(JavaBridge bridge, HexOutputBuffer buf) {
        this.bridge = bridge;
        this.buf=buf;
        this.currentWriter = this.writer = this.defaultWriter = newWriter(getDefaultDelegate());      
    }
    /** Flush the current output buffer and create a new Response object 
     * where are writers have their default value */
    public Response copyResponse() throws IOException {
        flush();
        return new Response(bridge, buf);
    }
    /**
      * Set the result packet.
      * @param object The result object.
      * @param cname The php name of the procedure
      * @param name The java name of the procedure
      * @param args The arguments
      */
    public void setResultProcedure(long object, String cname, String name, Object[] args) {
	writer.setResultProcedure(object, cname, name, args);
    }
    /**
      * Set the result packet.
      * @param value The throwable
      * @param asString The string representation of the throwable
      */
    public void setResultException(Throwable value, String asString) {
     	writer.setResultException(value, asString);
    }
    /**
      * Set the result packet.
      * @param value The result object.
      */
    public void setResultObject(Object value) {
     	writer.setResultObject(value);
    }
    /**
      * Set the result packet.
      * @param value The result object.
      */
    public void setResultClass(Class value) {
     	writer.setResultClass(value);
    }

    /**
      * Set the result packet.
      * @param value The result object.
      * @param type The type of the result object.
      */
    public void setResult(Object value, Class type) {
     	writer.setResult(value, type);
    }

    public void setFinish(boolean keepAlive) {
	setDefaultWriter();
        writer.setFinish(keepAlive);
    }
    /**
     * Selects a different writer.
     * @param writerType Must be Response#VALUES_WRITER or Response#COERCE_WRITER.
     * @return The seleted writer.
     * @see Response#VALUES_WRITER
     * @see Response#COERCE_WRITER
     * @deprecated Use setArrayValuesWriter or setCoerceWriter instead.
     */
    public Writer selectWriter(int writerType) {
     	switch(writerType) {
    	case VALUES_WRITER: return setArrayValuesWriter();
    	case COERCE_WRITER: return setCoerceWriter();
    	default: throw new IllegalArgumentException(String.valueOf(writerType));
    	}
    }
    /**
     * Selects a specialized writer which writes objects as an array.
     * Used by castToArray().
     * @see JavaBridge#castToArray(Object)
     */
    protected Writer setArrayValueWriter() {
	return writer = getArrayValueWriter();
    }
    /**
     * Selects a specialized writer which writes arrays as values.
     * Used by getValues() and in php 4.
     * @see JavaBridge#getValues(Object)
     */
    public Writer setArrayValuesWriter() {
	return writer = getArrayValuesWriter();
    }
    /**
     * Selects a specialized writer which casts the value.
     * Used by cast().
     * @see JavaBridge#cast(Object, Class)
     */
    public Writer setCoerceWriter() {
	return writer = getCoerceWriter();
    }
    /**
     * Selects a specialized writer which does not write anything.
     * Used by async. protocol.
     * @return The async. writer
     */
    public Writer setAsyncWriter() {
	return currentWriter = getAsyncWriter();
    }
    /**
     * Selects the default writer
     * @return The default writer
     */
    public Writer setDefaultWriter() {
	return writer = currentWriter = defaultWriter;
    }
    protected void setID(long id) {}
    
    static final byte[] e="\"/>".getBytes();
    static final byte[] c="\">".getBytes();
    static final byte[] I="\" i=\"".getBytes();
    static final byte[] S="<S v=\"".getBytes();
    static final byte[] B="<B v=\"".getBytes();
    static final byte[] L="<L v=\"".getBytes();
    static final byte[] D="<D v=\"".getBytes();
    static final byte[] E="<E v=\"".getBytes();
    static final byte[] O="<O v=\"".getBytes();
    static final byte[] N="<N i=\"".getBytes();
    static final byte[] Nul="<N />".getBytes();
    static final byte[] m="\" m=\"".getBytes();
    static final byte[] n="\" n=\"".getBytes();
    static final byte[] p="\" p=\"".getBytes();
    static final byte[] pa="\" p=\"A".getBytes();
    static final byte[] pc="\" p=\"C".getBytes();
    static final byte[] pe="\" p=\"E".getBytes();
    static final byte[] po="\" p=\"O".getBytes();
    static final byte[] Xa="<X t=\"A".getBytes();
    static final byte[] Xh="<X t=\"H".getBytes();
    static final byte[] Xe="</X>".getBytes();
    static final byte[] A="<A v=\"".getBytes();
    static final byte[] Ae="</A>".getBytes();
    static final byte[] P="<P>".getBytes();
    static final byte[] Pn="<P t=\"N\" v=\"".getBytes();
    static final byte[] Ps="<P t=\"S\" v=\"".getBytes();
    static final byte[] Pe="</P>".getBytes();
    static final byte[] Fa = "<F p=\"A\"/>".getBytes();
    static final byte[] Fe = "<F p=\"E\"/>".getBytes();   
    static final byte[] quote="&quot;".getBytes();
    static final byte[] amp="&amp;".getBytes();

    void writeString(byte s[]) {
	buf.appendString(s);
        buf.append(e);	
    }
    void writeString(String s) {
	writeString(bridge.options.getBytes(s));
    }
    void writeBoolean(boolean b) {
	buf.append(B); buf.write(b==true?'T':'F');
	buf.append(e);
    }
    void writeLong(long l) {
	buf.appendLong(l);
	buf.append(e);	
    }
    void writeDouble(double d) {
	buf.append(D); buf.append(d);
	buf.append(e);
    }
    void writeNull() {
	buf.append(Nul);
    }
    protected byte[] getType(Class type) {
        if (type.isArray() ||  
        List.class.isAssignableFrom(type) || 
        Map.class.isAssignableFrom(type)) {
            return pa;
        } else if(Collection.class.isAssignableFrom(type)) {
            return pc;
        } else if(Throwable.class.isAssignableFrom(type)) {
            return pe;
        }
        return po;
    }
    void writeObject(Object o) {
        if(o==null) { writeNull(); return; }
        Class dynamicType = o.getClass();
	buf.append(O); buf.append(this.bridge.globalRef.append(o));
	buf.append(getType(dynamicType));
	buf.append(e);
    }
    void writeClass(Class o) {
        if(o==null) { writeNull(); return; }
    	buf.append(O); buf.append(this.bridge.globalRef.append(o));
    	buf.append(po);
    	buf.append(e);
    }
    void writeException(Object o, String str) {
	buf.append(E); buf.append(this.bridge.globalRef.append(o));
	buf.append(m); buf.appendQuoted(str);
	buf.append(e);
    }
    void writeFinish(boolean keepAlive) {
        if(keepAlive) buf.append(Fa); else buf.append(Fe);
    }
    void writeCompositeBegin_a() {
	buf.append(Xa);
	buf.append(c);
    }
    void writeCompositeBegin_h() {
	buf.append(Xh);
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
	buf.append(Pn); buf.append(key);
	buf.append(c);
    }
    void writePairBegin() {
	buf.append(P);
    }
    void writePairEnd() {
	buf.append(Pe);
    }
    void writeApplyBegin(long object, String pos, String str, int argCount) {
 	buf.append(A); buf.append(object);
 	buf.append(p); buf.appendQuoted(pos);
 	buf.append(m); buf.appendQuoted(str);
 	buf.append(n); buf.append(argCount);
 	buf.append(c);
    }
    void writeApplyEnd() {
	buf.append(Ae);
    }
    
    /**
     * Write the response.
     * @throws IOException
     */
    public void flush() throws IOException {
      	writer.flush();
    }
    
    /**
     * Called at the end of each packed.
     */
    protected void reset() {
	writer.reset();
    }
    /** Set the current bridge object
     * @param bridge The bridge
     */
    protected void setBridge(JavaBridge bridge) {
	this.bridge = bridge;
    }
    /** re-initialize for keep alive */
    protected void recycle() {
        reset();
        setDefaultWriter();
    }
    public String toString() {
    	return newString(buf.getFirstBytes());
    }

    private String newString(byte[] b) {
        return bridge.getString(b, 0, b.length);
    }

    private Writer getArrayValuesWriter() {
        if(arrayValuesWriter==null) {
            WriterWithDelegate writer = new ClassicWriter();
            writer.delegate = new ArrayValuesWriter();
            arrayValuesWriter = writer;
        }
        return arrayValuesWriter;
    }

    private Writer getArrayValueWriter() {
        if(arrayValueWriter==null) {
            WriterWithDelegate writer = new ArrayValueWriter();
            writer.delegate = new ArrayValuesWriter();
            arrayValueWriter = writer;
        }
        return arrayValueWriter;
    }

    private Writer getCoerceWriter() {
        if(coerceWriter==null) return coerceWriter = new CoerceWriter();
        return coerceWriter;
    }
    private Writer getAsyncWriter() {
      if(asyncWriter==null) return asyncWriter = new AsyncWriter();
      return asyncWriter;
    }
}
