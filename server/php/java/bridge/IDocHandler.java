/*-*- mode: Java; tab-width:8 -*-*/

package php.java.bridge;

public interface IDocHandler {
    void begin(ParserTag[] tag);
    void end(ParserString[] strings);
}
 
