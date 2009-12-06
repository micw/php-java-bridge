<?php /*-*- mode: php; tab-width:4 -*-*/

  /* java_Protocol.php -- PHP/Java Bridge protocol implementation

  Copyright (C) 2003-2007 Jost Boekemeier

  This file is part of the PHP/Java Bridge.

  The PHP/Java Bridge ("the library") is free software; you can
  redistribute it and/or modify it under the terms of the GNU General
  Public License as published by the Free Software Foundation; either
  version 2, or (at your option) any later version.

  The library is distributed in the hope that it will be useful, but
  WITHOUT ANY WARRANTY; without even the implied warranty of
  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
  General Public License for more details.

  You should have received a copy of the GNU General Public License
  along with the PHP/Java Bridge; see the file COPYING.  If not, write to the
  Free Software Foundation, Inc., 59 Temple Place, Suite 330, Boston, MA
  02111-1307 USA.

  Linking this file statically or dynamically with other modules is
  making a combined work based on this library.  Thus, the terms and
  conditions of the GNU General Public License cover the whole
  combination.

  As a special exception, the copyright holders of this library give you
  permission to link this library with independent modules to produce an
  executable, regardless of the license terms of these independent
  modules, and to copy and distribute the resulting executable under
  terms of your choice, provided that you also meet, for each linked
  independent module, the terms and conditions of the license of that
  module.  An independent module is a module which is not derived from
  or based on this library.  If you modify this library, you may extend
  this exception to your version of the library, but you are not
  obligated to do so.  If you do not wish to do so, delete this
  exception statement from your version. */

require_once (java_get_base()."/Options.inc");
require_once (java_get_base()."/Client.inc");

/**
 * @access private
 */
function java_getHeader($name, $array) {
  if (array_key_exists($name, $array)) return $array[$name];
  $name="HTTP_$name"; // apache uses HTTP_ prefix
  if (array_key_exists($name, $array)) return $array[$name];
  return null;
}

/**
 * @access private
 */
function java_getCompatibilityOption($client) {
  static $compatibility = null;

  if ($compatibility) return $compatibility;

  $compatibility = $client->RUNTIME["PARSER"]=="NATIVE"
    ? (0103-JAVA_PREFER_VALUES)
    : (0100+JAVA_PREFER_VALUES);
  if(is_int(JAVA_LOG_LEVEL)) {
    $compatibility |= 128 | (7 & JAVA_LOG_LEVEL)<<2;
  }
  $compatibility = chr ($compatibility);
  return $compatibility;
}

/**
 * A simple channel, used in phase 1
 *
 * @access private
 */
class java_EmptyPipeChannel {
  protected $handler;
  private $res;

  function java_EmptyPipeChannel($handler) {
    $this->handler = $handler;
  }
  function shutdownBrokenConnection () {}
  function fwrite($data) {
	return $this->handler->fwrite($data);
  }
  function fread($size) {
	return $this->handler->fread($size);
  }

  function getKeepAliveA() {
	return "<F p=\"A\" />";
  }
  function getKeepAliveE() {
	return "<F p=\"E\" />";
  }
  function getKeepAlive() {
	return $this->getKeepAliveE();
  }

  function error() {
	trigger_error("An unchecked exception occured during script execution. Please check the server log files for details.", E_USER_ERROR);
  }
  function checkA() {
	$val = $this->res[6];
	if ($val !='A' && $val !='E') {
	  $this->error();
	}
  }
  function checkE() {
	$val = $this->res[6];
	if ($val !='E') {
	  $this->error();
	}
  }
  /** Used by socket channel (phase 2) */
  function keepAliveS() {
	$this->res = $this->fread(10); // <F p="E"/>
  }
  /** Used by chunked socket channel (phase 2) */
  function keepAliveSC() {
	$this->res = $this->fread(10); // <F p="E"/>
	$this->fwrite(""); // 0\r\n\r\n
	$this->fread(5);   // 0\r\n\r\n
  }

  /** Used by EmptyPipeChannel (phase 1) */
  function keepAliveH() {
	$this->res = $this->handler->read(10); //  HTTP response <F p="E"/>
  }

  function keepAlive() {
	$this->keepAliveH();
	$this->checkE();
  }
}

/**
 * Persistent version of the above
 *
 * @access private
 */
class java_EmptyPipeChannelP extends java_EmptyPipeChannel {
  function getKeepAlive() {return $this->getKeepAliveA();}
  function keepAlive() { $this->keepAliveH(); $this->checkA(); }
}

/**
 * A simple socket channel, used in phase 2 or
 * when connecting to a simple socket listener
 *
 * @access private
 */
abstract class java_SocketChannel extends java_EmptyPipeChannel {
  public $peer, $host;

  function java_SocketChannel($peer, $host) {
	$this->peer = $peer;
	$this->host = $host;
  }
  function fwrite($data) {
	return fwrite($this->peer, $data);
  }
  function fread($size) {
	return fread($this->peer, $size);
  }
  function shutdownBrokenConnection () {
    fclose($this->peer);
  }
}
/**
 * Persistent version of the above
 *
 * @access private
 */
class java_SocketChannelP extends java_SocketChannel {
  function getKeepAlive() {return $this->getKeepAliveA();}
  function keepAlive() { $this->keepAliveS(); $this->checkA(); }
}

/**
 * Chunked version of the socket channel. Used in phase 2.
 * 
 * @access private
 */
class java_ChunkedSocketChannel extends java_SocketChannel {
  function fwrite($data) {
    $len = dechex(strlen($data));
    return fwrite($this->peer, "${len}\r\n${data}\r\n");
  }
  function fread($size) {
  	$length = hexdec(fgets($this->peer, JAVA_RECV_SIZE));
	$data = "";
	while ($length > 0) {
	  $str = fread($this->peer, $length);
	  $length -= strlen($str);
	  $data .= $str;
	}
  	fread($this->peer, 2);
  	return $data;
  }
  function keepAlive() { $this->keepAliveSC(); $this->checkE(); fclose ($this->peer); }
}

/**
 * Named Pipes are used on Linux only to avoid socket-based
 * SecurityEnhancedLinux rules. Used in phase 2.
 *
 * @access private
 */
class java_PipeChannel extends java_EmptyPipeChannel {
  protected $peer, $peerr, $peerr_desc;

  function java_PipeChannel($handler) {
	parent::java_EmptyPipeChannel($handler);

	$this->peerr = fopen($this->handler->iname, "r");
	$this->peerr_desc = array($this->peerr);
	stream_set_blocking($this->peerr, false);
	stream_set_timeout($this->peerr, -1);

	$this->peer = fopen($this->handler->oname, "w");
	$this->handler->unlink ();
	stream_set_timeout($this->peer, -1);
	return $this;
  }
  function fwrite($data) {
      return fwrite($this->peer, $data);
  }
  function fread($size) {
	static $empty = NULL;
	stream_select($this->peerr_desc, $empty, $empty, 1677216);
	return fread($this->peerr, $size);
  }
  function keepAlive() {
	$this->keepAliveS();
	$this->checkE();
  }
  // no need to fclose peer/peerr explicitly as PHP automatically closes 
  // fopen'ed files in rshutdown.
}

/**
 * Used when PHP connects to a non-http socket or in phase 2
 *
 * Always uses a persistent connection to the socket.
 *
 * @access private
 */
class java_SocketHandler {
  public $protocol, $channel;

  function java_SocketHandler($protocol, $channel) {
	$this->protocol = $protocol;
	$this->channel = $channel;
  }
  function write($data) {
      return $this->channel->fwrite($data);
  }
  // fallback for EmptyPipeChannel
  function fwrite($data) {return $this->write($data);}

  function read($size) {
	return $this->channel->fread($size);
  }
  // fallback for EmptyPipeChannel
  function fread($size) {return $this->read($size);}

  function redirect() {}
  function getKeepAlive() {
    return $this->channel->getKeepAlive();
  }
  function keepAlive() {
	$this->channel->keepAlive();
  }
  function dieWithBrokenConnection($msg) {
	unset($this->protocol->client->protocol);
    trigger_error ("${msg}", E_USER_ERROR);
  }
  function shutdownBrokenConnection ($msg) {
    $this->channel->shutdownBrokenConnection();
	$this->dieWithBrokenConnection($msg);
  }
}
/**
 * Used when PHP is running within a servlet environment.
 *
 * It connects to the ContextRunner directly w/o
 * sending a PUT request to the PhpJavaServlet.
 * Must not be used when a SocketContextServer
 * is not available in the back end.
 *
 * @access private
 */
class java_SimpleHttpHandler extends java_SocketHandler {
  public $headers, $cookies;
  
  public $context, $ssl, $port; // used by reopen
  public $host; // used when creating a socket channel lazily. the port# is passed via X_JAVABRIDGE_CHANNEL
  /**
   * Create a SocketChannel which is used to communicate with the SocketContextServer
   */
  function createChannel() {
	$channelName = java_getHeader("X_JAVABRIDGE_REDIRECT", $_SERVER);
	$context = java_getHeader("X_JAVABRIDGE_CONTEXT", $_SERVER);
	$len = strlen($context);
	$len0 = java_getCompatibilityOption($this->protocol->client); // short path S1: no PUT request
	$len1 = chr($len&0xFF); $len>>=8;
	$len2 = chr($len&0xFF);
	// simulate phase 1: PUT request to the back end
	$this->channel = new java_EmptyPipeChannel($this);
	// simulate phase 2 : parse headers and extract comm. channel
	$this->channel = $this->getChannel($channelName);
	// we know the comm. channel is a socket channel, otherwise this class wouldn't be used
	$this->protocol->socketHandler=new java_SocketHandler($this->protocol, $this->channel);
	$this->protocol->write("\177${len0}${len1}${len2}${context}");
	$this->context = sprintf("X_JAVABRIDGE_CONTEXT: %s\r\n", $context);
	
	$this->protocol->handler = $this->protocol->socketHandler;
	$this->protocol->handler->write($this->protocol->client->sendBuffer)
		or $this->protocol->handler->shutdownBrokenConnection("Broken local connection handle");
	$this->protocol->client->sendBuffer=null;
	$this->protocol->handler->read(1)
		or $this->protocol->handler->shutdownBrokenConnection("Broken local connection handle");
  }

  function java_SimpleHttpHandler($protocol, $ssl, $host, $port) {
	$this->cookies = array();
	$this->protocol = $protocol;
	$this->ssl = $ssl;
	$this->host = $host;
	$this->port = $port;
	$this->createChannel();
  }
  function getCookies() {
	$str="";
	$first=true;
	foreach($_COOKIE as $k => $v) {
	  $str .= ($first ? "Cookie: $k=$v":"; $k=$v");
	  $first=false;
	}
	if(!$first) $str .= "\r\n";
	return $str;
  }
  function getContextFromCgiEnvironment() {
	$ctx = java_getHeader('X_JAVABRIDGE_CONTEXT', $_SERVER);
	return $ctx;
  }
  function getChannelName() {
	return null;
  }
  function getContext() {
	static $context = null;
	if($context) return $context;

	$ctx = $this->getContextFromCgiEnvironment();
	$context = "";
	if($ctx) {
	  $context = sprintf("X_JAVABRIDGE_CONTEXT: %s\r\n", $ctx);
	}
	return $context;
  }
  function getWebAppInternal() {
	// from createHttpHandler
	$context = $this->protocol->webContext;
	if(isset($context)) return $context;

	/* Coerce a http://xyz.com/kontext/foo.php request to the back
	   end: http://xyz.com:{java_hosts[0]}/kontext/foo.php.  For
	   example if we receive a request:
	   http://localhost/sessionSharing.php and java.servlet is On and
	   java.hosts is "127.0.0.1:8080" the code would connect to the
	   back end:
	   http://127.0.0.1:8080/sessionSharing.phpjavabridge. This
	   creates a cookie with PATH value "/".  If java_servlet is User
	   the request http://localhost/myContext/sessionSharing.php the
	   code would connect to
	   http://127.0.0.1/myContext/sessionSharing.phpjavabridge and a
	   cookie with a PATH value "/myContext" would be created.
	*/
	return (JAVA_SERVLET == "User" &&
			array_key_exists('PHP_SELF', $_SERVER) &&
			array_key_exists('HTTP_HOST', $_SERVER))
	  ? $_SERVER['PHP_SELF']."javabridge"
	  : null;
  }
  function getWebApp() {
	$context = $this->getWebAppInternal();
	if(is_null($context)) $context = JAVA_SERVLET;
	if(is_null($context) || $context[0]!="/") 
	  $context = "/JavaBridge/JavaBridge.phpjavabridge";
	return $context;
  }
  function write($data) {
	return $this->protocol->socketHandler->write($data);
  }
  function doSetCookie($key, $val, $path) {
	$path=trim($path);

	$webapp = $this->getWebAppInternal(); if(!$webapp) $path="/";
	setcookie($key, $val, 0, $path);
  }
  function read($size) {
	return $this->protocol->socketHandler->read($size);
  }
  /**
   * Unlink a pipe channel, if any. 
   */
  function unlink() {}
  /**
   * Create a PipeChannel, if necessary
  */
  function getChannel($channelName) {
  	$errstr = null; $errno = null;
	if($channelName[0]=='/') return new java_PipeChannel($this);
    $this->unlink(); // remove the created pipes as early as possible
	$peer = pfsockopen($this->host, $channelName, $errno, $errstr, 20);
	if (!$peer) throw new java_IllegalStateException("No ContextServer for {$this->host}:{$channelName}. Error: $errstr ($errno)\n");
	stream_set_timeout($peer, -1);
	return new java_SocketChannelP($peer, $this->host);
  }
  /**
   * unlink the PipeChannel created by getChannel()
   */
  function keepAlive() {
	parent::keepAlive();
	$this->unlink();
  }

  /**
   * Prepare for phase 2: Open a SocketChannel and redirect further
   * communication to this channel.
   */
  function redirect() {}
}

/**
 * Used when PHP connects to a servlet environment using a PUT request.
 * It uses a chunked, non-persistent stream in phase 1 and will
 * redirect to a simple java_SocketHandler for phase 2.
 *
 * @access private
 */
class java_HttpTunnelHandler extends java_SimpleHttpHandler {
  // the initial socket connection, may redirect to
  // a pipe- or socket connection later on
  public $socket;

  // the proposed pipe connection channel sent to the server
  // if it accepts it via X_JAVABRIDGE_REDIRECT, it is opened
  // in function redirect()
  public $name, $iname, $fifor, $oname, $fifo;

  /**
   * Unlink a pipe channel, if any. 
   */
  function unlink() {
	if (isset($this->name)) {
	  unlink($this->iname);
	  unlink($this->oname);
	  unlink($this->name);
	  unset ($this->name);
	}
  }

  function getChannelName() {
	return isset($this->name) ? sprintf("X_JAVABRIDGE_CHANNEL: %s\r\n", $this->name) : null;
  }

  function createSimpleChannel () {
	$this->channel = new java_EmptyPipeChannel($this);
  }
  function createChannel() {
	$pipe_dir = $this->protocol->client->RUNTIME['PIPE_DIR'];
	if($pipe_dir && ($this->host == "127.0.0.1" || (substr($this->host,0,9) == "localhost"))) {
	  $this->name = tempnam($pipe_dir, ".php_java_bridge");
	  $this->iname = $this->name . ".i";
	  $mask = umask(0);
	  $this->fifor = posix_mkfifo($this->iname, 0666);
	  $this->oname = $this->name . ".o";
	  $this->fifo = posix_mkfifo($this->oname, 0666);
	  umask($mask);
	}
	$this->createSimpleChannel();
  }
  function shutdownBrokenConnection ($msg) {
    fclose($this->socket);
    $this->dieWithBrokenConnection($msg);
  }
  function checkSocket($socket, &$errno, &$errstr) {
  	if (!$socket) 
	  throw new java_ConnectException("Could not connect to the JEE server {$this->ssl}{$this->host}:{$this->port}. Please start it. Or define('JAVA_HOSTS', 9267); define('JAVA_SERVLET', false); before including 'Java.inc' and try again. Error message: $errstr ($errno)\n");
  }
  function open() {
  	$errno = null; $errstr = null;
	$socket = fsockopen("{$this->ssl}{$this->host}", $this->port, $errno, $errstr, 20);
	$this->socket_desc = array($socket);
	$this->checkSocket($socket, $errno, $errstr);
	
	stream_set_timeout($socket, -1);
	$this->socket = $socket;
  }
  // fallback for EmptyPipeChannel
  function fread($size) {
  	$length = hexdec(fgets($this->socket, JAVA_RECV_SIZE));
	$data = "";
	while ($length > 0) {
	  $str = fread($this->socket, $length);
	  $length -= strlen($str);
	  $data .= $str;
	}
  	fread($this->socket, 2);
  	return $data;
  }
  // fallback for EmptyPipeChannel
  function fwrite($data) {
    $len = dechex(strlen($data));
    return fwrite($this->socket, "${len}\r\n${data}\r\n");
  }
  function close() {
	fwrite($this->socket, "0\r\n\r\n");
	fgets($this->socket, JAVA_RECV_SIZE); fread($this->socket, 2);
    fclose($this->socket);
  }
  function java_HttpTunnelHandler($protocol, $ssl, $host, $port) {
	parent::java_SimpleHttpHandler($protocol, $ssl, $host, $port);
	$this->open();
  }
  function read($size) {
 	if(is_null($this->headers)) $this->parseHeaders();
	if (isset($this->headers["http_error"])) {
	   $this->shutdownBrokenConnection(fread($this->socket, $this->headers['content_length']), $this->headers["http_error"]);
	}
	return $this->fread(JAVA_RECV_SIZE);
  }
  function getBodyFor ($compat, $data) {
	$len = dechex(2+strlen($data));
	return "Cache-Control: no-cache\r\nPragma: no-cache\r\nTransfer-Encoding: chunked\r\n\r\n${len}\r\n\177${compat}${data}\r\n";
  }
  function write($data) {
	$compat = java_getCompatibilityOption($this->protocol->client);
	$this->headers = null;
	$socket = $this->socket;
	$webapp = $this->getWebApp();
	$cookies = $this->getCookies();
	$channel = $this->getChannelName();
	$context = $this->getContext();
	$res = "PUT ";
	$res .= $webapp;
	$res .= " HTTP/1.1\r\n"; 
	$res .= "Host: {$this->host}:{$this->port}\r\n";
	$res .= $context;
	$res .= $cookies;
	if(!is_null($channel)) $res .= $channel;
	$res .= $this->getBodyFor($compat, $data);
	
	$count = fwrite($socket, $res) or $this->shutdownBrokenConnection("Broken connection handle");
	fflush($socket)                or $this->shutdownBrokenConnection("Broken connection handle");
	
	return $count;
  }
     
  function parseHeaders() {
	$this->headers = array();

	$line = trim(fgets($this->socket, JAVA_RECV_SIZE));
	$ar = explode (" ", $line);

	$code = ((int)$ar[1]);
	if  ($code != 200) $this->headers["http_error"] = $code;

	while (($str = trim(fgets($this->socket, JAVA_RECV_SIZE)))) {
	  if($str[0]=='X') {
		if(!strncasecmp("X_JAVABRIDGE_REDIRECT", $str, 21)) {
		  $this->headers["redirect"]=trim(substr($str, 22));
		} else if(!strncasecmp("X_JAVABRIDGE_CONTEXT", $str, 20)) {
		  $this->headers["context"]=trim(substr($str, 21));
		}
	  } else if($str[0]=='S') {	// Set-Cookie:
		if(!strncasecmp("SET-COOKIE", $str, 10)) {
		  $str=substr($str, 12);
		  $this->cookies[]=$str;

		  $ar = explode(";", $str);
		  $cookie = explode("=",$ar[0]);
		  $path = "";
		  if(isset($ar[1])) $p=explode("=", $ar[1]);
		  if(isset($p)) $path=$p[1];
		  $this->doSetCookie($cookie[0], $cookie[1], $path);
		}
	  } else if($str[0]=='C') { // Content-Length
		if(!strncasecmp("CONTENT-LENGTH", $str, 14)) {
		  $this->headers["content_length"]=trim(substr($str, 15));
		} else if(!strncasecmp("CONNECTION", $str, 10) && !strncasecmp("close", trim(substr($str, 11)), 5)) {
		  $this->headers["connection_close"] = true;
		}
	  } else  if($str[0]=='T') { // Transfer-Encoding
		if(!strncasecmp("TRANSFER-ENCODING", $str, 17) && !strncasecmp("chunked", trim(substr($str, 18)), 7)) {
			$this->headers["transfer_chunked"] = true;
			unset ($this->headers["http_errors"]); // automatically handled by parser
		}
	  }
	}
  }
  function getSimpleChannel() {
    $this->unlink(); // remove the created pipes as early as possible
	return new java_ChunkedSocketChannel($this->socket, $this->protocol, $this->host);
  }

  function redirect() {
    $this->isRedirect = isset($this->headers["redirect"]);
	if ($this->isRedirect)
	  $channelName = $this->headers["redirect"];
	
	$context = $this->headers["context"];
	$len = strlen($context);
	$len0 = chr(0xFF);
	$len1 = chr($len&0xFF); $len>>=8;
	$len2 = chr($len&0xFF);
	if ($this->isRedirect) { 
	  /* Pipe- or SocketContextServer: redirect to ContextRunner */
	  $this->protocol->socketHandler=new java_SocketHandler($this->protocol, $this->getChannel($channelName));
	  $this->protocol->write("\177${len0}${len1}${len2}${context}");
	  $this->context = sprintf("X_JAVABRIDGE_CONTEXT: %s\r\n", $context);

      $this->close ();

	  $this->protocol->handler = $this->protocol->socketHandler;
	  // short path for $this->protocol->flush(): avoid ContextRunner wait timeout
	  $this->protocol->handler->write($this->protocol->client->sendBuffer)
		or $this->protocol->handler->shutdownBrokenConnection("Broken local connection handle");
	  $this->protocol->client->sendBuffer=null;

	  // dummy: avoid ack delay
      $this->protocol->handler->read(1)
		or $this->protocol->handler->shutdownBrokenConnection("Broken local connection handle");
	} else           { 
	   /* No Pipe- or SocketContextServer: keep using the current HTTP connection */
	  $this->protocol->handler = $this->protocol->socketHandler=new java_SocketHandler($this->protocol, $this->getSimpleChannel());
	}
  }
}
/**
 * Used when PHP connects to a servlet environment and the option
 * JAVA_PERSISTENT_SERVLET_CONNECTIONS is on.  It uses a non-chunked
 * persistent stream in phase 1 and will redirect to a simple
 * java_SocketHandler for phase 2.
 *
 * @access private
 */
class java_HttpHandler extends java_HttpTunnelHandler {
  function open() {
  	$errno = null; $errstr = null;
	$socket = pfsockopen("{$this->ssl}{$this->host}", $this->port, $errno, $errstr, 20);
	$this->checkSocket($socket, $errno, $errstr);
	stream_set_timeout($socket, -1);
	$this->socket = $socket;
  }
  function close() {
	if (isset($this->headers["connection_close"]))
	  fclose ($this->socket);
	else
	  fflush($this->socket);
  }
  function parseHeaders() {
	parent::parseHeaders();
	if (!isset($this->headers["redirect"])) {
	  $this->shutdownBrokenConnection ("No ContextServer. define ('JAVA_PERSISTENT_SERVLET_CONNECTIONS', false); or set java.persistent_servlet_connections = Off and try again.<br>\n See files: \"Java.inc\", \"java.ini\". Or, if you run PHP from java, the file \"global.properties\" within JavaBridge.jar within the JavaBridge.war zip file.");
	}
  }
  function read($size) {
 	if(is_null($this->headers)) $this->parseHeaders();
	if (isset($this->headers["http_error"])) {
	  shutdownBrokenConnection(fread($this->socket, $this->headers['content_length']), $this->headers["http_error"]);
	}
	return $this->fread($this->headers['content_length']);
  }
  function fread($size) {
	return fread($this->socket, $size);
  }
  function fwrite($data) {
	return fwrite($this->socket, $data);
  }
  function getBodyFor ($compat, $data) {
	$len = 2+strlen($data);
	return "Content-Length: ${len}\r\nConnection: keep-alive\r\n\r\n\177${compat}${data}";
  }	
  function createSimpleChannel () {
	$this->channel = new java_EmptyPipeChannelP($this);
  }
}

/**
 * @access private
 */
class java_Protocol {
  public $client;
  public $webContext;
  public $serverName;

  function getOverrideHosts() {
      if(array_key_exists('X_JAVABRIDGE_OVERRIDE_HOSTS', $_ENV)) {
          $override = $_ENV['X_JAVABRIDGE_OVERRIDE_HOSTS'];
          if(!is_null($override) && $override!='/') return $override;
	  }
	  // fcgi: override for redirect
	  return 
		java_getHeader('X_JAVABRIDGE_OVERRIDE_HOSTS_REDIRECT', $_SERVER);
  }
  static function getHost() {
	static $host = null;
	if(is_null($host)) {
	  $hosts = explode(";", JAVA_HOSTS);
	  $host = explode(":", $hosts[0]); // TODO: check host list
	}
	return $host;
  }
  function createHttpHandler() {
	$overrideHosts = $this->getOverrideHosts();
	$ssl = "";
	if($overrideHosts) {
	  // handle "s:127.0.0.1:8080//JavaBridge/test.phpjavabridge" 
	  // or "s:127.0.0.1:8080" 
	  // or "/" 
	  // or ""
	 $s=$overrideHosts;
     if((strlen($s)>2) && ($s[1]==':')) {
       if($s[0]=='s')
         $ssl="ssl://";
       $s = substr($s, 2);
     }
     $webCtx = strpos($s, "//");
     if($webCtx)
       $host = substr($s, 0, $webCtx);
     else
       $host = $s;

     $idx = strpos($host, ':');
     if($idx) {
       if($webCtx)
         $port = substr($host, $idx+1, $webCtx);
       else
         $port = substr($host, $idx+1);
       $host = substr($host, 0, $idx);
     } else {
       $port = "8080";
     }
 
     if($webCtx) $webCtx = substr($s, $webCtx+1);
	  $this->webContext = $webCtx;
	} else {
	  $hostVec = java_Protocol::getHost();
	  $host = $hostVec[0];
	  $port = $hostVec[1];
	}

	$this->serverName = "$host:$port";

	// short path S1: no PUT request
	if ((array_key_exists("X_JAVABRIDGE_REDIRECT", $_SERVER)) || 
		(array_key_exists("HTTP_X_JAVABRIDGE_REDIRECT", $_SERVER))) 
	  return new java_SimpleHttpHandler($this, $ssl, $host, $port);

	return JAVA_PERSISTENT_SERVLET_CONNECTIONS ? new java_HttpHandler($this, $ssl, $host, $port) : new java_HttpTunnelHandler($this, $ssl, $host, $port);
  }
  /**
   * connect to a local channel (no servlet engine or app server)
   */
  function createSimpleHandler($name, $again=true) {
	$channelName = $name;
    $errno = null; $errstr = null;
    if(is_numeric($channelName)) {
	  $peer = @pfsockopen($host="127.0.0.1", $channelName, $errno, $errstr, 5);
      /* raise mono connect exception--do not delete this line, it is used when generating Mono.inc from Java.inc */
	} else {
	  $type = $channelName[0];
	  if($type=='@' || $type=='/') {		 // unix domain socket
		if($type=='@') $channelName[0]="\0"; // abstract name space
		$peer = @pfsockopen($host="unix://${channelName}", $channelName = null, $errno, $errstr, 5);
	  } else {					// tcp socket
		list($host, $channelName) = explode(":", $channelName);
		$peer = pfsockopen($host, $channelName, $errno, $errstr, 20);
		if (!$peer) 
		    throw new java_ConnectException("No Java server at $host:$channelName. Error message: $errstr ($errno)");
	  }
	}
    if (!$peer) {

	  if(is_null($channelName) && ($type=='@' || $type=='/')) {
	      // unix domain sockets
		  $java = (ini_get("extension_dir")."/java");
		  if (!file_exists($java)) trigger_error("Could not find $java. Remove java.so and php_java.dll and try again.", E_USER_ERROR);
          $java_cmd = "$java -Dphp.java.bridge.daemon=true LOCAL:$name 0";
      } else {
	  	  $java=file_exists(ini_get("extension_dir")."/JavaBridge.jar")?ini_get("extension_dir")."/JavaBridge.jar":(java_get_base()."/JavaBridge.jar");
		  if (!file_exists($java)) 
		      throw new java_IOException("Could not find $java in ".getcwd().". Download it from http://sf.net/projects/php-java-bridge/files/Binary%20package/php-java-bridge_".JAVA_PEAR_VERSION."/JavaBridge.jar/download and try again.");
	      $java_cmd = "java -Dphp.java.bridge.daemon=true -jar \"${java}\" INET_LOCAL:$channelName 0"; 
      }

	  if (!$again) 
	     throw new java_ConnectException("No Java! Please run it with: $java_cmd. Error message: $errstr ($errno)");

	  system ($java_cmd);
	  return $this->createSimpleHandler($name, false);
    }
    stream_set_timeout($peer, -1);
    $handler = new java_SocketHandler($this, new java_SocketChannelP($peer, $host));

    $compatibility = java_getCompatibilityOption($this->client);
    $this->write("\177$compatibility");
    $this->serverName = "127.0.0.1:$channelName";
    return $handler;
  }
  function java_get_simple_channel() {
	return (JAVA_HOSTS&&(!JAVA_SERVLET||(JAVA_SERVLET=="Off"))) ? JAVA_HOSTS : null;
  }
  function createHandler() {
	if(!java_getHeader('X_JAVABRIDGE_OVERRIDE_HOSTS', $_SERVER)&&
	   ((function_exists("java_get_default_channel")&&($defaultChannel=java_get_default_channel())) ||
		($defaultChannel=$this->java_get_simple_channel())) ) {
      // if bind.c has started a local back end from Apache/IIS
      return $this->createSimpleHandler($defaultChannel);
    } else {
      return $this->createHttpHandler();
    }
  }
  function java_Protocol ($client) {
    $this->client = $client;
	$this->handler = $this->createHandler();
  }

  function redirect() {
	$this->handler->redirect();
  }

  function read($size) {
	return $this->handler->read($size);
  }

  function sendData() {
	$this->handler->write($this->client->sendBuffer);
    $this->client->sendBuffer=null;
  }
  function flush() {
	if(JAVA_DEBUG) {echo "sending::: "; echo $this->client->sendBuffer; echo "\n";}
	$this->sendData();
  }
  function getKeepAlive() {
    return $this->handler->getKeepAlive();
  }
  function keepAlive() {
	$this->handler->keepAlive();
  }
  function handle() {
    $this->client->handleRequests();
  }
  function write($data) {
    $this->client->sendBuffer.=$data;
  }
  function finish() {
    $this->flush();
    $this->handle();
	$this->redirect();
  }
  
  function referenceBegin($name) {
	$this->client->sendBuffer.=$this->client->preparedToSendBuffer;
	if(JAVA_DEBUG) {echo "flushed preparedToSendBuffer: ".$this->client->preparedToSendBuffer."\n";}
	$this->client->preparedToSendBuffer=null;

    $signature=sprintf("<H p=\"1\" v=\"%s\">", $name);
    $this->write($signature);
    $signature[6]="2";
    $this->client->currentArgumentsFormat = $signature;
  }
  function referenceEnd() {
    $this->client->currentArgumentsFormat.=$format="</H>";
    $this->write($format);
    $this->finish();
	$this->client->currentCacheKey=null;
  }
  function createObjectBegin($name) {
	$this->client->sendBuffer.=$this->client->preparedToSendBuffer;
	if(JAVA_DEBUG) {echo "flushed preparedToSendBuffer: ".$this->client->preparedToSendBuffer."\n";}
	$this->client->preparedToSendBuffer=null;

    $signature=sprintf("<K p=\"1\" v=\"%s\">", $name);
    $this->write($signature);
    $signature[6]="2";
    $this->client->currentArgumentsFormat = $signature;
  }
  function createObjectEnd() {
    $this->client->currentArgumentsFormat.=$format="</K>";
    $this->write($format);
    $this->finish();
	$this->client->currentCacheKey=null;
  }
  function propertyAccessBegin($object, $method) {
	$this->client->sendBuffer.=$this->client->preparedToSendBuffer;
	if(JAVA_DEBUG) {echo "flushed preparedToSendBuffer: ".$this->client->preparedToSendBuffer."\n";}
	$this->client->preparedToSendBuffer=null;

    $this->write(sprintf("<G p=\"1\" v=\"%x\" m=\"%s\">", $object, $method));
    $this->client->currentArgumentsFormat="<G p=\"2\" v=\"%x\" m=\"${method}\">";
  }
  function propertyAccessEnd() {
    $this->client->currentArgumentsFormat.=$format="</G>";
    $this->write($format);
    $this->finish();
	$this->client->currentCacheKey=null;
  }
  function invokeBegin($object, $method) {
	$this->client->sendBuffer.=$this->client->preparedToSendBuffer;
	if(JAVA_DEBUG) {echo "flushed preparedToSendBuffer: ".$this->client->preparedToSendBuffer."\n";}
	$this->client->preparedToSendBuffer=null;

    $this->write(sprintf("<Y p=\"1\" v=\"%x\" m=\"%s\">", $object, $method));
    $this->client->currentArgumentsFormat="<Y p=\"2\" v=\"%x\" m=\"${method}\">";
  }
  function invokeEnd() {
    $this->client->currentArgumentsFormat.=$format="</Y>";
    $this->write($format);
    $this->finish();
	$this->client->currentCacheKey=null;
  }
  function resultBegin() {
	$this->client->sendBuffer.=$this->client->preparedToSendBuffer;
	if(JAVA_DEBUG) {echo "flushed preparedToSendBuffer: ".$this->client->preparedToSendBuffer."\n";}
	$this->client->preparedToSendBuffer=null;
	
	$this->write("<R>");
  }
  function resultEnd() {
    $this->client->currentCacheKey=null;
    $this->write("</R>");
	$this->flush();
  }
  function writeString($name) {
    $this->client->currentArgumentsFormat.=$format="<S v=\"%s\"/>";
    $this->write(sprintf($format, htmlspecialchars($name, ENT_COMPAT)));

  }
  function writeBoolean($boolean) {
    $this->client->currentArgumentsFormat.=$format="<T v=\"%s\"/>";
    $this->write(sprintf($format, $boolean));
  }
  function writeLong($l) {
    $this->client->currentArgumentsFormat.="<J v=\"%d\"/>";
    if($l<0) {
      $this->write(sprintf("<L v=\"%x\" p=\"A\"/>",-$l));
    } else {
      $this->write(sprintf("<L v=\"%x\" p=\"O\"/>",$l));
    }
  }
  function writeULong($l) {
    $this->client->currentArgumentsFormat.=$format="<L v=\"%x\" p=\"O\"/>";
	$this->write(sprintf($format,$l));
  }
  function writeDouble($d) {
    $this->client->currentArgumentsFormat.=$format="<D v=\"%.14e\"/>";
    $this->write(sprintf($format, $d));
  }
  function writeObject($object) {
    $this->client->currentArgumentsFormat.=$format="<O v=\"%x\"/>";
	$this->write(sprintf($format, $object));
  }

  /* the following routines are not cached */
  function writeException($object, $str) {
	$this->write(sprintf("<E v=\"%x\" m=\"%s\"/>",$object, htmlspecialchars($str, ENT_COMPAT)));
  }
  function writeCompositeBegin_a() {
    $this->write("<X t=\"A\">");
  }
  function writeCompositeBegin_h() {
    $this->write("<X t=\"H\">");
  }
  function writeCompositeEnd() {
    $this->write("</X>");
  }
  function writePairBegin_s($key) {
    $this->write(sprintf("<P t=\"S\" v=\"%s\">", htmlspecialchars($key, ENT_COMPAT)));
  }
  function writePairBegin_n($key) {
    $this->write(sprintf("<P t=\"N\" v=\"%x\">",$key));
  }
  function writePairBegin() {
    $this->write("<P>");
  }
  function writePairEnd() {
    $this->write("</P>");
  }
  function writeUnref($object) {
	$this->client->sendBuffer.=$this->client->preparedToSendBuffer;
	//echo "clear prepared to send buffer\n";
	$this->client->preparedToSendBuffer=null;
    $this->write(sprintf("<U v=\"%x\"/>", $object));
  }

  function getServerName() {
	return $this->serverName;
  }
}
?>
