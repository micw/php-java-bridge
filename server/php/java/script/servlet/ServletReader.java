/*-*- mode: Java; tab-width:8 -*-*/

package php.java.script.servlet;

/*
 * Copyright (C) 2003-2007 Jost Boekemeier and others.
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


import java.io.IOException;
import java.io.OutputStream;
import java.io.PrintWriter;
import java.io.Reader;
import java.net.URL;
import java.util.Arrays;
import java.util.Collections;
import java.util.Enumeration;
import java.util.Locale;
import java.util.Map;

import javax.servlet.RequestDispatcher;
import javax.servlet.ServletContext;
import javax.servlet.ServletException;
import javax.servlet.ServletOutputStream;
import javax.servlet.http.Cookie;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletRequestWrapper;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpServletResponseWrapper;

import php.java.bridge.NotImplementedException;
import php.java.bridge.Util;
import php.java.bridge.http.HeaderParser;
import php.java.bridge.http.HttpServer;
import php.java.script.IScriptReader;

/**
 * This class is similar to the URLReader, except that it connects to the FastCGIServlet directly using dispatcher.include();
 * the request is not routed through the servlet pool. <p>
 * This class can only be used to execute a simple PHP script, for PHP method invocation the URLReader must be used instead.
 * @author jostb
 *
 */
public class ServletReader extends Reader implements IScriptReader {
    final RequestDispatcher dispatcher;
    final URL url;
    final HttpServletRequest req;
    final HttpServletResponse res;
    final String servletPath;
    
    public ServletReader(ServletContext ctx, String resourcePath, URL url, HttpServletRequest req, HttpServletResponse res) throws IOException {
	this.url = url;
	this.req = req;
	this.res = res;
	
	this.dispatcher = req.getRequestDispatcher(resourcePath);
	if(Util.logLevel>5) Util.logDebug("creating request dispatcher for: " +resourcePath);
	this.servletPath = resourcePath;
    }
    /**
     * {@inheritDoc}
     */
    public void read(final Map env, final OutputStream out, final HeaderParser headerParser)
	    throws IOException {
	final HttpServletRequest req = new HttpServletRequestWrapper(this.req) {
            public String getHeader(String arg0) {
		return String.valueOf(env.get(arg0));
            }

            public String getServletPath() {
        	return servletPath;
            }
	    
            public Enumeration getHeaderNames() {
		return Collections.enumeration(Arrays.asList(IScriptReader.HEADER));
	    }

	    
            public Enumeration getHeaders(String arg0) {
		return Collections.enumeration(Arrays.asList(new String[]{getHeader(arg0)}));
            }

	    
            public int getIntHeader(String arg0) {
	        return Integer.parseInt(getHeader(arg0));
            }

	    
            public String getMethod() {
		return HttpServer.GET;
	    }

	    
            public String getPathInfo() {
		return null;
	    }

	    
            public String getPathTranslated() {
        	return null;
            }

	    
            public String getQueryString() {
	        return null;
            }

	    
            public String getRequestURI() {
        	return this.getContextPath()+this.getServletPath();
            }

	    
            public StringBuffer getRequestURL() {
		return new StringBuffer(url.toExternalForm());
	    }
            public String getProtocol() {
		return url.getProtocol();
	    }

            public String getRemoteAddr() {
        	return ServletReader.this.req.getLocalAddr();
            }

	    
            public String getRemoteHost() {
        	return ServletReader.this.req.getLocalName();
	    }

	    
            public int getRemotePort() {
		return ServletReader.this.req.getLocalPort();
	    }

            public String getScheme() {
		return url.getProtocol();
	    }

	};
	HttpServletResponse res = new HttpServletResponseWrapper(this.res) {

	    
            public void addCookie(Cookie arg0) {
		headerParser.addHeader("Set-Cookie", arg0.toString());
	    }

	    
            public void addDateHeader(String arg0, long arg1) {
	        throw new NotImplementedException();
            }

	    
            public void addHeader(String arg0, String arg1) {
		headerParser.addHeader(arg0, arg1);
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
		headerParser.addHeader("Status", String.valueOf(arg0));
	    }

	    
            public void sendError(int arg0, String arg1) throws IOException {
		headerParser.addHeader("Status", String.valueOf(arg0));
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
		headerParser.addHeader("Status", String.valueOf(arg0));
            }

	    
            public void setStatus(int arg0, String arg1) {
		headerParser.addHeader("Status", String.valueOf(arg0));
            }

	    
            public void flushBuffer() throws IOException {
	        throw new NotImplementedException();
            }

	    
            public int getBufferSize() {
	        throw new NotImplementedException();
            }

	    
            public String getCharacterEncoding() {
	        throw new NotImplementedException();
            }

	    
            public String getContentType() {
	        throw new NotImplementedException();
            }

	    
            public Locale getLocale() {
	        throw new NotImplementedException();
            }

	    
            public ServletOutputStream getOutputStream() throws IOException {
		return new ServletOutputStream() {
		    public void write(byte buf[], int off, int buflength) throws IOException {
			out.write(buf, off, buflength);
		    }
		    
                    public void write(int b) throws IOException {
		        throw new NotImplementedException();
		    }
		};
	    }

            public PrintWriter getWriter() throws IOException {
	        throw new NotImplementedException();
	        //return new PrintWriter(new OutputStreamWriter(out));
            }

	    
            public boolean isCommitted() {
	        throw new NotImplementedException();
           }

	    
            public void reset() {
	        throw new NotImplementedException();
            }

	    
            public void resetBuffer() {
	        throw new NotImplementedException();
            }

	    
            public void setBufferSize(int arg0) {
	        throw new NotImplementedException();
            }

	    
            public void setCharacterEncoding(String arg0) {
	        throw new NotImplementedException();
            }

	    
            public void setContentLength(int arg0) {
		headerParser.addHeader("Content-Length", String.valueOf(arg0));
	    }

	    
            public void setContentType(String arg0) {
	        throw new NotImplementedException();
            }

	    
            public void setLocale(Locale arg0) {
	        throw new NotImplementedException();

            }};
            
            try {
	        dispatcher.include(req, res);
            } catch (ServletException e) {
        	IOException ex = new IOException("include failed");
        	ex.initCause(e);
        	throw ex;
            }
    }
    /** {@inheritDoc} */
    public void close() throws IOException {}

    /** {@inheritDoc} */
    public int read(char[] cbuf, int off, int len) throws IOException {
        throw new IllegalStateException("Use servletReader.read(Hashtable, OutputStream) or use a FileReader() instead.");
    }

}
