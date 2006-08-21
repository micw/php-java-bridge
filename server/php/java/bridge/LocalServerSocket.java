/*-*- mode: Java; tab-width:8 -*-*/

package php.java.bridge;

/*
 * Copyright (C) 2006 Jost Boekemeier
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
import java.net.Socket;

class LocalServerSocket implements ISocketFactory {

    public static final String DefaultSocketname = "/var/run/.php-java-bride_socket";
    private String name;
    private int peer;
	
    public static ISocketFactory create(int logLevel, String name, int backlog) throws IOException {
	if(name==null) name=DefaultSocketname;
	if(name.startsWith("INET:") || name.startsWith("INET_LOCAL:")) return null;

	return new LocalServerSocket(logLevel, name==null?DefaultSocketname:name, backlog);
    }
    private LocalServerSocket(int logLevel, String name, int backlog)
	throws IOException {
	if(name.startsWith("LOCAL:")) name=name.substring(6);
	this.name=name;
	if(0==(this.peer=JavaBridge.startNative(logLevel, backlog, name))) throw new IOException("Unix domain sockets not available.");
		
    }
    public void close() throws IOException {
	JavaBridge.sclose(this.peer);
    }

    public Socket accept() throws IOException {
	int peer = JavaBridge.accept(this.peer);
	return new LocalSocket(peer);
    }
    public String getSocketName() {
    	return name;
    }
    public String toString() {
    	return "LOCAL:" +getSocketName();
    }
}
