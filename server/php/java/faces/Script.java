package php.java.faces;

import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.UnknownHostException;

import javax.faces.context.FacesContext;
import javax.script.Invocable;
import javax.script.ScriptException;

import php.java.bridge.Util;

/**
 * Proxy for calling php scripts.
 * 
 * @author jostb
 *
 */
public class Script
{
    private String script = null;
    private String port = null;
	
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
    }

    /**
     * Call "@port:/script.php". For example "@80:/java-server-faces/helloWorld.php"
     * @param name The php procedure name
     * @param args The arguments
     * @param script The script, e.g. http://127.0.0.1:80/JavaBridge/java-server-faces/helloWorld.php
     * @return The result.
     * @throws UnknownHostException If the local host doesn't exist. (?)
     * @throws MalformedURLException May indicate a problem, too.
     * @throws IOException If the port cannot be reached, for example when apache is down.
     */
    private Object call(String name, Object[] args, String script) throws UnknownHostException, MalformedURLException, IOException {
	try {
	    return ((Invocable)((PhpFacesContext)FacesContext.getCurrentInstance()).getScriptEngine(this, new URL(script))).invoke(name, args); 
	} catch (ScriptException e1) {
	    Util.printStackTrace(e1);
	    return null;		
	} catch (NoSuchMethodException e) {
	    Util.printStackTrace(e);
	    throw new RuntimeException(e);
     } 
    }
    /**
     * If the connection to @port:/script.php failed, try a second time with the port of the 
     * servlet engine. This may invoke the CGI machinery, though...
     * @param name The script name
     * @param args The arguments
     * @param script The local script, will be invoked through CGI/FastCGI
     * @return The result
     */
    private Object callWithExceptionHandler(String name, Object[] args, String script) {
            try {
                return call(name, args, script);
            } catch (UnknownHostException e1) {
    	    Util.printStackTrace(e1);
	    return null;		
            } catch (MalformedURLException e1) {
    	    Util.printStackTrace(e1);
	    return null;		
           } catch (IOException e1) {
   	    Util.printStackTrace(e1);
	    return null;		
            } 
    }
    private Object call(String name, Object[] args) {
        try {
            return call(name, args, getScript(port));
        } catch (UnknownHostException e) {
            return callWithExceptionHandler(name, args, getScript(null));
        } catch (MalformedURLException e) {
            return callWithExceptionHandler(name, args, getScript(null));
        } catch (IOException e) {
            return callWithExceptionHandler(name, args, getScript(null));
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
    
    private String getBase(String port) {
        return port!=null ? 
                ((PhpFacesContext)FacesContext.getCurrentInstance()).getBaseURL(port):
                    ((PhpFacesContext)FacesContext.getCurrentInstance()).getBaseURL();
       
    }

    private String getScript(String port) {
        return script.startsWith("/") ?
                getBase(port)+script:
                    script;
    }
    /**
     * Called for the managed Property "script".
     * @param value The script, for example "/helloWorld.php" or "http://.../helloWorld.php" or "@80:/.../helloWorld.php" as a short form for "http://127.0.0.1:80/.../helloWorld.php".
     */
    public void setScript(Object value) {
        String script = String.valueOf(value);
        if(script.startsWith("@")) {
            int idx = script.indexOf(':');
            if(idx!=-1) {
                port = script.substring(1, idx);
                script = script.substring(idx+1);
            } 
        } 
        this.script = script;
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
