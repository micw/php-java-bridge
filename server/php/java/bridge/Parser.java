package php.java.bridge;
/*
 * Created on Feb 13, 2005
 *
 * TODO To change the template for this generated file go to
 * Window - Preferences - Java - Code Style - Code Templates
 */

public class Parser {
    // parse return codes
    static final short OK=0, PING=1, EOF=2, IO_ERROR=3;

    private final JavaBridge bridge;
    IDocHandler handler;
    Parser(JavaBridge bridge, IDocHandler handler) {
	this.handler=handler;
	this.bridge = bridge;
	tag=new ParserTag[]{new ParserTag(bridge, 1), new ParserTag(bridge, MAX_ARGS), new ParserTag(bridge, MAX_ARGS) };
    }

    static final byte[] ZERO={0};
    static final int BUF_SIZE = 5;//FIXME: use 8K
    static final int MAX_ARGS = 10;
    ParserTag tag[] = null;
    byte buf[] = new byte[BUF_SIZE];
    int len=2;//FIXME: use 255
    byte s[]= new byte[len];
    byte ch, mask;
    static final short BEGIN=0, KEY=1, VAL=2, ENTITY=3, BLOB=4, VOID=5; short type=VOID;
    short level=0, eor=0, blen=0; boolean in_dquote;
    int pos=0, c=0, i=0, i0=0, e;

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
	handler.begin((ParserTag[])tag);
    }
    void CALL_END() {
	handler.end(tag[0].strings);
    }
    void PUSH(int t) { 
	ParserString str[] = tag[t].strings;
	short n = tag[t].n;
	s[i]=0;
	str[n].string=s;
	str[n].off=i0;
	str[n].length=i-i0;
	++tag[t].n;
	APPEND((byte)0);
	i0=i;
    }
    short parse(long peer) {
    	byte options;
	pos=JavaBridge.sread(peer, buf, BUF_SIZE); 

	/*
	 * Special handling if the first byte is neither a space nor "<"
	 * 
	 */
	switch(ch=buf[c]) {
	case '<': case '\t': case '\f': case '\n': case '\r': case ' ': break;
	case 0:		
	    // PING
	    JavaBridge.swrite(peer, ZERO, 1);
	    return PING;

	    // OPTIONS
	default: options=(byte) (ch&3); c++;
	}

	while(eor==0) {
	    if(c==pos) { 
		if(0!=JavaBridge.seof(peer)) return EOF;

		pos=JavaBridge.sread(peer, buf, BUF_SIZE); 
		c=0; 

	    }
	    switch((ch=buf[c])&mask) 
		{/* --- This block must be compilable with an ansi C compiler  --- */
		case '<':
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
		    if(type==BEGIN) level--;
		    level--;
		    break;
		case '>': if(in_dquote) {APPEND(ch); break;}
		    if(type==BEGIN){
			PUSH(type);
			CALL_END();
		    } else {
			if(type==VAL) PUSH(type);
			CALL_BEGIN();
		    }
		    tag[0].n=tag[1].n=tag[2].n=0; i0=i=0;      		/* RESET */
		    type=VOID;
		    if(level==0) eor=1;
		    break;
		case '\0':
		    if(mask==0) {type=BLOB; mask=0; break;}
		    if(type==VOID) mask=~0;
				
		    if(0!=blen) {
			APPEND(ch);
			if(0==--blen) type=VOID;
		    }
		    else {
			blen=ch;
		    }
		    break;
		case ';':
		    if(type==ENTITY) {
			switch (s[e]) {
			case 'l': s[e]='<'; break; /* lt */
			case 'g': s[e]='>'; break; /* gt */
			case 'a': s[e]=(byte) (s[e+1]=='m'?'&':'\''); break; /* amp, apos */
			case 'q': s[e]='"'; break; /* quot */
			}
			i=e+1;
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
	s=null;
	return OK;
    }
}
