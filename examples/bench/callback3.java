import java.io.FileInputStream;
import java.io.InputStream;
import java.io.IOException;

import javax.xml.parsers.SAXParser;

import org.xml.sax.Attributes;
import org.xml.sax.ContentHandler;
import org.xml.sax.DTDHandler;
import org.xml.sax.ErrorHandler;
import org.xml.sax.Locator;
import org.xml.sax.SAXException;
import org.xml.sax.SAXParseException;
import org.xml.sax.XMLReader;
import org.xml.sax.helpers.DefaultHandler;
import org.xml.sax.ext.DeclHandler;
import org.xml.sax.ext.LexicalHandler;

public class callback3 implements org.xml.sax.DocumentHandler, org.xml.sax.ErrorHandler
{

    public void setDocumentLocator(Locator locator) {
    }

    public void startDocument() throws SAXException {
	System.out.println("\n");
    }

    public void processingInstruction(String target, String data)
        throws SAXException {
    }

    public void startPrefixMapping(String prefix, String uri)
        throws SAXException {
    }

    public void characters(char[] ch, int offset, int length) throws SAXException {
	System.out.print(new String(ch, offset, length));
    }

    public void ignorableWhitespace(char[] ch, int offset, int length) throws SAXException {
	System.out.print(new String(ch, offset, length));
    }
    public void startElement(java.lang.String s,org.xml.sax.AttributeList l) {
	System.out.print("[");
    }
    public void endElement(java.lang.String s) {
	System.out.print("]");
    }

    public void endPrefixMapping(String prefix) throws SAXException {
    }

    public void skippedEntity(String name) throws SAXException {
    }

    public void endDocument() throws SAXException {
	System.out.print("\n");
    }

    /** Warning. */
    public void warning(SAXParseException ex) {
        System.err.println("[Warning] "+ ex.getMessage());
    }

    /** Error. */
    public void error(SAXParseException ex) {
        System.err.println("[Error] "+ ex.getMessage());
    }

    /** Fatal error. */
    public void fatalError(SAXParseException ex) throws SAXException {
        System.err.println("[Fatal Error] "+ ex.getMessage());
        throw ex;
    }

    public static void main(String[] argv) throws Exception {
        int i, n=500, sum;
	long result[] = new long[n];
	for (i=0; i<n; i++) {
	    long t1=System.currentTimeMillis();
	    callback3 handler = new callback3();

	    org.xml.sax.Parser parser = javax.xml.parsers.SAXParserFactory.newInstance().newSAXParser().getParser();

	    parser.setDocumentHandler(handler);
	    parser.setErrorHandler(handler);
	    parser.parse(argv.length==0?"../XML/phpinfo.xml":argv[0]);
	    long t2=System.currentTimeMillis();
	    System.err.println(result[i]=t2-t1);
	}
	for(sum=0, i=0; i<n; i++) {
	    sum+=result[i];
	}
	System.err.println("---\n" +(sum/n));
    } 
}
