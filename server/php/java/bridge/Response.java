/*-*- mode: Java; tab-width:8 -*-*/

package php.java.bridge;
import java.io.IOException;

public class Response {
    StringBuffer buf;
    long result, peer;
    private byte options;
    
    private JavaBridge bridge;
    public Response(JavaBridge bridge) {
	buf=new StringBuffer();
	this.bridge=bridge;
    }
    
    public void setResult(long id, byte options) {
    	this.result=id;
    	this.options=options;
    }
    
    public boolean sendArraysAsValues() {
    	return (options & 1)==1;
    }
    
    static final String e="\"/>";
    static final String I="\" i=\"";
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
	buf.append(S); buf.append(s.replace("\"","&quot;"));
	buf.append(I); buf.append(String.valueOf(result));
	buf.append(e);
    }
    void writeBoolean(boolean b) {
	buf.append(B); buf.append(b==true?'T':'F');
	buf.append(I); buf.append(String.valueOf(result));
	buf.append(e);
    }
    void writeLong(long l) {
	buf.append(L);
	buf.append(String.valueOf(l));
	buf.append(I); buf.append(String.valueOf(result));
	buf.append(e);
    }
    void writeDouble(double d) {
	buf.append(D); buf.append(String.valueOf(d));
	buf.append(I); buf.append(String.valueOf(result));
	buf.append(e);
    }
    void writeObject(Object o) {
	buf.append(O); buf.append(o==null?"":
				  String.valueOf(this.bridge.globalRef.append(o)));
	buf.append(I); buf.append(String.valueOf(result));
	buf.append(e);
    }
    void writeException(Object o, String str) {
	buf.append(E); buf.append(String.valueOf(this.bridge.globalRef.append(o)));
	buf.append(m); buf.append(String.valueOf(str));
	buf.append(I); buf.append(String.valueOf(result));
	buf.append(e);
    }
    void writeCompositeBegin_a() {
	buf.append(Xa);
	buf.append(I); buf.append(String.valueOf(result));
	buf.append(e);
    }
    void writeCompositeBegin_h() {
	buf.append(Xh);
	buf.append(I); buf.append(String.valueOf(result));
	buf.append(e);
    }
    void writeCompositeEnd() {
	buf.append(Xe);
    }
    void writePairBegin_s(String key) {
	buf.append(Ps);	buf.append(key);
	buf.append(I); buf.append(String.valueOf(result));
	buf.append(e);
    }
    void writePairBegin_n(int key) {
	buf.append(Pn); buf.append(String.valueOf(key));
	buf.append(I); buf.append(String.valueOf(result));
	buf.append(e);
    }
    void writePairEnd() {
	buf.append(Pe);
    }
    void flush() throws IOException {
    	String s = buf.toString();
	if(Util.logLevel>=4) Util.logDebug("<-- " +buf.toString());
	byte[] b = s.getBytes();
	bridge.out.write(b, 0, b.length);
	buf.setLength(0);
    }
}
