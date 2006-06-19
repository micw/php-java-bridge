/*-*- mode: Java; tab-width:8 -*-*/

package php.java.bridge;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.UnsupportedEncodingException;
import java.util.AbstractMap;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.Set;
import java.util.TreeMap;

/**
 * This class is used to handle requests from the front-end.
 * @author jostb
 *
 */
public final class Request implements IDocHandler {

    private Parser parser;
    private JavaBridge bridge;
    protected static final class IntegerComparator implements Comparator {
      public int compare(Object arg0, Object arg1) {
	  int k0 = ((PhpArrayKey)arg0).key;
	  int k1 = ((PhpArrayKey)arg1).key;
	  if(k0 < k1) return -1; else if(k0 > k1) return 1;
	  return 0;
      }
    }
    protected static final IntegerComparator PHP_ARRAY_KEY_COMPARATOR = new IntegerComparator();
    protected static final class PhpArrayKey extends Number {
        private static final long serialVersionUID = 2816799824753952383L;
	protected int key;
        public PhpArrayKey(int key) {
            this.key = key;
        }
	public int intValue() {
	  return key;
	}
	public long longValue() {
	  return key;
	}
	public float floatValue() {
	  return key;
	}
	public double doubleValue() {
	  return key;
	}
	public String toString() {
	    return String.valueOf(key);
	}
    }
    protected static final class PhpArray extends AbstractMap { // for PHP's array()
	private static final long serialVersionUID = 3905804162838115892L;
	private TreeMap t = new TreeMap(PHP_ARRAY_KEY_COMPARATOR);
	private HashMap m = null;
	public Object put(Object key, Object value) {
	    if(m!=null) return m.put(key, value);
	    try {
	        return t.put((PhpArrayKey)key, value);
	    } catch (ClassCastException e) {
	        m = new HashMap(t);
	        t = null;
	        return m.put(key, value);
	    }
	}
	public Set entrySet() {
	    if(t!=null) return t.entrySet();
	    return m.entrySet();
	}

	public int arraySize() {
	    if(t!=null) {
		if(t.size()==0) return 0;
		return 1+((PhpArrayKey)t.lastKey()).intValue();
	    }
	    throw new IllegalArgumentException("The passed PHP \"array\" is not a sequence but a dictionary");
	}
    }
 
    // Only used when the async. protocol is enabled.
    protected static final class PhpNull {}
    protected static final PhpNull PHPNULL = new PhpNull();
    protected Object getGlobalRef(int i) {
	Object ref = bridge.globalRef.get(i);
	if(ref == PHPNULL) return null;
	return ref;
    }

    /**
     * A php string is a UTF-8 coded byte array.
     *
     */
   protected static abstract class PhpString {
        /**
         * Get the encoded string representation
         * @return The encoded string.
         */
        public abstract String getString();
        

        /**
         * Get the encoded byte representation
         * @return The encoded bytes.
         */
        public abstract byte[] getBytes();

        public String toString() {
            return getString();
        }
    }
   protected static final class SimplePhpString extends PhpString {
       String s; 
       JavaBridge bridge;
       
       SimplePhpString(JavaBridge bridge, String s) {
           this.bridge = bridge;
           this.s = s;
        }

       public String getString() {
        return s;
    }
    public byte[] getBytes() {
        return bridge.options.getBytes(s);
    }
    }
    static final class PhpParserString extends PhpString {
        ParserString st;
        private JavaBridge bridge;
        /**
         * @param st The ParserString
         */
        public PhpParserString(JavaBridge bridge, ParserString st) {
            this.bridge = bridge;
            getBytes(st);
        }
        private byte[] bytes;
        private void getBytes(ParserString st) {
             if(bytes==null) {
                bytes=new byte[st.length];
                System.arraycopy(st.string,st.off,bytes,0,bytes.length);
            }
        }
        public byte[] getBytes() {
            return bytes;
        }
        private String newString(byte[] b) {
            return bridge.getString(b, 0, b.length);
        }
        /**
         * Get the encoded string representation
         * @param res The response.
         * @return The encoded string.
         */
        public String getString() {
            return newString(getBytes());
        }
        /**
         * Use UTF-8 encoding, for debugging only
         */
        public String toString() {
            try {
                return new String(getBytes(), Util.UTF8);
            } catch (UnsupportedEncodingException e) {
                return new String(getBytes());               
            }
         }
    }
    protected static final class PhpNumber extends Number {

	/**
	 * 
	 */
	private static final long serialVersionUID = 3257566187666749240L;
	private long l;

	/**
	 * @param l
	 */
	public PhpNumber(long l) {
	    this.l = l;
	}

	/* (non-Javadoc)
	 * @see java.lang.Number#intValue()
	 */
	public int intValue() {
			
	    return (int)l;
	}

	/* (non-Javadoc)
	 * @see java.lang.Number#longValue()
	 */
	public long longValue() {
	    return l;
	}

	/* (non-Javadoc)
	 * @see java.lang.Number#floatValue()
	 */
	public float floatValue() {
	    return l;
	}

	/* (non-Javadoc)
	 * @see java.lang.Number#doubleValue()
	 */
	public double doubleValue() {
	    return l;
	}
		
	public String toString() {
	    return String.valueOf(l);
	}
    	
    };

    static final Object[] ZERO_ARGS = new Object[0];
    private abstract class Arg {
    	protected byte type;
    	protected Object callObject;
    	protected Throwable exception;
    	protected String method;
    	protected boolean predicate;
    	protected long id;
    	protected Object key;
    	protected byte composite;
    	 
    	public abstract void add(Object val);
    	public abstract Object[] getArgs();
    	public abstract void reset();
    }
    private final class SimpleArg extends Arg {
    	private LinkedList list;
   	public void add(Object val) {
   	    if(list==null) list=new LinkedList();
   	    list.add(val);
    	}
    	public Object[] getArgs() {
	    return (list==null) ? Request.ZERO_ARGS : list.toArray();
    	}
    	public void reset() {
	    list=null;
	    composite=0;
	    type=0;
	    callObject=null;
	    method=null;
	    id=0;
	    key=null;
     	}
    }
    private final class CompositeArg extends Arg {
    	private PhpArray ht = null; 
    	private int count = 0;
    	private Arg parent;
    	
    	public CompositeArg(Arg parent) {
    	    this.parent = parent;
    	}
    	
        public void add(Object val) {
		if(ht==null) ht=new PhpArray();
		if(key!=null) {
		    ht.put(key, val);
		}
		else {
		    ht.put(new PhpArrayKey(count++), val);
		}

        }
        protected Arg pop() {
            if(ht==null) ht=new PhpArray();
            parent.add(ht);
            return parent;
        }

        /* (non-Javadoc)
         * @see php.java.bridge.Request.Arg#getArgs()
         */
        public Object[] getArgs() {
            bridge.logError("Protocol error: getArgs");
            return ZERO_ARGS;
        }

        /* (non-Javadoc)
         * @see php.java.bridge.Request.Arg#reset()
         */
        public void reset() {
            bridge.logError("Protocol error: reset");
        }
    }
    private Arg arg;
    
    /**
     * The current response handle or null.
     * There is only one response handle for each request object.
     * <code>response.reset()</code> or <code>response.flush()</code> must be called at the end of each packet.
     */
    public Response response = null;

    /**
     * Creates an empty request object.
     * @param bridge The bridge instance.
     * @see Request#init(InputStream, OutputStream)
     */
    public Request(JavaBridge bridge) {
	this.bridge=bridge;
	this.parser=new Parser(bridge, this);
	this.arg=new SimpleArg();
    }
    static final byte[] ZERO={0};
    static final Object ZERO_OBJECT=new Object();
    
    /**
     * This method must be called with the current input and output streams. 
     * It reads the protocol header and initializes the request object.
     * 
     * @param in The input stream.
     * @param out The output stream.
     * @return true, if the protocol header was valid, false otherwise.
     * @throws IOException
     */
    public boolean init(InputStream in, OutputStream out) throws IOException {
    	switch(parser.initOptions(in)) {

    	case Parser.PING:
            bridge.logDebug("PING - PONG - Closing Request");
            out.write(ZERO, 0, 1);
            return false;
    	case Parser.IO_ERROR:
            bridge.logDebug("IO_ERROR - Closing Request");
            return false;
    	case Parser.EOF:
            bridge.logDebug("EOF - Closing Request");
            return false;
        }
    	return true;
    }
    
    private long getPhpLong(ParserString st[]) {
	    long val = st[0].getLongValue();
	    if(bridge.options.hexNumbers() && st[1].string[st[1].off]!='O')
		val *= -1;
	    return val;
    }
    private Object createExact(ParserString st[]) {
        if(!bridge.options.extJavaCompatibility()) {
	    int val = st[0].getIntValue();
	    if(bridge.options.hexNumbers() && st[1].string[st[1].off]!='O')
		val *= -1;
            return (new Integer(val));
	} else {
	    long val = st[0].getLongValue();
	    if(bridge.options.hexNumbers() && st[1].string[st[1].off]!='O')
		val *= -1;
            return (new Long(val));
	}
    }
    
    /* (non-Javadoc)
     * @see php.java.bridge.IDocHandler#begin(php.java.bridge.ParserTag[])
     */
    public void begin(ParserTag[] tag) {
	ParserString[] st=tag[2].strings;
	byte ch;
	switch (ch=tag[0].strings[0].string[0]) {
	case 'I': {
	    arg.type=ch;
	    int i= st[0].getIntValue();
	    arg.callObject=i==0?bridge:getGlobalRef(i);
	    arg.method=st[1].getCachedStringValue();
	    arg.predicate=st[2].string[st[2].off]=='P';
	    arg.id=st[3].getLongValue();
	    break;
	}
	case 'C': {
	    arg.type=ch;
	    arg.callObject=st[0].getCachedStringValue();
	    arg.predicate=st[1].string[st[1].off]=='C';
	    arg.id=st[2].getLongValue();
	    break;
	}
	case 'F': {
	    arg.type=ch;
	    arg.predicate = st[0].string[st[0].off]=='A';
	    break;
	}
	case 'R': {
	    arg.type=ch;
	    arg.id=st[0].getLongValue();
	    break;
	}
	
	case 'X': {
	    arg = new CompositeArg(arg);
	    arg.composite=st[0].string[st[0].off];
	    break;
	}
	case 'P': {
	    if(arg.composite=='H') {// hash
		if(st[0].string[st[0].off]=='S')
		    arg.key = st[1].getCachedStringValue();
		else {
		   arg.key = new PhpArrayKey(st[1].getIntValue());
		}
	    } else // array
		arg.key=null;
	    break;
	}

	case 'U': {
	    int i=st[0].getIntValue();
	    bridge.globalRef.remove(i);
	    break;
	}
	case 'S': {
	    if(arg.composite!='H') 
	        arg.add(new PhpParserString(bridge, st[0]));
	    else // hash has no type information
	        arg.add(st[0].getStringValue());
	    break;
	}
	case 'B': {
	    arg.add(new Boolean(st[0].string[st[0].off]=='T'));
	    break;
	}
	case 'L': {
	    if(arg.composite!='H')
	        arg.add(new PhpNumber(getPhpLong(st)));
	    else // hash has no type information
	        arg.add(createExact(st));
	    break;
	}
	case 'D': {
	    arg.add(new Double(st[0].getDoubleValue())); 
	    break;
	}
	case 'E': {
	    if(0==st[0].length)
		arg.callObject=new Exception(st[1].getStringValue());
	    else {
		int i=st[0].getIntValue();
		if(0==i) {
		    arg.callObject=new Exception(st[1].getStringValue());
		}
		else
		    arg.callObject=getGlobalRef(i);
	    }
	    break;
	}
	case 'O': {
	    if(0==st[0].length)
		arg.add(null);
	    else {
		int i=st[0].getIntValue();
		if(0==i)
		    arg.add(null);
		else
		    arg.add(getGlobalRef(i));
	    }
	    break;
	}
	}
    }
    
    /* (non-Javadoc)
     * @see php.java.bridge.IDocHandler#end(php.java.bridge.ParserString[])
     */
    public void end(ParserString[] string) {
    	switch(string[0].string[0]) {
    	case 'X': {
	    arg = ((CompositeArg)arg).pop();
    	}
    	}
    }
    private static final String SUB_FAILED = "Invocation of sub request failed. PHP method invocation is not available in your environment (due to security restrictions). Set the system property php.java.bridge.promiscuous=true";
    private void setIllegalStateException(String s) {
        IllegalStateException ex = new IllegalStateException(s);
        response.setResultException(bridge.lastException = ex, s);
    }
    private int handleRequest() throws IOException {
	int retval;
	if(Parser.OK==(retval=parser.parse(bridge.in))) {
	    response.setResultID(arg.id);
	    switch(arg.type){
	    case 'I':
		if(arg.predicate)
		    bridge.GetSetProp(arg.callObject, arg.method, arg.getArgs(), response);
		else
		    bridge.Invoke(arg.callObject, arg.method, arg.getArgs(), response);
		response.flush();
		break;
	    case 'C':
		if(arg.predicate)
		    bridge.CreateObject((String)arg.callObject, false, arg.getArgs(), response);
		else
		    bridge.CreateObject((String)arg.callObject, true, arg.getArgs(), response);
		response.flush();
		break;
	   case 'F': 
	         if(arg.predicate) { // keep alive
	           bridge.recycle();
	           try {
	     	       ((ThreadPool.Delegate)Thread.currentThread()).setPersistent();
	           } catch (ClassCastException ex) {/*no thread pool*/}
	           response.setFinish(true);
	         } else { // terminate or terminate keep alive
	           response.setFinish(false);
	         }
	         response.flush();
	         break;
	   case 'R':
	       setIllegalStateException(SUB_FAILED);
	       response.flush();
	       break;
	    }
	    arg.reset();
	}
	return retval;
    }
    
    /**
     * Start handling requests until EOF. Creates a response object and handles all packets.
     * @throws IOException
     */
    public void handleRequests() throws IOException {
    	if(response==null) response=new Response(bridge);
	while(Parser.OK==handleRequest())
	    ;
    }
    
    /**
     * Handle protocol sub-requests, see <code>R</code> and <code>A</code> in the protocol spec.
     * @return An array of one object. The object is the result of the Apply call.
     * @throws IOException
     * @throws Throwable thrown by the PHP code.
     */
    protected Object[] handleSubRequests() throws IOException, Throwable {
    	response.flush();
    	Arg current = arg;
    	arg = new SimpleArg();
	while(Parser.OK==parser.parse(bridge.in)){
	    response.setResultID(arg.id); 
	    switch(arg.type){
	    case 'I':
		if(arg.predicate)
		    bridge.GetSetProp(arg.callObject, arg.method, arg.getArgs(), response);
		else
		    bridge.Invoke(arg.callObject, arg.method, arg.getArgs(), response);
		response.flush();   
		break;
	    case 'C':
		if(arg.predicate)
		    bridge.CreateObject((String)arg.callObject, false, arg.getArgs(), response);
		else
		    bridge.CreateObject((String)arg.callObject, true, arg.getArgs(), response);
		response.flush();   
		break;
	    case 'R':
	    	Arg ret = arg;
	    	arg = current;
	    	response.reset();
	    	
	    	if(ret.callObject!=null) 
	    	    throw (Throwable)ret.callObject;
	    	return ret.getArgs();
	    }
	    arg.reset();
	}
	arg = current;
	throw new IllegalStateException(SUB_FAILED);
    }

    /**
     * Reset the internal state so that a new input and output stream
     * can be used for the next packed. Note that request options
     * (from init()) remain valid.
     * @see #init(InputStream, OutputStream)
     */
    public void reset() {
	parser.reset();
    }
    
    /** re-initialize for new requests */
    public void recycle() {
        reset();
        arg.reset();
        response.recycle();
    }

    /**
     * Create a parser string, according to options
     * @return The parser string
     */
    public ParserString createParserString() {
        if(bridge.options.hexNumbers())
            return new ParserString(bridge);
        else
            return new ClassicParserString(bridge);
    }
}
