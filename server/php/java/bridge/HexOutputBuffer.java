/*-*- mode: Java; tab-width:8 -*-*/
package php.java.bridge;

import java.io.ByteArrayOutputStream;
import java.io.IOException;

/** numbers are base 16 */
class HexOutputBuffer extends ByteArrayOutputStream {
	/**
     * 
     */
    private final JavaBridge bridge;
    /**
     * @param response
     */
    HexOutputBuffer(JavaBridge bridge) {
        this.bridge = bridge;
    }

    /*
     * Return up to 256 bytes. Useful for logging.
     */
    protected byte[] getFirstBytes() {
        int c = super.count;
        byte[] append = (c>256) ? Response.append_for_OutBuf_getFirstBytes : Response.append_none_for_OutBuf_getFirstBytes;
        if(c>256) c=256;
        byte[] ret = new byte[c+append.length];
        System.arraycopy(super.buf, 0, ret, 0, c);
        System.arraycopy(append, 0, ret, ret.length-append.length, append.length);
        return ret;
    }

	protected void append(byte[] s) {
        try {
    	write(s);
        } catch (IOException e) {/*not possible*/}
    }

	protected void appendQuoted(byte[] s) {
        for(int i=0; i<s.length; i++) {
    	byte ch;
    	switch(ch=s[i]) {
    	case '&':
    	    append(Response.amp);
    	    break;
    	case '\"':
    	    append(Response.quote);
    	    break;
    	default:
    	    write(ch);
    	}
        }
	}
	protected void appendQuoted(String s) {
        appendQuoted(bridge.options.getBytes(s));
	}

	private byte[] buf = new byte[16];
	/** append an unsigned long number */
	protected void append(long i) {
        int pos = 16;
	    do {
	        buf[--pos] = Response.digits[(int)(i & 0xF)];
	        i >>>= 4;
	    } while (i != 0);
	    write(buf, pos, 16-pos);
    }

	/** append a double value, base 10 for now (not every C compiler supports the C99 "a" conversion) */ 
    protected void append(double d) {
      append(Double.toString(d).getBytes());
    }

    /** append a long number */
    protected void appendLong(long l) {
        append(Response.L);
        if(l<0) {
            append(-l);
            append(Response.pa);
        } else {
            append(l);
            append(Response.po);
        }
    }
    /** append a string */
    protected void appendString(byte s[]) {
        append(Response.S);
        appendQuoted(s);
        append(Response.e);
    }
}