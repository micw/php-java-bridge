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
public class Request implements IDocHandler {

    private Parser parser;
    private JavaBridge bridge;
    protected static class PhpArray extends HashMap { // for PHP's array()
	private static final long serialVersionUID = 3905804162838115892L;
    };
 
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
   protected static class SimplePhpString extends PhpString {
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
    static protected class PhpParserString extends PhpString {
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
    protected static class PhpNumber extends Number {

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
    private static class Args {
    	PhpArray ht; 
    	ArrayList array;
    	int count;

    	byte composite;
    	byte type;
    	Object callObject;
    	Throwable exception;
    	String method;
    	boolean predicate;
        long id;
        Object key;

   	void add(Object val) {
	    if(composite!=0) {
		if(ht==null) ht=new PhpArray();
		if(key!=null) {
		    ht.put(key, val);
		}
		else {
		    ht.put(new Long(count++), val);
		}
	    } else {
		if(array==null) array=new ArrayList();
		array.add(val);
	    }
    	}
    	void reset() {
	    ht=null;
	    array=null;
	    count=0;
	    composite=0;
	    type=0;
	    callObject=null;
	    method=null;
	    id=0;
	    key=null;
     	}
    	private static final PhpArray empty0=new PhpArray();
    	void push() {
	    if(composite!=0) {
		if(array==null) array=new ArrayList();
		array.add(ht==null?empty0:ht);
		ht=null;
	    }
	    composite=0;
    	}
        private static final Object[] empty = new Object[0];
    	Object[] getArgs() {
	    return (array==null) ? empty : array.toArray();
    	}
    }
    private Args args;
    
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
	this.args=new Args();
    }
    static final byte[] ZERO={0};
    
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
	    args.type=ch;
	    int i=Integer.parseInt(st[0].getStringValue(), 10);
	    args.callObject=i==0?bridge:bridge.globalRef.get(i);
	    args.method=st[1].getStringValue();
	    args.predicate=st[2].string[st[2].off]=='P';
	    args.id=Long.parseLong(st[3].getStringValue(), 10);
	    break;
	}
	case 'C': {
	    args.type=ch;
	    args.callObject=st[0].getStringValue();
	    args.predicate=st[1].string[st[1].off]=='C';
	    args.id=Long.parseLong(st[2].getStringValue(), 10);
	    break;
	}
	case 'R': {
	    args.type=ch;
	    args.id=Long.parseLong(st[0].getStringValue(), 10);
	    break;
	}
	
	case 'X': {
	    args.composite=st[0].string[st[0].off];
	    break;
	}
	case 'P': {
	    if(args.composite=='H') {// hash
		if(st[0].string[st[0].off]=='S')
		    args.key = st[1].getStringValue();
		else {
		   args.key = bridge.options.createExact(st[1]);
		}
	    } else // array
		args.key=null;
	    break;
	}

	case 'U': {
	    int i=Integer.parseInt(st[0].getStringValue(), 10);
	    bridge.globalRef.remove(i);
	    break;
	}
	case 'S': {
	    if(args.composite!='H') 
	        args.add(new PhpParserString(bridge, st[0]));
	    else // hash has no type information
	        args.add(st[0].getStringValue());
	    break;
	}
	case 'B': {
	    args.add(new Boolean(st[0].string[st[0].off]=='T'));
	    break;
	}
	case 'L': {
	    if(args.composite!='H')
	        args.add(new PhpNumber(Long.parseLong(st[0].getStringValue(), 10)));
	    else // hash has no type information
	        args.add(bridge.options.createExact(st[0]));
	    break;
	}
	case 'D': {
	    args.add(new Double(Double.parseDouble(st[0].getStringValue())));
	    break;
	}
	case 'E': {
	    if(0==st[0].length)
		args.callObject=new Exception(st[1].getStringValue());
	    else {
		int i=Integer.parseInt(st[0].getStringValue(), 10);
		if(0==i) {
		    args.callObject=new Exception(st[1].getStringValue());
		}
		else
		    args.callObject=bridge.globalRef.get(i);
	    }
	    break;
	}
	case 'O': {
	    if(0==st[0].length)
		args.add(null);
	    else {
		int i=Integer.parseInt(st[0].getStringValue(), 10);
		if(0==i)
		    args.add(null);
		else
		    args.add(bridge.globalRef.get(i));
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
	    args.push();
    	}
    	}
    }
    private int handleRequest() throws IOException {
	int retval;
	if(Parser.OK==(retval=parser.parse(bridge.in))) {
	    response.setResultID(args.id);
	    switch(args.type){
	    case 'I':
		if(args.predicate)
		    bridge.GetSetProp(args.callObject, args.method, args.getArgs(), response);
		else
		    bridge.Invoke(args.callObject, args.method, args.getArgs(), response);
		response.flush();
		break;
	    case 'C':
		if(args.predicate)
		    bridge.CreateObject((String)args.callObject, false, args.getArgs(), response);
		else
		    bridge.CreateObject((String)args.callObject, true, args.getArgs(), response);
		response.flush();
		break;
	    }
	    args.reset();
	}
	return retval;
    }
    
    /**
     * Start handling requests until EOF. Creates a response object and handles all packets.
     * @throws IOException
     */
    public void handleRequests() throws IOException {
    	response=new Response(bridge);
	while(Parser.OK==handleRequest())
	    ;
    }
    
    /**
     * Start handling requests until EOF. Creates a response object and handles only the first packet. 
     * All following packets are discarded.
     * @throws IOException
     */
    public void handleOneRequest() throws IOException {
    	response=new Response(bridge);
	if(Parser.OK==handleRequest()) {
	    while(Parser.OK==parser.parse(bridge.in));
	}
    }
    private static final Object[] empty = new Object[] {null};
    
    /**
     * Handle protocol sub-requests, see <code>R</code> and <code>A</code> in the protocol spec.
     * @return An array of one object. The object is the result of the Apply call.
     * @throws IOException
     * @throws Throwable thrown by the PHP code.
     */
    protected Object[] handleSubRequests() throws IOException, Throwable {
    	response.flush();
    	Args current = args;
    	args = new Args();
	while(Parser.OK==parser.parse(bridge.in)){
	    response.setResultID(args.id); 
	    switch(args.type){
	    case 'I':
		if(args.predicate)
		    bridge.GetSetProp(args.callObject, args.method, args.getArgs(), response);
		else
		    bridge.Invoke(args.callObject, args.method, args.getArgs(), response);
		response.flush();   
		break;
	    case 'C':
		if(args.predicate)
		    bridge.CreateObject((String)args.callObject, false, args.getArgs(), response);
		else
		    bridge.CreateObject((String)args.callObject, true, args.getArgs(), response);
		response.flush();   
		break;
	    case 'R':
	    	Args ret = args;
	    	args = current;
	    	response.reset();
	    	
	    	if(ret.callObject!=null) 
	    	    throw (Throwable)ret.callObject;
	    	return ret.getArgs();
	    }
	    args.reset();
	}
	args = current;
	return empty;
    }
}
