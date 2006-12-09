/*-*- mode: Java; tab-width:8 -*-*/

package php.java.bridge;

/*
 * Copyright (C) 2006 Jost Boekemeier
 *
 * Permission is hereby granted, free of charge, to any person obtaining a
 * copy of this software and associated documentation files (the "Software"),
 * to deal in the Software without restriction, including without limitation
 * the rights to use, copy, modify, merge, publish, distribute, sublicense,
 * and/or sell copies of the Software, and to permit persons to whom the
 * Software is furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT.  IN NO EVENT SHALL
 * THE COPYRIGHT HOLDER(S) OR AUTHOR(S) BE LIABLE FOR ANY CLAIM, DAMAGES OR
 * OTHER LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE,
 * ARISING FROM, OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR
 * OTHER DEALINGS IN THE SOFTWARE.
 */

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.LinkedList;

/**
 * This class is used to handle requests from the front-end.
 * @author jostb
 *
 */
public final class Request implements IDocHandler {

    private Parser parser;
    private JavaBridge defaultBridge, bridge;
    protected static final IntegerComparator PHP_ARRAY_KEY_COMPARATOR = new IntegerComparator();
    // Only used when the async. protocol is enabled.
    protected static final class PhpNull {}
    protected static final PhpNull PHPNULL = new PhpNull();
    protected Object getGlobalRef(int i) {
	Object ref = bridge.globalRef.get(i);
	if(ref == PHPNULL) return null;
	return ref;
    }

    static final Object[] ZERO_ARGS = new Object[0];
    private class SimpleContext {
	public void parseID(ParserString string) {}
	public void setID(Response response) {}
    }
    private class Context extends SimpleContext {
    	protected long id;
	public void parseID(ParserString string) {
	    this.id = string.getLongValue();
	}
	public void setID(Response response) {
	    response.setID(id);
	}
    }
    private SimpleContext contextCache;
    SimpleContext getContext() {
	if(contextCache!=null) return contextCache;
	if(bridge.options.passContext()) {
	    return contextCache = new Context();
	}
	return contextCache = new SimpleContext();
    }
    private abstract class Arg {
    	protected byte type;
    	protected Object callObject;
    	protected Throwable exception;
    	protected String method;
    	protected SimpleContext id = getContext();
    	protected boolean predicate;
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
		    ht.put(new Integer(count++), val);
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
        {
	    int val = st[0].getIntValue();
	    if(bridge.options.hexNumbers() && st[1].string[st[1].off]!='O')
		val *= -1;
            return (new Integer(val));
	}
    }

    /* (non-Javadoc)
     * @see php.java.bridge.IDocHandler#begin(php.java.bridge.ParserTag[])
     */
    public boolean begin(ParserTag[] tag) {
	boolean reply = true;
	ParserString[] st=tag[2].strings;
	byte ch;
	switch (ch=tag[0].strings[0].string[0]) {
	case 'I': {
	    arg.type=ch;
	    int i= st[0].getIntValue();
	    arg.callObject=i==0?bridge:getGlobalRef(i);
	    arg.method=st[1].getCachedStringValue();
	    arg.predicate=st[2].string[st[2].off]=='P';
	    arg.id.parseID(st[3]);
	    break;
	}
	case 'C': {
	    arg.type=ch;
	    arg.callObject=st[0].getCachedStringValue();
	    arg.predicate=st[1].string[st[1].off]=='C';
	    arg.id.parseID(st[2]);
	    break;
	}
	case 'F': {
	    arg.type=ch;
	    arg.predicate = st[0].string[st[0].off]=='A';
	    break;
	}
	case 'R': {
	    arg.type=ch;
	    arg.id.parseID(st[0]);
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
		   arg.key = new Integer(st[1].getIntValue());
		}
	    } else // array
		arg.key=null;
	    break;
	}

	case 'U': {
	    int i=st[0].getIntValue();
	    bridge.globalRef.remove(i);
	    reply=false; // U is the only top-level request which doesn't need a reply
	    break;
	}
	case 'S': 
	  if(tag[2].n>0) { // handle <S v=... />
	    if(arg.composite!='H') 
	        arg.add(new PhpParserString(bridge, st[0]));
	    else // hash has no type information
	        arg.add(st[0].getStringValue());
	  }
	  break;
	case 'B': {
	    arg.add(new Boolean(st[0].string[st[0].off]=='T'));
	    break;
	}
	case 'L': {
	    if(arg.composite!='H')
	        arg.add(new PhpExactNumber(getPhpLong(st)));
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
	return reply;
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
    private static final String SUB_FAILED = "PHP callback execution failed.";
    private void setIllegalStateException(String s) {
        IllegalStateException ex = new IllegalStateException(s);
        response.setResultException(bridge.lastException = ex, s);
    }
    private int handleRequest() throws IOException {
	int retval;
	if(Parser.OK==(retval=parser.parse(bridge.in))) {
	    arg.id.setID(response);
	    switch(arg.type){
	    case 'I':
		try {
		    if(arg.predicate)
			bridge.GetSetProp(arg.callObject, arg.method, arg.getArgs(), response);
		    else
			bridge.Invoke(arg.callObject, arg.method, arg.getArgs(), response);
		    response.flush();
		} catch (AbortException sub) {
		    bridge.printStackTrace(sub);
		}
		break;
	    case 'C':
		try {
		    if(arg.predicate)
			bridge.CreateObject((String)arg.callObject, false, arg.getArgs(), response);
		    else
			bridge.CreateObject((String)arg.callObject, true, arg.getArgs(), response);
		    response.flush();
		} catch (AbortException sub) {
		    bridge.printStackTrace(sub);
		}
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
    	if(response==null) response=bridge.createResponse();
	this.arg=new SimpleArg();    	
	while(Parser.OK==handleRequest())
	    ;
    }
    /** This exception isn't an exception but a construct to emulate a one-shot continuation in Java. 
     * It is used to quickly clear the stack and to jumb back to some top-level loop.
     * <p>
     * The exception is thrown when the client exits while running a callback, 
     * it aborts everything and jumps back to the top-level request-handling loop, see handleRequests() above.
     * </p>
     * <p>Do not catch this exception! If you must catch Exception(), check for AbortException. If
     * you invoke using the reflection interface, check for the wrapped exception, unwrap it, if necessary 
     * and re-throw the exception</p>
     * @see Request#handleRequest()
     */
    public static class AbortException extends RuntimeException {
	private static final long serialVersionUID = 7778150395848350732L;
    }
    /**
     * Handle protocol sub-requests, see <code>R</code> and <code>A</code> in the protocol spec.
     * @return An array of one object. The object is the result of the Apply call.
     * @throws IOException
     * @throws Throwable thrown by the PHP code.
     */
    protected Object[] handleSubRequests() throws AbortException, IOException, Throwable {
      	Response currentResponse = response; Arg current = arg;
    	response = response.copyResponse(); // must keep the current response state, for example coerceWriter for castToString() 
    	arg = new SimpleArg();
	while(Parser.OK==parser.parse(bridge.in)){
	    arg.id.setID(response); 
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
	 	response = currentResponse;
	 	arg = current;
		throw new AbortException();	         
	    case 'R':
	    	Arg ret = arg;
	    	arg = current;
	    	
	    	// remove retval from the output buffer and return it to the parent.
	    	response.reset();
	    	response = currentResponse;
	    	if(ret.callObject!=null) 
	    	    throw (Throwable)ret.callObject;
	    	return ret.getArgs();
	    }
	    arg.reset();
	}
	arg = current;
	response = currentResponse;
	throw new AbortException();
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
    /**
     * Set a temporary bridge into this request. The bridge and its 
     * associated session-/contextFactory will be automatically destroyed when the request is done. 
     * The {@link php.java.bridge.http.ContextRunner#recycle(php.java.bridge.http.IContextFactory) }
     * uses this to temporarily update the runner with the new bridge obtained from the 
     * ContextServer's ContextFactory while keeping the same connection to the client: in a shared environment
     * a PHP client may be subscribed to n web contexts while using only one persistent ContextRunner, but 
     * JavaBridges and their ContextFactories may contain state (session vars, globalRef, ...) and belong to 
     * exactly one web context ContextServer.
     * 
     * @param bridge The fresh bridge and its ContextFactory
     */
    public void setBridge(JavaBridge bridge) {
	defaultBridge = this.bridge;
	bridge.in = this.bridge.in;
	bridge.out = this.bridge.out;
	this.bridge = bridge;
	this.bridge.request = this;
	response.setBridge(bridge);
	parser.setBridge(bridge);
    }
    private void resetBridge() {
        if(defaultBridge!=null) {
            bridge.sessionFactory.destroy();
            bridge = defaultBridge;
            response.setBridge(bridge);
            parser.setBridge(bridge);
            defaultBridge = null;
        }
    }
    /** re-initialize for new requests */
    public void recycle() {
        reset();
        arg.reset();
        resetBridge();
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
