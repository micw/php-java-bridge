/*-*- mode: Java; tab-width:8 -*-*/

package php.java.bridge;

import java.io.UnsupportedEncodingException;
import java.util.HashMap;
import java.util.Map;

/**
 * Cache [Entry(byte[], enc) -> String].  No
 * synchronization, so use this class per thread or per request
 * only.
 */
public final class StringCache {
    private Map map;
    private JavaBridge bridge;

    private void init() {
        map = new HashMap();
    }
    /**
     * Create a new StringCache
     * @param bridge The JavaBridge
     */
    public StringCache(JavaBridge bridge) {
        this.bridge = bridge;
        init();
    }
    
    /**
     * A cache entry.
     */
    protected static class Entry {
	byte[] name;
	String enc;
	int start;
	int length;
	
	protected Entry () {}
	protected Entry (byte[] name, int start, int length, String enc) {
	    this.name = name;
	    this.start = start;
	    this.length = length;
	    //how to check that enc is really a symbol? 
	    this.enc = enc;
	}
	private boolean hasResult = false;
	private int result = 1;
	public int hashCode() {
	    if(hasResult) return result;
	    int d = start+length;
	    for(int i=start; i<d; i++) {
	        result = result * 7 + name[i];
	    }
	    result = result * 31 + length;
	    result = result * 31 + enc.hashCode();
	    hasResult = true;
	    return result;
	}
	public boolean equals(Object o) {
	    Entry that = (Entry) o;
	    if(enc!=that.enc) return false;
	    if(length!=that.length) return false;
	    if(that.start!=0) throw new RuntimeException("bleh");
	    for(int i=length; i-->0;) {
	        // that.name.start is always 0
	        if(name[i+start]!=that.name[i]) return false;
	    }
	    return true;
	}
	public String toString() {
	    try {
	        return new String(name, start, length, enc);
	    } catch (UnsupportedEncodingException e) {
	        Util.printStackTrace(e);
	        return new String(name, start, length);
	    }
	}
    }
	
    /**
     * Get the method for the entry
     * @param entry The entry
     * @return The method
     */
    protected String get(Entry entry) {
        return (String) map.get(entry);
    }

    /**
     * Store a constructor with an entry
     * @param entry The cache entry
     * @param method The method
     */
    protected void put(Entry entry, String method) {
        byte[] b = new byte[entry.length];
        System.arraycopy(entry.name, entry.start, b, 0, entry.length);
        entry.start = 0;
        entry.name = b;
        map.put(entry, method);
    }

    /**
     * Get a cache entry from a name, class and arguments.
     * @param name The method name
     * @param clazz The class
     * @param args The arguments
     * @return A cache entry.
     */
    protected Entry getEntry (byte[] name, int start, int length, String enc){
	return new Entry(name, start, length, enc);
    }
    private String createString(byte[] name, int start, int length, String encoding) {
        try {
	    return new String(name, start, length, encoding);
	} catch (UnsupportedEncodingException e) {
	    bridge.printStackTrace(e);
	    return new String(name, start, length);
	}
    }
    /**
     * Get a string from the string cache.
     * @param name The representation of the string
     * @param start The start position within the byte array
     * @param length The length of the array
     * @param encoding The file.encoding.
     * @return
     */
    public String getString(byte[] name, int start, int length, String encoding) {
        Entry e = getEntry(name, start, length, encoding);
        String s = get(e);
        if(s == null)
          put(e, s = createString(name, start, length, encoding));
        return s;
    }

    /**
     * Removes all mappings from this cache.
     */
    public void clear() {
       init();
    }
}
