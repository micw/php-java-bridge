#!/bin/env php
<?php

/**
 * Check the standard name, java.so or php_java.dll
 */
function java_get_simple_ext_name() {
  static $ext_name=null;
  if($ext_name!=null) return $ext_name;
  $ext_name="java.so";
  if(PHP_SHLIB_SUFFIX != "so") $ext_name="php_java.dll";
  return $ext_name;
}
function check($name) {
  $len = strlen($name);
  return strncasecmp(php_uname(), $name, $len) == 0;
}
/**
 * Check if the user has copied the pre-compiled binaries from the
 * javabridge_JavaBridge.war to c:/php. We currently have binaries for Linux
 * (php5.1), Solaris (php5.1) and Windows (php5.1 and php5.0).
 */
function java_get_servlet_ext_name() {
  static $ext_name=null;
  if($ext_name!=null) return $ext_name;
  if(check("sunos")) $ext_name="java-x86-sunos.so";
  else if(check("linux")) $ext_name="java-i86-linux.so";
  else if(check("windows")) {
    $ext_name="java-x86-windows.dll";
    if ((version_compare("5.0.0", phpversion(), "<=")) && (version_compare("5.1.0", phpversion(), ">")))
      $ext_name="php-5.0-java-x86-windows.dll";
  }
  return $ext_name;
}
function java_get_ext_name() {
  $current_ext_name = java_get_simple_ext_name();
  if(!$current_ext_name) $current_ext_name = java_get_servlet_ext_name();
  return $current_ext_name;
}
function java_dl($ext) {
  echo "Please permanently activate the extension. Loading java extension $ext now...\n";
  return dl("$ext");
}
function java_load_extension() {
  if(extension_loaded('java')) return java_get_ext_name();
  $ext = java_get_ext_name();
  $ext_servlet = java_get_servlet_ext_name();
  $success = java_dl($current=$ext);
  if(!$success) $success = java_dl($current=$ext_servlet);
  if (!$success) {
    echo "Please permanently activate the extension. Loading java extension $ext now...\n";
    echo "\n<br><strong>Error: Either the java extension is not installed <br>\n";
    echo "or it was compiled against an older or newer php version.<br>\n";
    echo "See the HTTP (IIS or Apache) server log for details.</strong><br>\n";
    echo "Will use pure PHP implementation<br>\n";
    require_once("javabridge/Java.php");
  }
  return $current;
} 
/* load extension and check it */
function check_extension() {
  $current_ext_name = java_load_extension();
  if(!function_exists("java_get_server_name")) {
    echo "Fatal: The loaded java extension is not the PHP/Java Bridge";
    exit(7);
  }

  if (!extension_loaded('java')) {
    if ((version_compare("5.0.0", phpversion(), "<=")) && (version_compare("5.0.4", phpversion(), ">"))) {
      echo "This PHP version does not support dl().\n";
      echo "Please add an extension=java.so or extension=php_java.dll entry to your php.ini file.\n";
      exit(1);
    }
  }
}

check_extension();
if(java_get_server_name()!=null){

  phpinfo();
  print "\n\n";
  
  $v = new JavaClass("java.lang.System");
  $p = @$v->getProperties();
  if($ex=java_last_exception_get()) {
    $trace = new Java("java.io.ByteArrayOutputStream");
    $ex->printStackTrace(new java("java.io.PrintStream", $trace));
    echo "Exception $ex occured:<br>\n" . $trace . "\n";
    exit(1);
  }
  $arr=java_values($p);
  foreach ($arr as $key => $value) {
    print $key . " -> " .  $value . "<br>\n";
  }
  echo "<br>\n";
  $Util = new JavaClass("php.java.bridge.Util");
  echo "JavaBridge back-end version: {$Util->VERSION}<br>\n";
  echo "<br>\n";

 } else {
  
  phpinfo();
  print "\n\n";

  /* java_get_server_name() == null means that the back-end is not
   running */

  $ext_name=java_get_ext_name();
  if(isset($_SERVER['HTTPS'])) {
    echo "Error: Could not connect to " . ini_get("java.hosts") ."<br>\n";
    echo "Please add the following entry marked with a + to your<br>\n";
    echo "conf/server.xml (example for Tomcat):<br>\n";
    echo "  &lt;Service name=\"Catalina\"&gt;<br>\n";
    echo "  [...]<br>\n";
    echo "+  &lt;Connector port=\"9157\" address=\"127.0.0.1\"  /&gt;<br>\n";
    echo "  [...]<br>\n";
    echo " &lt;/Service&gt;<br>\n";
  } else {
    echo "Error: The PHP/Java Bridge back-end is not running.\n";
    echo "\n";
    echo "Please start it and/or check if the directory\n";
    echo "\n\t".ini_get("extension_dir")."\n\n";
    echo "contains \"$ext_name\" and \"JavaBridge.jar\".\n";
    echo "\n";
    echo " Check if the following values are correct:\n\n";
    echo "\tjava.java_home = ".ini_get("java.java_home")."\n";
    echo "\tjava.java = ".ini_get("java.java")."\n\n";
    echo "If you want to start the back-end automatically, disable:\n\n";
    echo "\tjava.socketname = ".ini_get("java.socketname")."\n";
    echo "\tjava.hosts = ".ini_get("java.hosts")."\n";
    echo "\tjava.servlet = ".ini_get("java.servlet")."\n";
    echo "\n";
    echo "If that still doesn't work, please check the \"java command\" above and\n";
    echo "report this problem to:\n\n";
    echo "\tphp-java-bridge-users@lists.sourceforge.net.\n";
  }
 }
?>
