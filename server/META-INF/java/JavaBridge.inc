<?php /*-*- mode: php; tab-width:4 -*-*/

  /* java_JavaBridge.php -- provides the PHP/Java Bridge PHP API.

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

if(!function_exists("java_get_base")) {
  $version = phpversion();
  if ((version_compare("5.1.2", $version, ">"))) {
	$msg = "<br><strong>PHP $version too old.</strong><br>\nFor PHP versions < 5.1.4 install the java.so or php_java.dll from the php-java-bridge-legacy download.<br>\nOr set the path to the PHP executable, see php_exec in the WEB-INF/web.xml";
	die($msg);
  }
  function java_get_base() {
	$ar = get_required_files();
	$arLen = sizeof($ar);
	if($arLen>0) {
	  $thiz = $ar[$arLen-1];
	  return dirname($thiz);
	} else {
	  return "java/";
	}
  }
  $JAVA_BASE=java_get_base();
  require_once("${JAVA_BASE}/JavaProxy.inc");

  class java_RuntimeException extends Exception {};
  class java_IllegalStateException extends java_RuntimeException {};
  class java_IllegalArgumentException extends java_RuntimeException {
	function __construct($ob) {
	  parent::__construct(is_object($ob)?$ob->__toString():(string)$ob);
	}
  };

  function Java($name) { 
	static $classMap = array();
	if(array_key_exists($name, $classMap)) return $classMap[$name];
	return $classMap[$name]=new JavaClass(array($name), false);
  }
  function java_get_closure() {return java_closure_array(func_get_args());}
  function java_get_values($arg) { return java_values($arg); }
  function java_get_session() {return java_session_array(func_get_args());}
  function java_get_context() {return java_context(); }
  function java_get_server_name() { return java_server_name(); }
  function java_set_library_path($arg) { return java_require($arg); }
 } //!java_defined  DO NOT REMOVE THIS LINE
?>
