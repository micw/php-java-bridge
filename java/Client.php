<?php /*-*- mode: php; tab-width:4 -*-*/

  /* java_Client.php -- parser callbacks for the PHP/Java Bridge.

  Copyright (C) 2006 Jost Boekemeier

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

require_once("java/JavaProxy.php");
require_once("java/Parser.php");
require_once("java/Protocol.php");
require_once("java/GlobalRef.php");

class java_SimpleFactory {
  var $client;
  function java_SimpleFactory($client) {
	$this->client = $client;
  }
  function getProxy($result,$wrap) {
	return $result;
  }
  function checkResult($result) {}
}
class java_ProxyFactory extends java_SimpleFactory {
  function java_ProxyFactory($client) {
	parent::java_SimpleFactory($client);
  }
  function create($result) {
	return new java_JavaProxy($result);
  }
  function createInternal($proxy) {
	return new java_InternalJavaObject($proxy);	// no array access
  }
  function getProxy($result, $wrap) {
	$proxy = $this->create($result);
	if($wrap) $proxy = $this->createInternal($proxy);
	return $proxy;
  }
}
class java_ArrayProxyFactory extends java_ProxyFactory {
  function java_ArrayProxyFactory($client) {
	parent::java_ProxyFactory($client);
  }
  function createInternal($proxy) {
	return new java_InternalJava($proxy); // array access
  }
  function create($result) {
	return new java_ArrayProxy($result);
  }	
}
class java_IteratorProxyFactory extends java_ProxyFactory {
  function java_IteratorProxyFactory($client) {
	parent::java_ProxyFactory($client);
  }
  function create($result) {
	return new java_IteratorProxy($result);
  }	
}
class java_ExceptionProxyFactory extends java_SimpleFactory {
  function java_ExceptionProxyFactory($client) {
	parent::java_SimpleFactory($client);
  }
  function create($result) {
	static $count = 0;
	return new java_ExceptionProxy($result);
  }
  function getProxy($result, $wrap) {
	$proxy = $this->create($result);
	if($wrap) $proxy = new java_InternalException($proxy);
	return $proxy;
  }
}
class java_ThrowExceptionProxyFactory extends java_ExceptionProxyFactory {
  function java_ThrowExceptionProxyFactory($client) {
	parent::java_ExceptionProxyFactory($client);
  }
  function getProxy($result, $wrap) {
	$proxy = $this->create($result);
	// don't check for $wrap, which may be wrong (type Java instead of
	// JavaException) when the user has managed to create an exception
	// from a Java constructor, e.g.: new Java("java.lang.String",
	// null). Since we'll discard the possibly wrong type anyway, we
	// can create a fresh proxy without any further checks:
	$proxy = new java_InternalException($proxy);
	return $proxy;
  }
  function checkResult($result) {
	throw $result;
  }
}

class java_Arg {
  var $client;
  var $exception; 				// string representation for php4
  var $factory, $val;
  
  function java_Arg($client) {
	$this->client = $client;
	$this->factory = $client->simpleFactory;
  }
  function linkResult(&$val) {
	$this->val = &$val;
  }
  function setResult($val) {
	$this->val = &$val;
  }
  function getResult($wrap) {
	$rc = $this->factory->getProxy($this->val, $wrap);
	$factory = $this->factory;
	$this->factory = $this->client->simpleFactory;
	$factory->checkResult($rc);
	return $rc;
  }
  function setFactory($factory) {
	$this->factory = $factory;
  }
  function setException($string) {
	$this->exception = $string;
  }
}
class java_CompositeArg extends java_Arg {
  var $parentArg;
  var $idx;						// position within $val;
  var $type;					// for A and X
  var $counter;

  function java_CompositeArg($client, $type) {
	parent::java_Arg($client);
	$this->type = $type;
	$this->val = array();
	$this->counter = 0;
  }
  function setNextIndex() {
	$this->idx = $this->counter++;
  }
  function setIndex($val) {
	$this->idx = $val;
  }
  function linkResult(&$val) {
	$this->val[$this->idx]=&$val;
  }
  function setResult($val) {
	$this->val[$this->idx]=$this->factory->getProxy($val, true);
	$this->factory = $this->client->simpleFactory;
  }
}
class java_ApplyArg extends java_CompositeArg {
  var $m, $p, $v, $n; 			// see PROTOCOL.TXT

  function java_ApplyArg($client, $type, $m, $p, $v, $n) {
	parent::java_CompositeArg($client, $type);
	$this->m = $m;
	$this->p = $p;
	$this->v = $v;
	$this->n = $n;
  }
}

class java_Handler {
  var $client;
  
  function java_Handler($client) {
	$this->client = $client;
  }

  function flush() {}

  function sendData() {
    $this->client->protocol->sendData();
  }

  function handleRequests() {
	$client = $this->client;
	do {
	  $tail_call = false;
	  $this->client->stack=array($this->client->arg=$this->client->simpleArg);
	  $client->idx = 0;
	  $client->parser->parse();

	  /* pull off A, if any */
	  if((count($client->stack)) > 1) {
		$arg = array_pop($client->stack);
		$client->apply($arg);
		$tail_call = 1;			// we don't expect a result
	  } else {
		$tail_call = 0;
	  }

	  $client->stack=null;
	} while($tail_call);
	return 1;
  }
}
class java_AsyncHandler extends java_Handler {
  var $arg;
  function java_AsyncHandler($client) {
	$this->client = $client;
	$this->arg = $client->simpleArg;
  }
  function flush() {
    $this->client->protocol->sendData();
  }
  function sendData() {
    $this->client->protocol->sendAsyncData();
  }

  function handleRequests() {
	$this->arg->setFactory($this->client->proxyFactory);
	$this->arg->setResult(++$this->client->asyncCtx);
  }
}
class java_Client /* implements IDocHandler */ {
  var $RUNTIME;

  var $RECV_SIZE=8192;
  var $SEND_SIZE=8192;

  var $result, $exception;
  var $parser;

  var $simpleArg, $compositeArg;
  var $simpleFactory, 
	$proxyFactory, $iteratorProxyFacroty, 
	$arrayProxyFactory, $exceptionProxyFactory, $throwExceptionProxyFactory;
  
  var $arg;
  var $asyncCtx;
  var $globalRef;
  var $defaultHandler, $asyncHandler, $handler;

  var $stack;

  function java_Client() {
	$this->RUNTIME = array();
	$this->parser = new java_Parser($this);
	$this->protocol = new java_Protocol($this);

	$this->simpleFactory = new java_SimpleFactory($this);
	$this->proxyFactory = new java_ProxyFactory($this);
	$this->arrayProxyFactory = new java_ArrayProxyFactory($this);
	$this->iteratorProxyFactory = new java_IteratorProxyFactory($this);
	$this->exceptionProxyFactory = new java_ExceptionProxyFactory($this);
	$this->throwExceptionProxyFactory = new java_ThrowExceptionProxyFactory($this);

	$this->simpleArg = new java_Arg($this);

	$this->globalRef = new java_GlobalRef();

	$this->asyncCtx = 0;
	$this->handler = $this->defaultHandler = new java_Handler($this);
	$this->asyncHandler = new java_AsyncHandler($this);
  }
  function __destruct() { 
	//FIXME: shutdown connection etc.
  }

  function read($size) {
	return $this->protocol->read($size);
  }

  function setDefaultHandler() {
	$this->handler->flush();
	$this->handler = $this->defaultHandler;
  }

  function setAsyncHandler() {
	$this->handler = $this->asyncHandler;
  }

  function handleRequests() {
	$this->handler->handleRequests();
  }

  function sendData() {
	$this->handler->sendData();
  }

  function getWrappedResult($wrap) {
	return $this->simpleArg->getResult($wrap);
  }
  function getInternalResult() {
	return $this->getWrappedResult(false);
  }
  function getResult() {
	return $this->getWrappedResult(true);
  }
  function getProxyFactory($type) {
	switch($type[0]) {
	case 'E':
	  $factory = $this->exceptionProxyFactory;
	  break;
	case 'C':
	  $factory = $this->iteratorProxyFactory;
	  break;
	case 'A':
	  $factory = $this->arrayProxyFactory;
	  break;
	case 'O':
	  $factory = $this->proxyFactory;
	}
	return $factory;
  }
  function link(&$arg, &$newArg) {
	$arg->linkResult($newArg->val);
	$newArg->parentArg = $arg;
  }
  function getExact($str) {
	return hexdec($str);
  }
  function getInexact($str) {
	sscanf($str, "%e", $val);
	return $val;
  }
  function begin($name, $st) {
	$arg = $this->arg;
    switch($name[0]) {
	case 'A':						/* receive apply args as normal array */
	  $object = $this->globalRef->get($this->getExact($st['v']));
	  $newArg = new java_ApplyArg($this, 'A',
								  $this->parser->getData($st['m']),
								  $this->parser->getData($st['p']),
								  $object,
								  $this->getExact($st['n']));
	  $this->link($arg, $newArg);
	  array_push($this->stack, $this->arg = $newArg);
	  break;
	case 'X': 
	  $newArg = new java_CompositeArg($this, $st['t']);
	  $this->link($arg, $newArg);
	  array_push($this->stack, $this->arg = $newArg);
	  break;
	case 'P':
	  if($arg->type=='H') { /* hash table */
		$s = $st['t'];
		if($s[0]=='N') { /* number */
		  $arg->setIndex($this->getExact($st['v']));
		} else {
		  $arg->setIndex($this->parser->getData($st['v']));
		}
	  } else {					/* array */
		$arg->setNextIndex();
	  }
	  break;
	case 'S':
	  $arg->setResult($this->parser->getData($st['v']));
	  break;
	case 'B':
	  $s=$st['v'];
	  $arg->setResult($s[0]=='T');
	  break;
	case 'L':					// unsigned long
	  $sign = $st['p'];
	  $val = $this->getExact($st['v']);
	  if($sign[0]=='A') $val*=-1;
	  $arg->setResult($val);
	  break;
	case 'D':
	  $arg->setResult($this->getInexact($st['v']));
	  break;
	case 'N':
	  $arg->setResult(null);
	  break;
	case 'F':
	  break;
	case 'O': 
	  $arg->setFactory($this->getProxyFactory($st['p']));
	  $arg->setResult($this->asyncCtx=$this->getExact($st['v']));
	  break;
	case 'E':
	  $arg->setFactory($this->throwExceptionProxyFactory);
	  $arg->setResult($this->asyncCtx=$this->getExact($st['v']));
	  $arg->setException($st['m']);
	  break;
	default: 
	  $this->parser->parserError();
	}
  }
  function end($name) {
    switch($name[0]) {
	case 'X':
	  $frame = array_pop($this->stack);
	  $this->arg = $frame->parentArg;
	  break;
	}
  }
  function createParserString() {
	return new java_ParserString();
  }
  
  function writeArg($arg) {
	if(is_string($arg)) {
	  $this->protocol->writeString($arg);
	} else if(is_object($arg)) {
	  $this->protocol->writeObject($arg->__java);
	  return $arg;
	} else if(is_null($arg)) {
	  $this->protocol->writeObject(null);
	} else if(is_bool($arg)) {
	  $this->protocol->writeBoolean($arg);
	} else if(is_integer($arg)) {
	  $this->protocol->writeLong($arg);
	} else if(is_float($arg)) {
	  $this->protocol->writeDouble($arg);
	} else if(is_array($arg)) {
	  $wrote_begin=false;
	  foreach($arg as $key=>$val) {
		if(is_string($key)) {
		  if(!$wrote_begin) {
			$wrote_begin=1;
			$this->protocol->writeCompositeBegin_h(); 		
		  }
		  $this->protocol->writePairBegin_s($key);
		  $this->writeArg($val);
		  $this->protocol->writePairEnd();
		} else {
		  if(!$wrote_begin) {
			$wrote_begin=1;
			$this->protocol->writeCompositeBegin_h();
		  }
		  $this->protocol->writePairBegin_n($key);
		  $this->writeArg($val);
		  $this->protocol->writePairEnd();
		}
	  }
	  if(!$wrote_begin) {
		$this->protocol->writeCompositeBegin_a();
	  }
	  $this->protocol->writeCompositeEnd();
	}
	return null;
  }
  function writeArgs($args) {
	$n = count($args);
	for($i=0; $i<$n; $i++) {
	  $this->writeArg($args[$i]);
	}
  }
  function createObject($name, $args, $createInstance) {
	$instance = $createInstance?"I":"C";
    $this->protocol->createObjectBegin($name, $instance);
    $this->writeArgs($args);
    $this->protocol->createObjectEnd();
	$val = $this->getInternalResult();
	return $val;
  }
  function getProperty($object, $property) {
	$this->protocol->invokeBegin($object, $property, "P");
	$this->protocol->invokeEnd();
	return $this->getResult();
  }
  function setProperty($object, $property, $arg) {
	$this->protocol->invokeBegin($object, $property, "P");
	$this->writeArg($arg);
	$this->protocol->invokeEnd();
	$this->getResult();
  }
  function invokeMethod($object, $method, $args) {
	$this->protocol->invokeBegin($object, $method, "I");
	$this->writeArgs($args);
	$this->protocol->invokeEnd();
	$val = $this->getResult();
	return $val;
  }
  function unref($object) {
	$this->protocol->writeUnref($object);
  }
  function apply($arg) {
	$name = $arg->p;
	$object = $arg->v;
	$ob = $object ? array(&$object, $name) : $name;
	try {
	  $res = call_user_func_array($ob, $arg->getResult(true));
	  $this->protocol->resultBegin();
	  $this->writeArg($res);
	  $this->protocol->resultEnd();
	} catch (JavaException $e) {
	  $trace = $e->getTraceAsString();
	  $this->protocol->resultBegin();
	  $this->protocol->writeException($e->__java, $trace);
	  $this->protocol->resultEnd();
	} catch(Exception $ex) {
	  $e = new Java("java.lang.Exception", $ex->getMessage);
	  $t = new Java("java.lang.reflect.UndeclaredThrowableException",$e);
	  $trace = $ex->getTraceAsString();
	  $this->protocol->resultBegin();
	  $this->protocol->writeException($t->__java, $trace);
	  $this->protocol->resultEnd();
	}	  
  }
  function cast($object, $type) {
    switch($type[0]) {
	case 'S': case 's':
	  return $this->invokeMethod(0, "castToString", array($object));
	case 'B': case 'b':
	  return $this->invokeMethod(0, "castToBoolean", array($object));
	case 'L': case 'I': case 'l': case 'i':
	  return $this->invokeMethod(0, "castToExact", array($object));
	case 'D': case 'd': case 'F': case 'f':
	  return $this->invokeMethod(0, "castToInExact", array($object));
	case 'N': case 'n':
	  return null;
	case 'A': case 'a':
	  return $this->invokeMethod(0, "castToArray", array($object));
	case 'O': case 'o':			// eh?
	  return $object;
	default: 
	  die("$type illegal");
	}
  }
  function getContext() {
	return $this->invokeMethod(0, "getContext", array());
  }
  function getSession($args) {
	//TODO: Handle override redirect when java_session() is not
	// the first statement in a script
	$this->protocol->getSession($args);
	return $this->invokeMethod(0, "getSession", $args);
  }
  function getServerName() {
	return $this->protocol->getServerName();
  }
}
?>
