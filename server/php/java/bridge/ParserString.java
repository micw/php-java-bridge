/*-*- mode: Java; tab-width:8 -*-*/


package php.java.bridge;


/**
 * This class holds the parser string.
 * @author jostb
 *
 */
public class ParserString {
    protected byte[] string;
    protected int off;
    protected int length;
    private JavaBridge bridge;
    
    /* 0..9, A..F, a..f */
    static final byte[] digits = { 
    (byte)127, (byte)127, (byte)127, (byte)127, (byte)127, (byte)127, 
    (byte)127, (byte)127, (byte)127, (byte)127, (byte)127, (byte)127, 
    (byte)127, (byte)127, (byte)127, (byte)127, (byte)127, (byte)127, 
    (byte)127, (byte)127, (byte)127, (byte)127, (byte)127, (byte)127, 
    (byte)127, (byte)127, (byte)127, (byte)127, (byte)127, (byte)127, 
    (byte)127, (byte)127, (byte)127, (byte)127, (byte)127, (byte)127, 
    (byte)127, (byte)127, (byte)127, (byte)127, (byte)127, (byte)127, 
    (byte)127, (byte)127, (byte)127, (byte)127, (byte)127, (byte)127,
    (byte)0, (byte)1, (byte)2, (byte)3, (byte)4, (byte)5, (byte)6, 
    (byte)7, (byte)8, (byte)9,  (byte)127, (byte)127, (byte)127, (byte)127, 
    (byte)127, (byte)127, (byte)127,(byte)10, (byte)11, (byte)12, (byte)13, 
    (byte)14, (byte)15,  (byte)127, (byte)127, (byte)127, (byte)127, 
    (byte)127, (byte)127, (byte)127, (byte)127, (byte)127, (byte)127, 
    (byte)127, (byte)127, (byte)127, (byte)127, (byte)127, (byte)127, 
    (byte)127, (byte)127, (byte)127, (byte)127, (byte)127, (byte)127, 
    (byte)127, (byte)127, (byte)127, (byte)127,(byte)10, (byte)11, (byte)12, 
    (byte)13, (byte)14, (byte)15};

    /** Create a new ParserString */
    protected ParserString(JavaBridge bridge) {
        this.bridge = bridge;
    }
    
    /**
     * Returns the UTF8 string representation. Useful for debugging only
     * @return The UTF-8 encoded string.
     */
    public String getUTF8StringValue() {
        try { 
	    return new String(string, off, length, Util.UTF8);
        } catch (java.io.UnsupportedEncodingException e) { 
	    Util.printStackTrace(e);
	    return new String(string, off, length);
	}
    }
    /**
     * Returns the string encoded via java_set_file_encoding().
     * @param options The current request options.
     * @return The encoded string value.
     * @see php.java.bridge.Options#getEncoding()
     */
    public String getStringValue() {
        return bridge.getString(string, off, length);
    }
    /**
     * Returns the cached string encoded via java_set_file_encoding().
     * @param options The current request options.
     * @return The encoded string value.
     * @see php.java.bridge.Options#getEncoding()
     */
    public String getCachedStringValue() {
        return bridge.getCachedString(string, off, length);
    }
    /**
     * Returns the ASCII string representation. Useful for serialized objects, float, long.
     * @return The ASCII encoded string.
     */
    public String getASCIIStringValue() {
        return bridge.stringCache.getString(string, off, length, Util.ASCII);
    }

    /**
     * Returns the int value.
     * @return The int value.
     */    
    public int getIntValue() {
      if(length==0) return 0;
      int val = 0;
      int pos = 0;
      byte[] string = this.string;
      int off = this.off;

      while(true) {
	  val+=digits[string[off++]];
	  if(++pos==length) break;
	  val<<=4;
      }
      return val;
    }
    /**
     * Returns the long value.
     * @return The long value.
     */    
    public long getLongValue() {
      if(length==0) return 0;
      long val = 0;
      int pos = 0;
      byte[] string = this.string;
      int off = this.off;
      
      while(true) {
	  val+=digits[string[off++]];
	  if(++pos==length) break;
	  val<<=4;
      }
      return val;
    }
    /**
     * Returns the double value.
     * @return The double value.
     */
    public double getDoubleValue() {
        return(Double.parseDouble(getASCIIStringValue()));
    }
    /**
     * Returns the UTF8 string representation. Useful for debugging only
     * @return The description of the string.
     */
    public String toString() {
    	return "{" + getUTF8StringValue() + " @:" + String.valueOf(off) + " l:" + String.valueOf(length) + "}";
    }
}
