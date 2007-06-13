<?php /*-*- mode: php; tab-width:4 -*-*/

  /* java_Proxy.php -- contains the main interface

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

require_once("${JAVA_BASE}/Client.inc");

/* implemented by JavaException and Java */
interface java_JavaType {};

function __javaproxy_Client_getClient() {
  static $client;
  if(!isset($client)) {
	$client=new java_Client();
  }
  return $client;
}
function java_last_exception_get() {
  $client = __javaproxy_Client_getClient();
  return $client->getProperty(0, "lastException");
}
function java_last_exception_clear() {
  $client = __javaproxy_Client_getClient();
  $client->setProperty(0, "lastException", null);
}
function java_values($object) {
  if(!$object instanceof java_JavaType) return $object;
  $client = __javaproxy_Client_getClient();
  if($client->isAsync()) 
	throw new java_RuntimeException("values not allowed while in stream mode");
  return $client->invokeMethod(0, "getValues", array($object));
}
function java_reset() {
  $client = __javaproxy_Client_getClient();
  user_error("Your script has called the privileged procedure \"java_reset()\" which resets the java back-end to its initial state. Therefore all java caches are gone.");
  return $client->invokeMethod(0, "reset", array());
}
function java_inspect($object) {
  if(!$object instanceof java_JavaType) throw new java_IllegalArgumentException($object);
  $client = __javaproxy_Client_getClient();
  if($client->isAsync()) 
	throw new java_RuntimeException("inspect not allowed while in stream mode");
  return $client->invokeMethod(0, "inspect", array($object));
}
function java_set_file_encoding($enc) {
  $client = __javaproxy_Client_getClient();
  return $client->invokeMethod(0, "setFileEncoding", array($enc));
}
function java_instanceof($ob, $clazz) {
  if(!$ob instanceof java_JavaType) throw new java_IllegalArgumentException($ob);
  if(!$clazz instanceof java_JavaType) throw new java_IllegalArgumentException($clazz);
  $client = __javaproxy_Client_getClient();
  if($client->isAsync()) 
	throw new java_RuntimeException("instanceof not allowed while in stream mode");
  return $client->invokeMethod(0, "instanceOf", array($ob, $clazz));
}
function java_cast($object, $type) { 
    if(!$object instanceof java_JavaType) throw new java_IllegalArgumentException($object);
	return $object->__cast($type); 
}
function java_require($arg) {
  $client = __javaproxy_Client_getClient();
  return $client->invokeMethod(0, "updateJarLibraryPath", 
						array($arg, ini_get("extension_dir")));
}
function java_session_array($args) {
  $client = __javaproxy_Client_getClient();
  if($client->isAsync()) 
	throw new java_RuntimeException("get_session not allowed while in stream mode");
  if(!isset($args[0])) $args[0]=null;
  if(!isset($args[1])) $args[1]=false;
  if(!isset($args[2])) {
	$sesion_max_lifetime=(int)ini_get("session.gc_maxlifetime");
	if(!isset($session_max_lifetime)) $session_max_lifetime=1440;
	$args[2] = $session_max_lifetime;
  }
  return $client->getSession($args);
}
function java_session() {
  return java_session_array(func_get_args());
}
function java_server_name() {
  $client = __javaproxy_Client_getClient();
  if($client->isAsync()) 
	throw new java_RuntimeException("get_server_name not allowed while in stream mode");
  return $client->getServerName();
}
function java_context() {
  $client = __javaproxy_Client_getClient();
  return $client->getContext();
}
function java_closure_array($args) {
  if(isset($args[2]) && ((!($args[2] instanceof java_JavaType))&&!is_array($args[2])))
	throw new java_IllegalArgumentException($args[2]);

  $client = __javaproxy_Client_getClient();
  if($client->isAsync()) 
	throw new java_RuntimeException("closure not allowed while in stream mode");
  $args[0] = isset($args[0]) ? $client->globalRef->add($args[0]) : 0;

  /* The following is identical to 
   return $client->invokeMethod(0, "makeClosure", $args); 
   except that the ref (args[0]) must be an unsigned value */
  $client->protocol->invokeBegin(0, "makeClosure", "I");
  $n = count($args);
  $client->protocol->writeULong($args[0]); // proper PHP "long" -> Java 64 bit value conversion
  for($i=1; $i<$n; $i++) {
	$client->writeArg($args[$i]);
  }
  $client->protocol->invokeEnd();
  $val = $client->getResult();
  return $val;
}
function java_closure() {
  return java_closure_array(func_get_args());
}
function java_begin_document() {
  $client = __javaproxy_Client_getClient();
  if($client->isAsync()) 
	throw new java_RuntimeException("begin_document not allowed while in stream mode");
  $rc = $client->invokeMethod(0, "beginDocument", array());
  $client->setAsyncHandler();
  return $rc;
}
function java_end_document() {
  $client = __javaproxy_Client_getClient();
  if(!$client->isAsync()) 
	throw new java_RuntimeException("end_document not allowed when not in stream mode");
  $client->setDefaultHandler();
  return $client->invokeMethod(0, "endDocument", array());
}

function java_create($args, $instance) {
  $client = __javaproxy_Client_getClient();
  $name = array_shift($args);

  // compatibility with the C implementation
  if(is_array($name)) return java_create($name, $instance);

  $rc = $client->createObject($name, $args, $instance);
  return $rc;
}
class java_JavaProxy {
  var $__serialID, $__java, $__client;

  function java_JavaProxy($java){ 
	$this->__client = __javaproxy_Client_getClient();
	$this->__java=$java;
  }
  function __cast($type) {
	return $this->__client->cast($this, $type);
  }
  function __sleep() {
	if($this->__client->isAsync()) 
	  throw new java_RuntimeException("__sleep not allowed when not in stream mode");
	$lifetime = (int)ini_get("session.gc_maxlifetime");
	if(!isset($lifetime)) $lifetime = 1440;
	$args = array($this, $lifetime);
	$this->__serialID = $this->__client->invokeMethod(0, "serialize", $args);
    return array("__serialID");
  }
  function __wakeup() {
	$lifetime = (int)ini_get("session.gc_maxlifetime");
	if(!isset($lifetime)) $lifetime = 1440;
	if(!isset($this->__client)) $this->__client=__javaproxy_Client_getClient();
	if($this->__client->isAsync()) 
	  throw new java_RuntimeException("__wakeup not allowed when not in stream mode");
	$args = array($this->__serialID, $lifetime);
    $this->__java = $this->__client->invokeMethod(0, "deserialize", $args);
  }
  function __destruct() { 
	if(isset($this->__client)) $this->__client->unref($this->__java);
  }
  function __get($key) { 
    return $this->__client->getProperty($this->__java, $key);
  }
  function __set($key, $val) {
    $this->__client->setProperty($this->__java, $key, $val);
  }
  function __call($method, $args) { 
    return $this->__client->invokeMethod($this->__java, $method, $args);
  }
  function __toString() {
	if($this->__client->isAsync()) 
	  throw new java_RuntimeException("__toString not allowed when not in stream mode");
    return $this->__client->invokeMethod(0,"ObjectToString",array($this));
  }
}
class java_JavaProxyClass extends java_JavaProxy {
  function java_JavaProxyClass($java){ 
	parent::java_JavaProxy($java);
  }
}

class java_objectIterator implements Iterator {
  var $proxy;
  var $__java, $__client;
  var $phpMap; // must keep a reference otherwise it will be gc'ed.
  var $hasNext;

  function java_ObjectIterator($javaProxy) {
	$this->proxy = $javaProxy;
	$this->__client = $javaProxy->__client;

	if($this->__client->isAsync()) 
	  throw new java_RuntimeException('object iterator not allowed while in stream mode. Use $arr=java_values($java_obj); java_begin_document(); foreach($arr as ...) ...; java_end_document(); instead.');
  }
  function rewind() {
	$proxy = array($this->proxy);
	$this->phpMap = 
	  $phpMap = $this->__client->invokeMethod(0, "getPhpMap", $proxy);
	$this->__java = $phpMap->__java;
  }
  function valid() {
	if(isset($this->hasNext)) return $this->hasNext;
	return $this->hasNext =
	  $this->__client->invokeMethod($this->__java, "hasMore", array());
  }
  function next() {
	return $this->hasNext = 
	  $this->__client->invokeMethod($this->__java, "moveForward", array());
  }
  function key() {
	return 
	  $this->__client->invokeMethod($this->__java, "currentKey", array());
  }
  function current() {
	return 
	  $this->__client->invokeMethod($this->__java, "currentData", array());
  }
}
class java_IteratorProxy extends java_JavaProxy implements IteratorAggregate {
  function java_IteratorProxy($java) {
	parent::java_JavaProxy($java);
  }
  function getIterator() {
	return new java_ObjectIterator($this);
  }
}
class java_ArrayProxy extends java_IteratorProxy implements ArrayAccess {
  
  function java_ArrayProxy($java) {
	parent::java_JavaProxy($java);
  }
  function offsetExists($idx) {
	$ar = array($this, $idx);
    return $this->__client->invokeMethod(0,"offsetExists", $ar);
  }  
  function offsetGet($idx) {
	$ar = array($this, $idx);
    return $this->__client->invokeMethod(0,"offsetGet", $ar);
  }
  function offsetSet($idx, $val) {
	$ar = array($this, $idx, $val);
    return $this->__client->invokeMethod(0,"offsetSet", $ar);
  }
  function offsetUnset($idx) {
	$ar = array($this, $idx);
    return $this->__client->invokeMethod(0,"offsetUnset", $ar);
  }
}
class java_ExceptionProxy extends java_JavaProxy {
  function java_ExceptionProxy($java){ 
	parent::java_JavaProxy($java);
  }
  function __toExceptionString($trace) {
	$args = array($this, $trace);
	return $this->__client->invokeMethod(0,"ObjectToString",$args);
  }
}
/**
 * This decorator/bridge overrides all magic methods and delegates to
 * the proxy so that it may handle them or pass them on to the
 * back-end.  The actual implementation of this bridge depends on the
 * back-end response, see PROTOCOL.TXT: "p: char ([A]rray,
 * [C]ollection, [O]bject, [E]xception)". See the getProxy() and
 * create() methods in Client.php and writeObject() and getType() in
 * Response.java.<p>
 *
 * The constructor is an exception. If it is called, the user has
 * already allocated Java, so that $wrap is false and the proxy is
 * returned and set into $__delegate. 
 * @see #java_InternalJava
*/
class Java extends java_JavaProxy implements IteratorAggregate, ArrayAccess, java_JavaType {
  var $__delegate;
  function Java() {
	$delegate = $this->__delegate = java_create(func_get_args(), true);
	$this->__java = $delegate->__java;
	$this->__client = $delegate->__client;
  }
  function __cast($type) {
	return $this->__delegate->__cast($type);
  }
  function __sleep() {
	$rc = $this->__delegate->__sleep();
	$this->__serialID = $this->__delegate->__serialID;
	return array("__delegate");
  }
  function __wakeup() {
	$this->__delegate->__wakeup();
	$this->__java = $this->__delegate->__java;
  }
  function __destruct() { 
	$this->__delegate = null;
	$this->__client = null;
  }
  function __get($key) { 
    return $this->__delegate->__get($key);
  }
  function __set($key, $val) {
    $this->__delegate->__set($key, $val);
  }
  function __call($method, $args) { 
    return $this->__delegate->__call($method, $args);
  }
  function __toString() {
    return $this->__delegate->__toString();
  }

  // The following functions are for backward compatibility
  function getIterator() {
	if(func_num_args()==0) return $this->__delegate->getIterator();
	$args = func_get_args(); return $this->__call("getIterator", $args);
  }
  function offsetExists($idx) {
	if(func_num_args()==1) return $this->__delegate->offsetExists($idx);
	$args = func_get_args(); return $this->__call("offsetExists", $args);
  }
  function offsetGet($idx) {
	if(func_num_args()==1) return $this->__delegate->offsetGet($idx);
	$args = func_get_args(); return $this->__call("offsetGet", $args);
  }
  function offsetSet($idx, $val) {
	if(func_num_args()==2) return $this->__delegate->offsetSet($idx, $val);
	$args = func_get_args(); return $this->__call("offsetSet", $args);
  }
  function offsetUnset($idx) {
	if(func_num_args()==1) return $this->__delegate->offsetUnset($idx);
	$args = func_get_args(); return $this->__call("offsetUnset", $args);
  }
}
class JavaObject extends Java {
  function Java() {
	$delegate = $this->__delegate = java_create(func_get_args(), true);
	$this->__java = $delegate->__java;
	$this->__client = $delegate->__client;
  }
}  
class java_class extends JavaObject {
  function java_class() {
	$delegate = $this->__delegate = java_create(func_get_args(), false);
	$this->__java = $delegate->__java;
	$this->__client = $delegate->__client;
  }
}
class java_InternalJavaObject extends JavaObject {
  function java_InternalJavaObject($proxy) {
	$this->__delegate = $proxy;
	$this->__java = $proxy->__java;
	$this->__client = $proxy->__client;
  }
}
class JavaClass extends java_class{}
class java_InternalJava extends Java {
  function java_InternalJava($proxy) {
	$this->__delegate = $proxy;
	$this->__java = $proxy->__java;
	$this->__client = $proxy->__client;
  }
}
/**
 * A decorator pattern which overrides all magic methods.
 */
class java_exception extends Exception implements java_JavaType {
  var $__serialID, $__java, $__client;
  var $__delegate;
  function java_exception() {
	$delegate = $this->__delegate = java_create(func_get_args(), true);
	$this->__java = $delegate->__java;
	$this->__client = $delegate->__client;
  }
  function __cast($type) {
	return $this->__delegate->__cast($type);
  }
  function __sleep() {
	$rc = $this->__delegate->__sleep();
	$this->__serialID = $this->__delegate->__serialID;
	return $rc;
  }
  function __wakeup() {
	$this->__delegate->__wakeup();
	$this->__java = $this->__delegate->__java;
  }
  function __destruct() { 
	$this->__delegate = null;
	$this->__client = null;
  }
  function __get($key) { 
    return $this->__delegate->__get($key);
  }
  function __set($key, $val) {
    $this->__delegate->__set($key, $val);
  }
  function __call($method, $args) { 
    return $this->__delegate->__call($method, $args);
  }
  function __toString() {
	return $this->__delegate->__toExceptionString($this->getTraceAsString());
  }
}
class JavaException extends java_exception {}
class java_InternalException extends JavaException {
  function java_InternalException($proxy) {
	$this->__delegate = $proxy;
	$this->__java = $proxy->__java;
	$this->__client = $proxy->__client;
  }
}
	
?>
