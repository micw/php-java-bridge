/*-*- mode: Java; tab-width:8 -*-*/


package php.java.bridge;

import java.lang.reflect.Array;
import java.util.Collection;
import java.util.Iterator;
import java.util.Map;

public abstract class PhpMap {
    JavaBridge bridge;
    Object value;
    Class componentType;
    boolean keyType; //false: key is integer (array), true: key is string (hash)
    public PhpMap(JavaBridge bridge, Object value, boolean keyType) {
	this.bridge=bridge;
    	this.value=value;
	this.keyType=keyType;
	this.componentType = value.getClass().getComponentType();
	init();
    }
    protected Object coerce(Object val) {
	return bridge.coerce(new Class[]{componentType}, new Object[]{val}, bridge.request.response)[0];
    }
    abstract void init();
    public abstract Object currentData();
    public abstract byte[] currentKey();
    public abstract boolean moveForward();
    public abstract boolean hasMore();
    public boolean getType() {
	return keyType;
    }

    public abstract boolean offsetExists(Object pos);
    public abstract Object offsetGet(Object pos);
    public abstract void offsetSet(Object pos, Object val);
    public abstract void offsetUnset(Object pos); //remove
    
    //
    // Return map for the value (PHP 5 only)
    //
    public static PhpMap getPhpMap(Object value, JavaBridge bridge) { 
	if(bridge.logLevel>3) bridge.logDebug("returning map for "+ value.getClass());

	if(value.getClass().isArray()) {
	    return 
		new PhpMap(bridge, value, false) {
		    boolean valid;
		    int i;
		    int length;
		    
		    void init() {
			i=0;
			length = Array.getLength(this.value);
			valid=length>0;
		    }
		    public Object currentData() {
			if(!valid) return null;
			return Array.get(this.value, i);
		    }
		    public byte[] currentKey() {
			if(!valid) return null;
			return String.valueOf(i).getBytes();
		    }
		    public boolean moveForward() {
			valid=++i<length;
			return valid?true:false;
		    }
		    public boolean hasMore() {
			return valid?true:false;
		    }

		    public boolean offsetExists(Object pos) {
			int i = ((Number)pos).intValue();
			return (i>0 && i<length && (Array.get(this.value, i)!=this));
		    }
		    public Object offsetGet(Object pos) {
			int i = ((Number)pos).intValue();
			Object o = Array.get(this.value, i);
			return o==this ? null : o;
		    }
		    public void offsetSet(Object pos, Object val) {
			int i = ((Number)pos).intValue();
			Array.set(this.value, i, coerce(val));
		    }
		    public void offsetUnset(Object pos) {
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
		    
		    void init() {
			iter = ((Collection)(this.value)).iterator();
			i = 0;
			currentKey=null;
			if(iter.hasNext()) {
			    currentKey=iter.next();
			}
		    }
		    public Object currentData() {
			return currentKey;
		    }
		    public byte[] currentKey() {
			return String.valueOf(i).getBytes();
		    }
		    public boolean moveForward() {
			if(iter.hasNext()) {
			    i++;
			    currentKey = iter.next();
			    return true;
			} else {
			    return false;
			}
		    }
		    public boolean hasMore() {
			return currentKey==null?false:true;
		    }

		    void bail() {
			throw new UnsupportedOperationException("A collection does not have an offset. You can only iterate over its hook.");
		    }

		    // Should we really care?
		    public boolean offsetExists(Object pos) {
			bail();
			return false;
		    }
		    public Object offsetGet(Object pos) {
			bail();
			return null;
		    }
		    public void offsetSet(Object pos, Object val) {
			bail();
		    }
		    public void offsetUnset(Object pos) {
			bail();
		    }
		};
	}
	if(value instanceof Map) {
	    return
		new PhpMap(bridge, value, true){
		    Object currentKey;
		    Iterator iter;
		    
		    void init() {
			iter = ((Map)(this.value)).keySet().iterator();
			currentKey=null;
			if(iter.hasNext()) {
			    currentKey=iter.next();
			}
		    }
		    public Object currentData() {
			if(currentKey==null) return null;
			return ((Map)(this.value)).get(currentKey);
		    }
		    public byte[] currentKey() {
			return String.valueOf(currentKey).getBytes();
		    }
		    public boolean moveForward() {
			currentKey = iter.hasNext() ? iter.next() : null;
			return currentKey==null?false:true;
		    }
		    public boolean hasMore() {
			return currentKey==null?false:true;
		    }

		    public boolean offsetExists(Object pos) {
			return ((Map)(this.value)).containsKey(pos);
		    }
		    public Object offsetGet(Object pos) {
			return ((Map)(this.value)).get(pos);
		    }
		    public void offsetSet(Object pos, Object val) {
			((Map)(this.value)).put(pos, coerce(val));
		    }
		    public void offsetUnset(Object pos) {
			((Map)(this.value)).remove(pos);
		    }
		};
	}
	return null;
    }

}
