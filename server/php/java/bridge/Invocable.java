/*-*- mode: Java; tab-width:8 -*-*/

package php.java.bridge;

/**
 * @author jostb
 *
 */
public interface Invocable {
	
    /**
     * Call the continuation with the continuation <code>kont</code> as its argument.
     * @param kont - The continuation.
     * @return True on success, false otherwise.
     * @throws Exception
     */
    public boolean call(PhpProcedureProxy kont) throws Exception;
}
