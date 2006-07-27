/*-*- mode: Java; tab-width:8 -*-*/

package php.java.bridge.http;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

import php.java.bridge.ThreadPool;
import php.java.bridge.Util;

/**
 * This class represents the physical connection on Unix or Linux machines.
 * PHP clients create a pair of named pipes and pass their location via the X_JAVABRIDGE_REDIRECT
 * header (see PhpJavaServlet}. When isAvailable() returns true, all further communication goes
 * through the pair of pipes, see response header X_JAVABRIDGE_REDIRECT.
 * <p>
 * It is possible to switch off this server by setting the VM property php.java.bridge.no_pipe_server to true,
 * e.g.: -Dphp.java.bridge.no_pipe_server=true.
 * </p>
 * @see php.java.bridge.http.SocketContextServer
 * @see php.java.bridge.http.ContextServer
 */
public class PipeContextServer implements IContextServer {
    protected ThreadPool threadPool;
    protected ContextServer contextServer;
    private boolean isAvailable = true;
    
    protected static class Channel extends IContextServer.Channel {
        protected InputStream in = null;
        protected OutputStream out = null;
        private String channelName;
        protected ContextRunner runner;
        
        protected Channel(String channelName, InputStream in, OutputStream out) {
            this(channelName);
            this.in = in;
            this.out = out;
        }
        protected Channel(String channelName) {
            this.channelName = channelName;
         }
                
        public InputStream getInputStream() throws FileNotFoundException {
	    if(in!=null) return in;
            return in = new FileInputStream(new File(channelName+".o"));
        }
        
        public OutputStream getOuptutStream() throws FileNotFoundException {
	    if(out!=null) return out;
            return out = new FileOutputStream(new File(channelName+".i"));
        }
        
         protected static void shutdown(InputStream in, OutputStream out) {
	       	if(in!=null) try {in.close();}catch (IOException e){}
	    	if(out!=null) try {out.close();}catch (IOException e){}           
        }
        public void shutdown() {
            shutdown(in, out);
         }
	public String getName() {
	    return channelName;
	}    
     }
    /**
     * Create a new ContextServer using the ThreadPool. 
     * @param threadPool Obtain runnables from this pool. If null, new threads will be created.
     */
    public PipeContextServer (ContextServer contextServer, ThreadPool threadPool) {
        this.contextServer = contextServer;
    	this.threadPool = threadPool;
    }
    public boolean start(IContextServer.ChannelName channelName) {
        if(!isAvailable()) return false;
        try {
            ContextRunner runner = new ContextRunner(contextServer, new Channel(channelName.getName()));
            if(threadPool!=null) {
	        threadPool.start(runner);
	    } else {
	    	Thread t = new Thread(runner, "JavaBridgeContextRunner");
	    	t.start();
	    }
	} catch (SecurityException t) {
	    ContextFactory.destroyAll();
	    Util.printStackTrace(t);
	    return isAvailable=false;
	} catch (Throwable t) {
	    Util.printStackTrace(t);
	    return isAvailable=false;
	}
	return true;
    }
    /**
     * Destroy the server
     *
     */
    public void destroy() {
    }
    protected static boolean checkTestTunnel(String property) {
        try {
          return !"true".equals(System.getProperty(property));
        } catch (Throwable t) {return false;}
    }
    /** Don't use named pipes if 
     * -Dphp.java.bridge.promiscuous=true or 
     * -Dphp.java.bridge.no_pipe_server=true
     */
    private static final boolean pipeServer = !Util.JAVABRIDGE_PROMISCUOUS && checkTestTunnel("php.java.bridge.no_pipe_server");
    /**
     * Check if the ContextServer is ready
     * @return true, if the server is available
     */
    public boolean isAvailable() {
    	return pipeServer && isAvailable;
    }
}
