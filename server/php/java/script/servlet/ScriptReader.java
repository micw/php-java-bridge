/*-*- mode: Java; tab-width:8 -*-*/

package php.java.script.servlet;

import java.io.FilterReader;
import java.io.IOException;
import java.io.Reader;
import java.io.StringReader;

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
 * This reader should be used to create a "one-shot", shared reader
 */
class ScriptReader extends FilterReader implements IScriptReader {
    
    private boolean isClosed;
    
    /**
     * Create a new reader
     * @param in the php input stream
     */
    public ScriptReader(Reader in) {
	super(in);
    }

    /**
     * Create a new reader
     * @param str the php string
     */
    public ScriptReader (String str) {
	super(new StringReader(str));
    }
    
    /** {@inheritDoc} */
    public synchronized void close () throws IOException {
	super.close ();
	isClosed = true;
    }
 
    /* (non-Javadoc)
     * @see php.java.script.servlet.IScriptReader#isClosed()
     */
    public synchronized boolean isClosed () {
	return isClosed;
    }
}
