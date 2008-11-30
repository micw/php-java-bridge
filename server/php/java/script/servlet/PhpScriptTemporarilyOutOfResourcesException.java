/*-*- mode: Java; tab-width:8 -*-*/

package php.java.script.servlet;

import php.java.script.PhpScriptException;

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
 * Thrown when the bridge refuses to allocate another continuation
 * from the pool. This happens when the servlet already has captured
 * <code>php.java.bridge.threads</code>/2-1 active continuations.
 * See system propety <code>php.java.bridge.threads</code>. Change the
 * pool size in your server.xml and declare the
 * new pool size with <code>-Dphp.java.bridge.threads=YOUR_POOL_SIZE</code>
 * Note: If <code>Util.getMBeanProperty("*:type=ThreadPool,name=http*", "maxThreads")</code>
 * returns a valid result, the mbean property value is used instead of the system property 
 * <code>php.java.bridge.threads</code>.
 */
public class PhpScriptTemporarilyOutOfResourcesException extends PhpScriptException {
    private static final long serialVersionUID = 8462790974758317348L;

    /**
     * Create a new exception.
     * @param string The exception string
     */
    public PhpScriptTemporarilyOutOfResourcesException(String string) {
      super(string);
    }

    /**
     * Create a new exception 
     * @param string The exception string
     * @param cause The chained exception
     */
    public PhpScriptTemporarilyOutOfResourcesException(String string, Throwable cause) {
      super(string, cause);
    }
}
