<?php /*-*- mode: php; tab-width:4 -*-*/

/* javabridge_Client.php -- parser callbacks for the PHP/Java Bridge.

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

require_once("javabridge/IDocHandler.php");
require_once("javabridge/JavaProxy.php");
require_once("javabridge/Parser.php");
require_once("javabridge/Protocol.php");
require_once("javabridge/GlobalRef.php");

class javabridge_SimpleFactory {
  var $client;
  function javabridge_SimpleFactory($client) {
	$this->client = $client;
  }
  function getProxy($result,$create) {
	return $result;
  }
  function getResult($create) {
	return $this->getProxy($this->client->getResult(), $create);
  }
}
class javabridge_ProxyFactory extends javabridge_SimpleFactory {
  function javabridge_ProxyFactory($client) {
	$this->client = $client;
  }
  function getProxy($result, $create) {
	$this->client->factory = $this->client->simpleFactory;
	if(!$create) return $result;
	$proxy = new javabridge_InternalJavaProxy($result);
	return $proxy;
  }
}

class javabridge_Arg {
  var $client;
  function javabridge_Arg($client) {
	$this->client = $client;
  }
  function setResult($val) {
	$this->client->result = $val;
  }
  function setException($val) {
	$this->client->exception=$val;
  }
}
class javabridge_CompositeArg extends javabridge_Arg {
  var $idx;
  function javabridge_CompositeArg($client) {
	$this->client = $client;
  }
  function setIndex($val) {
	$this->idx = $val;
  }
  function setResult($val) {
	$res = $this->client->factory->getProxy($val, true);
	$this->client->result[$this->idx]=$res;
  }
}

class javabridge_Handler {
  var $client;
  
  function javabridge_Handler($client) {
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
	  $client->arg = $client->simpleArg;
	  $client->factory = $client->simpleFactory;
	  $client->compositeFactory = $client->proxyFactory;
	  $client->stack = array();
	  $client->idx = 0;

	  $client->rc = $client->parser->parse();
	  if(!$client->rc) { $client->stack=null; return 0; }

	  /* pull off A, if any */
	  $stack_elem = array_pop($client->stack);
	  if(isset($stack_elem)) {
		$client->apply($stack_elem, $client->factory->getResult(true));
		$client->stack=null;
		$tail_call = 1;			// we don't expect a result
	  } else {
		$tail_call = 0;
	  }
	} while($tail_call);
	return 1;
  }
}
class javabridge_AsyncHandler extends javabridge_Handler {
  function javabridge_AsyncHandler($client) {
	$this->client = $client;
  }

  function flush() {
    $this->client->protocol->sendData();
  }

  function sendData() {
    $this->client->protocol->sendAsyncData();
  }

  function handleRequests() {
	$client = $this->client;

	$client->factory = $client->proxyFactory;
	$client->arg->setResult(++$client->asyncCtx);
  }
}
class javabridge_Client /* implements IDocHandler */ {
  var $RECV_SIZE=8192;
  var $SEND_SIZE=8192;

  var $result, $exception;
  var $parser;

  var $simpleArg, $compositeArg;
  var $simpleFactory, $proxyFactory;
  
  var $arg, $factory;
  var $idx;
  
  var $asyncCtx;

  var $globalRef;

  var $defaultHandler, $asyncHandler, $handler;

  function javabridge_Client() {
	$this->parser = new javabridge_Parser($this);
	$this->protocol = new javabridge_Protocol($this);

	$this->simpleArg = new javabridge_Arg($this);
	$this->compositeArg = new javabridge_CompositeArg($this);

	$this->simpleFactory = new javabridge_SimpleFactory($this);
	$this->proxyFactory = new javabridge_ProxyFactory($this);

	$this->globalRef = new javabridge_GlobalRef();

	$this->asyncCtx = 0;
	$this->handler = $this->defaultHandler = new javabridge_Handler($this);
	$this->asyncHandler = new javabridge_AsyncHandler($this);
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

  function getNextIndex() {
	return $this->idx++;
  }
  function getResult() {
	return $this->result;
  }
  function setCompositeResult() {
	$this->arg = $this->compositeArg;
	$this->result = array();
  }
  function begin(&$tag) {
	$st=&$tag[2]->strings;
    $t=&$tag[0]->strings[0];
    $s=$t->string[$t->off];
    switch($s[0]) {
	case 'A':						/* receive apply args as normal array */
	  $object = $this->globalRef->get($st[0]->getExact());
	  $stack_elem = array(
						  "type"=> "A",
						  "m"=> $st[2]->getString(),
						  "p"=> $st[1]->getString(),
						  "v"=> $object,
						  "n"=> $st[3]->getExact());
	  
	  array_push($this->stack, $stack_elem);
	  $this->setCompositeResult();
	  break;
	case 'X': 
	  $stack_elem = array("type"=>$st[0]->getString());
	  array_push($this->stack, $stack_elem);
	  $this->setCompositeResult();
	  break;
	case 'P':
	  $stack_elem = $this->stack[0];
	  if($stack_elem["type"]=='H') { /* hash table */
		$s = $st[0]->getString();
		if($s[0]=='N') { /* number */
		  $this->arg->setIndex($st[1]->getExact());
		} else {
		  $this->arg->setIndex($st[1]->getString());
		}
	  } else {					/* array */
		$this->arg->setIndex($this->getNextIndex());
	  }
	  break;
	case 'S':
	  $this->arg->setResult($st[0]->getString());
	  break;
	case 'B':
	  $s=$st[0]->getString();
	  $this->arg->setResult($s[0]=='T');
	  break;
	case 'L':
	  $sign = $st[1]->getString();
	  $arg = $st[0]->getExact();
	  if($sign[0]=='A') $arg*=-1;
	  $this->arg->setResult($arg);
	  break;
	case 'D':
	  $this->arg->setResult($st[0]->getInexact());
	  break;
	case 'N':
	  $this->arg->setResult(null);
	  break;
	case 'F':
	  break;
	case 'O': 
	  $this->factory = $this->proxyFactory;
	  $this->arg->setResult($this->asyncCtx=$st[0]->getExact());
	  break;
	case 'E':
	  $this->factory = $this->proxy;
	  $this->arg->setResult($this->asyncCtx=$st[0]->getExact());
	  $this->arg->setException($st[1]->getString());
	  break;
	default: 
	  die("not implemented");
	}
  }
  function end(&$tag) { //FIXME: what about trailing simple args?
    $t=&$tag[0]->strings[0];
    $s=$t->string[$t->off];
    switch($s[0]) {
	case 'X':
	  array_pop($this->stack);
	  break;
	}
  }
  function createParserString() {
	return new javabridge_ParserString();
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
		$this->protocol->writeCompositeEnd();
	  }
	}
	return null;
  }
  function writeArgs($args) {
	$n = count($args);
	for($i=0; $i<$n; $i++) {
	  $this->writeArg($args[$i]);
	}
  }
  function createObject($name, $args, $createInstance, $id) {
	$instance = $createInstance?"I":"C";
    $this->protocol->createObjectBegin($name, $instance, $id);
    $this->writeArgs($args);
    $this->protocol->createObjectEnd();
	return $this->factory->getResult(false);
  }
  function getProperty($object, $property, $id) {
	$this->protocol->invokeBegin($object, $property, "P", $id);
	$this->protocol->invokeEnd();
	return $this->factory->getResult(true);
  }
  function setProperty($object, $property, $arg, $id) {
	$this->protocol->invokeBegin($object, $property, "P", $id);
	writeArg($arg);
	$this->protocol->invokeEnd();
  }
  function invokeMethod($object, $method, $args, $id) {
	$this->protocol->invokeBegin($object, $method, "I", $id);
	$this->writeArgs($args);
	$this->protocol->invokeEnd();
	return $this->factory->getResult(true);
  }
  function unref($object) {
	$this->protocol->writeUnref($object->__java);
  }
  function apply($methodDesc, $args) {
	$name = $methodDesc["p"];
	$object = $methodDesc["v"];
	$res = call_user_func_array(array(&$object, $name), $args);

	$this->protocol->resultBegin(0);
	$this->writeArg($res);
	$this->protocol->resultEnd();
  }
  function getContext() {
	return $this->invokeMethod(0, "getContext", array(), 0);
  }
  function getSession($args) {
	//TODO: Handle override redirect when java_session() is not
	// the first statement in a script
	$this->protocol->getSession($args);
	return $this->invokeMethod(0, "getSession", $args, 0);
  }
  function getServerName() {
	return $this->protocol->serverName;
  }
}
?>
