#!/bin/env php
<?php

/* load extension and check it */
function check_extension() {
  if(!extension_loaded('java')) {
    $sapi_type = php_sapi_name();
    if ($sapi_type == "cgi" || $sapi_type == "cgi-fcgi" || $sapi_type == "cli") {
      if(!(PHP_SHLIB_SUFFIX=="so" && @dl('java.so'))&&!(PHP_SHLIB_SUFFIX=="dll" && @dl('php_java.dll'))&&!(include_once("java/Java.php"))) {
	echo "java extension not installed.";
	exit(2);
      }
    } else {
      require_once("java/Java.php");
    }
  }
  if(!function_exists("java_get_server_name")) {
    echo "Fatal: The loaded java extension is not the PHP/Java Bridge";
    exit(7);
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
  echo "JavaBridge back-end version: ".java_values($Util->VERSION)."<br>\n";
  echo "<br>\n";

 } else {
  
  phpinfo();
  print "\n\n";

  /* java_get_server_name() == null means that the back-end is not
   running */
  if(PHP_SHLIB_SUFFIX=="so") $ext_name="java.so";
  else if(PHP_SHLIB_SUFFIX=="dll") $ext_name="php_java.dll";
  else $ext_name="unknown suffix: ".PHP_SHLIB_SUFFIX;

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
?>
