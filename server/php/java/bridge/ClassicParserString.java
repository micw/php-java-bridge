/*-*- mode: Java; tab-width:8 -*-*/
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
