/*-*- mode: Java; tab-width:8 -*-*/

package php.java.bridge.http;

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
 * The ContextRunner manages the "high speed" communication link.  It
 * pulls a context and executes it.  After execution the context is destroyed.
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
    
    private IContextFactory ctx;
    private String key;
    private Request request;
    private InputStream in;
    private OutputStream out;
    private IContextServer.Channel channel;
    private ContextServer contextServer;
    
    protected ContextRunner(ContextServer contextServer, IContextServer.Channel channel) {
	this.contextServer = contextServer;
	this.channel = channel;
    }
    private int readLength() throws IOException{
	byte buf[] = new byte[1];
	in.read(buf);

	return (0xFF&buf[0]);
    }
    private String readString(int length) throws IOException {
	byte buf[] = new byte[length];
	in.read(buf);
	return new String(buf, Util.ASCII);
    }

    private String readName() throws IOException {
	return readString(readLength());
    }

    private void init() throws IOException {
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
    	put(key=name, this);
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
	
	bridge.setIO(in, out);
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
     * @param contextServer the current contextServer or null
     * @return the ContextRunner, if found, otherwise null.
     */
    public static synchronized String checkRunner(IContextServer.ChannelName channelName, ContextServer contextServer) {
	String id = channelName.getKontext();
	IContextFactory ctx = channelName.getCtx();
	ContextRunner runner = null;
	if(ctx!=null && id!=null) runner = (ContextRunner)runners.get(id);
	if(runner!=null) {
	    channelName.setRunner(runner);
	    return runner.channel.getName();
	}	
	return null;
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
     * @param contextServer the new ContextServer
     */
    public void recycle(IContextFactory ctx, ContextServer contextServer) {
	if(this.ctx == ctx) return; 
	
	request.setBridge(ctx.getBridge());
	this.contextServer = contextServer;
	this.ctx = ctx;
    }
    public void run() {
	try {
	    init();
	    request.handleRequests();
	} catch (IOException e) {
	    Util.printStackTrace(e);
	} finally {
	    if(ctx!=null) {
		remove(key);
		ctx.destroy();
	    }
	    channel.shutdown();
	}
    }
}
