/*-*- mode: Java; tab-width:8 -*-*/

package php.java.bridge;

import java.security.Permission;

/**
 * A custom security manager for the PHP/Java Bridge.
 * 
 * Example:<br>
 * <code> PHP_HOME=/usr/lib/php/modules java -Dphp.java.bridge.base=${PHP_HOME} -Djava.security.policy=${PHP_HOME}/javabridge.policy -jar ${PHP_HOME}/JavaBridge.jar </code>
 * <br>
 * Example options for eclipse:<br>
 * <code>
 * -Dphp.java.bridge.base=${workspace_loc}${project_path}/<br>
 * -Djava.security.policy=${workspace_loc}${project_path}/javabridge.policy
 * </code><br>
 * @author jostb
 *
 */
public class JavaBridgeSecurityManager extends SecurityManager {
    protected static final Permission MODIFY_THREADGROUP_PERMISSION = new RuntimePermission("modifyThreadGroup");
    protected static final Permission MODIFY_THREAD_PERMISSION = new RuntimePermission("modifyThread");

    /**
     * @inheritDoc 
     * Internal groups may pass, user groups are checked against the <code>javabridge.policy</code> file.
     */
    public void checkAccess(ThreadGroup g) {
	if (g == null) {
	    throw new NullPointerException("thread group can't be null");
	}
	// one of our request-handling thread groups, check only if called from an application thread
	if((g instanceof ThreadPool.Group) && ((ThreadPool.Group)g).isLocked) 
	    checkPermission(MODIFY_THREADGROUP_PERMISSION);
	// an application thread group, check this one
	else if(g instanceof ThreadPool.AppGroup) 
	    checkPermission(MODIFY_THREADGROUP_PERMISSION);
	// a system thread group
	// disabled: Sun jdk1.5 calls checkAccess from a system thread
	// running with our(!) privileges:
        // at java.lang.ThreadGroup.checkAccess(ThreadGroup.java:288)
        // at java.lang.Thread.init(Thread.java:310)
        // at java.lang.Thread.<init>(Thread.java:429)
        // at sun.misc.Signal.dispatch(Signal.java:199)
	// This is probably a bug. 
	// However, this means that we must not check system thread groups. 
	// If we do, dispatch will fail and the VM cannot
	// react to signals like SIGTERM or Control-C. 
	//else super.checkAccess(g);
    }
    /**
     * All user threads belong to the "JavaBridgeThreadPoolAppGroup" and all internal threads
     * to "JavaBridgeThreadPoolGroup".
     */
    public ThreadGroup getThreadGroup() {
        try {
            ThreadPool.Delegate delegate = (ThreadPool.Delegate)Thread.currentThread();
            return delegate.getAppGroup();
        } catch (ClassCastException e) {
            // must be a system thread
            return super.getThreadGroup();
        }
    }
    /**
     * @inheritDoc
     * yCode>System.exit(...)</code> can by switched off by removing 
     * <code>permission java.lang.RuntimePermission "exitVM";</code> 
     * from the policy file.
     */
    public void checkExit(int status) {
	super.checkExit(status);
    }
}
