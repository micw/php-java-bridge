package php.java.bridge;

/*
 * Created on Feb 13, 2005
 *
 * TODO To change the template for this generated file go to
 * Window - Preferences - Java - Code Style - Code Templates
 */


public interface IDocHandler {
	void begin(ParserTag[] tag);
	void end(ParserString[] strings);
}
 