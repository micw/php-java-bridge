/*-*- mode: Java; tab-width:8 -*-*/

package php.java.bridge.http;

import php.java.bridge.ISession;
import php.java.bridge.JavaBridge;

/**
 * Interface that the ContextFactories must implement.
 * 
 * @author jostb
 *
 */
public interface IContextFactory {

  /**
   * Synchronize the current state with id.
   * <p>
   * When persistent connections are used, the bridge instances recycle their context factories (persistent
   * php clients store their context id, so that they don't have to aquire a new one).
   * However, a client of a php client may have passed a fresh context id. 
   * If this happened, the bridge calls this method, 
   * which may update the current context with the fresh values from id.</p>
   * <p>Typically the ContextFactory implements this method. It should find the ContextFactory for id,
   * check that the Factory is not in use (and throw a SecurityException, if isInitialized() returns true),
   * remove it by calling removeOrphaned() and 
   * call its recycle() method passing it the current ContextFactory. 
   * </p>
   * @param id The fresh id
   * @throws NullPointerException if the current id is not initialized
   * @throws SecurityException if the found ContextFactory is initialized.
   * @see #recycle(ContextFactory)
   * @see php.java.bridge.JavaBridge#recycle()
   */
  public void recycle(String id) throws SecurityException;
  
  /**
   * Typically the visitor implements this method, it should attach itself to the target by calling
   * <code>target.accept(this)</code>.
   * @see #recycle(String)
   * @see php.java.bridge.http.ContextFactory#accept(IContextFactoryVisitor)
   * @see php.java.bridge.http.IContextFactoryVisitor#visit(ContextFactory)
   * @see php.java.bridge.http.SimpleContextFactory
   * @param target The persistent ContextFactory.
   */
  public void recycle(ContextFactory target);

  /**
   * Removes the context factory from the classloader's list of context factories
   * and destroys its content.
   */
  public void destroy();

  /**
   * Removes the unused context factory from the classloader's list of context factories.
   */
  public void removeOrphaned();

  /**
   * Wait until this context is finished.
   * @throws InterruptedException
   * @see php.java.bridge.http.ContextRunner
   */
  public void waitFor() throws InterruptedException;

  /**
   * Return the serializable ID of the context factory
   * @return The ID
   */
  public String getId();

  /**
   * Return a JSR223 context
   * @return The context
   * @see php.java.servlet.ServletContextFactory#getContext()
   * @see php.java.bridge.http.Context
   */
  public Object getContext();

  /**
   * Set the Context into this factory.
   * Should be called by Context.addNew() only.
   * @param context
   * @see php.java.bridge.http.ContextFactory#addNew()
   */
  public void setContext(Object context);

  /**
   * Return the JavaBridge.
   * @return Returns the bridge.
   */
  public JavaBridge getBridge();
  
  /**
   * @param name The session name. If name is null, the name PHPSESSION will be used.
   * @param clientIsNew true if the client wants a new session
   * @param timeout timeout in seconds. If 0 the session does not expire.
   * @return The session
   * @see php.java.bridge.ISession
   */
   public ISession getSession(String name, boolean clientIsNew, int timeout);

}