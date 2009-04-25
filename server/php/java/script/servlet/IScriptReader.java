package php.java.script.servlet;

import java.io.Closeable;
import java.io.IOException;

/**
 * Reader with a public isClosed() method.
 * 
 * @author jostb
 *
 */
public interface IScriptReader extends  Closeable {

    /**
     * Return the isClosed flag
     * @return true if the reader has been closed, false otherwise
     */
    public abstract boolean isClosed();

    /**
     * Reads characters into a portion of an array.  This method will block
     * until some input is available, an I/O error occurs, or the end of the
     * stream is reached.
     *
     * @param      cbuf  Destination buffer
     * @param      off   Offset at which to start storing characters
     * @param      len   Maximum number of characters to read
     *
     * @return     The number of characters read, or -1 if the end of the
     *             stream has been reached
     *
     * @exception  IOException  If an I/O error occurs
     */
    public abstract int read(char cbuf[], int off, int len) throws IOException;
}