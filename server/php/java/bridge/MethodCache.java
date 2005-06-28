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
	
    public class Entry {
	String name;
	Class clazz;
	Class params[];
		
	private Method method;
		
	public Entry (String name, Class clazz, Class params[]) {
	    this.name = name;
	    this.clazz = clazz;
	    this.params = params;
	}
	public int hashCode() {
	    int result = 1;
	    for(int i=0; i<params.length; i++) {
		result = result * 31 + (params[i] == null ? 0 : params[i].hashCode());
	    }
	    result = result * 31 + clazz.hashCode();
	    result = result * 31 + name.hashCode();
	    return result;
	}
	public boolean equals(Object o) {
	    if (o.getClass()!=getClass()) return false;
	    Entry that = (Entry) o;
	    if(clazz != that.clazz) return false;
	    if(!name.equals(that.name)) return false;
	    if(params.length != that.params.length) return false;
	    for(int i=0; i<params.length; i++) {
		if(params[i] != that.params[i]) return false;
	    }
	    return true;
	}
    }
	
    public Method get(Entry entry) {
	return (Method)map.get(entry);
    }
    public void put(Entry entry, Method method) {
	map.put(entry, method);
    }
    public Entry getEntry (String name, Class clazz, Class params[]){
	return new Entry(name, clazz, params);
    }
}
