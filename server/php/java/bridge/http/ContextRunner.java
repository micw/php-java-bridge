/*-*- mode: Java; tab-width:8 -*-*/

package php.java.bridge.http;

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

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

import php.java.bridge.JavaBridge;
import php.java.bridge.Request;
import php.java.bridge.SimpleJavaBridgeClassLoader;
import php.java.bridge.Util;

/**
 * The ContextRunner usually represents the physical connection, it
 * manages the "high speed" communication link.  It pulls a
 * ContextFactory and executes it.  After execution the context is
 * destroyed.  <p>ContextRunners are kept in a per-loader map and each
 * client may refer to its runner by keeping a persistent connection to it. The ContextFactory may ignore this
 * and prepare for a new physical connection by sending back the ID of a new ContextServer.
 * This usually happens when there are two separate
 * bridges installed in context A and context B and the client uses a
 * persistent connection to context A. An attempt to re-use the same
 * connection for B fails because the classes are loaded via two
 * separate class loaders.  For named pipes this means
 * that the connection should have been prepared and sent via
 * X_JAVABRIDGE_CHANNEL, as usual. Otherwise the bridge will use the
 * SocketContextServer instead. -- The client may destroy the new
 * pipe if the server has accepted the previous ID, of
 * course.  </p>
 * <p>
 * Example: Two web apps WA1 and WA2, two ContextServers, @9667 and @9668. Client has persistent connection to @9667, sends
 * initial HTTP PUT request to WA2. WA2 responds with a redirect to @9668. Client uses this new persistent connection (but keeps persistent
 * connection to @9667, of course).
 * </p>
 */
public class ContextRunner implements Runnable {
    
    private IContextFactory ctx; /* the persistent ContextFactory */
    private Request request;
    private InputStream in;
    private OutputStream out;
    private AbstractChannel channel;
    private ContextFactory.ICredentials contextServer; /* the ContextServer of the web application, used for security checks in ContextFactory.get(...)  */
    
    protected ContextRunner(ContextFactory.ICredentials contextServer, AbstractChannel channel) {
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
	} else 
	    throw new IllegalStateException("context length");
	
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

    private boolean init() throws IOException {
	if(Util.logLevel>4) Util.logDebug("starting a new ContextRunner " + this);
	out = channel.getOuptutStream();
	in = channel.getInputStream();

	int c = in.read();
	if(c!=0177) {
	    if(c==-1) return false; // client has closed the connection
	    
	    try {out.write(0); }catch(IOException e){}
	    throw new IOException("Protocol violation");
	}
	String name = readName();
    	ctx = (IContextFactory) ContextFactory.get(name, contextServer);
    	if(ctx == null) throw new IOException("No context available for: " + name + ". Please make sure that your script does not exceed php.java.bridge.max_wait, currently set to: "+Util.MAX_WAIT);
    	JavaBridge bridge = ctx.getBridge();
	if(Util.logLevel>4) Util.logDebug(ctx + " created new thread, using class loader: " + System.identityHashCode(ctx.getClassLoader().getParent()));
	SimpleJavaBridgeClassLoader loader = bridge.getClassLoader();
	loader.switcheThreadContextClassLoader();
	
	setIO(bridge, in, out);
	this.request = bridge.request;
	
	ctx.initialize();
	return true;
    }
   /**
     * Return the channel of the current runner.
     * @return The Channel
     */
    public AbstractChannel getChannel() {
	return channel;
    }
    public void run() {
	try {
	    if(init())
		request.handleRequests();
	} catch (IOException e) {
	    if(Util.logLevel>4) Util.printStackTrace(e);
        } catch (Exception e) {
    	    Util.printStackTrace(e);
        } finally {
	    if(ctx!=null) {
		ctx.destroy();
	    }
	    channel.shutdown();
	}
    }
}
