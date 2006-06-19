/*-*- mode: Java; tab-width:8 -*-*/

package php.java.bridge.http;

/**
 * Interface that ContextFactory visitors must implement.
 * 
 * @author jostb
 *
 */
public interface IContextFactoryVisitor extends IContextFactory {
 /**
   * Called when a visitor has been attached.
   * @param visited The context factory
   */
  public void visit(ContextFactory visited);
}