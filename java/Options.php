<?php /*-*- mode: php; tab-width:4 -*-*/

/*
 * Copyright (C) 2006 Jost Boekemeier.
 *
 * Permission is hereby granted, free of charge, to any person
 * obtaining a copy of this file (the "Software"), to deal in the
 * Software without restriction, including without limitation the
 * rights to use, copy, modify, merge, publish, distribute,
 * sublicense, and/or sell copies of the Software, and to permit
 * persons to whom the Software is furnished to do so, subject to the
 * following conditions:
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


// The version number of this pure PHP implementation

define ("JAVA_PEAR_VERSION", "0.7.0");


// Deploy JavaBridge.war and re-start the servlet engine or the
// application server. The following settings direct PHP to the 
// java virtual machine:

define ("JAVA_HOSTS", "127.0.0.1:8080"); // host1:port1;host2:port2;...
define ("JAVA_SERVLET", "On"); // On ;; On or User

// The request log level between 0 (log off) and 4 (log debug). The
// default request log level is initialized with the value from to the
// Java system property "php.java.bridge.default_log_level".  The
// servlet's init-param: servlet_log_level (see WEB-INF/web.xml)
// overrides this value. The default level is 2.

define ("JAVA_LOG_LEVEL", null); // integer between 0 and 4

// If you want to use log4j "chainsaw" running on localhost, port 4445
// start log4j viewer, for example with
//
// java -classpath log4j.jar org.apache.log4j.chainsaw.Main 
//
// set the log file to @127.0.0.1:4445. The default "log file" is the
// standard error.

define ("JAVA_LOG_FILE", null); // file or @host:port
