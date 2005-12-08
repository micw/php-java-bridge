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
    JavaBridge _bridge;
    Object value;
    Class componentType;
    boolean keyType; //false: key is integer (array), true: key is string (hash)

    protected PhpMap(JavaBridge bridge, Object value, boolean keyType) {
	this._bridge=bridge;
    	this.value=value;
	this.keyType=keyType;
	this.componentType = value.getClass().getComponentType();
	init();
    }
    protected Object coerce(Object val) {
	return _bridge.coerce(componentType, val, _bridge.request.response);
    }
    protected abstract void init();
    /**
     * Returns the object at the current position.
     * @return The current object.
     */
    public abstract Object currentData();
    
    /**
     * Returns the key at the current position.
     * @return The current key.
     */
    public abstract Request.PhpString currentKey();
    
    /**
     * Forward one element.
     * @return true if move was possible, false otherwise.
     */
    public abstract boolean moveForward();
    
    /**
     * Checks if it is possible to advance one element
     * @return true if next element exists, false otherwise
     */
    public abstract boolean hasMore();

    /**
     * Returns the key type.
     * @return false if key is integer (array index), true if key is string (hash key)
     */
    public boolean getType() {
	return keyType;
    }

    /**
     * Checks if a given position exists. Use one of the methods from JavaBridge instead.
     * @param pos The position
     * @return true if an element exists at this position, false otherwise.
     * @see php.java.bridge.JavaBridge#offsetExists(Object[], Object)
     * @see php.java.bridge.JavaBridge#offsetExists(Map, Object)
     */
    public abstract boolean offsetExists(Object pos);
    
    /**
     * Returns the object at the posisition. Use one of the methods from JavaBridge instead.
     * @param pos The position.
     * @return The object at the given position.
     * @see php.java.bridge.JavaBridge#offsetGet(Object[], Object)
     * @see php.java.bridge.JavaBridge#offsetGet(Map, Object)
     */
    public abstract Object offsetGet(Object pos);
    
    /**
     * Set an object at position. Use one of the methods from JavaBridge instead.
     * @param pos The position.
     * @param val The object.
     * @see php.java.bridge.JavaBridge#offsetSet(Object[], Object, Object)
     * @see php.java.bridge.JavaBridge#offsetSet(Map, Object, Object)
     */
    public abstract void offsetSet(Object pos, Object val);
    
    /**
     * Remove an object from the position. Use one of the methods from JavaBridge instead.
     * @param pos The position.
      * @see php.java.bridge.JavaBridge#offsetUnset(Object[], Object)
     * @see php.java.bridge.JavaBridge#offsetUnset(Map, Object)
     */
    public abstract void offsetUnset(Object pos); 
    

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
		    public Object currentData() {
			if(!valid) return null;
			return Array.get(this.value, i);
		    }
		    public Request.PhpString currentKey() {
			if(!valid) return null;
			return new Request.SimplePhpString(_bridge, (String.valueOf(i)));
		    }
		    public boolean moveForward() {
			valid=++i<length;
			return valid?true:false;
		    }
		    public boolean hasMore() {
			return valid?true:false;
		    }

		    public boolean offsetExists(Object pos) {
		        return _bridge.offsetExists(length, pos);
		    }
		    public Object offsetGet(Object pos) {
		        return _bridge.offsetGet((Object[])value, pos);
		    }
		    public void offsetSet(Object pos, Object val) {
		        _bridge.offsetSet((Object[])value, pos, val);
		    }
		    public void offsetUnset(Object pos) {
		        _bridge.offsetUnset((Object[])value, pos);
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
		    public Object currentData() {
			return currentKey;
		    }
		    public Request.PhpString currentKey() {
			return new Request.SimplePhpString(_bridge, String.valueOf(i));
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

		    private void bail() {
			throw new UnsupportedOperationException("A collection does not have an offset. You can only iterate over its values.");
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
		    
		    protected void init() {
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
		    public Request.PhpString currentKey() {
			return new Request.SimplePhpString(_bridge, String.valueOf(currentKey));
		    }
		    public boolean moveForward() {
			currentKey = iter.hasNext() ? iter.next() : null;
			return currentKey==null?false:true;
		    }
		    public boolean hasMore() {
			return currentKey==null?false:true;
		    }

		    public boolean offsetExists(Object pos) {
		        return _bridge.offsetExists((Map) value, pos);
		    }
		    public Object offsetGet(Object pos) {
		        return _bridge.offsetGet((Map) value, pos);
		    }
		    public void offsetSet(Object pos, Object val) {
			_bridge.offsetSet((Map) value, pos, val);
		    }
		    public void offsetUnset(Object pos) {
			_bridge.offsetUnset((Map) value, pos);
		    }
		};
	}
	return null;
    }

}
