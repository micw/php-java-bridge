package php.java.faces;

import java.net.URL;

import javax.faces.context.FacesContext;
import javax.script.Invocable;

import php.java.bridge.Util;
import php.java.script.PhpScriptEngine;


public class Script
{
    public String script = null;
    private String base = null;
	
    private PhpScriptEngine engine;
    public Script() {
	this.base = ((PhpFacesContext)FacesContext.getCurrentInstance()).getBase();
    }

    private Object call(String name, Object[] args) {
	if(script.startsWith("/")) script = base+script;
	try {
	    return ((Invocable)((PhpFacesContext)FacesContext.getCurrentInstance()).getScriptEngine(this, new URL(script))).call(name, args); 
	} catch (Exception e1) {
	    Util.printStackTrace(e1);
	    return null;		
	}
    }

    /**
     * @param index
     * @return
     */
    public synchronized Object getValue(int index) {
	return String.valueOf(call("getValueIndex", new Object[]{new Integer(index)}));
    }

    /**
     * @param property
     * @return
     */
    public synchronized Object getValue(Object property) {
	return String.valueOf(call("getValue", new Object[]{property}));
    }

    /**
     * @param index
     * @param value
     */
    public synchronized void setValue(int index, Object value) {
	call("setValueIndex", new Object[]{new Integer(index), value});
    }

    /**
     * @param property
     * @param value
     */
    public synchronized void setValue(Object property, Object value) {
	if("script".equals(property)) this.script=String.valueOf(value);
	else call("setValue", new Object[]{property, value});
    }

    /**
     * @param name2
     * @param classes
     * @param args
     */
    public synchronized Object call(String name, Class[] classes, Object[] args) {
	return String.valueOf(call(name, args));
    }
}
