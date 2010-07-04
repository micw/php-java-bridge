/*-*- mode: Java; tab-width:8 -*-*/

package php.java.script;

/*
 * Copyright (C) 2003-2007 Jost Boekemeier
 *
 * Permission is hereby granted, free of charge, to any person obtaining a
 * copy of this software and associated documentation files (the "Software"),
 * to deal in the Software without restriction, including without limitation
 * the rights to use, copy, modify, merge, publish, distribute, sublicense,
 * and/or sell copies of the Software, and to permit persons to whom the
 * Software is furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT.  IN NO EVENT SHALL
 * THE COPYRIGHT HOLDER(S) OR AUTHOR(S) BE LIABLE FOR ANY CLAIM, DAMAGES OR
 * OTHER LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE,
 * ARISING FROM, OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR
 * OTHER DEALINGS IN THE SOFTWARE.
 */

import java.io.File;
import java.io.IOException;
import java.io.Reader;

import javax.script.Bindings;
import javax.script.CompiledScript;
import javax.script.ScriptContext;
import javax.script.ScriptEngineFactory;
import javax.script.ScriptException;

/**
 * Abstract class for executing ScriptEngines. The abstract class itself provides default methods that pass 
 * all requests to the contained ScriptEngine. Subclasses of ScriptEngineDecorator should override some of
 * these methods and may also provide additional methods and fields.  
 * @author jostb
 */
public abstract class PhpScriptEngineDecorator implements IPhpScriptEngine {

    protected IPhpScriptEngine engine;

    /**
     * Create a new ScriptEngineDecorator
     * @param engine the ScriptEngine to decorate.
     */
    public PhpScriptEngineDecorator (IPhpScriptEngine engine) {
	this.engine = engine;
    }
    /**{@inheritDoc}*/
    public Bindings createBindings() {
	return engine.createBindings();
    }

    /**{@inheritDoc}*/
    public Object eval(Reader reader) throws ScriptException {
	return engine.eval(reader);
    }

    /**{@inheritDoc}*/
    public Object eval(Reader reader, Bindings namespace)
	    throws ScriptException {
	return engine.eval(reader, namespace);
    }

    /**{@inheritDoc}*/
    public Object eval(Reader reader, ScriptContext context)
	    throws ScriptException {
	return engine.eval(reader, context);
    }

    /**{@inheritDoc}*/
    public Object eval(String script) throws ScriptException {
	return engine.eval(script);
    }

    /**{@inheritDoc}*/
    public Object eval(String script, Bindings namespace)
	    throws ScriptException {
	return engine.eval(script, namespace);
    }

    /**{@inheritDoc}*/
    public Object eval(String script, ScriptContext context)
	    throws ScriptException {
	return engine.eval(script, context);
    }

    /**{@inheritDoc}*/
    public Object get(String key) {
	return engine.get(key);
    }

    /**{@inheritDoc}*/
    public Bindings getBindings(int scope) throws IllegalArgumentException {
	return engine.getBindings(scope);
    }

    /**{@inheritDoc}*/
    public ScriptContext getContext() {
	return engine.getContext();
    }

    /**{@inheritDoc}*/
    public ScriptEngineFactory getFactory() {
	return engine.getFactory();
    }

    /**{@inheritDoc}*/
    public void put(String key, Object value) throws IllegalArgumentException {
	engine.put(key, value);
    }

    /**{@inheritDoc}*/
    public void setBindings(Bindings namespace, int scope)
	    throws IllegalArgumentException {
	engine.setBindings(namespace, scope);
    }

    /**{@inheritDoc}*/
    public void setContext(ScriptContext ctx) {
	engine.setContext(ctx);
    }
    
    /**{@inheritDoc}*/
    public void close() throws IOException {
	engine.close();
    }
    /**{@inheritDoc}*/
    public void release() {
	engine.release();
    }
    /**{@inheritDoc}*/
    public CompiledScript compile(String script) throws ScriptException {
	return engine.compile(script);
    }
    /**{@inheritDoc}*/
    public CompiledScript compile(Reader reader) throws ScriptException {
	return engine.compile(reader);
    }
    /**{@inheritDoc}*/
    public boolean accept(File pathname) {
	return engine.accept(pathname);
    }
}
