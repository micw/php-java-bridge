package php.java.bridge;
/*
 * Created on Feb 13, 2005
 *
 * TODO To change the template for this generated file go to
 * Window - Preferences - Java - Code Style - Code Templates
 */

public class Response {
    public int version=4;//FIXME
    StringBuffer buf;
    long result, peer;
    private JavaBridge bridge;
    public Response(JavaBridge bridge, long result) {
	buf=new StringBuffer();
	this.bridge=bridge;
	this.result=result;
	bridge.globalRef=new GlobalRef(bridge);
    }

    static final String e="\"/>";
    static final String S="<S v=\"";
    static final String B="<B v=\"";
    static final String L="<L v=\"";
    static final String D="<D v=\"";
    static final String E="<E v=\"";
    static final String O="<O v=\"";
    static final String m="\" m=\"";
    static final String Xa="<X t=\"A\"";
    static final String Xh="<X t=\"H\"";
    static final String Xe="</X>";
    static final String Pn="<Pt=\"N\" v=\"";
    static final String Ps="<P t=\"S\" v=\"";
    static final String Pe="</P>";
    void writeString(String s) {
	buf.append(S);
	buf.append(s);
	buf.append(e);
    }
    void writeBoolean(boolean b) {
	buf.append(B);
	buf.append(b==true?'T':'F');
	buf.append(e);
    }
    void writeLong(long l) {
	buf.append(L);
	buf.append(String.valueOf(l));
	buf.append(e);
    }
    void writeDouble(double d) {
	buf.append(D);
	buf.append(String.valueOf(d));
	buf.append(e);
    }
    void writeObject(Object o) {
	buf.append(O);
	buf.append(String.valueOf(this.bridge.globalRef.append(o)));
	buf.append(e);
    }
    void writeException(Object o, String str) {
	buf.append(E);
	buf.append(String.valueOf(this.bridge.globalRef.append(o)));
	buf.append(m);
	buf.append(String.valueOf(str));
	buf.append(e);
    }
    void writeCompositeBegin_a() {
	buf.append(Xa);
	buf.append(e);
    }
    void writeCompositeBegin_h() {
	buf.append(Xa);
	buf.append(e);
    }
    void writeCompositeEnd() {
	buf.append(Xe);
    }
    void writePairBegin_s(String key) {
	buf.append(Ps);
	buf.append(key);
	buf.append(e);
    }
    void writePairBegin_n(int key) {
	buf.append(Pn);
	buf.append(String.valueOf(key));
	buf.append(e);
    }
    void writePairEnd() {
	buf.append(Pe);
    }
    void flush() {
	byte[] b=buf.toString().getBytes();
	JavaBridge.swrite(bridge.peer, b, b.length);
    }
}
