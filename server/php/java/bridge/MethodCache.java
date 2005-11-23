/*-*- mode: Java; tab-width:8 -*-*/

package php.java.bridge;

import java.lang.reflect.Method;
import java.util.HashMap;

/**
 * Cache [(object, method, parameters) -> Method] mappings.  No
 * synchronization, so use this class per thread or per request
 * only.
 */
public class MethodCache {
    HashMap map = new HashMap();
    static final Entry noCache = new Entry();
	
    /**
     * A cache entry carrying the method name, class and the parameters
     * 
     * @author jostb
     *
     */
    public static class Entry {
	String symbol;
	Class clazz;
	Class params[];
		
	protected Entry () {}
	protected Entry (String name, Class clazz, Class params[]) {
	    this.symbol = name.intern();
	    this.clazz = clazz;
	    this.params = params;
	}
	private boolean hasResult = false;
	private int result = 1;
	public int hashCode() {
	    if(hasResult) return result;
	    for(int i=0; i<params.length; i++) {
		result = result * 31 + (params[i] == null ? 0 : params[i].hashCode());
	    }
	    result = result * 31 + clazz.hashCode();
	    result = result * 31 + symbol.hashCode();
	    hasResult = true;
	    return result;
	}
	public boolean equals(Object o) {
	    Entry that = (Entry) o;
	    if(clazz != that.clazz) return false;
	    if(symbol != that.symbol) return false;
	    if(params.length != that.params.length) return false;
	    for(int i=0; i<params.length; i++) {
		if(params[i] != that.params[i]) return false;
	    }
	    return true;
	}
    }
	
    /**
     * Get the method for the entry
     * @param entry The entry
     * @return The method
     */
    public Method get(Entry entry) {
    	if(entry==noCache) return null;
	return (Method)map.get(entry);
    }

    /**
     * Store a constructor with an entry
     * @param entry The cache entry
     * @param method The method
     */
    public void put(Entry entry, Method method) {
    	if(entry!=noCache) map.put(entry, method);
    }

    /**
     * Get a cache entry from a name, class and arguments.
     * @param name The method name
     * @param clazz The class
     * @param args The arguments
     * @return A cache entry.
     */
    public Entry getEntry (String name, Class clazz, Object args[]){
    	Class params[] = new Class[args.length];
    	for (int i=0; i<args.length; i++) {
	    Class c = args[i] == null ? null : args[i].getClass();
	    if(c == Request.PhpArray.class) return noCache;
	    params[i] = c;
    	}
	return new Entry(name, clazz, params);
    }
    
}
