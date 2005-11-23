/*-*- mode: Java; tab-width:8 -*-*/

package php.java.bridge;

/**
 * A global array of object references that the client keeps during the connection.
 * (int -> Object mappings).
 * After connection shutdown the request-handling bridge instance and its global ref array 
 * are destroyed.
 * 
 * NOTE: We  guarantee that for each entry < 1024: entry2 = entry1+1 so that simple clients
 * can "guess" the next handle value.
 * 
 * The current implementation uses an array and new entries are always appended at the end 
 * (until the request terminates or until an OOM error occurs).  A future implementation
 * may use an int hash table instead (but see note above).
 */
class GlobalRef {
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
