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
import java.util.Enumeration;

/**
 * A logger which uses the log4j default appender or chainsaw, if no log4j.properties exists. Requires that log4j.jar is in the classpath.<br>
 */
public class Log4jLogger extends ChainsawLogger {
    private boolean forceChainsawLogger;
    public void configure (String defaultHost, int defaultPort) throws Exception {
	Class clazz = loader.forName("org.apache.log4j.Category");
	Method method = clazz.getMethod("getRoot", new Class[]{});
	Object root = method.invoke(clazz, new Object[]{});
	method = clazz.getMethod("getAllAppenders", new Class[]{});
	Object o = method.invoke(root, new Object[]{});
	method = Enumeration.class.getMethod("hasMoreElements", new Class[]{});
	o = method.invoke(o, new Object[]{});
	Boolean b = (Boolean) o;
	forceChainsawLogger = !b.booleanValue();
	if(forceChainsawLogger) 
	    super.configure(defaultHost, defaultPort);
    }
    /**
     * Create a new chainsaw logger.
     * @see php.java.bridge.Util#setLogger(ILogger)
     * @throws UnknownHostException If the host does not exist.
     * @throws IOException If chainsaw isn't running.
     */
    public static ChainsawLogger createChainsawLogger() throws Exception {
	ChainsawLogger logger = new Log4jLogger();
	logger.init();
	return logger;
    }
    public String toString() {
	return forceChainsawLogger ? super.toString() : "Log4j logger";
    }
}
