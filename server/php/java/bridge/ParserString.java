package php.java.bridge;
/*
 * Created on Feb 13, 2005
 *
 * TODO To change the template for this generated file go to
 * Window - Preferences - Java - Code Style - Code Templates
 */

class ParserString {
    private final JavaBridge bridge;
    /**
     * @param bridge
     */
    ParserString(JavaBridge bridge) {
	this.bridge = bridge;
	// TODO Auto-generated constructor stub
    }
    byte[] string;
    int off;
    int length;
}
