<?php /*-*- mode: php; tab-width:4 -*-*/

/* javabridge_Proxy.php -- contains the main interface

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

require_once("javabridge/Client.php");
  
function __javaproxy_Client_getClient() {
    static $client;
    if(!isset($client)) {
      $client=new javabridge_Client();
	}
	return $client;
}
//FIXME: obsolete
function __javaproxy__getId() {
  static $id=1;
  return $id++;
}
function javabridge_values($object) {
  $client = __javaproxy_Client_getClient();
  $client->invokeMethod(0, "getValues", array($object), 0);
  return $client->getResult();
}
function javabridge_inspect($object) {
  $client = __javaproxy_Client_getClient();
  $client->invokeMethod(0, "inspect", array($object), 0);
  return $client->getResult();
}
function javabridge_set_file_encoding($enc) {
  $client = __javaproxy_Client_getClient();
  $client->invokeMethod(0, "setFileEncoding", array($enc), 0);
  return $client->getResult();
}
function javabridge_instanceof($ob, $clazz) {
  $client = __javaproxy_Client_getClient();
  $client->invokeMethod(0, "instanceOf", array($ob, $clazz), 0);
  return $client->getResult();
}
function javabridge_require($arg) {
  $client = __javaproxy_Client_getClient();
  $client->invokeMethod(0, "updateJarLibraryPath", 
						array($arg, ini_get("extension_dir")), 0);
  return $client->getResult();
}
function javabridge_session($args) {
  $client = __javaproxy_Client_getClient();
  if(!isset($args[1])) $args[1]=false;
  if(!isset($args[2])) {
	$sesion_max_lifetime=ini_get("session.gc_maxlifetime");
	if(is_null($session_max_lifetime)) $session_max_lifetime=1440;
	$args[2] = $session_max_lifetime;
  }
  return $client->getSession($args);
}
function javabridge_servername() {
  $client = __javaproxy_Client_getClient();
  return $client->getServerName();
}
function javabridge_context() {
  $client = __javaproxy_Client_getClient();
  return $client->getContext();
}
function javabridge_closure($args) {
  $client = __javaproxy_Client_getClient();
  if(isset($args[0])) {
	$args[0]=$client->globalRef->add($args[0]);
  }
  return $client->invokeMethod(0, "makeClosure", $args, 0);
}
function javabridge_begin_document() {
  $client = __javaproxy_Client_getClient();
  $rc = $client->invokeMethod(0, "beginDocument", array(), 0);
  $client->setAsyncHandler();
  return $rc;
}
function javabridge_end_document() {
  $client = __javaproxy_Client_getClient();
  $client->setDefaultHandler();
  return $client->invokeMethod(0, "endDocument", array(), 0);
}

class javabridge_JavaProxy {
  var $__id, $__java, $__client;

  function javabridge_JavaProxy(){ 
	$client = $this->__client = __javaproxy_Client_getClient();
    $this->__id=__javaproxy__getId();
	$args = func_get_args();
	$name = array_shift($args);
	$this->__java=$client->createObject($name, $args, true, $this->__id);
  }
  function __cast() {
	die("not implemented");
  }
  function __destruct() { 
	$this->__client->unref($this);
  }
  function __get($key) { 
    return $this->__client->getProperty($this->__java, $key, $this->__id);
  }
  function __put($key, $val) {
    $this->__client->setProperty($this->__java, $key, $val, $this->__id);
  }
  function __call($method, $args) { 
    return $this->__client->invokeMethod($this->__java, $method, $args, $this->__id);
  }
  function __toString() {
    return $this->__client->invokeMethod(0,"ObjectToString",array($this), $this->__id);
  }
}
class javabridge_InternalJavaProxy extends javabridge_JavaProxy {
  function javabridge_InternalJavaProxy($java){ 
	$this->__client = __javaproxy_Client_getClient();
    $this->__id=__javaproxy__getId();
	$this->__java=$java;
  }
}
class javabridge_JavaProxyClass extends javabridge_JavaProxy {
    function javabridge_JavaProxyClass(){ 
    $client = $this->__client = __javaproxy_Client_getClient();
    $this->__id = $this->__id=__javaproxy__getId();
    $args = func_get_args();
    $name = array_shift($args);
    $client->createObject($name, $args, false, $this->__id);
    $this->__java=$client->getResult();
  }
}
?>
