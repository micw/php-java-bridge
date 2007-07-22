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
  $client=__javaproxy_Client_getClient();
  return $client->getProperty(0, "lastException");
}
function java_last_exception_clear() {
  $client=__javaproxy_Client_getClient();
  $client->setProperty(0, "lastException", null);
}
function java_values($object) {
  if(!$object instanceof java_JavaType) return $object;
  $client=__javaproxy_Client_getClient();
  return $client->invokeMethod(0, "getValues", array($object));
}
function java_reset() {
  $client=__javaproxy_Client_getClient();
  user_error("Your script has called the privileged procedure \"java_reset()\" which resets the java back-end to its initial state. Therefore all java caches are gone.");
  return $client->invokeMethod(0, "reset", array());
}
function java_inspect($object) {
  if(!$object instanceof java_JavaType) throw new java_IllegalArgumentException($object);
  $client=__javaproxy_Client_getClient();
  return $client->invokeMethod(0, "inspect", array($object));
}
function java_set_file_encoding($enc) {
  $client=__javaproxy_Client_getClient();
  return $client->invokeMethod(0, "setFileEncoding", array($enc));
}
function java_instanceof($ob, $clazz) {
  if(!$ob instanceof java_JavaType) throw new java_IllegalArgumentException($ob);
  if(!$clazz instanceof java_JavaType) throw new java_IllegalArgumentException($clazz);
  $client=__javaproxy_Client_getClient();
  return $client->invokeMethod(0, "instanceOf", array($ob, $clazz));
}
function java_cast($object, $type) { 
    if(!$object instanceof java_JavaType) throw new java_IllegalArgumentException($object);
	return $object->__cast($type); 
}
function java_require($arg) {
  $client=__javaproxy_Client_getClient();
  return $client->invokeMethod(0, "updateJarLibraryPath", 
						array($arg, ini_get("extension_dir")));
}
function java_session_array($args) {
  $client=__javaproxy_Client_getClient();
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
  $client=__javaproxy_Client_getClient();
  return $client->getServerName();
}
function java_context() {
  $client=__javaproxy_Client_getClient();
  return $client->getContext();
}
function java_closure_array($args) {
  if(isset($args[2]) && ((!($args[2] instanceof java_JavaType))&&!is_array($args[2])))
	throw new java_IllegalArgumentException($args[2]);

  $client=__javaproxy_Client_getClient();
  $args[0] = isset($args[0]) ? $client->globalRef->add($args[0]) : 0;

  /* The following is identical to 
   return $client->invokeMethod(0, "makeClosure", $args); 
   except that the ref (args[0]) must be an unsigned value */
  $client->protocol->invokeBegin(0, "makeClosure", "5", "6");
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
  if(JAVA_CACHE_ENABLED) return false;
  $client = __javaproxy_Client_getClient();
  if($client->isAsync()) 
	throw new java_RuntimeException("begin_document not allowed while in stream mode");
  $rc = $client->invokeMethod(0, "beginDocument", array());
  $client->setAsyncHandler();
  return $rc;
}
function java_end_document() {
  if(JAVA_CACHE_ENABLED) return false;
  $client = __javaproxy_Client_getClient();
  if(!$client->isAsync()) 
	throw new java_RuntimeException("end_document not allowed when not in stream mode");
  $client->setDefaultHandler();
  return $client->invokeMethod(0, "endDocument", array());
}

class java_JavaProxy {
  var $__serialID, $__java;
  var $__signature;
  var $__client;
  function java_JavaProxy($java, $signature){ 
	$this->__java=$java;
	$this->__signature=$signature;
	$this->__client = __javaproxy_Client_getClient();
  }
  function __cast($type) {
	return $this->__client->cast($this, $type);
  }
  function __sleep() {
	$lifetime = (int)ini_get("session.gc_maxlifetime");
	if(!isset($lifetime)) $lifetime = 1440;
	$args = array($this, $lifetime);
	$this->__serialID = $this->__client->invokeMethod(0, "serialize", $args);
	if(JAVA_DEBUG) echo "proxy sleep called for $this->__java, $this->__signature\n";
    return array("__serialID");
  }
  function __wakeup() {
	$lifetime = (int)ini_get("session.gc_maxlifetime");
	if(!isset($lifetime)) $lifetime = 1440;
	$args = array($this->__serialID, $lifetime);
	if(JAVA_DEBUG) echo "proxy wakeup called for $this->__java, $this->__signature\n";
	$this->__client = __javaproxy_Client_getClient();
    $this->__java = $this->__client->invokeMethod(0, "deserialize", $args);
  }
  function __destruct() { 
	if(isset($this->__client)) 
	  $this->__client->unref($this->__java);
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
    return $this->__client->invokeMethod(0,"ObjectToString",array($this));
  }
}

class java_objectIterator implements Iterator {
  var $proxy;
  var $__java, $client;
  var $phpMap; // must keep a reference otherwise it will be gc'ed.
  var $hasNext;

  function java_ObjectIterator($javaProxy) {
	$this->proxy = $javaProxy;
	$this->client = __javaproxy_Client_getClient();
  }
  function rewind() {
	$proxy = array($this->proxy);
	$this->phpMap = 
	  $phpMap = $this->client->invokeMethod(0, "getPhpMap", $proxy);
	$this->__java = $phpMap->__java;
  }
  function valid() {
	if(isset($this->hasNext)) return $this->hasNext;
	return $this->hasNext =
	  $this->client->invokeMethod($this->__java, "hasMore", array());
  }
  function next() {
	return $this->hasNext = 
	  $this->client->invokeMethod($this->__java, "moveForward", array());
  }
  function key() {
	return 
	  $this->client->invokeMethod($this->__java, "currentKey", array());
  }
  function current() {
	return 
	  $this->client->invokeMethod($this->__java, "currentData", array());
  }
}
class java_IteratorProxy extends java_JavaProxy implements IteratorAggregate {
  function java_IteratorProxy($java, $signature) {
	parent::java_JavaProxy($java, $signature);
  }
  function getIterator() {
	return new java_ObjectIterator($this);
  }
}
class java_ArrayProxy extends java_IteratorProxy implements ArrayAccess {
  
  function java_ArrayProxy($java, $signature) {
	parent::java_JavaProxy($java, $signature);
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
  function java_ExceptionProxy($java, $signature){ 
	parent::java_JavaProxy($java, $signature);
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
abstract class java_AbstractJava implements IteratorAggregate,ArrayAccess,java_JavaType {
  var $__delegate;

  var $__serialID;

  var $__factory;
  var $__java, $__signature;

  function __createDelegate() {
	$proxy = $this->__delegate = 
	  $this->__factory->create($this->__java, $this->__signature);
	$this->__java = $proxy->__java;
	$this->__signature = $proxy->__signature;
  }
  function __cast($type) {
	if(!isset($this->__delegate)) $this->__createDelegate();
	return $this->__delegate->__cast($type);
  }
  function __sleep() {
	if(!isset($this->__delegate)) $this->__createDelegate();
	$rc = $this->__delegate->__sleep();
	$this->__serialID = $this->__delegate->__serialID;
	return array("__delegate");
  }
  function __wakeup() {
	if(!isset($this->__delegate)) $this->__createDelegate();
	$this->__delegate->__wakeup();
	$this->__java = $this->__delegate->__java;
	$this->__client = $this->__delegate->__client;
  }
  function __get($key) { 
 	if(!isset($this->__delegate)) $this->__createDelegate();
	return $this->__delegate->__get($key);
  }
  function __set($key, $val) {
 	if(!isset($this->__delegate)) $this->__createDelegate();
	$this->__delegate->__set($key, $val);
  }
  function __call($method, $args) { 
	if(!isset($this->__delegate)) $this->__createDelegate();
    return $this->__delegate->__call($method, $args);
  }
  function __toString() {
	if(!isset($this->__delegate)) $this->__createDelegate();
    return $this->__delegate->__toString();
  }

  // The following functions are for backward compatibility
  function getIterator() {
	if(!isset($this->__delegate)) $this->__createDelegate();
	if(func_num_args()==0) return $this->__delegate->getIterator();
	$args = func_get_args(); return $this->__call("getIterator", $args);
  }
  function offsetExists($idx) {
	if(!isset($this->__delegate)) $this->__createDelegate();
	if(func_num_args()==1) return $this->__delegate->offsetExists($idx);
	$args = func_get_args(); return $this->__call("offsetExists", $args);
  }
  function offsetGet($idx) {
	if(!isset($this->__delegate)) $this->__createDelegate();
	if(func_num_args()==1) return $this->__delegate->offsetGet($idx);
	$args = func_get_args(); return $this->__call("offsetGet", $args);
  }
  function offsetSet($idx, $val) {
	if(!isset($this->__delegate)) $this->__createDelegate();
	if(func_num_args()==2) return $this->__delegate->offsetSet($idx, $val);
	$args = func_get_args(); return $this->__call("offsetSet", $args);
  }
  function offsetUnset($idx) {
	if(!isset($this->__delegate)) $this->__createDelegate();
	if(func_num_args()==1) return $this->__delegate->offsetUnset($idx);
	$args = func_get_args(); return $this->__call("offsetUnset", $args);
  }
}

class Java extends java_AbstractJava {
  var $__client;

  function Java() {
	$client = $this->__client = __javaproxy_Client_getClient();
	
	$args = func_get_args();
	$name = array_shift($args);

	// compatibility with the C implementation
	if(is_array($name)) {$args = $name; $name = array_shift($args);}

    $sig="&{$this->__signature}@{$name}";
	$len = count($args);
	$args2 = array();
	for($i=0; $i<$len; $i++) {
      switch(gettype($val = $args[$i])) {
      case 'boolean': array_push($args2, $val); $sig.='@b'; break; 
      case 'integer': array_push($args2, $val); $sig.='@i'; break; 
      case 'double': array_push($args2, $val); $sig.='@d'; break; 
      case 'string': array_push($args2, htmlspecialchars($val, ENT_COMPAT)); $sig.='@s'; break; 
      case 'array':$sig="~INVALID"; break; 
      case 'object':
		if($val instanceof java_JavaType) {
		  array_push($args2, $val->__java);
		  $sig.="@o{$val->__signature}"; 
		}
		else {
		  $sig="~INVALID";
		}
		break;
      case 'resource': array_push($args2, $val); $sig.='@r'; break; 
      case 'NULL': array_push($args2, $val); $sig.='@N'; break; 
      case 'unknown type': array_push($args2, $val); $sig.='@u'; break;
	  default: throw new java_IllegalArgumentException($val);
      }
    }

    if(JAVA_CACHE_ENABLED && array_key_exists($sig, $client->methodCache)) {
	  if(JAVA_DEBUG) { echo "cache hit for new Java: $sig\n"; }
      $cacheEntry = &$client->methodCache[$sig];
	  $client->sendBuffer.= $client->preparedToSendBuffer;
	  $client->preparedToSendBuffer=vsprintf($cacheEntry->fmt, $args2);

	  if(JAVA_DEBUG) {
		print_r($args2);
		echo "set prepared to send buffer: $client->preparedToSendBuffer, $cacheEntry->fmt, for key: $sig\n";
	  }
 	  $this->__java = ++$client->asyncCtx;
	  if(JAVA_DEBUG) {echo "setresult from new Java cache: object:"; echo sprintf("%x", $client->asyncCtx); echo "\n";}
	  $this->__factory = $cacheEntry->factory;
  	  $this->__signature = $cacheEntry->signature;
	} else {
	  if(JAVA_DEBUG) { echo "cache miss for new Java: $sig\n"; }
      $client->currentCacheKey = $sig;
	  $delegate = $this->__delegate = $client->createObject($name, $args);
	  $this->__java = $delegate->__java;
	  $this->__signature = $delegate->__signature;
	}
  }
  function __destruct() {
	if(!isset($this->__client)) return;
	$client = $this->__client;

	$preparedToSendBuffer = &$client->preparedToSendBuffer;
	$asyncCtx = &$client->asyncCtx;

	if($preparedToSendBuffer && ($asyncCtx==$this->__java)) {
	  $preparedToSendBuffer[6]="3";
	  if(JAVA_DEBUG) {
		echo "cancel result proxy creation:"; echo $this->__java; echo " {$client->preparedToSendBuffer}"; echo "\n";
	  }
	  $client->sendBuffer.=$preparedToSendBuffer;
	  $preparedToSendBuffer = null;
	  --$asyncCtx;
	} else {
	  if(!$this->__delegate) { // write unref ourselfs if we don't have a delegate yet
		if(JAVA_DEBUG) {
		  echo "unref java:"; echo $this->__java; echo "\n";
		}
		$client->unref($this->__java);
	  }
	}	
  }
  function __call($method, $args) { 
	$client = $this->__client;

    $sig="@{$this->__signature}@$method";
	$len = count($args);
	$args2=array($this->__java);
	for($i=0; $i<$len; $i++) {
      switch(gettype($val = $args[$i])) {
      case 'boolean': array_push($args2, $val); $sig.='@b'; break; 
      case 'integer': array_push($args2, $val); $sig.='@i'; break; 
      case 'double': array_push($args2, $val); $sig.='@d'; break; 
      case 'string': array_push($args2, htmlspecialchars($val, ENT_COMPAT)); $sig.='@s'; break; 
      case 'array':$sig="~INVALID"; break; 
      case 'object':
		if($val instanceof java_JavaType) {
		  array_push($args2, $val->__java);
		  $sig.="@o{$val->__signature}"; 
		}
		else {
		  $sig="~INVALID";
		}
		break;
      case 'resource': array_push($args2, $val); $sig.='@r'; break; 
      case 'NULL': array_push($args2, $val); $sig.='@N'; break; 
      case 'unknown type': array_push($args2, $val); $sig.='@u'; break; 
	  default: throw new java_IllegalArgumentException($val);
      }
    }

    if(JAVA_CACHE_ENABLED && array_key_exists($sig, $client->methodCache)) {
	  if(JAVA_DEBUG) { echo "cache hit for __call: $sig\n"; }
      $cacheEntry = &$client->methodCache[$sig];
	  $client->sendBuffer.=$client->preparedToSendBuffer;
	  $client->preparedToSendBuffer=vsprintf($cacheEntry->fmt, $args2);
	  if(JAVA_DEBUG) {
		print_r($args2);
		echo "set prepared to send buffer: {$client->preparedToSendBuffer}, {$cacheEntry->fmt}\n";
	  }
	  if($cacheEntry->resultVoid) {
		return null;
	  } else {
		$result = clone($client->cachedJavaPrototype);
		$result->__factory = $cacheEntry->factory;
		$result->__java = ++$client->asyncCtx;
		if(JAVA_DEBUG) {echo "setresult from __call cache: object:"; echo sprintf("%x", $client->asyncCtx); echo "\n";}
		$result->__signature = $cacheEntry->signature;
		return $result;
	  }
    } else {
	  if(JAVA_DEBUG) { echo "cache miss for __call: $sig\n"; }
      $client->currentCacheKey = $sig;
      $retval = parent::__call($method, $args);
      return $retval;
    }
  }
}

class java_InternalJava extends Java {
  function java_InternalJava($proxy) {
	$this->__delegate = $proxy;
	$this->__java = $proxy->__java;
	$this->__signature = $proxy->__signature;
	$this->__client = $proxy->__client;
  }
}

class java_class extends Java {
  function java_class() {
	$this->__client = __javaproxy_Client_getClient();

	$args = func_get_args();
	$name = array_shift($args);

	// compatibility with the C implementation
	if(is_array($name)) { $args = $name; $name = array_shift($args); }

	$delegate = $this->__delegate = $this->__client->referenceObject($name, $args);

	$this->__java = $delegate->__java;
	$this->__signature = $delegate->__signature;
  }
}
class JavaClass extends java_class{}
/**
 * A decorator pattern which overrides all magic methods.
 */
class java_exception extends Exception implements java_JavaType {
  var $__serialID, $__java, $__client;
  var $__delegate;
  var $__signature;
  function java_exception() {
	$this->__client = __javaproxy_Client_getClient();

	$args = func_get_args();
	$name = array_shift($args);

	// compatibility with the C implementation
	if(is_array($name)) { $args = $name; $name = array_shift($args); }

	$delegate = $this->__delegate = $this->__client->createObject($name, $args);

	$this->__java = $delegate->__java;
	$this->__signature = $delegate->__signature;
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
	$this->__client = $this->__delegate->__client;
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
	$this->__signature = $proxy->__signature;
	$this->__client = $proxy->__client;
  }
}

class java_JavaProxyProxy extends Java {
  function java_JavaProxyProxy($client) {
	$this->__client = $client;
  }
}
?>
