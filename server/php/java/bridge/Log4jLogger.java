/*-*- mode: Java; tab-width:8 -*-*/

package php.java.bridge;

/*
 * Copyright (C) 2003-2007 Jost Boekemeier
 *
 * Permission is hereby granted, free of charge, to any person obtaining a
 * copy of this software and associated documentation files (the "Software"),
 * to deal in the Software without restriction, including without limitation
 * the rights to use, copy, modify, merge, publish, distribute, sublicense,
 * and/or sell copies of the Software, and to permit persons to whom the
 * Software is furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT.  IN NO EVENT SHALL
 * THE COPYRIGHT HOLDER(S) OR AUTHOR(S) BE LIABLE FOR ANY CLAIM, DAMAGES OR
 * OTHER LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE,
 * ARISING FROM, OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR
 * OTHER DEALINGS IN THE SOFTWARE.
 */

import java.lang.reflect.Method;

/**
 * A logger which uses the log4j default appender. Requires that log4j.jar is in the classpath.<br>
 */
public class Log4jLogger implements ILogger {
    protected LoggerProxy logger;
    protected SimpleJavaBridgeClassLoader loader;
    
    protected class LoggerProxy {
	Object logger;
	protected Class priority;
	protected Object fatal, error, warn, info, debug;
	protected LoggerProxy() throws Exception {
	    Class c = loader.forName("org.apache.log4j.Logger");
	    Method m = c.getMethod("getLogger", new Class[]{String.class});
	    logger = m.invoke(c, new Object[]{"php.java.bridge.JavaBridge"});
	    c = priority = loader.forName("org.apache.log4j.Priority");
	    fatal=c.getField("FATAL").get(c);
	    error=c.getField("ERROR").get(c);
	    warn=c.getField("WARN").get(c);
	    info=c.getField("INFO").get(c);
	    debug=c.getField("DEBUG").get(c);
	}
	private Method errorMethod;
	public synchronized void error(String string, Throwable t) throws Exception {
	    if(errorMethod==null)
		errorMethod = logger.getClass().getMethod("error", new Class[]{Object.class, Throwable.class});
	    errorMethod.invoke(logger, new Object[]{string, t});
	}
	private Method logMethod;
	public synchronized void log(int level, String msg) throws Exception {
	    if(logMethod==null)
		logMethod = logger.getClass().getMethod("log", new Class[]{priority, Object.class});
	    switch(level) {
	    case 1: logMethod.invoke(logger, new Object[]{fatal, msg}); break;
	    case 2: logMethod.invoke(logger, new Object[]{error, msg}); break;
	    case 3: logMethod.invoke(logger, new Object[]{info, msg}); break;
	    case 4: logMethod.invoke(logger, new Object[]{debug, msg}); break;
	    default: logMethod.invoke(logger, new Object[]{warn, msg}); break;
	    }
	}
    }
    protected void init() throws Exception {
	Class clazz = loader.forName("org.apache.log4j.BasicConfigurator");
	Method method = clazz.getMethod("configure", new Class[]{});
	method.invoke(clazz, new Object[]{});
	logger = new LoggerProxy();      
    }
    /**
     * Create a new log4j logger using the default appender.
     * @see php.java.bridge.Util#setLogger(ILogger)
     */
    protected Log4jLogger() {
        loader = new SimpleJavaBridgeClassLoader();
    }
    /**
     * Create a new log4j logger using the default appender.
     * @see php.java.bridge.Util#setLogger(ILogger)
     */
    public static Log4jLogger createLog4jLogger() throws Exception {
       Log4jLogger logger = new Log4jLogger();
       logger.init();
       return logger;
    }
    public void printStackTrace(Throwable t) {
	try {
	    logger.error("JavaBridge exception ", t);
	} catch (Exception e) {
	    e.printStackTrace();
	    throw new RuntimeException(e);
	}
    }
    public void log(int level, String msg) {
	try {
	    logger.log(level, msg);
	} catch (Exception e) {
	    e.printStackTrace();
	    throw new RuntimeException(e);
	}
    }
    public void warn(String msg) {
	try {
	    logger.log(-1, msg);
	} catch (Exception e) {
	    e.printStackTrace();
	    throw new RuntimeException(e);
	}
    }
}
