/*-*- mode: Java; tab-width:8 -*-*/


package php.java.bridge;

import java.lang.reflect.Array;
import java.util.Collection;
import java.util.Iterator;
import java.util.Map;

public abstract class PhpMap {
    Object value;
    boolean keyType; //false: key is integer (array), true: key is string (hash)
    public PhpMap(Object value, boolean keyType) {
	this.value=value;
	this.keyType=keyType;
	init();
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
    public static PhpMap getPhpMap(Object value) { 
	Util.logDebug("returning map for "+ value.getClass());

	if(value.getClass().isArray()) {
	    return 
		new PhpMap(value, false) {
		    boolean valid;
		    int i;
		    long length;
		    
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
			int i = ((Long)pos).intValue();
			return (i>0 && i<length && (Array.get(this.value, i)!=this));
		    }
		    public Object offsetGet(Object pos) {
			int i = ((Long)pos).intValue();
			Object o = Array.get(this.value, i);
			return o==this ? null : o;
		    }
		    public void offsetSet(Object pos, Object val) {
			int i = ((Long)pos).intValue();
			Array.set(this.value, i, val);
		    }
		    public void offsetUnset(Object pos) {
			int i = ((Long)pos).intValue();
			Array.set(this.value, i, this);
		    }
		};
	}
	if(value instanceof Collection) {
	    return 
		new PhpMap(value, false) {
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

		    // Should we really care?
		    public boolean offsetExists(Object pos) {
			return false;
		    }
		    public Object offsetGet(Object pos) {
			return null;
		    }
		    public void offsetSet(Object pos, Object val) {
		    }
		    public void offsetUnset(Object pos) {
		    }
		};
	}
	if(value instanceof Map) {
	    return
		new PhpMap(value, true){
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
			((Map)(this.value)).put(pos, val);
		    }
		    public void offsetUnset(Object pos) {
			((Map)(this.value)).remove(pos);
		    }
		};
	}
	return null;
    }

}
