/*-*- mode: Java; tab-width:8 -*-*/

package php.java.bridge;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;

public class Request implements IDocHandler {

    private Parser parser;
    private ArrayList args;
    private JavaBridge bridge;
    public Request(JavaBridge bridge) {
	this.bridge=bridge;
	this.parser=new Parser(bridge, this);
	this.args=new ArrayList();
    }
    static final byte[] ZERO={0};
    boolean initOptions(InputStream in, OutputStream out) throws IOException {
    	switch(parser.initOptions(in)) {
    	case Parser.PING: out.write(ZERO, 0, 1); return false;
    	case Parser.IO_ERROR: 
    	case Parser.EOF: return false;
    	}
    	return true;
    }
 
    // args for Invoke, Create, see protocol.txt
    int id;
    byte t, ch;
    String vs, m; Object vo; boolean p; 

    public void begin(ParserTag[] tag) {
	ParserString[] st=tag[2].strings;
	switch (ch=tag[0].strings[0].string[0]) {
	case 'I': {
	    t=ch;
	    int i=Integer.parseInt(st[0].getStringValue(), 10);
	    vo=bridge.globalRef.get(i);
	    m=st[1].getStringValue();
	    p=st[2].string[0]=='P';
	    id=Integer.parseInt(st[3].getStringValue(), 10);
	    break;
	}
	case 'C': {
	    t=ch;
	    vs=st[0].getStringValue();
	    p=st[1].string[0]=='C';
	    id=Integer.parseInt(st[2].getStringValue(), 10);
	    break;
	}
	case 'F':
	case 'M': {
	    t=ch;
	    int i=Integer.parseInt(st[0].getStringValue(), 10);
	    vo=bridge.globalRef.get(i);
	    m=st[1].getStringValue();
	    id=Integer.parseInt(st[2].getStringValue(), 10);
	    break;
	}
	case 'U': {
	    int i=Integer.parseInt(st[0].getStringValue(), 10);
	    bridge.globalRef.remove(id);
	    break;
	}
	case 'S': {// TODO avoid newString(..).getBytes() here.
	    args.add(st[0].getStringValue().getBytes());
	    break;
	}
	case 'B': {
	    args.add(new Boolean(st[0].string[0]=='T'));
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
		vo=null;
	    else {
		int i=Integer.parseInt(st[0].getStringValue(), 10);
		args.add(bridge.globalRef.get(i));
	    }
	    break;
	}
	}	
    }
    public void end(ParserString[] string) {
    }

    void handleRequests() throws IOException {
    	Response res=new Response(bridge);
	bridge.globalRef=new GlobalRef(bridge);
	while(Parser.OK==parser.parse(bridge.in)){
	    res.setResult(id, parser.options);
	    switch(t){
	    case 'I':
		if(p) 
		    bridge.GetSetProp(vo, m, args.toArray(), res);
		else 
		    bridge.Invoke(vo, m, args.toArray(), res);
		break;
	    case 'C':
		if(p)
		    bridge.CreateObject(vs, true, args.toArray(), res); 
		else
		    bridge.CreateObject(vs, false, args.toArray(), res);
		break;
	    case 'F':
		//FIXME split invoke
		//CallMethod(v, m, args, res);
		break;
	    case 'M':
		//FIXME split invoke
		//GetMethod(v, m, args, res);
		break;
	    }
	    res.flush();
	    args.clear();
	}
	bridge.globalRef=null;
    }
}
