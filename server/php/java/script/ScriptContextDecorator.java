package php.java.script;

import java.io.Reader;
import java.io.Writer;
import java.util.List;

import javax.script.Bindings;
import javax.script.ScriptContext;

public abstract class ScriptContextDecorator implements ScriptContext {

    protected ScriptContext ctx;

    public ScriptContextDecorator(ScriptContext ctx) {
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
    public Object removeAttribute(String name, int scope) {
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
}
