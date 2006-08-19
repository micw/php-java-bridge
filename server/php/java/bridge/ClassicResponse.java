/*-*- mode: Java; tab-width:8 -*-*/

package php.java.bridge;
import java.io.IOException;

/**
 * This class is used to write the response to the front-end.
 * 
 * @author jostb
 *
 */
public class ClassicResponse extends Response {

    protected long result;
    /** numbers are base 10 */

    private class ClassicOutputBuffer extends Base64EncodingOutputBuffer {
	ClassicOutputBuffer(JavaBridge bridge) {
	    super(bridge);
	    // TODO Auto-generated constructor stub
	}
	protected void append(long i) {
	    append(String.valueOf(i).getBytes());
	}
	protected void appendLong(long l) {
	    append(L);
	    append(l);
	}
	protected void appendString(byte s[]) {
	    append(S);
	    appendQuoted(s);
	    append(I); append(result);
	    append(e);
	}
    }
    private class Base64OutputBuffer extends ClassicOutputBuffer {
	Base64OutputBuffer(JavaBridge bridge) {
	    super(bridge);
	}
	protected void appendQuoted(byte s[]) {
	    appendBase64(s);
	}
    }
    protected HexOutputBuffer createBase64OutputBuffer() {
	return new Base64OutputBuffer(bridge);
    }
    protected HexOutputBuffer createOutputBuffer() {
        if(bridge.options.hexNumbers())
            return super.createOutputBuffer();
        else
            return new ClassicOutputBuffer(bridge);
    }    
    /**
     * Creates a new response object. The object is re-used for each packed.
     * @param bridge The bridge.
     */
    public ClassicResponse(JavaBridge bridge) {
	super(bridge);
    }

    protected ClassicResponse(JavaBridge bridge, HexOutputBuffer buf) {
	super(bridge, buf);
    }
    /** Flush the current output buffer and create a new Response object 
     * where are writers have their default value */
    public Response copyResponse() throws IOException {
        flush();
        return new ClassicResponse(bridge, buf);
    }
    protected void setID(long id) {
	this.result = id;
    }

    void writeBoolean(boolean b) {
	buf.append(B); buf.write(b==true?'T':'F');
	buf.append(I); buf.append(result);
	buf.append(e);
    }
    void writeLong(long l) {
	buf.appendLong(l);
	buf.append(I); buf.append(result);
	buf.append(e);
	
    }
    void writeDouble(double d) {
	buf.append(D); buf.append(d);
	buf.append(I); buf.append(result);
	buf.append(e);
    }
    void writeNull() {
	buf.append(N);
	buf.append(result);
	buf.append(e);
    }
    void writeObject(Object o) {
        if(o==null) { writeNull(); return; }
        Class dynamicType = o.getClass();
	buf.append(O); buf.append(this.bridge.globalRef.append(o));
	buf.append(getType(dynamicType));
	buf.append(I); buf.append(result);
	buf.append(e);
    }
    void writeClass(Class o) {
        if(o==null) { writeNull(); return; }
    	buf.append(O); buf.append(this.bridge.globalRef.append(o));
    	buf.append(po);
    	buf.append(I); buf.append(result);
    	buf.append(e);
    }
    void writeException(Object o, String str) {
	buf.append(E); buf.append(this.bridge.globalRef.append(o));
	buf.append(m); buf.appendQuoted(str);
	buf.append(I); buf.append(result);
	buf.append(e);
    }
    void writeFinish(boolean keepAlive) {
        if(keepAlive) buf.append(Fa); else buf.append(Fe);
    }
    void writeCompositeBegin_a() {
	buf.append(Xa);
	buf.append(I); buf.append(result);
	buf.append(c);
    }
    void writeCompositeBegin_h() {
	buf.append(Xh);
	buf.append(I); buf.append(result);
	buf.append(c);
    }
    void writeApplyBegin(long object, String pos, String str, int argCount) {
 	buf.append(A); buf.append(object);
 	buf.append(p); buf.appendQuoted(pos);
 	buf.append(m); buf.appendQuoted(str);
 	buf.append(n); buf.append(argCount);
 	buf.append(I); buf.append(result);
 	buf.append(c);
    }
}
