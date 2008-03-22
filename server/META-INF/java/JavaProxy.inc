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

/**
 * implemented by JavaException and Java 
 * @access public
 */
interface java_JavaType {};

/**
 * @access private
 */
function __javaproxy_Client_getClient() {
  static $client;
  if(!isset($client)) {
	$client=new java_Client();
  }
  return $client;
}

/**
 * @deprecated: Use PHP5 try/catch instead
 * @access private
 */
function java_last_exception_get() {
  $client=__javaproxy_Client_getClient();
  return $client->getProperty(0, "lastException");
}
/**
 * @deprecated: Use PHP5 try/catch instead.
 * @access private
 */
function java_last_exception_clear() {
  $client=__javaproxy_Client_getClient();
  $client->setProperty(0, "lastException", null);
}
/**
 * Only for internal use
 * @access private
 */
function java_values_internal($object) {
  if(!$object instanceof java_JavaType) return $object;
  $client=__javaproxy_Client_getClient();
  return $client->invokeMethod(0, "getValues", array($object));
}
/**
 * Evaluates the object and fetches its content, if possible.
 *
 * A java array, Map or Collection object is returned
 * as a php array. An array, Map or Collection proxy is returned as a java array, Map or Collection object, and a null proxy is returned as null. All values of java types for which a primitive php type exists are returned as php values. Everything else is returned unevaluated. Please make sure that the values do not not exceed
 * php's memory limit. Example:
 *
 * 
 * <code>
 * $str = new java("java.lang.String", "hello");
 * echo $str;
 * => [o(String):"hello"]
 * echo java_values($str);
 * => hello
 * $chr = $str->toCharArray();
 * echo $chr;
 * => [o(array_of-C):"[C@1b10d42"]
 * $ar = java_values($chr);
 * print $ar;
 * => Array
 * print $ar[0];
 * => [o(Character):"h"]
 * print java_values($ar[0]);
 * => h
 * </code>
 * 
 * @param $object A java object or type.
 * @access public
 */
function java_values($object) {
  return java_values_internal($object);
}
/**
 * Only for internal use.
 * @access public
 */
function java_reset() {
  $client=__javaproxy_Client_getClient();
  user_error("Your script has called the privileged procedure \"java_reset()\" which resets the java back-end to its initial state. Therefore all java caches are gone.");
  return $client->invokeMethod(0, "reset", array());
}
/**
 * Only for internal use
 * @access private
 */
function java_inspect_internal($object) {
  if(!$object instanceof java_JavaType) throw new java_IllegalArgumentException($object);
  $client=__javaproxy_Client_getClient();
  return $client->invokeMethod(0, "inspect", array($object));
}
/**
 * Returns the contents (public fields, public methods, public
 * classes) of object as a string.
 *
 * Example:
 * <code>
 * echo java_inspect(java_get_context());
 * </code>
 * @param $object A java object or type.
 * @access public
 */
function java_inspect($object) {
  return java_inspect_internal($object);
}
/**
 * Set the java file encoding, for example UTF-8 or ASCII.
 *
 * Needed
 * because php does not support unicode. All string to byte array
 * conversions use this encoding. Example: 
 * <code>
 * java_set_file_encoding("ISO-8859-1");
 * </code>
 *
 * @param $enc A valid file.encoding string. Please see your Java
 * <code>file.encoding</code> documentation for a list of valid
 * encodings.
 * @access public
 */
function java_set_file_encoding($enc) {
  $client=__javaproxy_Client_getClient();
  return $client->invokeMethod(0, "setFileEncoding", array($enc));
}
/**
 * Only for internal use
 * @access private
 */
function java_instanceof_internal($ob, $clazz) {
  if(!$ob instanceof java_JavaType) throw new java_IllegalArgumentException($ob);
  if(!$clazz instanceof java_JavaType) throw new java_IllegalArgumentException($clazz);
  $client=__javaproxy_Client_getClient();
  return $client->invokeMethod(0, "instanceOf", array($ob, $clazz));
}
/**
 * Tests if object is an instance of clazz.
 *
 * Example: 
 * <code>
 * return($o instanceof Java && $c instanceof Java && java_instanceof($o, $c)); 
 * </code>
 * @param $ob A java object
 * @param $clazz A java object or type.
 * @access public
 */
function java_instanceof($ob, $clazz) {
  return java_instanceof_internal($ob, $clazz);
}
/**
 * Only for internal use
 * @access private
 */
function java_cast_internal($object, $type) { 
    if(!$object instanceof java_JavaType) {
      switch($type[0]) {
	case 'S': case 's':
	  return (string)$object;
	case 'B': case 'b':
	  return (boolean)$object;
	case 'L': case 'I': case 'l': case 'i':
	  return (integer)$object;
	case 'D': case 'd': case 'F': case 'f':
	  return (float) $object;
	case 'N': case 'n':
	  return null;
	case 'A': case 'a':
	  return (array)$object;
	case 'O': case 'o':
	  return (object)$object;
      }
    }    
    return $object->__cast($type); 
}
/**
 * Converts the java object obj into a PHP value. 
 *
 * The second argument
 * must be [s]tring, [b]oolean, [i]nteger, [f]loat or [d]ouble,
 * [a]rray, [n]ull or [o]bject (which does nothing).<p> This procedure
 * is for compatibility with the pure PHP implementation, in the C
 * implementation this procedure is called automatically for each type
 * cast or when settype() is called.
 *
 * <br>
 * Example:
 * <code>
 * $str = new java("java.lang.String", "12");
 * echo $str;
 * => [o(String):"12"]
 * $phpString = "$str";
 * echo $phpString;
 * => "12"
 * $phpNumber = (integer)$str;
 * echo $phpNumber;
 * => 12
 * $phpNumber2 = java_cast($str, "integer");
 * echo $phpNumber2;
 * => 12
 * </code>
 * @param $object A java object
 * @param $type A PHP type description, either [Ss]tring, [Bb]oolean, [Ll]ong or [Ii]nteger, [Dd]ouble or [Ff]loat, [Nn]ull, [Aa]rray, [Oo]bject.
 * @access public
 */
function java_cast($object, $type) { 
  return java_cast_internal($object, $type);
}

/**
 * Set the library path.
 *
 * Example: 
 * <code>
 * java_require("foo.jar;bar.jar"); 
 * </code>
 *
 * The .jar files should be stored in /usr/share/java or
 * extension_dir/lib one of its sub-directories or under the PHP
 * include_path/LIBNAME/LIBNAME.jar. However, it is also possible to
 * fetch .jar files from a remote server, for example:
 * <code>
 * java_require("http://php-java-bridge.sf.net/kawa.jar;...");
 * </code>
 *
 * Note that the classloader isolates the loaded libraries: When you
 * call java_require("foo.jar"); java_require("bar.jar"), the classes
 * from foo cannot see the classes loaded from bar. If you get a
 * NoClassDefFound error saying that one of your classes cannot
 * access the library you have loaded, you must reset the back-end to
 * clear the loader cache and load your classes and the library in one
 * java_require() call.
 * @param $arg The list of Java libraries.
 * @access public
 */
function java_require($arg) {
  $client=__javaproxy_Client_getClient();
  return $client->invokeMethod(0, "updateJarLibraryPath", 
							   array($arg, ini_get("extension_dir"), getcwd(), ini_get("include_path")));
}
/**
 * @access private
 */
function java_get_lifetime ()
{
  $session_max_lifetime=ini_get("session.gc_maxlifetime");
  return $session_max_lifetime ? (int)$session_max_lifetime : 1440;
}
  
/**
 * Only for internal use
 * @access private
 */
function java_session_array($args) {
  $client=__javaproxy_Client_getClient();
  if(!isset($args[0])) $args[0]=null;
  if(!isset($args[1])) $args[1]=false;
  if(!isset($args[2])) {
	$args[2] = java_get_lifetime ();
  }
  if(function_exists("session_start")) {
	@session_start();
	$client->RUNTIME['SESSION']=true;
  }
  return $client->getSession($args);
}
/**
 * Return a session handle.
 *
 * When java_session() is called without 
 * arguments, the session is shared with java.
 * Example: 
 * <code>
 * java_get_session()->put("key", new Java("java.lang.Object"));
 * [...]
 * </code>
 * The java components (jsp, servlets) can retrieve the value, for
 * example with:
 * <code>
 * getSession().getAttribute("key");
 * </code>
 *
 * When java_get_session() is called with a session name, the session
 * is not shared with java and no cookies are set. Example:
 * <code>
 * java_get_session("myPublicApplicationStore")->put("key", "value");
 * </code>
 *
 * When java_get_session() is called with a second argument set to true,
 * a new session is allocated, the old session is destroyed if necessary.
 * Example:
 * <code>
 * java_get_session(null, true)->put("key", "val");
 * </code>
 *
 * The optional third argument specifies the default lifetime of the session, it defaults to \code session.gc_maxlifetime \endcode. The value 0 means that the session never times out.
 *
 * @access public
 * @see #java_context()
 */
function java_session() {
  return java_session_array(func_get_args());
}

/**
 * Returns the name of the back-end or null, if the back-end is not running.O
 *
 * Example:
 * <code>
 * $backend = java_get_server_name();
 * if(!$backend) wakeup_administrator("back-end not running");
 * echo "Connected to the back-end: $backend\n";
 * </code>
 * @access public
*/
function java_server_name() {
  $client=__javaproxy_Client_getClient();
  return $client->getServerName();
}

/**
 * Returns the jsr223 script context handle.
 *
 * Example which closes over the current environment and passes it back to java:
 * <code>
 * java_get_context()->call(java_closure()) || die "Script should be called from java";
 * </code>
 *
 * It is possible to access implicit web objects (the session, the
 * application store etc.) from the context. Please see the JSR223
 * documentation for details. Example:
 * <code>
 * java_get_context()->getHttpServletRequest();
 * </code>
 * @access public
 * @see #java_session()
 */
function java_context() {
  $client=__javaproxy_Client_getClient();
  return $client->getContext();
}
/**
 * Only for internal use
 * @access private
 */
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
/**
 * Closes over the php environment and packages it up as a java
 * class. 
 *
 * Example: 
 * <code>
 * function toString() {return "helloWorld";};
 * $object = java_get_closure();
 * echo "Java says that PHP says: $object\n";
 * </code>
 *
 * When a php instance is supplied as an argument, the environment will be used
 * instead. When a string or key/value map is supplied as a second argument,
 * the java procedure names are mapped to the php procedure names. Example:
 * <code>
 * function hello() {return "hello";};
 * echo (string)java_get_closure(null, "hello");
 * </code>
 * 
 * When an array of java interfaces is supplied as a third argument,
 * the environment must implement these interfaces.
 * Example:
 * <code>
 * class Listener {
 *   function actionPerformed($actionEvent) {
 *       ...
 *     }
 * }
 * function getListener() {
 *     return java_get_closure(new Listener(), null, array(new Java("java.awt.event.ActionListener")));
 * }
 * </code>
 * @access public
 */
function java_closure() {
  return java_closure_array(func_get_args());
}

/**
 * Enters stream mode (asynchronuous protocol). 
 *
 * The statements are
 * sent to the back-end in one XML stream.  
 *
 * Use this protocol
 * mode when you have a large number of set operations and you don't
 * expect an exception. Any exception raised during stream mode is
 * reported when java_end_document() is called.
 * @access public
 */
function java_begin_document() {
  $client = __javaproxy_Client_getClient();
  if (!$client->isAsync) {
	$client->invokeMethod(0, "beginDocument", array());
	$client->setAsyncHandler();
  }
  $client->isAsync+=1;
}
/**
 * Ends stream mode. Fires a JavaException if any statement executed during
 * stream mode raised an exception.
 * @access public
 */
function java_end_document() {
  $client = __javaproxy_Client_getClient();
  if ($client->isAsync==1) {
	$client->setDefaultHandler();
	$client->invokeMethod(0, "endDocument", array());
  }
  if ($client->isAsync > 0) $client->isAsync-=1;
}

/**
 * @access private
 */
class java_JavaProxy implements java_JavaType {
  public $__serialID, $__java;
  public $__signature;
  public $__client;
  function java_JavaProxy($java, $signature){ 
	$this->__java=$java;
	$this->__signature=$signature;
	$this->__client = __javaproxy_Client_getClient();
  }
  function __cast($type) {
	return $this->__client->cast($this, $type);
  }
  function __sleep() {
	$args = array($this, java_get_lifetime());
	$this->__serialID = $this->__client->invokeMethod(0, "serialize", $args);
	if(JAVA_DEBUG) echo "proxy sleep called for $this->__java, $this->__signature\n";
    return array("__serialID");
  }
  function __wakeup() {
	$args = array($this->__serialID, java_get_lifetime());
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

/**
 * @access private
 */
class java_objectIterator implements Iterator {
  private $var;

  function java_ObjectIterator($javaProxy) {
	$this->var = java_cast ($javaProxy, "A");
  }
  function rewind() {
	reset($this->var);
  }
  function valid() {
	return $this->current() !== false;
  }
  function next() {
	return next($this->var);
  }
  function key() {
	return key($this->var);
  }
  function current() {
	return current($this->var);
  }
}
/**
 * @access private
 */
class java_IteratorProxy extends java_JavaProxy implements IteratorAggregate {
  function java_IteratorProxy($java, $signature) {
	parent::java_JavaProxy($java, $signature);
  }
  function getIterator() {
	return new java_ObjectIterator($this);
  }
}
/**
 * @access private
 */
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
/**
 * @access private
 */
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
 * @access private
 * @see #java_InternalJava
*/
abstract class java_AbstractJava implements IteratorAggregate,ArrayAccess,java_JavaType {
  public $__delegate;

  public $__serialID;

  public $__factory;
  public $__java, $__signature;

  public $__cancelProxyCreationTag;

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
	$this->__delegate->__sleep();
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

/**
 * The Java proxy class.
 * @access public
 * @see #JavaException
 */
class Java extends java_AbstractJava {
  public $__client;

  /**
   * Create a new instance of a java type. 
   *
   * Use this constructor to create an instance. Use the Java function to access the type.
   *
   * Example which creates an instance:
   * <code>
   * $s = new Java("java.lang.String", "hello");
   * </code>
   * 
   * Example which accesses the System class:
   * <code>
   * $s = Java("java.lang.System");
   * </code>
   *
   * @see #Java()
   */
  function Java() {
	$client = $this->__client = __javaproxy_Client_getClient();
	
	$args = func_get_args();
	$name = array_shift($args);

	// compatibility with the C implementation
	if(is_array($name)) {$args = $name; $name = array_shift($args);}

	/* do not delete this line, it is used when generating Mono.inc from Java.inc */

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

    if(array_key_exists($sig, $client->methodCache)) {
	  if(JAVA_DEBUG) { echo "cache hit for new Java: $sig\n"; }
      $cacheEntry = &$client->methodCache[$sig];
	  $client->sendBuffer.= $client->preparedToSendBuffer;
	  if(strlen($client->sendBuffer)>=JAVA_SEND_SIZE) {
	      if($client->protocol->handler->write($client->sendBuffer)<=0) 
	         throw new java_IllegalStateException("Connection out of sync, check backend log for details.");
	      $client->sendBuffer=null;
	  }
	  
	  $client->preparedToSendBuffer=vsprintf($cacheEntry->fmt, $args2);

	  if(JAVA_DEBUG) {
		print_r($args2);
		echo "set prepared to send buffer: $client->preparedToSendBuffer, $cacheEntry->fmt, for key: $sig\n";
	  }
	  $this->__java = ++$client->asyncCtx;

	  if(JAVA_DEBUG) {echo "setresult from new Java cache: object:"; echo sprintf("%x", $client->asyncCtx); echo "\n";}
	  $this->__factory = $cacheEntry->factory;
  	  $this->__signature = $cacheEntry->signature;

	  $this->__cancelProxyCreationTag = ++$client->cancelProxyCreationTag;
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

	// Cancel proxy creation: If the created instance is collected
	// before the next java statement is executed, we set the result
	// type to void
	if($preparedToSendBuffer &&
	   $client->cancelProxyCreationTag==$this->__cancelProxyCreationTag) {

	  $preparedToSendBuffer[6]="3";
	  if(JAVA_DEBUG) {
		echo "cancel result proxy creation:"; echo $this->__java; echo " {$client->preparedToSendBuffer}"; echo "\n";
	  }
	  $client->sendBuffer.=$preparedToSendBuffer;
	  $preparedToSendBuffer = null;
	  $client->asyncCtx -= 1;
	} else {
	  if(!$this->__delegate) { // write unref ourselfs if we don't have a delegate yet (see cachedJavaPrototype and Java::__factory in __call below)
		if(JAVA_DEBUG) {
		  echo "unref java:"; echo $this->__java; echo "\n";
		}
		$client->unref($this->__java);
	  }
	}	
  }
  /**
   * Call a method on a Java object
   *
   * Example:
   *<code>
   * $s->substring(1, 10);
   * </code>
   * @param $method The method name
   * @param $args The argument array
   */
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

    if(array_key_exists($sig, $client->methodCache)) {
	  if(JAVA_DEBUG) { echo "cache hit for __call: $sig\n"; }
      $cacheEntry = &$client->methodCache[$sig];
	  $client->sendBuffer.=$client->preparedToSendBuffer;
	  if(strlen($client->sendBuffer)>=JAVA_SEND_SIZE) {
	      if($client->protocol->handler->write($client->sendBuffer)<=0) 
	         throw new java_IllegalStateException("Out of sync. Check backend log for details.");
	      $client->sendBuffer=null;
	  }
	  $client->preparedToSendBuffer=vsprintf($cacheEntry->fmt, $args2);
	  if(JAVA_DEBUG) {
		print_r($args2);
		echo "set prepared to send buffer: {$client->preparedToSendBuffer}, {$cacheEntry->fmt}\n";
	  }
	  if($cacheEntry->resultVoid) {
		$client->cancelProxyCreationTag += 1; // expire tag
		return null;
	  } else {
		$result = clone($client->cachedJavaPrototype);
		$result->__factory = $cacheEntry->factory;
		$result->__java = ++$client->asyncCtx;
		if(JAVA_DEBUG) {echo "setresult from __call cache: object:"; echo sprintf("%x", $client->asyncCtx); echo "\n";}
		$result->__signature = $cacheEntry->signature;
		$result->__cancelProxyCreationTag = ++$client->cancelProxyCreationTag;
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

/**
 * @access private
 */
class java_InternalJava extends Java {
  function java_InternalJava($proxy) {
	$this->__delegate = $proxy;
	$this->__java = $proxy->__java;
	$this->__signature = $proxy->__signature;
	$this->__client = $proxy->__client;
  }
}

/**
 * @access private
 */
class java_class extends Java {
  function java_class() {
	$this->__client = __javaproxy_Client_getClient();

	$args = func_get_args();
	$name = array_shift($args);

	// compatibility with the C implementation
	if(is_array($name)) { $args = $name; $name = array_shift($args); }

	/* do not delete this line, it is used when generating Mono.inc from Java.inc */

	$delegate = $this->__delegate = $this->__client->referenceObject($name, $args);

	$this->__java = $delegate->__java;
	$this->__signature = $delegate->__signature;
  }
}
/**
 * @access private
 */
class JavaClass extends java_class{}
/**
 * A decorator pattern which overrides all magic methods.
 * 
 * @access public
 */
class java_exception extends Exception implements java_JavaType {
  public $__serialID, $__java, $__client;
  public $__delegate;
  public $__signature;
  
  /**
   * Create a new Exception.
   * <code>
   * $ex = new JavaException("java.lang.NullPointerException");
   * throw $ex;
   * </code>
   */
  function java_exception() {
	$this->__client = __javaproxy_Client_getClient();

	$args = func_get_args();
	$name = array_shift($args);

	// compatibility with the C implementation
	if(is_array($name)) { $args = $name; $name = array_shift($args); }

	/* do not delete this line, it is used when generating Mono.inc from Java.inc */

	$delegate = $this->__delegate = $this->__client->createObject($name, $args);

	$this->__java = $delegate->__java;
	$this->__signature = $delegate->__signature;
  }
  /**
   * @access private
   */
  function __cast($type) {
	return $this->__delegate->__cast($type);
  }
  /**
   * @access private
   */
  function __sleep() {
	$rc = $this->__delegate->__sleep();
	$this->__serialID = $this->__delegate->__serialID;
	return $rc;
  }
  /**
   * @access private
   */
  function __wakeup() {
	$this->__delegate->__wakeup();
	$this->__java = $this->__delegate->__java;
	$this->__client = $this->__delegate->__client;
  }
  /**
   * @access private
   */
  function __get($key) { 
    return $this->__delegate->__get($key);
  }
  /**
   * @access private
   */
  function __set($key, $val) {
    $this->__delegate->__set($key, $val);
  }
  /**
   * @access private
   */
  function __call($method, $args) { 
    return $this->__delegate->__call($method, $args);
  }
  /**
   * @access private
   */
  function __toString() {
	return $this->__delegate->__toExceptionString($this->getTraceAsString());
  }
}
/**
 * The java exception proxy
 * @access public
 */
class JavaException extends java_exception {}
/**
 * @access private
 */
class java_InternalException extends JavaException {
  function java_InternalException($proxy) {
	$this->__delegate = $proxy;
	$this->__java = $proxy->__java;
	$this->__signature = $proxy->__signature;
	$this->__client = $proxy->__client;
  }
}

/**
 * @access private
 */
class java_JavaProxyProxy extends Java {
  function java_JavaProxyProxy($client) {
	$this->__client = $client;
  }
}

?>
