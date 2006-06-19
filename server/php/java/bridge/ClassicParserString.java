/*-*- mode: Java; tab-width:8 -*-*/


package php.java.bridge;


/**
 * This class holds the classic parser string for backward compatibility.
 * 
 * The exact numbers used to be base 10.
 * 
 * @author jostb
 *
 */
public class ClassicParserString extends ParserString {

    /** Create a new ClassicParserString*/
    protected ClassicParserString(JavaBridge bridge) { 
        super(bridge);
    }
    
    /**
     * Returns the int value.
     * @return The int value.
     */
    public int getIntValue() {
        int sign;
        if(length==0) return 0;
        int off = this.off;
        int length = this.length;
        int val = 0;
        
        if(string[off]=='-') { 
            off++; length--; sign=-1;
        }
        else if(string[off]=='+') { 
            off++; length--; sign=1; 
        }
        else sign=1;
        
        int pos=1;
        while(length-->0) {
            val+=((int)(string[off+length]-(byte)'0')) * pos;
            pos*=10;
        }
        return val*sign;
    }
    /**
     * Returns the long value.
     * @return The long value.
     */
    public long getLongValue() {
        long sign;
        if(length==0) return 0;
        int off = this.off;
        int length = this.length;
        long val = 0;
        
        if(string[off]=='-') { 
            off++; length--; sign=-1;
        }
        else if(string[off]=='+') { 
            off++; length--; sign=1; 
        } 
        else sign=1;
        
        long pos=1;
        while(length-->0) {
            val+=((long)(string[off+length]-(byte)'0')) * pos;
            pos*=10;
        }
        return val*sign;
    }
}
