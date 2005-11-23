/*-*- mode: Java; tab-width:8 -*-*/


package php.java.bridge;

/**
 * This class holds the parser string.
 * @author jostb
 *
 */
public class ParserString {
    byte[] string;
    int off;
    int length;

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
     * Returns the ASCII string representation. Useful serialized objects, float, long.
     * @return The ASCII encoded string.
     */
    public String getStringValue() {
	return new String(string, off, length);
    }
    
    /**
     * Returns the UTF8 string representation. Useful for debugging only
     * @return The description of the string.
     */
    public String toString() {
    	return "{" + getUTF8StringValue() + " @:" + String.valueOf(off) + " l:" + String.valueOf(length) + "}";
    }
}
