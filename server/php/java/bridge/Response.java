/*-*- mode: Java; tab-width:8 -*-*/

package php.java.bridge;
import java.io.IOException;
import java.util.regex.Pattern;

public class Response {
    StringBuffer buf;
    long result, peer;
    private byte options;
    private static Pattern quotePattern = java.util.regex.Pattern.compile("\"");
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
    	return (options & 2)==2;
    }
    
    static final String e="\"/>";
    static final String c="\">";
    static final String I="\" i=\"";
    static final String S="<S v=\"";
    static final String B="<B v=\"";
    static final String L="<L v=\"";
    static final String D="<D v=\"";
    static final String E="<E v=\"";
    static final String O="<O v=\"";
    static final String m="\" m=\"";
    static final String Xa="<X t=\"A";
    static final String Xh="<X t=\"H";
    static final String Xe="</X>";
    static final String P="<P>";
    static final String Pn="<P t=\"N\" v=\"";
    static final String Ps="<P t=\"S\" v=\"";
    static final String Pe="</P>";
    void writeString(String s) {
    	
	buf.append(S); buf.append(quotePattern.matcher(s).replaceAll("&quot;"));
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
	buf.append(c);
    }
    void writeCompositeBegin_h() {
	buf.append(Xh);
	buf.append(I); buf.append(String.valueOf(result));
	buf.append(c);
    }
    void writeCompositeEnd() {
	buf.append(Xe);
    }
    void writePairBegin_s(String key) {
	buf.append(Ps);	buf.append(key);
	buf.append(c);
    }
    void writePairBegin_n(int key) {
	buf.append(Pn); buf.append(String.valueOf(key));
	buf.append(c);
    }
    void writePairBegin() {
	buf.append(P);
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
