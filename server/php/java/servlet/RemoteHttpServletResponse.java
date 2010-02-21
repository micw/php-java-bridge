/*-*- mode: Java; tab-width:8 -*-*/

package php.java.servlet;

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

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.Locale;

import javax.servlet.ServletOutputStream;
import javax.servlet.http.Cookie;
import javax.servlet.http.HttpServletResponse;

/**
 * A servlet response which writes its output to an internal buffer. The buffer can be fetched using
 * "getBufferContents()". May be used by remote PHP scripts (those accessing PhpJavaServlet) through the "java_context()->getHttpServletResponse()" API. 
 * Also used by the "java_virtual()" API.
 * 
 * @author jostb
 *
 */
public class RemoteHttpServletResponse implements HttpServletResponse, BufferedResponse {
    
    private ByteArrayOutputStream buffer;

    public RemoteHttpServletResponse() {
	this.buffer = new ByteArrayOutputStream();
    }
    public byte[] getBufferContents() throws IOException {
	committed = true;
	flushBuffer();
	return buffer.toByteArray();
    }
    public void flushBuffer() throws IOException {
	getWriter().flush();
    }

    public int getBufferSize() {
	return buffer.size();
    }

    private String encoding;
    public String getCharacterEncoding() {
	return encoding;
    }
    private String contentType;
    public String getContentType() {
	return contentType;
    }

    private Locale locale;
    public Locale getLocale() {
	return locale;
    }

    private ServletOutputStream out = null;
    public ServletOutputStream getOutputStream() throws IOException {
	if (out!=null) return out;
	return out = new ServletOutputStream() {
	    public void write(byte[] arg0, int arg1, int arg2) throws IOException {
		buffer.write(arg0, arg1, arg2);
	    }
	    public void write(int arg0) throws IOException {
		buffer.write(arg0);
	    }};
    }
    private PrintWriter writer = null;
    public PrintWriter getWriter() throws IOException {
	if (writer != null) return writer;
        return writer = new PrintWriter(getOutputStream());
    }

    private boolean committed; 
    public boolean isCommitted() {
	return committed;
    }

    public void reset() {
	buffer.reset();
    }

    public void resetBuffer() {
	reset();
    }

    public void setBufferSize(int arg0) {
    }

    public void setCharacterEncoding(String arg0) {
	encoding = arg0;
    }

    public void setContentLength(int arg0) {
    }

    public void setContentType(String arg0) {
	contentType = arg0;
    }

    public void setLocale(Locale arg0) {
	locale = arg0;
    }

    public void addCookie(Cookie arg0) {
	throw new IllegalStateException("Use the appropriate PHP API procedure instead");
    }

    public void addDateHeader(String arg0, long arg1) {
	throw new IllegalStateException("Use the appropriate PHP API procedure instead");
    }

    public void addHeader(String arg0, String arg1) {
	throw new IllegalStateException("Use the appropriate PHP API procedure instead");
    }

    public void addIntHeader(String arg0, int arg1) {
	throw new IllegalStateException("Use the appropriate PHP API procedure instead");
    }

    public boolean containsHeader(String arg0) {
	throw new IllegalStateException("Use the appropriate PHP API procedure instead");
    }

    public String encodeRedirectURL(String arg0) {
	throw new IllegalStateException("Use the appropriate PHP API procedure instead");
    }

    /**@param arg0 
     * @return none
     * @deprecated*/
    public String encodeRedirectUrl(String arg0) {
	throw new IllegalStateException("Use the appropriate PHP API procedure instead");
    }

    public String encodeURL(String arg0) {
	throw new IllegalStateException("Use the appropriate PHP API procedure instead");
    }

    /**@param arg0 
     * @return none
     * @deprecated*/
    public String encodeUrl(String arg0) {
	throw new IllegalStateException("Use the appropriate PHP API procedure instead");
    }

    public void sendError(int arg0) throws IOException {
	throw new IllegalStateException("Use the appropriate PHP API procedure instead");
    }

    public void sendError(int arg0, String arg1) throws IOException {
	throw new IllegalStateException("Use the appropriate PHP API procedure instead");
    }

    public void sendRedirect(String arg0) throws IOException {
	throw new IllegalStateException("Use the appropriate PHP API procedure instead");
    }

    public void setDateHeader(String arg0, long arg1) {
	throw new IllegalStateException("Use the appropriate PHP API procedure instead");
    }

    public void setHeader(String arg0, String arg1) {
	throw new IllegalStateException("Use the appropriate PHP API procedure instead");
    }

    public void setIntHeader(String arg0, int arg1) {
	throw new IllegalStateException("Use the appropriate PHP API procedure instead");
    }

    public void setStatus(int arg0) {
	throw new IllegalStateException("Use the appropriate PHP API procedure instead");
    }

    /**@param arg0 
     * @param arg1 
     * @deprecated*/
    public void setStatus(int arg0, String arg1) {
	throw new IllegalStateException("Use the appropriate PHP API procedure instead");
    }
}