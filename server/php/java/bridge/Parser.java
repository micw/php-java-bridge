/*-*- mode: Java; tab-width:8 -*-*/

package php.java.bridge;

import java.io.IOException;
import java.io.InputStream;


public class Parser {
    static final int RECV_SIZE = 8192; // initial size of the receive buffer
    static final int MAX_ARGS = 100; // max # of method arguments
    static final int SLEN = 256; // initial length of the parser string

    static final short OK=0, PING=1, EOF=2, IO_ERROR=3;    // parse return codes

    IDocHandler handler;
    JavaBridge bridge;
    Parser(JavaBridge bridge, IDocHandler handler) {
        this.bridge = bridge;
	this.handler=handler;
	tag=new ParserTag[]{new ParserTag(1), new ParserTag(MAX_ARGS), new ParserTag(MAX_ARGS) };
    }
    
    byte options;
    short initOptions(InputStream in) throws IOException {
	if((pos=in.read(buf, 0, RECV_SIZE)) >0) { 

	    /*
	     * Special handling if the first byte is neither a space nor "<"
	     * 
	     */
	    switch(ch=buf[c]) {
	    case '<': case '\t': case '\f': case '\n': case '\r': case ' ': break;
	    case 0:		
		// PING
		return PING;

		// OPTIONS
	    default: options=(byte) (ch&3); c++;
	    }
	} else {
		return EOF;
	}
	return OK; 
    }
    
    ParserTag tag[] = null;
    byte buf[] = new byte[RECV_SIZE];
    int len=SLEN;
    byte s[]= new byte[len];
    byte ch, mask=(byte)~0;
    // VOJD is VOID for f... windows (VOID is in winsock2.h)
    static final short BEGIN=0, KEY=1, VAL=2, ENTITY=3, BLOB=4, VOJD=5, END=6; short type=VOJD;
    short level=0, eor=0, blen=0; boolean in_dquote, eot=false;
    int pos=0, c=0, i=0, i0=0, e;

    void RESET() {
    	type=VOJD;
     	mask=~(byte)0;
    	level=0;
    	eor=0;
    	blen=0;
    	eot=in_dquote=false;
    	i=0;
    	i0=0;
    }
    void APPEND(byte c) {
	if(i>=len-1) {
	    int newlen=len*2;
	    byte[] s1=new byte[newlen];
	    System.arraycopy(s, 0, s1, 0, len);
	    len=newlen;
	    s=s1;
	} 
	s[i++]=c; 
    }
    void CALL_BEGIN() {
    	if(Util.logLevel>=4) {
	    StringBuffer buf=new StringBuffer("--> <");   
	    buf.append(tag[0].strings[0].getUTF8StringValue());
	    buf.append(" ");
    		
	    for(int i=0; i<tag[1].n; i++) {
		buf.append(tag[1].strings[i].getUTF8StringValue()); buf.append("=\""); buf.append(tag[2].strings[i].getUTF8StringValue());buf.append("\" ");
	    }
	    buf.append(eot?"/>":">");
	    Util.logDebug(this.bridge + " " + buf.toString());
    	}
	handler.begin(tag);
    }
    void CALL_END() {
    	if(Util.logLevel>=4) {
	    StringBuffer buf=new StringBuffer("--> </");   
	    buf.append(tag[0].strings[0].getUTF8StringValue());
	    buf.append(">");
	    Util.logDebug(this.bridge + " " + buf.toString());
    	}
	handler.end(tag[0].strings);
    }
    void PUSH(int t) { 
	ParserString str[] = tag[t].strings;
	short n = tag[t].n;
	s[i]=0;
	if(str[n]==null) str[n]=new ParserString();
	str[n].string=s;
	str[n].off=i0;
	str[n].length=i-i0;
	++tag[t].n;
	APPEND((byte)0);
	i0=i;
    }
    short parse(InputStream in) throws IOException {
  
    	while(eor==0) {
	    if(c==pos) { 

	    	pos=in.read(buf, 0, RECV_SIZE); 
		if(pos<=0) return EOF;
		c=0; 

	    }
	    switch((ch=buf[c])&mask) 
		{/* --- This block must be compilable with an ansi C compiler or javac --- */
		case '<': if(in_dquote) {APPEND(ch); break;}
		    level++;
		    type=BEGIN;
		    break;
		case '\t': case '\f': case '\n': case '\r': case ' ': if(in_dquote) {APPEND(ch); break;}
		    if(type==BEGIN) {
			PUSH(type); 
			type = KEY; 
		    }
		    break;
		case '=': if(in_dquote) {APPEND(ch); break;}
		    PUSH(type);
		    type=VAL;
		    break;
		case '/': if(in_dquote) {APPEND(ch); break;}
		    if(type==BEGIN) { type=END; level--; }
		    level--;
		    eot=true; // used for debugging only
		    break;
		case '>': if(in_dquote) {APPEND(ch); break;}
		    if(type==END){
			PUSH(BEGIN);
			CALL_END();
		    } else {
			if(type==VAL) PUSH(type);
			CALL_BEGIN();
		    }
		    tag[0].n=tag[1].n=tag[2].n=0; i0=i=0;      		/* RESET */
		    type=VOJD;
		    if(level==0) eor=1; 
		    break;
		case ';':
		    if(type==ENTITY) {
			switch (s[e+1]) {
			case 'l': s[e]='<'; i=e+1; break; /* lt */
			case 'g': s[e]='>'; i=e+1; break; /* gt */
			case 'a': s[e]=(byte) (s[e+2]=='m'?'&':'\''); i=e+1; break; /* amp, apos */
			case 'q': s[e]='"'; i=e+1; break; /* quot */
			default: APPEND(ch);
			}
			type=VAL; //& escapes may only appear in values
		    } else {
		    	APPEND(ch);
		    }
		    break;
		case '&': 
		    type = ENTITY;
		    e=i;
		    APPEND(ch);
		    break;
		case '"':
		    in_dquote = !in_dquote;
		    if(!in_dquote && type==VAL) {
			PUSH(type);
			type = KEY;
		    }
		    break;
		default:
		    APPEND(ch);
		} /* ------------------ End of ansi C block ---------------- */
	    c++;
	}
   	RESET();
	return OK;
    }
}
