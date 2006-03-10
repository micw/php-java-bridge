/*-*- mode: Java; tab-width:8 -*-*/

package php.java.bridge;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.UnsupportedEncodingException;
import java.util.ArrayList;
import java.util.HashMap;

/**
 * This class is used to handle requests from the frontent.
 * @author jostb
 *
 */
public final class Request implements IDocHandler {

    private Parser parser;
    private JavaBridge bridge;
    protected static final class PhpArray extends HashMap { // for PHP's array()
	private static final long serialVersionUID = 3905804162838115892L;
    };
 
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
        /**
         * Get the encoded string representation
         * @param res The response.
         * @return The encoded string.
         */
        public String getString() {
            return bridge.options.newString(getBytes());
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
    	private ArrayList array;
   	public void add(Object val) {
   	    if(array==null) array=new ArrayList();
   	    array.add(val);
    	}
    	public Object[] getArgs() {
	    return (array==null) ? Request.ZERO_ARGS : array.toArray();
    	}
    	public void reset() {
	    array=null;
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
		    ht.put(new Long(count++), val);
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
     * @return true if the protocol header was valid, false otherwise.
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
	    arg.method=st[1].getStringValue(bridge.options);
	    arg.predicate=st[2].string[st[2].off]=='P';
	    arg.id=st[3].getLongValue();
	    break;
	}
	case 'C': {
	    arg.type=ch;
	    arg.callObject=st[0].getStringValue(bridge.options);
	    arg.predicate=st[1].string[st[1].off]=='C';
	    arg.id=st[2].getLongValue();
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
		    arg.key = st[1].getStringValue(bridge.options);
		else {
		   arg.key = bridge.options.createExact(st[1]);
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
	        arg.add(st[0].getStringValue(bridge.options));
	    break;
	}
	case 'B': {
	    arg.add(new Boolean(st[0].string[st[0].off]=='T'));
	    break;
	}
	case 'L': {
	    if(arg.composite!='H')
	        arg.add(new PhpNumber(st[0].getLongValue()));
	    else // hash has no type information
	        arg.add(bridge.options.createExact(st[0]));
	    break;
	}
	case 'D': {
	    arg.add(new Double(st[0].getDoubleValue())); 
	    break;
	}
	case 'E': {
	    if(0==st[0].length)
		arg.callObject=new Exception(st[1].getStringValue(bridge.options));
	    else {
		int i=st[0].getIntValue();
		if(0==i) {
		    arg.callObject=new Exception(st[1].getStringValue(bridge.options));
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
    private static final String SUB_FAILED = "Invocation of sub request failed. Probably php method invocation is not available in your environment (due to security restrictions).";
    private void setIllegalStateException(String s) {
        IllegalStateException ex = new IllegalStateException(s);
        response.writeException(ex, s);
	bridge.lastException = ex;
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
}
