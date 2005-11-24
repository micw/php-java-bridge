/*-*- mode: Java; tab-width:8 -*-*/

package php.java.bridge;

/**
 * Classes which implement this interface are able to call php code.
 * Invocable PHP scripts must end with the line:<br>
 * <code>
 * java_context()-&gt;call(java_closure());
 * </code>
 * <br>
 * @see php.java.bridge.PhpProcedureProxy#getProxy(Class[])
 * @see php.java.bridge.PhpProcedure#invoke(Object, String, Object[])
 * @see php.java.bridge.PhpProcedure#invoke(Object, Method, Object[])
 * @author jostb
 *
 */
public interface Invocable {
	
    /**
     * Call the java continuation with the current continuation <code>kont</code> as its argument.
     * @param kont The continuation.
     * @return True on success, false otherwise.
     * @throws Exception
     */
    public boolean call(PhpProcedureProxy kont) throws Exception;
}
