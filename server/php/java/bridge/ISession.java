/*-*- mode: Java; tab-width:8 -*-*/

package php.java.bridge;
import java.util.Map;

public interface ISession {
    public Object get(Object ob);
		
    public void put(Object ob1, Object ob2);
		
    public Object remove(Object ob);
		
    public void setTimeout(int timeout);
		
    public int getTimeout();
		
    public int getSessionCount();
		
    public boolean isNew();
		
    public void destroy();
		
    public void putAll(Map vars);

    public Map getAll();
}
