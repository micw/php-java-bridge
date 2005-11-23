/*-*- mode: Java; tab-width:8 -*-*/

package php.java.bridge;

/**
 * Defines the parser callbacks.
 * @author jostb
 *
 */
public interface IDocHandler {
    
    /**
     * Called for each &lt;tag arg1 ... argn&gt;
     * @param tag The tag and the args.
     */
    void begin(ParserTag[] tag);
    
    /**
     * Called for each &lt;/tag&gt;
     * @param strings The tag and the args.
     * @see IDocHandler#begin(ParserTag[])
     */
    void end(ParserString[] strings);
}
 
