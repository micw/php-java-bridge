/*-*- mode: Java; tab-width:8 -*-*/


package php.java.bridge;

class ParserString {
    byte[] string;
    int off;
    int length;
    
    public String getStringValue() {
    	return new String(string, off, length);
    }
    public String toString() {
    	return "{" + getStringValue() + " @:" + String.valueOf(off) + " l:" + String.valueOf(length) + "}";
    }
}
