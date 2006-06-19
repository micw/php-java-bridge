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
    public void begin(ParserTag[] tag);
    
    /**
     * Called for each &lt;/tag&gt;
     * @param strings The tag and the args.
     * @see IDocHandler#begin(ParserTag[])
     */
    public void end(ParserString[] strings);

    /** 
     * Parser string factory
     * @return The parser string
     */
    public ParserString createParserString();
}
 
