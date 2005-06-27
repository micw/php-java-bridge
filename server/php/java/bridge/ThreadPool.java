/*-*- mode: Java; tab-width:8 -*-*/

package php.java.bridge;

import java.util.LinkedList;

public class ThreadPool {
    private String name;
    private int threads = 0, idles = 0, poolMaxSize;
    private LinkedList runnables = new LinkedList();

    /*
     * Threads continues to pull runnables and run them in the thread
     * environment.
     */
    private class Delegate extends Thread {
	public Delegate(String n) { super(n); threads++; }
	public void run() {
	    try {
		while(true) getNextRunnable().run();
	    } catch (InterruptedException t) { threads--; }
	}
    }

    /*
     * Helper: Pull a runnable off the list of runnables. If there's
     * no work, sleep the thread until we receive a notify.
     */
    private synchronized Runnable getNextRunnable() throws InterruptedException {
	if(runnables.isEmpty()) {
	    idles++; wait(); idles--;
	}
	return (Runnable)runnables.removeFirst();
    }

    /*
     * Push a runnable to the list of runnables. The notify will fail
     * if all threads are busy. Since the pool contains at least one
     * thread, it will pull the runnable off the list when it becomes
     * available.
     */
    public synchronized void start(Runnable r) {
	runnables.add(r);
	if(idles==0 && threads < poolMaxSize) {
	    Delegate d = new Delegate(name);
            ClassLoader c = DynamicJavaBridgeClassLoader.newInstance();
            if (c!=null) {
	      d.setContextClassLoader(c);
            }
	    d.start();
	}
	else
	    notify();
    }

    public ThreadPool (String name, int poolMaxSize) {
	this.name = name;
    	this.poolMaxSize = poolMaxSize;
    }
}
