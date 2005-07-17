/*-*- mode: Java; tab-width:8 -*-*/

package php.java.bridge;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.HashMap;

public class Request implements IDocHandler {

    private Parser parser;
    private JavaBridge bridge;
    public static class PhpArray extends HashMap { // for PHP's array()
		private static final long serialVersionUID = 3905804162838115892L;
	};
    private static class Args {
    	PhpArray ht; 
    	ArrayList array;
    	int count;

    	byte composite;
    	byte type;
    	Object callObject;
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
    Response response;

    public Request(JavaBridge bridge) {
	this.bridge=bridge;
	this.parser=new Parser(bridge, this);
	this.args=new Args();
    }
    static final byte[] ZERO={0};
    public boolean initOptions(InputStream in, OutputStream out) throws IOException {
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
			else
				args.key = new Long(Long.parseLong(st[1].getStringValue(), 10));
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
            byte[] bytes=new byte[st[0].length];
            System.arraycopy(st[0].string,st[0].off,bytes,0,bytes.length);
            args.add(bytes);
	    break;
	}
	case 'B': {
	    args.add(new Boolean(st[0].string[st[0].off]=='T'));
	    break;
	}
	case 'L': {
	    args.add(new Long(Long.parseLong(st[0].getStringValue(), 10)));
	    break;
	}
	case 'D': {
	    args.add(new Double(Double.parseDouble(st[0].getStringValue())));
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
    public void end(ParserString[] string) {
    	switch(string[0].string[0]) {
    	case 'X': {
	    args.push();
    	}
    	}
    }
    public void handleRequests() throws IOException {
    	response=new Response(bridge);
	while(Parser.OK==parser.parse(bridge.in)){
	    response.setResult(args.id, parser.options);
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
    }
    private static final Object[] empty = new Object[] {null};
    public Object[] handleSubRequests() throws IOException {
    	response.flush();
    	Args current = args;
    	args = new Args();
	while(Parser.OK==parser.parse(bridge.in)){
	    response.setResult(args.id, parser.options); 
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
	    	Object[] retval = args.getArgs();
	    	response.reset();
	    	args = current;
	    	return retval;
	    }
	    args.reset();
	}
	args = current;
	return empty;
    }
}
