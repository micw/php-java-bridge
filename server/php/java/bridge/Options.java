/*-*- mode: Java; tab-width:8 -*-*/

package php.java.bridge;



/**
 * Exposes the request options. There is one Options instance for each request, but certain options may change for each packet.
 * For example if a user calls java_set_file_encoding(enc), the new file encoding becomes available in the next packet.
 * @author jostb
 *
 */
public final class Options {

    private byte options = 0;

    /**
     * Default encoding: UTF-8
     */
    private String encoding = null;

    /**
     * Returns the file encoding, see java_set_file_encoding(). This option may change for each packet.
     * @return The file encoding
     */
    public String getEncoding() {
	if(encoding!=null) return encoding;
	return encoding=extJavaCompatibility()?Util.UTF8:Util.DEFAULT_ENCODING;
    }
    
    /**
     * Return a byte array using the current file encoding (see java_set_file_encoding()).
     * @param s The string
     * @return The encoded bytes.
     */
    public byte[] getBytes(String s) { 
        try { 
	    return s.getBytes(getEncoding());
        } catch (java.io.UnsupportedEncodingException e) { 
	    Util.printStackTrace(e);
	    return s.getBytes();
	}
    }
   
    
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
 
    /** re-initialize for keep alive */
    protected void recycle() {
        encoding = null;
    }

    /**
     * Set the new file encoding.
     * @param symbol The new file encoding, for example "UTF-8".
     */
    public void setEncoding(String symbol) {
	this.encoding = symbol;
    }

    /**
     * Update the current request options
     * @param b The options from the request header.
     */
    public void updateOptions(byte b) {
	encoding = null;
	this.options = b;
    }
}
