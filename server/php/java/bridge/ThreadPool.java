/*-*- mode: Java; tab-width:8 -*-*/

package php.java.bridge;


/**
 * A thread pool, accepts runnables and runs them in a thread
 * environment.  Example:<br> <code> ThreadPool pool = new
 * ThreadPool("MyThreadPool", 20);<br> pool.start(new
 * YourRunnable());<br> </code>
 *
 */
public class ThreadPool extends BaseThreadPool {

    /** Every Delegate has its own locked ThreadGroup. */
    static final class Group extends ThreadGroup {
	boolean isLocked = false;
	void lock() { isLocked = true; }
	void unlock() { isLocked = false; }
	private void init() { setDaemon(true); }
	public Group(String name) { super(name); init(); }
	public Group(ThreadGroup group, String name) { super(group, name); init(); }
    }

    /** Application threads belong to this group */
    static final class AppGroup extends ThreadGroup {
	private void init() { setDaemon(true); }
	public AppGroup(String name) { super(name); init(); }
	public AppGroup(ThreadGroup group, String name) { super(group, name); init(); }
    }

    /**
     * A specialized delegate which can handle persistent connections
     * and interrupts application threads when end() or terminate()
     * is called.
     */
    final class Delegate extends BaseThreadPool.Delegate {
	protected ThreadGroup appGroup = null;
	/** 
	 * Create a new delegate. The thread runs until
	 * terminatePersistent() is called.
	 * @param name The name of the delegate. 
	 */
	protected Delegate(String name) { super(new Group(name), name); ((Group) getThreadGroup()).lock(); }
	/**
	 * Return the app group for this delegate. All user-created
	 * threads live in this group and receive an interrupt (which
	 * should terminate them), when the request is done.
	 * @return The application group
	 */
	public ThreadGroup getAppGroup() {
	    if(appGroup!=null) return appGroup;
	    Group group = (Group) getThreadGroup();
	    group.unlock(); appGroup = new AppGroup("JavaBridgeThreadPoolAppGroup"); group.lock();
	    return appGroup;
	}
	/**
	 * Make this thread a daemon thread. A daemon is not visible
	 * but still managed by the thread pool.
	 */
	public void setPersistent() {
	    if(!checkReserve() && !terminate) {
	        // pool is nearly full, store new long-running clients outside
	        terminate = true;
	        String name = getName();
	        setName(name+",isDaemon=true");
	        createThread(name);
	    }
	    end();
	}
	protected void createThread(String name) {
	    Group group = (Group) getThreadGroup();
	    group.unlock(); super.createThread(name); group.lock();
	}
	protected void terminate() {
	    if(Util.logLevel>4) Util.logDebug("term: " + this);
	    ThreadGroup group = appGroup;
	    if(group!=null) {  
	        try {
		    group.interrupt();
	        } catch (SecurityException e) {return;}
	        try {
		    group.destroy();
	        } catch (SecurityException e) {/*ignore*/
	        } catch (IllegalThreadStateException e1) { Util.printStackTrace(e1); 
	        } catch (Exception e2) { Util.printStackTrace(e2); 
	        } finally { appGroup = null; }
	    }
	}
	protected void end() {
	    if(Util.logLevel>4) Util.logDebug("end: " + this);
	    ThreadGroup group = appGroup;
	    if(group!=null)  
	        try {
		    group.interrupt();
	        } catch (SecurityException e) {/*ignore*/
	        } catch (Exception e2) { Util.printStackTrace(e2); 
	        } finally { appGroup = null; }
	}
	
    }
    protected BaseThreadPool.Delegate createDelegate(String name) {
	return new Delegate(name);
    }
    /**
     * Creates a new thread pool.
     * @param name - The name of the pool threads.
     * @param poolMaxSize - The max. number of threads, must be >= 1.
     */
    public ThreadPool(String name, int poolMaxSize) { super(name, poolMaxSize); }
    /**
     * Creates a new thread pool.
     * @param name  The name of the pool threads.
     * @param poolMaxSize The max. number of threads, must be >= 1.
     * @param loader The class loader, may be null.
     */
    public ThreadPool (String name, int poolMaxSize, ClassLoader loader) { super(name, poolMaxSize, loader); }
}
