/*-*- mode: Java; tab-width:8 -*-*/


package php.java.bridge;

import java.lang.reflect.Array;
import java.util.Collection;
import java.util.Iterator;
import java.util.Map;

/**
 * Maps php array get/set and foreach to java array, Map and Collections access.
 * @author jostb
 *
 */
public abstract class PhpMap {
    JavaBridge bridge;
    Object value;
    Class componentType;
    boolean keyType; //false: key is integer (array), true: key is string (hash)

    protected PhpMap(JavaBridge bridge, Object value, boolean keyType) {
	this.bridge=bridge;
    	this.value=value;
	this.keyType=keyType;
	this.componentType = value.getClass().getComponentType();
	init();
    }
    protected Object coerce(Object val) {
	return bridge.coerce(new Class[]{componentType}, new Object[]{val}, bridge.request.response)[0];
    }
    protected abstract void init();
    protected abstract Object currentData();
    protected abstract Request.PhpString currentKey();
    protected abstract boolean moveForward();
    protected abstract boolean hasMore();
    protected boolean getType() {
	return keyType;
    }

    protected abstract boolean offsetExists(Object pos);
    protected abstract Object offsetGet(Object pos);
    protected abstract void offsetSet(Object pos, Object val);
    protected abstract void offsetUnset(Object pos); //remove
    

    /**
     * Returns a PhpMap for a given value.
     * @param value The value, must be an array or implement Map or Collection
     * @param bridge The bridge instance
     * @return The PhpMap
     */
    public static PhpMap getPhpMap(Object value, JavaBridge bridge) { 
	if(bridge.logLevel>3) bridge.logDebug("returning map for "+ value.getClass());

	if(value.getClass().isArray()) {
	    return 
		new PhpMap(bridge, value, false) {
		    boolean valid;
		    int i;
		    int length;
		    
		    protected void init() {
			i=0;
			length = Array.getLength(this.value);
			valid=length>0;
		    }
		    protected Object currentData() {
			if(!valid) return null;
			return Array.get(this.value, i);
		    }
		    protected Request.PhpString currentKey() {
			if(!valid) return null;
			return new Request.SimplePhpString(bridge, (String.valueOf(i)));
		    }
		    protected boolean moveForward() {
			valid=++i<length;
			return valid?true:false;
		    }
		    protected boolean hasMore() {
			return valid?true:false;
		    }

		    protected boolean offsetExists(Object pos) {
			int i = ((Number)pos).intValue();
			return (i>0 && i<length && (Array.get(this.value, i)!=this));
		    }
		    protected Object offsetGet(Object pos) {
			int i = ((Number)pos).intValue();
			Object o = Array.get(this.value, i);
			return o==this ? null : o;
		    }
		    protected void offsetSet(Object pos, Object val) {
			int i = ((Number)pos).intValue();
			Array.set(this.value, i, coerce(val));
		    }
		    protected void offsetUnset(Object pos) {
			int i = ((Number)pos).intValue();
			Array.set(this.value, i, this);
		    }
		};
	}
	if(value instanceof Collection) {
	    return 
		new PhpMap(bridge, value, false) {
		    Object currentKey;
		    int i;
		    Iterator iter;
		    
		    protected void init() {
			iter = ((Collection)(this.value)).iterator();
			i = 0;
			currentKey=null;
			if(iter.hasNext()) {
			    currentKey=iter.next();
			}
		    }
		    protected Object currentData() {
			return currentKey;
		    }
		    protected Request.PhpString currentKey() {
			return new Request.SimplePhpString(bridge, String.valueOf(i));
		    }
		    protected boolean moveForward() {
			if(iter.hasNext()) {
			    i++;
			    currentKey = iter.next();
			    return true;
			} else {
			    return false;
			}
		    }
		    protected boolean hasMore() {
			return currentKey==null?false:true;
		    }

		    private void bail() {
			throw new UnsupportedOperationException("A collection does not have an offset. You can only iterate over its hook.");
		    }

		    // Should we really care?
		    protected boolean offsetExists(Object pos) {
			bail();
			return false;
		    }
		    protected Object offsetGet(Object pos) {
			bail();
			return null;
		    }
		    protected void offsetSet(Object pos, Object val) {
			bail();
		    }
		    protected void offsetUnset(Object pos) {
			bail();
		    }
		};
	}
	if(value instanceof Map) {
	    return
		new PhpMap(bridge, value, true){
		    Object currentKey;
		    Iterator iter;
		    
		    protected void init() {
			iter = ((Map)(this.value)).keySet().iterator();
			currentKey=null;
			if(iter.hasNext()) {
			    currentKey=iter.next();
			}
		    }
		    protected Object currentData() {
			if(currentKey==null) return null;
			return ((Map)(this.value)).get(currentKey);
		    }
		    protected Request.PhpString currentKey() {
			return new Request.SimplePhpString(bridge, String.valueOf(currentKey));
		    }
		    protected boolean moveForward() {
			currentKey = iter.hasNext() ? iter.next() : null;
			return currentKey==null?false:true;
		    }
		    protected boolean hasMore() {
			return currentKey==null?false:true;
		    }

		    protected boolean offsetExists(Object pos) {
			return ((Map)(this.value)).containsKey(pos);
		    }
		    protected Object offsetGet(Object pos) {
			return ((Map)(this.value)).get(pos);
		    }
		    protected void offsetSet(Object pos, Object val) {
			((Map)(this.value)).put(pos, coerce(val));
		    }
		    protected void offsetUnset(Object pos) {
			((Map)(this.value)).remove(pos);
		    }
		};
	}
	return null;
    }

}
