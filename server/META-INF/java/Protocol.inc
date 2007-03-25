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

require_once ("${JAVA_BASE}/Options.inc");
require_once ("${JAVA_BASE}/Client.inc");

class java_SocketChannel {
  var $peer, $protocol;
  function java_SocketChannel($protocol, $host, $channel) {
  $this->protocol = $protocol;
	$this->peer = pfsockopen($host, $channel, $errno, $errstr, 30);
	if (!$this->peer) throw new java_RuntimeException("Could not connect to socket channel: $errstr ($errno)\n");
	stream_set_timeout($this->peer, -1);
  }
  function fwrite($data) {
	fwrite($this->peer, $data);
  }
  function fread($size) {
	return fread($this->peer, $size);
  }
  function __destruct() {
	// required by protocol
	$this->protocol->flush();
	fwrite($this->peer, "<F p=\"A\" />");
	fread($this->peer, 10); // <F p="A"/>
  }
}
class java_EmptyPipeChannel {
  function open() {
	throw new java_RuntimeException("protocol error: socket channel names must not start with a slash");
  }
  function getName() {
	return null;
  }
}
class java_PipeChannel extends java_EmptyPipeChannel {
  var $peer, $peerr, $peerr_desc, $name;
  var $fifo, $fifor;
  var $iname, $oname;

  function java_PipeChannel($name) {
	$this->name = $name;
	$this->iname = $this->name . ".i";
	$this->fifor = posix_mkfifo($this->iname, 0666);
	$this->oname = $this->name . ".o";
	$this->fifo = posix_mkfifo($this->oname, 0666);
  }
  function open($handler) {
	$this->peerr = fopen($this->iname, "r");
	$this->peerr_desc = array($this->peerr);
	stream_set_blocking($this->peerr, false);
	$this->peer = fopen($this->oname, "w");
	unlink($this->iname);
	unlink($this->oname);
	unlink($this->name);
	return $this;
  }
  function fwrite($data) {
	fwrite($this->peer, $data);
  }
  function fread($size) {
	stream_select($this->peerr_desc, $out=NULL, $ex=NULL, 30);
	return fread($this->peerr, $size);
  }
  function getName() {
	return $this->name;
  }
}

class java_SocketHandler {
  var $protocol, $channel;
  var $handler; // the original HTTP handler

  function getChannel($protocol, $handler, $channelName) {
	if($channelName[0]=='/') return $handler->channel->open($handler);
	return new java_SocketChannel($protocol, $handler->host, $channelName);
  }
  function java_SocketHandler($protocol, $handler, $channelName) {
	$this->handler = $handler;
	$this->channel = $this->getChannel($protocol, $handler, $channelName);
	$this->protocol = $protocol;
  }
  function write($data) {
	$this->channel->fwrite($data);
  }
  function read($size) {
	return $this->channel->fread($size);
  }
  function redirect() {}

  function overrideRedirect() {
	$this->protocol->flush();
	$this->protocol->handler = new java_OverrideRedirectHandler($this->handler);
  }

  function getSession() {
	throw new java_RuntimeException("getSession cannot be called w/o overrideRedirect");
  }
}
class java_HttpHandler extends java_SocketHandler {
  var $headers;
  var $redirect;
  
  var $context, $ssl, $port; // used by reopen (overrideRedirect)
  var $host; // used when creating a socket channel lazily. the port# is passed via X_JAVABRIDGE_CHANNEL

  var $socket; // we write to the socket directly and keep the pipe channel for later use
  
  function createPipeChannel($host, $pipe_dir) {
	if(!is_null($pipe_dir) && ($host == "127.0.0.1" || (substr($host,0,9) == "localhost")))
	  return new java_PipeChannel(tempnam($pipe_dir, ".php_java_bridge"));
	return new java_EmptyPipeChannel();
  }
  function close() {
    if($this->headers['connection_close'])
      fclose($this->socket);
    else
      fflush($this->socket);
  }
  function open() {
	$socket = pfsockopen("{$this->ssl}{$this->host}", $this->port, $errno, $errstr, 30);
	if (!$socket) throw new java_RuntimeException("Could not connect to the J2EE server {$this->ssl}{$this->host}:{$this->port}. Please start it, for example with the command: \"java -jar JavaBridge.jar SERVLET:8080 3 JavaBridge.log\" or, if the back end has been compiled to native code, with \"modules/java SERVLET:8080 3 JavaBridge.log\". Error message: $errstr ($errno)\n");
	return $socket;
  }
  function java_HttpHandler($protocol, $ssl, $host, $port) {
	$this->protocol = $protocol;
	$this->ssl = $ssl;
	$this->host = $host;
	$this->port = $port;
	$this->channel = $this->createPipeChannel($host, $protocol->client->RUNTIME['PIPE_DIR']);
	$this->socket = $this->open();
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
	$ctx = (array_key_exists('HTTP_X_JAVABRIDGE_CONTEXT', $_SERVER)
			?$_SERVER['HTTP_X_JAVABRIDGE_CONTEXT']
			:(array_key_exists('X_JAVABRIDGE_CONTEXT', $_SERVER)
			  ?$_SERVER['X_JAVABRIDGE_CONTEXT']
			  :null));
	return $ctx;
  }
  function getChannel() {
	$name = $this->channel->getName();
	return !is_null($name) ? sprintf("X_JAVABRIDGE_CHANNEL: %s\r\n", $name) : null;
  }
  function getContext() {
	$ctx = $this->getContextFromCgiEnvironment();
	$context = "";
	if($ctx) {
	  $context = sprintf("X_JAVABRIDGE_CONTEXT: %s\r\n", $ctx);
	}
	return $context;
  }
  function getSession() {
	$this->redirect = "X_JAVABRIDGE_REDIRECT: 2\r\n";
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
	  ? $_SERVER['PHP_SELF']
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

	$compatibility = $this->protocol->client->RUNTIME["PARSER"]=="NATIVE"
	  ? chr(0103)
	  : $compatibility = chr(0100);
	$this->protocol->client->RUNTIME["COMPATIBILITY"]=$compatibility;
	if(is_int(JAVA_LOG_LEVEL)) {
	  $compatibility |= 128 | (7 & JAVA_LOG_LEVEL)<<2;
	}

	$this->headers = null;
	$socket = $this->socket;
	$len = 2 + strlen($data);
	$webapp = $this->getWebApp();
	$cookies = $this->getCookies();
	$channel = $this->getChannel();
	$context = $this->getContext();
	$redirect = $this->redirect;
	$res = "PUT ";
	$res .= $webapp;
	$res .= " HTTP/1.1\r\n";
	$res .= "Host: localhost\r\n";
	$res .= "Content-Length: "; $res .= $len; $res .= "\r\n";
	$res .= $context;
	$res .= $cookies;
	$res .= $redirect;
	if(!is_null($channel)) $res .= $channel;
	$res .= "\r\n";
	$res .= chr(127);
	$res .= $compatibility;
	$res .= $data;
	fwrite($socket, $res); fflush($socket);
  }
  function doSetCookie($key, $val, $path) {
	$path=trim($path);

	$webapp = $this->getWebAppInternal(); if(!$webapp) $path="/";
	setcookie($key, $val, 0, $path);
  }
  function parseHeaders() {
	$this->headers = array();
	while ($str = trim(fgets($this->socket, java_Client::RECV_SIZE))) {
	  if($str[0]=='X') {
		if(!strncasecmp("X_JAVABRIDGE_CONTEXT_DEFAULT", $str, 28)) {
		  $this->headers["kontext"]=trim(substr($str, 29));
		} else if(!strncasecmp("X_JAVABRIDGE_REDIRECT", $str, 21)) {
		  $this->headers["redirect"]=trim(substr($str, 22));
		} else if(!strncasecmp("X_JAVABRIDGE_CONTEXT", $str, 20)) {
		  $this->headers["context"]=trim(substr($str, 21));
		}
	  } else if($str[0]=='S') {	// Set-Cookie:
		if(!strncasecmp("SET-COOKIE", $str, 10)) {
		  $str=substr($str, 12);
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
		}
		if(!strncasecmp("CONNECTION", $str, 10)) {
		  $this->headers["connection_close"]=!strncasecmp("CLOSE", trim(substr($str, 11)), 5);
		}
	  }
	}
  }
  function read($size) {
 	if(is_null($this->headers)) $this->parseHeaders();
	$data = fread($this->socket, $this->headers['content_length']);
	return $data;
  }
  function overrideRedirect() {}
  function redirect() {
	if(!isset($this->protocol->socketHandler)) {
	  $hostVec = java_Protocol::getHost();
	  $host = $hostVec[0];
	  $channelName = $this->headers["redirect"];
	  $context = $this->headers["context"];
	  $len = strlen($context);
	  $len0 = chr(0xFF);
	  $len1 = chr($len&0xFF); $len>>=8;
	  $len2 = chr($len&0xFF);
	  $this->protocol->socketHandler=new java_SocketHandler($this->protocol, $this, $channelName);
	  $this->protocol->write("\077${len0}${len1}${len2}${context}");
	  $this->context = sprintf("X_JAVABRIDGE_CONTEXT: %s\r\n", $context);
	}
	$this->close();
	$this->protocol->handler = $this->protocol->socketHandler;
  }
}
class java_OverrideRedirectHandler extends java_HttpHandler {
  function java_OverrideRedirectHandler($handler) {
	$this->protocol = $handler->protocol;
	$this->ssl = $handler->ssl;
	$this->host = $handler->host;
	$this->port = $handler->port;
	$this->channel = $handler->channel;
	$this->context = $handler->context;
	$this->socket = $handler->open();
  }
  function overrideRedirect() {
	// cannot happen
	throw new java_RuntimeException("protocol error: overrideRedirect called for a override redirect connection");
  }
  function redirect() {
	$this->protocol->handler = $this->protocol->socketHandler;
	$this->close();
  }				 
  function getSession() {
	$this->redirect = "X_JAVABRIDGE_REDIRECT: 1\r\n";
  }
  function getContext() {
	return $this->context;
  }
}
class java_Protocol {
  var $send;
  var $client;
  var $webContext;
  var $serverName;

  function getOverrideHosts() {
      if(array_key_exists('X_JAVABRIDGE_OVERRIDE_HOSTS', $_ENV)) {
          $override = $_ENV['X_JAVABRIDGE_OVERRIDE_HOSTS'];
          if(!is_null($override) && $override!='/') return $override;
	  }
	  // fcgi: override for redirect
	  return 
		(array_key_exists('HTTP_X_JAVABRIDGE_OVERRIDE_HOSTS_REDIRECT', $_SERVER)
		 ?$_SERVER['HTTP_X_JAVABRIDGE_OVERRIDE_HOSTS_REDIRECT']
		 :(array_key_exists('X_JAVABRIDGE_OVERRIDE_HOSTS_REDIRECT', $_SERVER)
		   ?$_SERVER['X_JAVABRIDGE_OVERRIDE_HOSTS_REDIRECT']
		   :null));
  }
  static function getHost() {
	static $host;
	if(!isset($host)) {
	  $hosts = explode(";", JAVA_HOSTS);
	  $host = explode(":", $hosts[0]); // TODO: check host list
	}
	return $host;
  }
  function createHttpHandler() {
	$hostVec = java_Protocol::getHost();
	$host = $hostVec[0];
	$port = $hostVec[1];

	$overrideHosts = $this->getOverrideHosts();
	$ssl = "";
	if($overrideHosts) {
	  // handle "s:127.0.0.1:8080//JavaBridge/test.phpjavabridge" 
	  // or "s:127.0.0.1:8080" 
	  // or "/" 
	  // or ""
	  $ar = split(":|//", $overrideHosts);
	  $ssl              = (isset($ar[0]) && ($ar[0] == 's')) ? "ssl://" : "";
	  $host             = $ar[1];
	  $port             = $ar[2];
	  if(isset($ar[3])) $this->webContext = "/".$ar[3];
	}
	$this->serverName = "$host:$port";
	return new java_HttpHandler($this, $ssl, $host, $port);
  }
  function java_Protocol ($client) {
    $this->client = $client;
	$this->handler = $this->createHttpHandler();
  }

  function redirect() {
	$this->handler->redirect();
  }
  function overrideRedirect() {
	$this->handler->overrideRedirect();
  }

  function read($size) {
	return $this->handler->read($size);
  }

  function sendData() {
	$this->handler->write($this->send);
    $this->send=null;
  }
  function sendAsyncData() {
	if(strlen($this->send)>=java_client::SEND_SIZE*3/4) {
	  $this->handler->write($this->send);
	  $this->send=null;
	}
  }
  function flush() {
	$this->client->sendData();
  }
  function handle() {
    $this->client->handleRequests();
  }
  function write($data) {
    $this->send.=$data;
  }
  function finish() {
    $this->flush();
    $this->handle();
	$this->redirect();
  }
  
  function createObjectBegin($name, $createInstance) {
	$this->write(sprintf("<C v=\"%s\" p=\"%s\">", $name, $createInstance));
  }
  function createObjectEnd() {
    $this->write("</C>");
    $this->finish();
  }
  function invokeBegin($object, $method, $property) {
 	$this->write(sprintf("<I v=\"%x\" m=\"%s\" p=\"%s\">",$object, $method, $property));
  }
  function invokeEnd() {
    $this->write("</I>");
    $this->finish();
  }
  function resultBegin() {
	$this->write("<R>");
  }
  function resultEnd() {
    $this->write("</R>");
	$this->flush();
  }
  function writeString($name) {
    $this->write(sprintf("<S v=\"%s\"/>",htmlspecialchars($name, ENT_COMPAT)));
  }
  function writeBoolean($boolean) {
    $c=$boolean?"T":"F";
    $this->write(sprintf("<B v=\"%s\"/>",$c));
  }
  function writeLong($l) {
    if($l<0) {
      $this->write(sprintf("<L v=\"%x\" p=\"A\"/>",-$l));
    } else {
      $this->write(sprintf("<L v=\"%x\" p=\"O\"/>",$l));
    }
  }
  function writeULong($l) {
	$this->write(sprintf("<L v=\"%x\" p=\"O\"/>",$l));
  }
  function writeDouble($d) {
    $this->write(sprintf("<D v=\"%.14e\"/>", $d));
  }
  function writeObject($object) {
    if(is_null($object)) {
      $this->write("<O v=\"\"/>");
    } else {
      $this->write(sprintf("<O v=\"%x\"/>", $object));
    }
  }
  function writeException($object, $str) {
    if(is_null($object)) {
      $this->write(sprintf("<E v=\"\" m=\"%s\"/>", htmlspecialchars($str, ENT_COMPAT)));
    } else {
      $this->write(sprintf("<E v=\"%x\" m=\"%s\"/>",$object, htmlspecialchars($str, ENT_COMPAT)));
    }
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
    $this->write(sprintf("<U v=\"%x\"/>", $object));
  }

  function getSession($args) {
	return $this->handler->getSession($args);
  }
  function getServerName() {
	return $this->serverName;
  }
}
?>
