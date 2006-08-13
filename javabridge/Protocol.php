<?php /*-*- mode: php; tab-width:4 -*-*/

/* javabridge_Client.php -- PHP/Java Bridge protocol implementation

   Copyright (C) 2006 Jost Boekemeier

This file is part of the PHP/Java Bridge.

This file ("the library") is free software; you can redistribute it
and/or modify it under the terms of the GNU General Public License as
published by the Free Software Foundation; either version 2, or (at
your option) any later version.

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

class javabridge_SocketHandler {
  var $protocol, $sock;

  function javabridge_SocketHandler($protocol, $sock) {
	$this->protocol = $protocol;
	$this->sock = $sock;
  }
  function write($data) {
	fwrite($this->sock, $data);
  }
  function read($size) {
	return fread($this->sock, $size);
  }
  function redirect() {}

  function overrideRecirect() {
	$this->protocol->handler = createHttpHandler();
  }

  function getSession() {
	die("not implemented: use java_session() as the first statement in a script");
  }
}
class javabridge_HttpHandler extends javabridge_SocketHandler {
  var $headers;
  var $RECV_SIZE;
  var $context;
  var $redirect;
  
  function javabridge_HttpHandler($protocol, $sock) {
	$this->protocol = $protocol;
	$this->sock = $sock;
	$this->RECV_SIZE = $protocol->client->RECV_SIZE;
  }
  function getCookies() {
	$str="";
	$first=true;
	foreach($_COOKIE as $k => $v) {
	  $str = $str . ($first ? "Cookie: $k=$v":"; $k=$v");
	  $first=false;
	}
	if(!$first) $str = $str . '\r\n';
	return $str;
  }
  function getContextFromCgiEnvironment() {
	$ctx = (array_key_exists('HTTP_X_JAVABRIDGE_CONTEXT', $_SERVER)
			?$_SERVER['HTTP_X_JAVABRIDGE_CONTEXT']
			:(array_key_exists('X_JAVABRIDGE_CONTEXT', $_SERVER)
			  ?$_SERVER['X_JAVABRIDGE_CONTEXT']
			  :null));
  }
  function getContext() {
	$ctx = $this->getContextFromCgiEnvironment();
	$context = "";
	if($ctx) {
	  sprintf($context, "X_JAVABRIDGE_CONTEXT: %s\r\n", $ctx);
	}
	return $context;
  }
  function getSession() {
	$this->redirect = "X_JAVABRIDGE_REDIRECT: 2\r\n";
  }
  function getWebapp() {
	// from createHttpHandler
	$context = $this->protocol->webContext;
	if(isset($context)) return $context;

	// guess
	return (array_key_exists('PHP_SELF', $_SERVER) &&
			array_key_exists('HTTP_HOST', $_SERVER)) 
	  ?$_SERVER['PHP_SELF']
	  :null;
  }
  function write($data) {
	$compatibility = chr(0101); //hex numbers + id
	$this->headers = null;
	$sock = $this->sock;
	$len = 1 + strlen($data);
	$webapp = $this->getWebApp();
	$cookies = $this->getCookies();
	$context = $this->getContext();
	$redirect = $this->redirect;
	if(!$webapp) $webapp = "/JavaBridge/JavaBridge.phpjavabridge";

	fputs($sock, "PUT ${webapp} HTTP/1.0\r\n");
	fputs($sock, "Host: localhost\r\n");
	fputs($sock, "Content-Length: " . $len . "\r\n");
	if($context) fputs($sock, $context);
	if($cookies) fputs($sock, $cookies);
	if($redirect) fputs($sock, $redirect);
	fputs($sock, "\r\n");
	fwrite($sock, "${compatibility}${data}");
  }
  function setCookie($path, $key, $val) {
	$path=trim($path);

	$webapp = $this->getWebApp(); if(!$webapp) $path=null;

	if($path[0]!='/') $path='/'.$path;
	setcookie($key, $val, 0, $path);
  }
  function parseHeaders() {
	$this->headers = array();
	while ($str = trim(fgets($this->sock, $this->RECV_SIZE))) {
	  if($str[0]=='X') {
		if(!strncasecmp("X_JAVABRIDGE_CONTEXT_DEFAULT", $str, 28)) {
		  $this->headers["kontext"]=substr($str, 30);
		} else if(!strncasecmp("X_JAVABRIDGE_REDIRECT", $str, 21)) {
		  $this->headers["redirect"]=substr($str, 23);
		} else if(!strncasecmp("X_JAVABRIDGE_CONTEXT", $str, 20)) {
		  $this->headers["context"]=substr($str, 22);
		}
	  } else if($str[0]=='S') {	// Set-Cookie:
		if(!strncasecmp("SET-COOKIE", $str, 10)) {
		  $str=substr($str, 12);
		  $ar = explode(";", $str);
		  $cookie = explode("=",$ar[0]);
		  $path = "";
		  if(isset($ar[1])) $p=explode("=", $ar[1]);
		  if(isset($p)) $path=$p[1];
		  $this->setCookie($cookie[0], $cookie[1], $path);
		}
	  }
	}
  }
  function read($size) {
	if(is_null($this->headers)) $this->parseHeaders();
	$data = "";
	while(!feof($this->sock)) {
	  $data .= fread($this->sock, $size);
	}
	return $data;
  }
  function overrideRedirect() {}
  function redirect() {
	if(!isset($this->protocol->socketHandler)) {
	  $port = $this->headers["redirect"];
	  $context = $this->headers["context"];
	  $len = strlen($context);
	  $len0 = chr(0xFF);
	  $len1 = chr($len&0xFF); $len>>=8;
	  $len2 = chr($len&0xFF);
	  $sock = fsockopen("127.0.0.1", $port, $errno, $errstr, 30);
	  if (!$sock) die("$errstr ($errno)\n");
	  $this->protocol->socketHandler=new javabridge_SocketHandler($this->protocol,$sock);
	  $this->protocol->write("\077${len0}${len1}${len2}${context}");
	}
	fclose($this->sock);
	$this->protocol->handler = $this->protocol->socketHandler;
  }
}
class javabridge_Protocol {
  var $send;
  var $client;
  var $webContext;
  var $serverName;

  function getOverrideHosts() {
	return 
	  (array_key_exists('HTTP_X_JAVABRIDGE_OVERRIDE_HOSTS_REDIRECT', $_SERVER)
	   ?$_SERVER['HTTP_X_JAVABRIDGE_OVERRIDE_HOSTS_REDIRECT']
	   :(array_key_exists('X_JAVABRIDGE_OVERRIDE_HOSTS_REDIRECT', $_SERVER)
		 ?$_SERVER['X_JAVABRIDGE_OVERRIDE_HOSTS_REDIRECT']
		 :null));
  }
  function createHttpHandler() {
	$host = "127.0.0.1";
	$port = "8080";

	$overrideHosts = $this->getOverrideHosts();
	if($overrideHosts) {
	  $ar = split(":|//", $overrideHosts);
	  $host = $ar[0];
	  $port = $ar[1];
	  $this->webContext = $ar[3];
	}
	$this->serverName = "http://$host:$port";
	$sock = fsockopen($host, $port, $errno, $errstr, 30);
	if (!$sock) die("$errstr ($errno)\n");
	return new javabridge_HttpHandler($this, $sock);
  }
  function javabridge_Protocol ($client) {
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
	if(sizeof($this->send)>=$this->client->SEND_SIZE) {
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
  
  function createObjectBegin($name, $createInstance, $result) {
	$this->write(sprintf("<C v=\"%s\" p=\"%s\" i=\"%x\">", $name, $createInstance, $result));
  }
  function createObjectEnd() {
    $this->write("</C>");
    $this->finish();
  }
  function invokeBegin($object, $method, $property, $result) {
 	$this->write(sprintf("<I v=\"%x\" m=\"%s\" p=\"%s\" i=\"%x\">",$object, $method, $property, $result));
  }
  function invokeEnd() {
    $this->write("</I>");
    $this->finish();
  }
  function resultBegin($result) {
	$this->write(sprintf("<R i=\"%x\">", $result));
  }
  function resultEnd() {
    $this->write("</R>");
	$this->flush();
  }
  function replaceQuote($s) {
    return str_replace(array("\"","&"), array("&quot;", "&amp;"), $s);
  }
  function writeString($name) {
    $this->write(sprintf("<S v=\"%s\"/>",$this->replaceQuote($name)));
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
  function writeDouble($d) {
    $this->write(sprintf("<D v=\"%14e\"/>", $d));
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
      $this->write(sprintf("<E v=\"\" m=\"%s\"/>", $str));
    } else {
      $this->write(sprintf("<E v=\"%x\" m=\"%s\"/>",$object, $str));
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
    $this->write("<P t=\"S\" v=\"%s\">", $key);
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
}
?>
