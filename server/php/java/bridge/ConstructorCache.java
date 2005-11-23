/*-*- mode: Java; tab-width:8 -*-*/

package php.java.bridge;

import java.lang.reflect.Constructor;
import java.util.HashMap;

/**
 * Cache [(method, parameters) -> Method] mappings.  No
 * synchronization, so use this class per thread or per request
 * only.
 */
public class ConstructorCache {
    HashMap map = new HashMap();
    static final Entry noCache = new Entry();

     /**
     * A cache entry carrying the method name and the parameters
     * 
     * @author jostb
     *
     */
    public static class Entry {
	String symbol;
	Class params[];
		
	protected Entry () {}

	protected Entry (String name, Class params[]) {
	    this.symbol = name.intern();
	    this.params = params;
	}
	private boolean hasResult = false;
	private int result = 1;
	public int hashCode() {
	    if(hasResult) return result;
	    for(int i=0; i<params.length; i++) {
		result = result * 31 + (params[i] == null ? 0 : params[i].hashCode());
	    }
	    result = result * 31 + symbol.hashCode();
	    hasResult = true;
	    return result;
	}
	public boolean equals(Object o) {
	    Entry that = (Entry) o;
	    if(symbol != that.symbol) return false;
	    if(params.length != that.params.length) return false;
	    for(int i=0; i<params.length; i++) {
		if(params[i] != that.params[i]) return false;
	    }
	    return true;
	}
    }

    /**
     * Get the constructor for the entry
     * @param entry The entry
     * @return The constructor
     */
    public Constructor get(Entry entry) {
    	if(entry==noCache) return null;
	return (Constructor)map.get(entry);
    }
    
    /**
     * Store a constructor with an entry
     * @param entry The cache entry
     * @param method The constructor
     */
    public void put(Entry entry, Constructor method) {
    	if(entry!=noCache) map.put(entry, method);
    }
    
    /**
     * Get a cache entry from a name args pair
     * @param name The constructor name
     * @param args The arguments
     * @return A cache entry.
     */
    public Entry getEntry (String name, Object args[]){
    	Class params[] = new Class[args.length];
    	for (int i=0; i<args.length; i++) {
	    Class c = args[i] == null ? null : args[i].getClass();
	    if(c == Request.PhpArray.class) return noCache;
	    params[i] = c;
    	}
	return new Entry(name, params);
    }
    
}
