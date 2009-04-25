/*-*- mode: Java; tab-width:8 -*-*/

package php.java.script.servlet;

import java.io.File;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.Reader;

import javax.servlet.ServletContext;

import php.java.bridge.Util;
import php.java.servlet.CGIServlet;

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


final class ScriptFileReader extends Reader {
    private String path;
    private IScriptReader reader;
    private Reader realReader;
    
    ScriptFileReader(String path, IScriptReader reader) throws IOException {
	this.path = path;
	this.reader = reader;
    }
    
    ScriptFileReader(String path) throws IOException {
	this.path = path;
	this.reader = null;
    }
    public String getResourcePath(ServletContext ctx) throws IOException {
	init(CGIServlet.getRealPath(ctx, path));
	return path;
    }
    private static void createFile(File file, IScriptReader reader) throws IOException {
	FileOutputStream fout = new FileOutputStream(file);
	OutputStreamWriter writer = new OutputStreamWriter(fout);
	char[] cbuf = new char[Util.BUF_SIZE];
	int length;
	while((length=reader.read(cbuf, 0, cbuf.length))>0) 
	    writer.write(cbuf, 0, length);
	writer.close();
    }
    private boolean readerIsClosed() {
	return reader.isClosed();
    }
    public void init(String realPath) throws IOException {
	File realFile = new File(realPath);
	if (reader!=null && !readerIsClosed()) {
	    createFile(realFile, reader);
	    reader.close();
	    reader = null;
	}
	realReader = new FileReader(realFile);
    }
    public void close() throws IOException {
	if (realReader!=null) {
	    realReader.close();
	    realReader = null;
	}
    }
    public int read(char[] cbuf, int off, int len) throws IOException {
	return realReader.read(cbuf, off, len);
    }
}
