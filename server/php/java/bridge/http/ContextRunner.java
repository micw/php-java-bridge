/*-*- mode: Java; tab-width:8 -*-*/

package php.java.bridge.http;

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
import java.io.InputStream;
import java.io.OutputStream;
import java.util.HashMap;

import php.java.bridge.DynamicJavaBridgeClassLoader;
import php.java.bridge.JavaBridge;
import php.java.bridge.JavaBridgeClassLoader;
import php.java.bridge.Request;
import php.java.bridge.Util;

/**
 * The ContextRunner usually represents the physical connection,
 * it manages the "high speed" communication link.  It
 * pulls a ContextFactory and executes it.  After execution the context is destroyed.
 * <p>ContextRunners are kept in a per-loader map and each client may refer to its runner by
 * passing X_JAVARIDGE_CONTEXT_DEFAULT. The ContextRunner may ignore this hint
 * and prepare for a new physical connection, if the ID does not exist in its map. This
 * usually happens when there are two separate bridges installed in context A and context B and the client
 * uses a persistent connection to context A. An attempt to re-use the same connection for B fails
 * because the classes are loaded via two separate class loaders. A client must check the returned 
 * X_JAVABRIDGE_CONTEXT_DEFAULT value. If it is not set, the client must create a new physical connection.
 * For named pipes this means that the connection should have been prepared and sent via X_JAVABRIDGE_CHANNEL,
 * as usual. Otherwise the bridge will use the SocketContextServer instead. -- The client may destroy 
 * the new pipe, if the server has accepted X_JAVABRIDGE_CONTEXT_DEFAULT, of course.
 * </p>
 * <p>See <a href="http://php-java-bridge.sourceforge.net#global-servlet">http://php-java-bridge.sourceforge.net#global-servlet</a> for details how to install
 * the bridge globally.</p>
 */
public class ContextRunner implements Runnable {
    
    private static final HashMap runners = new HashMap(Integer.parseInt(Util.THREAD_POOL_MAX_SIZE));
    
    private IContextFactory ctx; /* the persistent ContextFactory */
    private Request request;
    private InputStream in;
    private OutputStream out;
    private IContextServer.Channel channel;
    private ContextServer contextServer; /* the persistent ContextServer */
    
    protected ContextRunner(ContextServer contextServer, IContextServer.Channel channel) {
	this.contextServer = contextServer;
	this.channel = channel;
    }
    private int readLength() throws IOException{
	byte buf[] = new byte[1];
	in.read(buf);
	int val = (0xFF&buf[0]);
	if(val==0xFF) {
	    buf = new byte[2];
	    in.read(buf);
	    val = (0xFF&buf[0]) | (0xFF00&(buf[1]<<8));
	}
	return val;
    }
    private String readString(int length) throws IOException {
	byte buf[] = new byte[length];
	in.read(buf);
	return new String(buf, Util.ASCII);
    }

    private String readName() throws IOException {
	return readString(readLength());
    }
    /**
     * Sets a new Input/OutputStream into the bridge
     * @param bridge the JavaBridge
     * @param in the new InputStream
     * @param out the new OutputStream
     */
    private void setIO(JavaBridge bridge, InputStream in, OutputStream out) {
	bridge.request.reset();
    	bridge.in=in;
    	bridge.out=out;	
    }

    private void init() throws IOException {
	if(Util.logLevel>4) Util.logDebug("starting a new ContextRunner " + this);
	out = channel.getOuptutStream();
	in = channel.getInputStream();

	int c = in.read();
	if(c!=077) {
	    try {out.write(0); }catch(IOException e){}
	    throw new IOException("Protocol violation");
	}
	String name = readName();
    	ctx = (IContextFactory) ContextFactory.get(name, contextServer);
    	if(ctx == null) throw new IOException("No context available for: " + name + ".");
    	put(name, this);
    	JavaBridge bridge = ctx.getBridge();
	// The first statement was executed with the default
	// classloader, now set the dynamic class loader into the
	// bridge:
	JavaBridgeClassLoader loader = bridge.getClassLoader();
	DynamicJavaBridgeClassLoader xloader = null;
	try {
	    xloader = 
		(DynamicJavaBridgeClassLoader) 
		Thread.currentThread().getContextClassLoader();
	} 
	catch (SecurityException e) {/*ignore*/}
	catch (ClassCastException e1) {/*ignore*/}
	loader.setClassLoader(xloader);
	
	setIO(bridge, in, out);
	this.request = bridge.request;
    }
    /**
     * May be called to recycle the runner from the pool of ContextRunners for a new contextServer. 
     * Reduces the # of physical connections when there is only one 
     * ContextFactory factory but several ContextServer instances per ClassLoader, 
     * for example when the PhpCGIServlet is used globally, see ABOUT.HTM#global-servlet".
     * In this setup we have n PHP childs using n physical connections (n ContextRunner instances), k web contexts
     * (k contextServers) and up to k*n ContextFactory and JavaBridge instances.
     * 
     * @param channelName the channelName. This procedure sets the runner into channelName as a side effect.
     * @return the ContextRunner, if found, otherwise null.
     */
    public static synchronized ContextRunner checkRunner(IContextServer.ChannelName channelName) {
	String id = channelName.getKontext();
	IContextFactory ctx = channelName.getCtx();
	ContextRunner runner = null;
	if(ctx!=null && id!=null) runner = (ContextRunner)runners.get(id);
	return runner;
    }
    private static synchronized void put(String kontext, ContextRunner runner) {
	runners.put(kontext, runner);
    }
    private static synchronized void remove(String kontext) {
	runners.remove(kontext);
    }
    /**
     * Recycle the runner for a different web context.
     * @param ctx the old contextFacory belonging to a different contextServer
     */
    public void recycle(IContextFactory ctx) {
	if(this.ctx == ctx) return; 
	if(Util.logLevel>4) Util.logDebug("recycle " + this.ctx + " for " + ctx + ", ContextRunner: " + this);
	
	request.setBridge(ctx.getBridge());
    }
    
    /**
     * Return the channel of the current runner.
     * @return The Channel
     */
    public IContextServer.Channel getChannel() {
	return channel;
    }
    public void run() {
	try {
	    init();
	    request.handleRequests();
	} catch (IOException e) {
	    Util.printStackTrace(e);
	} finally {
	    if(ctx!=null) {
		remove(ctx.getId());
		ctx.destroy();
	    }
	    channel.shutdown();
	}
    }
}
