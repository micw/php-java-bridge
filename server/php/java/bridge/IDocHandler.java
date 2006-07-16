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
     * @return true, if the parser should stop after reading the top-level end tag, false otherwise. 
     * Implements a short path: Set this to true, if you already know that the current top-level request doesn't need a reply. 
     */
    public boolean begin(ParserTag[] tag);
    
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
 
