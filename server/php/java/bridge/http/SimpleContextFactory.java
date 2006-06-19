/*-*- mode: Java; tab-width:8 -*-*/

package php.java.bridge.http;

import php.java.bridge.ISession;
import php.java.bridge.JavaBridge;


/**
 * Base of a set of visitors which can extend the standard ContextFactory.
 *  
 * @see php.java.servlet.ServletContextFactory
 * @see php.java.script.PhpScriptContextFactory
 * @see php.java.faces.PhpFacesScriptContextFactory
 */
public class SimpleContextFactory implements IContextFactoryVisitor {
    
    /**
     * The visited ContextFactory
     */
    private ContextFactory visited;

    /**
     * The data attached to the visited ContextFatory
     */
    protected Object context;
    
    protected SimpleContextFactory() {
  	visited = new ContextFactory();
  	visited.accept(this);
    }
    
    public void recycle(String id) throws SecurityException {
        visited.recycle(id);
    }

    public void destroy() {
        visited.destroy();
    }
    
    public void waitFor() throws InterruptedException {
        visited.waitFor();
    }
    
    public String getId() { 
        return visited.getId();
    }
    public String toString() {
	return "Visited: " + visited + ", Current: ";
    }
    protected Object createContext() {
      return new Context();
  }
    public Object getContext() {
	if(context==null) setContext(createContext());
        return context;
    }
	
    public void setBridge(JavaBridge bridge) {
        visited.setBridge(bridge);
    }
    public JavaBridge getBridge() {
        return visited.getBridge();
    }
    public void visit(ContextFactory visited) {
        this.visited=visited;
    }
    public ISession getSession(String name, boolean clientIsNew, int timeout) {
        return visited.getSimpleSession(name, clientIsNew, timeout);
    }
    public void setContext(Object context) {
        this.context = context;
    }
    public void recycle(ContextFactory visited) {
        visited.accept(this);
    }

    public void remove() {
        visited.remove();
    }
}
