/*-*- mode: Java; tab-width:8 -*-*/

package php.java.bridge;

/**
 * This structure carries the parsed tag and the arguments.
 * @author jostb
 *
 */
public class ParserTag {
    /**
     * The number of strings.
     */
    public short n;
    
    /**
     * The strings.
     */
    public ParserString strings[];
    
    protected ParserTag (int n) { strings = new ParserString[n]; }
}
