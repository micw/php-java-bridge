package php.java.bridge;
/*
 * Created on Feb 13, 2005
 *
 * TODO To change the template for this generated file go to
 * Window - Preferences - Java - Code Style - Code Templates
 */

class ParserTag {
    private final JavaBridge bridge;
    short n;
    ParserString strings[];
    ParserTag (JavaBridge bridge, int n) { strings = new ParserString[n];
    this.bridge = bridge; }
}
