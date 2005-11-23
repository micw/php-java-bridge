package php.java.faces;

import java.net.MalformedURLException;
import java.net.URL;

import javax.faces.context.FacesContext;
import javax.script.Invocable;
import javax.script.ScriptException;

import php.java.bridge.Util;
import php.java.script.PhpScriptEngine;

/**
 * Proxy for calling php scripts.
 * 
 * @author jostb
 *
 */
public class Script
{
    public String script = null;
    private String base = null;
	
    private PhpScriptEngine engine;
    
    /**
     * Creates a new script proxy. Scripts may be defined in <code>faces-config.xml</code> as "/scriptname.php" for local scripts or "http://.../scriptname.php" for remote scripts.
     * Remote scripts are executed and invoked on the remote server.
     * Example from <code>faces-config.xml</code>:<br>
     * <code>
     * 	&lt;managed-bean&gt;<br>
		&lt;managed-bean-name&gt;helloWorldBacking&lt;/managed-bean-name&gt;<br>
		&lt;managed-bean-class&gt;php.java.faces.Script&lt;/managed-bean-class&gt;<br>
		&lt;managed-bean-scope&gt;request&lt;/managed-bean-scope&gt;<br>

        &lt;managed-property&gt;<br>
            &lt;property-name&gt;script&lt;/property-name&gt;<br>
            &lt;value&gt;/scriptname.php&lt;/value&gt;<br>
        &lt;/managed-property&gt;<br>

	&lt;/managed-bean&gt;<br>
     * </code>
     * 
     * @see php.java.bridge.Invocable
     * @see php.java.script.URLReader
     */
    public Script() {
	this.base = ((PhpFacesContext)FacesContext.getCurrentInstance()).getBaseURL();
    }

    private Object call(String name, Object[] args) {
	if(script.startsWith("/")) script = base+script;
	try {
	    return ((Invocable)((PhpFacesContext)FacesContext.getCurrentInstance()).getScriptEngine(this, new URL(script))).call(name, args); 
	} catch (ScriptException e1) {
	    Util.printStackTrace(e1);
	    return null;		
	} catch (MalformedURLException e) {
	    Util.printStackTrace(e);
	    throw new RuntimeException(e);
	}
    }

    /**
     * Cal the php method "getValueIndex".
     * @param index The index
     * @return The value at that position.
     */
    public synchronized Object getValue(int index) {
	return String.valueOf(call("getValueIndex", new Object[]{new Integer(index)}));
    }

    /**
     * Call the php method "getValue".
     * @param property The property
     * @return The value of the property.
     */
    public synchronized Object getValue(Object property) {
	return String.valueOf(call("getValue", new Object[]{property}));
    }

    /**
     * Call the php method "setValueIndex".
     * @param index The array index
     * @param value The new value
     */
    public synchronized void setValue(int index, Object value) {
	call("setValueIndex", new Object[]{new Integer(index), value});
    }

    /**
     * Call the php method "setValue".
     * @param property The property to set
     * @param value The new value
     */
    public synchronized void setValue(Object property, Object value) {
	if("script".equals(property)) setScript(value); // workaround needed for Apache myFaces.
	else call("setValue", new Object[]{property, value});
    }
    
    /**
     * Called for the managed Property "script".
     * @param value The script, for example "/helloWorld.php" or "http://.../helloWorld.php".
     */
    public void setScript(Object value) {
        this.script=String.valueOf(value);
    }
    
 
    /**
     * Call a php method.
     * @param name The name of the method
     * @param classes The parameters
     * @param args The arguments
     */
    public synchronized Object call(String name, Class[] classes, Object[] args) {
	return String.valueOf(call(name, args));
    }
}
