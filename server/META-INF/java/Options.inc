<?php /*-*- mode: php; tab-width:4 -*-*/

/*
 * Copyright (C) 2003-2007 Jost Boekemeier.
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


/**
 * Helper function: Extract the URL from base and
 * and set host, port and servlet accordingly.
 * For example when the user has called:
 * require_once("http://localhost:8080/JavaBridge/java/Java.inc");
 * the JAVA_HOSTS is set to localhost:8080 and
 * JAVA_SERVLET to /JavaBridge/JavaBridge.phpjavabridge.
 */
function defineHostFromInitialQuery($java_base) {
  if($java_base!="java/") {
	$url = parse_url($java_base);
	if(isset($url["scheme"]) && ($url["scheme"]=="http")) {
	  $host = $url["host"];
	  $port = $url["port"];
	  $path = $url["path"];
	  define ("JAVA_HOSTS", "$host:$port");
	  $dir = dirname($path);
	  define ("JAVA_SERVLET", "$dir/JavaBridge.phpjavabridge"); // On ;; On or User
	  return true;
	}
  }
  return false;
}

// The version number of this pure PHP implementation

define ("JAVA_PEAR_VERSION", "4.2.1");


// Deploy JavaBridge.war and re-start the servlet engine or the
// application server. The following settings direct PHP to the 
// java virtual machine:
if(!defined("JAVA_HOSTS")) {
  if(!defineHostFromInitialQuery($JAVA_BASE)) {
	define("JAVA_HOSTS", "127.0.0.1:8080"); // host1:port1;host2:port2;...
	define("JAVA_SERVLET", "On"); // On ;; On or User
  }
 } else {
  if(!defined("JAVA_SERVLET")) 
	define("JAVA_SERVLET", "On"); // On ;; On or User
 }
// The request log level between 0 (log off) and 4 (log debug). The
// default request log level is initialized with the value from to the
// Java system property "php.java.bridge.default_log_level".  The
// servlet's init-param: servlet_log_level (see WEB-INF/web.xml)
// overrides this value. The default level is 2.

if(!defined("JAVA_LOG_LEVEL"))
  define ("JAVA_LOG_LEVEL", null); // integer between 0 and 4


// May we use named pipes instead of local TCP sockets?  Set this to
// the directory in which the named pipes should be created.
// Note that pipes are a little bit slower than persistent TCP
// sockets. But they are more secure.
// Example: define("JAVA_PIPE_DIR", "/tmp");
// Default is to use /dev/shm on Linux, false otherwise.
if(!defined("JAVA_PIPE_DIR"))
  define("JAVA_PIPE_DIR", file_exists("/dev/shm") ? "/dev/shm" 
		 : file_exists("/tmp" ? "/tmp" : null ));

// debug mode
if(!defined("JAVA_DEBUG")) 
  define("JAVA_DEBUG", false);

// cache needs Proxy and ProxyException interfaces
if(!defined("JAVA_CACHE_ENABLED")) 
  define("JAVA_CACHE_ENABLED", false);

