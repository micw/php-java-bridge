/*-*- mode: Java; tab-width:8 -*-*/

package php.java.servlet.fastcgi;

import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.net.UnknownHostException;
import java.util.Map;

import php.java.bridge.Util;
import php.java.bridge.Util.Process;
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

class NPChannelName extends ChannelName {
    public static final String PREFIX="\\\\.\\pipe\\";
    
    private String raPath;
    private String testRaPath;
    
    public void test() throws ConnectException {
	// TODO Auto-generated method stub
    }
    private NPChannel doConnect() throws ConnectException {
	try {
	    return new NPChannel(new RandomAccessFile(raPath, "rw"));
	} catch (IOException e) {
	    throw new ConnectException(e);
	}
    }
    public Channel connect() throws ConnectException {
	return doConnect();
    }

     protected Process doBind(Map env, String php) throws IOException {
        if(proc!=null) return null;
	if(raPath==null) throw new IOException("No pipe name available.");
	// Set override hosts so that php does not try to start a VM.
	// The value itself doesn't matter, we'll pass the real value
	// via the (HTTP_)X_JAVABRIDGE_OVERRIDE_HOSTS header field
	// later.
	env.put("X_JAVABRIDGE_OVERRIDE_HOSTS", servlet.override_hosts?"/":"");
	env.put("REDIRECT_STATUS", "200");
	String[] args = new String[]{php, raPath, "-d", "allow_url_include=On"};
	File home = null;
	if(php!=null) try { home = ((new File(php)).getParentFile()); } catch (Exception e) {Util.printStackTrace(e);}
	proc = new FCGIProcess(args, home, env, CGIServlet.getRealPath(servlet.context, servlet.cgiPathPrefix), servlet.phpTryOtherLocations);
	proc.start();
    return (Process)proc;
    }
    protected void waitForDaemon() throws UnknownHostException, InterruptedException {
	Thread.sleep(5000);
    }
    public String getFcgiStartCommand(String base, String php_fcgi_max_requests) {
	StringBuffer buf = new StringBuffer(".");
	buf.append(File.separator);
	buf.append("php-cgi-");
	buf.append(Util.osArch);
	buf.append("-");
	buf.append(Util.osName);
	String wrapper = buf.toString();
	String msg =
		"Please start Apache or IIS or start a standalone PHP server.\n"+
		"For example with the commands: \n\n" +
		"cd " + base + "\n" + 
		"set %REDIRECT_STATUS%=200\n"+ 
		"set %X_JAVABRIDGE_OVERRIDE_HOSTS%=/\n"+ 
		"set %PHP_FCGI_CHILDREN%=5\n"+ 
		"set %PHP_FCGI_MAX_REQUESTS%=php_fcgi_max_requests\n"+ 
		"php-cgi -d allow_url_include=On -n\n\n" + 
		"Or copy your php-cgi.exe to " + wrapper + "\n\n.";
        return msg;
    }
   public void findFreePort(boolean select) {
	try {
	    if(select) {
		File testRafile = File.createTempFile("JavaBridge", ".socket");
		testRaPath = PREFIX+testRafile.getPath();
		testRafile.delete();
	} else {
		testRaPath  = FastCGIServlet.FCGI_PIPE;
	    }
		} catch (IOException e) {
		    Util.printStackTrace(e);
	}
    }
    public void setDefaultPort() {
	    raPath=FastCGIServlet.FCGI_PIPE;
    }
    protected void setDynamicPort() {
	    raPath=testRaPath;
    }
}
