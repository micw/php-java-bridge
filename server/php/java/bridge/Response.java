/*-*- mode: Java; tab-width:8 -*-*/

package php.java.bridge;
import java.io.ByteArrayOutputStream;
import java.io.IOException;

public class Response {
    private class OutBuf extends ByteArrayOutputStream {
	/*
	 * Return up to 256 bytes. Useful for logging.
	 */
	byte[] getFirstBytes() {
	    int c = super.count;
	    if(c>256) c=256;
	    byte[] ret = new byte[c];
	    System.arraycopy(super.buf, 0, ret, 0, c);
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
    private byte options;
    private String encoding;
    private JavaBridge bridge;
    public Response(JavaBridge bridge) {
	buf=new OutBuf();
	this.bridge=bridge;
    }
    
    public void setResult(long id, byte options) {
    	this.result=id;
	this.encoding=this.bridge.fileEncoding;
    	this.options=options;
    }
    
    public boolean sendArraysAsValues() {
    	return (options & 2)==2;
    }
    public String getEncoding() {
    	return encoding;
    }

    byte[] getBytes(String s) { 
        try { 
	    return s.getBytes(getEncoding());
        } catch (java.io.UnsupportedEncodingException e) { 
	    Util.printStackTrace(e);
	    return s.getBytes();
	}
    }
    String newString(byte[] b) {
        try { 
	    return new String(b, getEncoding());
        } catch (java.io.UnsupportedEncodingException e) { 
	    Util.printStackTrace(e);
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
    static final byte[] Xa="<X t=\"A".getBytes();
    static final byte[] Xh="<X t=\"H".getBytes();
    static final byte[] Xe="</X>".getBytes();
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
    void flush() throws IOException {
 	if(Util.logLevel>=4) {
	    Util.logDebug(this.bridge + " <-- " +newString(buf.getFirstBytes()));
	}
	buf.writeTo(bridge.out);
	buf.reset();
    }
}
