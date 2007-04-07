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

import php.java.bridge.NotImplementedException;

final class RemoteHttpServletResponse implements HttpServletResponse, BufferedResponse {
    
    private ServletContextFactory factory;
    private ByteArrayOutputStream buffer;

    public RemoteHttpServletResponse(ServletContextFactory factory) {
	this.factory = factory;
	this.buffer = new ByteArrayOutputStream();
    }
    public byte[] getBufferContents() {
	    return buffer.toByteArray();
    }
    public void flushBuffer() throws IOException {
	buffer.flush();
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

    public ServletOutputStream getOutputStream() throws IOException {
	return new ServletOutputStream() {
	    public void write(byte[] arg0, int arg1, int arg2) throws IOException {
		buffer.write(arg0, arg1, arg2);
	    }
	    public void write(int arg0) throws IOException {
		throw new NotImplementedException();
	    }};
    }

    public PrintWriter getWriter() throws IOException {
        return new PrintWriter(buffer);
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
	throw new NotImplementedException();
    }

    public void addDateHeader(String arg0, long arg1) {
	throw new NotImplementedException();
    }

    public void addHeader(String arg0, String arg1) {
	throw new NotImplementedException();
    }

    public void addIntHeader(String arg0, int arg1) {
	throw new NotImplementedException();
    }

    public boolean containsHeader(String arg0) {
	throw new NotImplementedException();
    }

    public String encodeRedirectURL(String arg0) {
	throw new NotImplementedException();
    }

    public String encodeRedirectUrl(String arg0) {
	throw new NotImplementedException();
    }

    public String encodeURL(String arg0) {
	throw new NotImplementedException();
    }

    public String encodeUrl(String arg0) {
	throw new NotImplementedException();
    }

    public void sendError(int arg0) throws IOException {
	throw new NotImplementedException();
    }

    public void sendError(int arg0, String arg1) throws IOException {
	throw new NotImplementedException();
    }

    public void sendRedirect(String arg0) throws IOException {
	throw new NotImplementedException();
    }

    public void setDateHeader(String arg0, long arg1) {
	throw new NotImplementedException();
    }

    public void setHeader(String arg0, String arg1) {
	throw new NotImplementedException();
    }

    public void setIntHeader(String arg0, int arg1) {
	throw new NotImplementedException();
    }

    public void setStatus(int arg0) {
	throw new NotImplementedException();
    }

    public void setStatus(int arg0, String arg1) {
	throw new NotImplementedException();
    }
}