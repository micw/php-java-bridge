/*-*- mode: Java; tab-width:8 -*-*/

package php.java.script;

import javax.script.Invocable;
import javax.script.ScriptException;

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


/**
 * A ScriptEngineDecorator implementing the Java 1.5 Closeable and Invocable interface.
 * @author jostb
 */
public class CloseableInvocablePhpScriptEngineDecorator extends PhpScriptEngineDecorator implements Invocable, java.io.Closeable {

    public CloseableInvocablePhpScriptEngineDecorator(IPhpScriptEngine engine) {
	super(engine);
    }

    /** {@inheritDoc} */
    public Object getInterface(Object thiz, Class clasz) {
	return ((Invocable)engine).getInterface(thiz, clasz);
    }

    /** {@inheritDoc} */
    public Object getInterface(Class clasz) {
	return ((Invocable)engine).getInterface(clasz);
    }

    /** {@inheritDoc} */
    public Object invokeFunction(String methodName, Object[] args)
            throws ScriptException, NoSuchMethodException {
	return ((Invocable)engine).invokeFunction(methodName, args);
    }

    /** {@inheritDoc} */
    public Object invokeMethod(Object thiz, String methodName, Object[] args)
            throws ScriptException, NoSuchMethodException {
	return ((Invocable)engine).invokeMethod(thiz, methodName, args);
    }
}
