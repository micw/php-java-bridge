/*-*- mode: Java; tab-width:8 -*-*/

package php.java.bridge;

import java.util.LinkedList;
/**
 * A standard thread pool, accepts runnables and runs them in a thread environment.
 * Example:<br>
 * <code>
 * ThreadPool pool = new ThreadPool("MyThreadPool", 20);<br>
 * pool.start(new YourRunnable());<br>
 * </code>
 *
 */
public class ThreadPool {
    private ClassLoader loader = null;
    private String name;
    private int threads = 0, idles = 0, poolMaxSize;
    private LinkedList runnables = new LinkedList();

    /**
     * Threads continue to pull runnables and run them in the thread
     * environment.
     */
    public final class Delegate extends Thread {
	private boolean isDaemon = false, terminate = false;
	/** 
	 * Create a new delegate. The thread runs until terminateDaemon() is called. 
	 * @param name The name of the delegate. 
	 */
	protected Delegate(String name) { super(name); threads++; }
	/**
	 * Make this thread a daemon thread. A daemon is not visible but still managed by the thread pool.
	 * @param val true or false
	 */
	public void setIsDaemon(boolean val) {
	    if(isDaemon != val) threads -= (isDaemon = val) ? 1 : -1;
	}
	/**
	 * Check if this thread is a daemon thread.
	 * @return true, if the thread is a daemon, false otherwise
	 */
	public boolean getIsDaemon() { return isDaemon; }
	/**
	 * Terminate a daemon thread.
	 * @throws IllegalStateException, if the thread is not a daemon
	 */
	public void terminateDaemon() { 
	  if(!isDaemon) throw new IllegalStateException("not a daemon");
	  terminate = true; 
	}
	public void run() {
	    try {
		while(!terminate) getNextRunnable().run();
	    } catch (Throwable t) { Util.printStackTrace(t); threads--; }
	}
    }

    /*
     * Helper: Pull a runnable off the list of runnables. If there's
     * no work, sleep the thread until we receive a notify.
     */
    private synchronized Runnable getNextRunnable() throws InterruptedException {
	while(runnables.isEmpty()) {
	    idles++; wait(); idles--;
	}
	return (Runnable)runnables.removeFirst();
    }

    /**
     * Push a runnable to the list of runnables. The notify will fail
     * if all threads are busy. Since the pool contains at least one
     * thread, it will pull the runnable off the list when it becomes
     * available.
     * @param r - The runnable
     */
    public synchronized void start(Runnable r) {
	runnables.add(r);
	if(idles==0 && threads < poolMaxSize) {
	    Delegate d = new Delegate(name);
	    ClassLoader loader = null;
	    if(this.loader!=null) loader=DynamicJavaBridgeClassLoader.newInstance(this.loader);
	    if(loader!=null) d.setContextClassLoader(loader);
	    d.start();
	}
	else
	    notify();
    }

    /**
     * Creates a new thread pool.
     * @param name - The name of the pool threads.
     * @param poolMaxSize - The max. number of threads, must be >= 1.
     */
    public ThreadPool (String name, int poolMaxSize) {
	this.name = name;
    	this.poolMaxSize = poolMaxSize;
	try {this.loader = Util.getContextClassLoader();} catch (SecurityException e) {/*ignore*/}

    }

    /**
     * Creates a new thread pool.
     * @param name  The name of the pool threads.
     * @param poolMaxSize The max. number of threads, must be >= 1.
     * @param loader The class loader, may be null.
     */
    public ThreadPool (String name, int poolMaxSize, ClassLoader loader) {
	this.name = name;
    	this.poolMaxSize = poolMaxSize;
	this.loader = loader;
    }
}
