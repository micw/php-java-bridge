/*-*- mode: Java; tab-width:8 -*-*/


package php.java.bridge;

class ParserString {
    byte[] string;
    int off;
    int length;
    
    /*
     * Returns the UTF8 string representation. Useful for debugging only
     */
    public String getStringValue() {
        try { 
	    return new String(string, off, length, Response.UTF8);
        } catch (java.io.UnsupportedEncodingException e) { 
	    Util.printStackTrace(e);
	    return new String(string, off, length);
	}
    }
    /*
     * Returns the UTF8 string representation. Useful for debugging only
     */
    public String toString() {
    	return "{" + getStringValue() + " @:" + String.valueOf(off) + " l:" + String.valueOf(length) + "}";
    }
}
