package php.java.bridge.http;

import java.io.IOException;
import java.io.OutputStream;

/**
 * A default output stream factory for use with parseBody.
 */
public abstract class OutputStreamFactory {
    /**
     * Return a new output stream
     * @return a new output stream
     */
    public abstract OutputStream getOutputStream() throws IOException;
}