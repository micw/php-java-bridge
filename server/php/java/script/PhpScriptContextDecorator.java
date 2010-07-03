package php.java.script;

import java.io.OutputStream;
import java.io.Reader;
import java.io.Writer;
import java.util.List;
import java.util.Map;

import javax.script.Bindings;

import php.java.bridge.ILogger;
import php.java.bridge.http.HeaderParser;

/**
 * Abstract class for ScriptContexts. The abstract class itself provides default methods that pass 
 * all requests to the contained ScriptContext. Subclasses of ScriptContextDecorator should override 
 * some of these methods and may also provide additional methods and fields.  
 * 
 * @author jostb
 */
public abstract class PhpScriptContextDecorator implements IPhpScriptContext {

    private IPhpScriptContext ctx;

    /**
     * Create a new ScriptEngineDecorator
     * @param engine the ScriptEngine to decorate.
     */
    public PhpScriptContextDecorator (IPhpScriptContext ctx) {
	this.ctx = ctx;
    }
    /**{@inheritDoc}*/
    public Object getAttribute(String name) throws IllegalArgumentException {
	return ctx.getAttribute(name);
    }

    /**{@inheritDoc}*/
    public Object getAttribute(String name, int scope)
	    throws IllegalArgumentException {
	return ctx.getAttribute(name, scope);
    }

    /**{@inheritDoc}*/
    public int getAttributesScope(String name) {
	return ctx.getAttributesScope(name);
    }

    /**{@inheritDoc}*/
    public Bindings getBindings(int scope) {
	return ctx.getBindings(scope);
    }

    /**{@inheritDoc}*/
    public Writer getErrorWriter() {
	return ctx.getErrorWriter();
    }

    /**{@inheritDoc}*/
    public Reader getReader() {
	return ctx.getReader();
    }

    /**{@inheritDoc}*/
    public List getScopes() {
	return ctx.getScopes();
    }

    /**{@inheritDoc}*/
    public Writer getWriter() {
	return ctx.getWriter();
    }

    /**{@inheritDoc}*/
    public Object removeAttribute(String name, int scope)
	    throws IllegalArgumentException {
	return ctx.removeAttribute(name, scope);
    }

    /**{@inheritDoc}*/
    public void setAttribute(String key, Object value, int scope)
	    throws IllegalArgumentException {
	ctx.setAttribute(key, value, scope);
    }

    /**{@inheritDoc}*/
    public void setBindings(Bindings namespace, int scope)
	    throws IllegalArgumentException {
	ctx.setBindings(namespace, scope);
    }

    /**{@inheritDoc}*/
    public void setErrorWriter(Writer writer) {
	ctx.setErrorWriter(writer);
    }

    /**{@inheritDoc}*/
    public void setReader(Reader reader) {
	ctx.setReader(reader);
    }

    /**{@inheritDoc}*/
    public void setWriter(Writer writer) {
	ctx.setWriter(writer);
    }

    /**{@inheritDoc}*/
    public Continuation getContinuation() {
	return ctx.getContinuation();
    }
    /**{@inheritDoc}*/
    public void setContinuation(Continuation kont) {
	ctx.setContinuation(kont);
    }
    /**{@inheritDoc}*/
    public Object init(Object callable) throws Exception {
	return ctx.init(callable);
    }
    /**{@inheritDoc}*/
    public void onShutdown(Object closeable) {
	ctx.onShutdown(closeable);
    }
    /**{@inheritDoc}*/
    public boolean call(Object kont) throws Exception {
	return ctx.call(kont);
    }
    /**{@inheritDoc}*/
    public Object get(String key) {
	return ctx.get(key);
    }
    /**{@inheritDoc}*/
    public Map getAll() {
	return ctx.getAll();
    }
    /**{@inheritDoc}*/
    public Object getHttpServletRequest() {
	return ctx.getHttpServletRequest();
    }
    /**{@inheritDoc}*/
    public Object getHttpServletResponse() {
	return ctx.getHttpServletResponse();
    }
    /**{@inheritDoc}*/
    public String getRealPath(String path) {
	return ctx.getRealPath(path);
    }
    /**{@inheritDoc}*/
    public Object getServlet() {
	return ctx.getServlet();
    }
    /**{@inheritDoc}*/
    public Object getServletConfig() {
	return ctx.getServletConfig();
    }
    /**{@inheritDoc}*/
    public Object getServletContext() {
	return ctx.getServletContext();
    }
    /**{@inheritDoc}*/
    public void put(String key, Object val) {
	ctx.put(key, val);
    }
    /**{@inheritDoc}*/
    public void putAll(Map map) {
	ctx.putAll(map);
    }
    /**{@inheritDoc}*/
    public void remove(String key) {
	ctx.remove(key);
    }
    /**{@inheritDoc}*/
    public Continuation createContinuation(Reader reader, Map env,
            OutputStream out, OutputStream err, HeaderParser headerParser,
            ResultProxy result, ILogger logger) {
	return ctx.createContinuation(reader, env, out, err, headerParser, result, logger);
    }
}
