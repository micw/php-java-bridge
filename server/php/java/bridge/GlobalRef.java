/*-*- mode: Java; tab-width:8 -*-*/

package php.java.bridge;
import java.util.Arrays;

public class GlobalRef {
    static final int DEFAULT_SIZE=1024;

    Object[] globalRef;
    int id;

    private JavaBridge bridge;
    public GlobalRef(JavaBridge bridge) {
	this.bridge=bridge;
	globalRef=new Object[DEFAULT_SIZE];
    }

    public Object get(int id) {
	Object o = globalRef[--id];
	if(o==null) throw new NullPointerException();
	return o;
    }

    public void remove(int id) {
	globalRef[--id]=null;
    }

    /* FIXME: What does this do?
     * We cannot destroy the entries until the client disconnects
     * because it keeps referencing them. A client may destroy
     * (protocol "U") individual entries, though.
     * After request termination the global ref is destroyed
     * anyway, so this method seems obsolete.
     *
     * ANSWER:
     * This clears the GlobalRef Object for recycling.
     * It's faster and more efficient (in terms of Garbage Collector activity and memory management overhead)
     * to clear and re-use this obect.
     *
     * FIX: I haven't seen the "globalRef = null;" statement in the execute method of the JavaBridge which resulted in a NullPointer Exception.
     * This is fixed.
     */
    public void clear() {
      Arrays.fill(globalRef, null);
      id = 0;
    }

    public String dump() {
      StringBuffer result = new StringBuffer();
      for (int i=0;i<id;i++) {
        if (globalRef[i]!=null) {
          result.append("globalRef["+i+"]="+JavaBridge.objectDebugDescription(globalRef[i])+"\n");
        }
      }
      return result.toString();
    }

    public int append(Object object) {
	try {
	    globalRef[id]=object;
	} catch (ArrayIndexOutOfBoundsException e) {
	    Object o[] = new Object[globalRef.length<<1];
	    System.arraycopy(globalRef, 0, o, 0, globalRef.length);
	    globalRef=o;
	    globalRef[id]=object;
	}
	return ++id; // 0 is interpreted as null
    }
}
