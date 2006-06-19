/*-*- mode: Java; tab-width:8 -*-*/

package php.java.bridge;
/**
 * The log interface for the PHP/Java Bridge log.
 * @see php.java.bridge.FileLogger
 * @see php.java.bridge.ChainsawLogger
 * @see php.java.bridge.Log4jLogger
 * @author jostb
 *
 */
public interface ILogger {

    /**
     * Log a stack trace
     * @param t The Throwable
     */
    public void printStackTrace(Throwable t);

    /**
     * Log a message.
     * @param level The log level 0: FATAL, 1:ERROR, 2: INFO, 3: DEBUG
     * @param msg The message
     */
    public void log(int level, String msg);

    /**
     * Display a warning if logLevel >= 1
     * @param msg The warn message
     */
    public void warn(String msg);
}
