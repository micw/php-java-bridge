/*-*- mode: Java; tab-width:8 -*-*/


package php.java.bridge;

class ParserString {
    byte[] string;
    int off;
    int length;
    private final String UTF8 = "UTF-8";

    /*
     * Returns the UTF8 string representation. Useful for debugging only
     */
    public String getUTF8StringValue() {
        try { 
	    return new String(string, off, length, Util.UTF8);
        } catch (java.io.UnsupportedEncodingException e) { 
	    Util.printStackTrace(e);
	    return new String(string, off, length);
	}
    }
    /*
     * Returns the ASCII string representation. Useful serialized objects, float, long.
     */
    public String getStringValue() {
	return new String(string, off, length);
    }
    /*
     * Returns the UTF8 string representation. Useful for debugging only
     */
    public String toString() {
    	return "{" + getUTF8StringValue() + " @:" + String.valueOf(off) + " l:" + String.valueOf(length) + "}";
    }
}
