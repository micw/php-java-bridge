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
  if ((version_compare("5.3.0", $version, ">"))) {
   1E512; // initialize errno
   if ((version_compare("5.1.2", $version, ">"))) {
	$msg = "<br><strong>PHP $version too old.</strong><br>\nPlease set the path to
a PHP 5.1.x executable, see php_exec in the WEB-INF/web.xml";
	die($msg);
   }
  }
  /**
   * @access private
   */
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

  /**
   * @access private
   */
  class java_RuntimeException extends Exception {};
  /**
   * @access private
   */
  class java_IOException extends Exception {};
  /**
   * @access private
   */
  class java_ConnectException extends java_IOException {};
  /**
   * @access private
   */
  class java_IllegalStateException extends java_RuntimeException {};
  /**
   * @access private
   */
  class java_IllegalArgumentException extends java_RuntimeException {
	function __construct($ob) {
	  parent::__construct("illegal argument: ".gettype($ob));
	}
  };


  /**
   * @access private
   */
  function java_autoload_function5($x) {
	$str=str_replace("_", ".", $x);

    $client=__javaproxy_Client_getClient();
    if(!($client->invokeMethod(0, "typeExists", array($str)))) return false;

	$instance = "class ${x} extends Java {".
	  "static function type(\$sub=null){if(\$sub) \$sub='\$'.\$sub; return java('${str}'.\"\$sub\");}".
	  'function __construct() {$args = func_get_args();'.
	  'array_unshift($args, '."'$str'".'); parent::__construct($args);}}';
	eval ("$instance");
	return true;
  }

  /**
   * @access private
   */
  function java_autoload_function($x) {
    $idx = strrpos($x, "::"); if (!$idx) return java_autoload_function5($x);

    $str=str_replace("::", ".", $x);

    $client=__javaproxy_Client_getClient();
    if(!($client->invokeMethod(0, "typeExists", array($str)))) return false;

    $package = substr($x,0, $idx);
    $name = substr($x, 2+$idx);
    $instance = "namespace $package; class ${name} extends ::Java {".
	  "static function type(\$sub=null){if(\$sub) \$sub='\$'.\$sub;return ::java('${str}'.\"\$sub\");}".
	  'function __construct() {$args = func_get_args();'.
	  'array_unshift($args, '."'$str'".'); parent::__construct($args);}}';
	eval ("$instance");
	return true;
  }

  /**
   * Load a set of java libraries and make them available in the current name space.
   *
   * Available since php 5.3. Example:
   * <code>
   * java_autoload();
   *
   * use org::apache::lucene;
   * use org::apache::lucene::index;
   * use org::apache::lucene::search;
   * use org::apache::lucene::search::IndexSearcher as LuceneSearcher;
   *
   * $searcher = new LuceneSearcher(...);
   * $term = new index::Term("name", "someTerm");
   * $phrase = new search::PhraseQuery();
   *
   * $phrase->add($term);
   * $hits = $searcher->search($phrase);
   * ...
   *</code>
   * @access public
   * @since PHP 5.3
   */
  function java_autoload($libs=null) {
	static $once = false;
	if($once) 
	  throw new java_IllegalStateException("java_autoload called more than once");
	$once = true;
	java_require($libs);
	if(function_exists("spl_autoload_register")) {
	  spl_autoload_register("java_autoload_function");
	} else {
	  function __autoload($x) {
		return java_autoload_function($x);
	  }
	}
  }

  /**
   * Access the java type with the given name. 
   *
   * This procedure can be
   * used to access constants or procedures within a class.  <br>
   *
   * To access class features, use the java constructor instead.<br>
   *
   * Example: <code> java("java.lang.Long")->MAX_VALUE </code>
   *
   * @access public
   * @param string The type name
   * @see Java
   */
  function Java($name) { 
	static $classMap = array();
	if(array_key_exists($name, $classMap)) return $classMap[$name];
	return $classMap[$name]=new JavaClass($name);
  }
  /**
   * Alias for java_closure();
   * @access private
   * @see java_closure();
   */
  function java_get_closure() {return java_closure_array(func_get_args());}

  /**
   * Alias for java_closure();
   * @access private
   * @see java_closure();
   */
  function java_wrap() {return java_closure_array(func_get_args());}
  
  /**
   * Alias for java_values();
   * @access private
   * @see java_values();
   */
  function java_get_values($arg) { return java_values($arg); }

  /**
   * Alias for java_session();
   * @access private
   * @see java_session();
   */
  function java_get_session() {return java_session_array(func_get_args());}

  /**
   * Alias for java_context();
   * @access private
   * @see java_context();
   */
  function java_get_context() {return java_context(); }

  /**
   * Alias for java_server_name();
   * @access private
   * @see java_server_name();
   */
  function java_get_server_name() { return java_server_name(); }

  /**
   * Alias for java_is_null();
   * @access private
   * @see java_is_null();
   */
  function java_isnull($value) { return is_null (java_values ($value)); }

  /**
   * Checks whether a value is null or not.
   *
   * Example: <code> java_is_null(java("java.lang.System")->getProperty("foo")) </code>
   *
   * @access public
   * @param mixed A Java object or a PHP value
   * @return true if $value is the PHP or Java null value.
   */
  function java_is_null($value) { return is_null (java_values ($value)); }

  /**
   * Alias for java_is_true();
   * @access private
   * @see java_is_true();
   */
  function java_istrue($value) { return  (boolean)(java_values ($value)); }

  /**
   * Checks whether a value is true or not.
   *
   * Example: <code> java_is_true(java("java.lang.System")->getProperty("foo")) </code>
   *
   * @access public
   * @param mixed A Java object or a PHP value
   * @return true if the PHP or Java value is true.
   */
  function java_is_true($value) { return (boolean)(java_values ($value)); }

  /**
   * Alias for java_is_false();
   * @access private
   * @see java_is_false();
   */
  function java_isfalse($value) { return !(java_values ($value)); }

  /**
   * Checks whether a value is false or not.
   *
   * Example: <code> java_is_false(java("java.lang.System")->getProperty("foo")) </code>
   *
   * @access public
   * @param mixed A Java object or a PHP value
   * @return true if the PHP or Java value is false.
   */
  function java_is_false($value) { return !(java_values ($value)); }

  /**
   * Alias for java_set_file_encoding();
   * @access private
   * @see java_set_file_encoding();
   */
  function java_set_encoding($enc) { return java_set_file_encoding ($enc); }

  /**
   * @deprecated: Use java_require() instead.
   * @access private
   * @see java_require();
   */
  function java_set_library_path($arg) { return java_require($arg); }
 } //!java_defined  DO NOT REMOVE THIS LINE
?>
