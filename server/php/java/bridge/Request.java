package php.java.bridge;

import java.util.ArrayList;

/*
 * Created on Feb 13, 2005
 *
 * TODO To change the template for this generated file go to
 * Window - Preferences - Java - Code Style - Code Templates
 */

public class Request implements IDocHandler {

    private Parser parser;
    private ArrayList args;
    private JavaBridge bridge;
    public Request(JavaBridge bridge) {
	this.bridge=bridge;
	this.parser=new Parser(bridge, this);
	this.args=new ArrayList();
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
	    int i=Integer.parseInt((new String(st[0].string)), 16);
	    vo=bridge.globalRef.get(i);
	    m=new String(st[1].string);
	    p=st[1].string[0]=='P';
	    id=Integer.parseInt((new String(st[0].string)), 16);
	    break;
	}
	case 'C': {
	    t=ch;
	    vs=new String(st[0].string);
	    p=st[1].string[0]=='C';
	    id=Integer.parseInt((new String(st[0].string)), 16);
	    break;
	}
	case 'F':
	case 'M': {
	    t=ch;
	    int i=Integer.parseInt((new String(st[0].string)), 16);
	    vo=bridge.globalRef.get(i);
	    m=new String(st[1].string);
	    id=Integer.parseInt((new String(st[0].string)), 16);
	    break;
	}
	case 'U': {
	    int i=Integer.parseInt((new String(st[0].string)), 16);
	    bridge.globalRef.remove(id);
	    break;
	}
	case 'S': {
	    args.add(st[0].string);
	    break;
	}
	case 'B': {
	    args.add(new Boolean(st[0].string[0]=='T'));
	    break;
	}
	case 'L': {
	    args.add(new Long(Long.parseLong((new String(st[0].string)), 16)));
	    break;
	}
	case 'D': {
	    args.add(new Double(Double.parseDouble(new String(st[0].string))));
	    break;
	}
	case 'O': {
	    if(0==st[0].length)
		vo=null;
	    else {
		int i=Integer.parseInt((new String(st[0].string)), 16);
		args.add(bridge.globalRef.get(id));
	    }
	    break;
	}
	}	
    }
    public void end(ParserString[] string) {
    }

    void handleRequests() {
	while(0!=parser.parse(bridge.peer)){
	    Response res=new Response(bridge, id);
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
	}
	bridge.globalRef=null;
    }
}
