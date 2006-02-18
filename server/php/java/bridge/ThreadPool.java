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

    /*
     * Threads continue to pull runnables and run them in the thread
     * environment.
     */
    private class Delegate extends Thread {
	public Delegate(String n) { super(n); threads++; }
	public void run() {
	    try {
		while(true) getNextRunnable().run();
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
	    ClassLoader loader =
		DynamicJavaBridgeClassLoader.newInstance(this.loader);
	    d.setContextClassLoader(loader);
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
	this.loader = Util.getContextClassLoader();

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
