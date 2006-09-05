/*-*- mode: Java; tab-width:8 -*-*/

package php.java.bridge;


/*
 * Copyright (c) 2006 Jost Boekemeier
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


/**
 * Exposes the request options. There is one Options instance for each request, but certain options may change for each packet.
 * For example if a user calls java_set_file_encoding(enc), the new file encoding becomes available in the next packet.
 * @author jostb
 *
 */
public final class StandardOptions extends Options {

    /**
     * Returns true, if bit 1 of the request header is set (see PROTOCOL.TXT). This option stays the same for all packets.
     * @return the value of the request header bit 1.
     */
    public boolean sendArraysAsValues() {
        return (options & 2)==2;
    }

    /**
     * Returns true, if bit 0 of the request header is set (see PROTOCOL.TXT). This options stays the same for all packets.
     * @return the value of the request header
     */
    public boolean extJavaCompatibility() {
    	return this.options == 3;
    }
 
    /**
     * Returns true, if exact numbers are base 16 (see PROTOCOL.TXT). This options stays the same for all packets.
     * @return the value of the request header
     */
    public boolean hexNumbers() {
    	return this.options == 1;
    }
    public boolean passContext() {
	return true;
    }
    public boolean base64Data() {
	return false;
    }
}
