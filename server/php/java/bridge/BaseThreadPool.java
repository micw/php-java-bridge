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
public class BaseThreadPool {
    private ClassLoader loader = null;
    private String name;
    private int threads = 0, idles = 0, poolMaxSize, poolReserve;
    private LinkedList runnables = new LinkedList();

    /**
     * Threads continue to pull runnables and run them in the thread
     * environment.
     */
    protected class Delegate extends Thread {
	protected boolean terminate = false;

	public Delegate(String name) { super(name); }
	public Delegate(ThreadGroup group, String name) { super(group, name); }
	protected void terminate() {}
	protected void end() {}
	protected void createThread(String name) { startNewThread(name); }
	
	public void run() {
	    try {
		while(!terminate) { getNextRunnable().run(); end(); }
	    } catch (Throwable t) { Util.printStackTrace(t); createThread(getName()); 
	    } finally { terminate(); }
	}
    }
    protected Delegate createDelegate(String name) {
        return new Delegate(name);
    }
    protected void startNewThread(String name) {
        Delegate d = createDelegate(name);
	ClassLoader loader = null;
	if(this.loader!=null) loader=DynamicJavaBridgeClassLoader.newInstance(this.loader);
	if(loader!=null) d.setContextClassLoader(loader);
	d.start();
    }
    protected synchronized boolean checkReserve() {
      return idles+(poolMaxSize-threads) > poolReserve;
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
	    threads++;
	    startNewThread(name+"#"+String.valueOf(threads));
	}
	else
	    notify();
    }
    
    protected void init(String name, int poolMaxSize, ClassLoader loader) {
	this.name = name;
    	this.poolMaxSize = poolMaxSize;
    	this.poolReserve = (poolMaxSize>>>2)*3;
	this.loader = loader;        
    }
    /**
     * Creates a new thread pool.
     * @param name - The name of the pool threads.
     * @param poolMaxSize - The max. number of threads, must be >= 1.
     */
    public BaseThreadPool (String name, int poolMaxSize) {
        ClassLoader loader = null;
        try {loader = Util.getContextClassLoader();} catch (SecurityException e) {/*ignore*/}
        init(name, poolMaxSize, loader);
    }

    /**
     * Creates a new thread pool.
     * @param name  The name of the pool threads.
     * @param poolMaxSize The max. number of threads, must be >= 1.
     * @param loader The class loader, may be null.
     */
    public BaseThreadPool (String name, int poolMaxSize, ClassLoader loader) {
        init(name, poolMaxSize, loader);
    }
}
