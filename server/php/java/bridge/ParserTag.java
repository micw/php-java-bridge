/*-*- mode: Java; tab-width:8 -*-*/

package php.java.bridge;

class ParserTag {
    short n;
    ParserString strings[];
    ParserTag (int n) { strings = new ParserString[n]; }
}
