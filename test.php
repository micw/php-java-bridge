#!/usr/bin/php
<?php
if (!extension_loaded('java')) {
  if ((version_compare("5.0.0", phpversion(), "<=")) && (version_compare("5.0.4", phpversion(), ">"))) {
    echo "This PHP version does not support dl().\n";
    echo "Please add an extension=java.so or extension=php_java.dll entry to your php.ini file.\n";
    exit(1);
  }

  echo "Please permanently activate the extension. Loading java extension now...\n";
  if (!dl('java.so')&&!dl('php_java.dll')) {
    echo "Error: The java extension is not installed.\n";
  }
 }
if(java_get_server_name() != null) {

  phpinfo();
  print "\n\n";
  
  $v = new java("java.lang.System");
  $p = @$v->getProperties();
  if($ex=java_last_exception_get()) {
    $trace = new java("java.io.ByteArrayOutputStream");
    $ex->printStackTrace(new java("java.io.PrintStream", $trace));
    echo "Exception $ex occured:<br>\n" . $trace . "\n";
    exit(1);
  }
  $arr=java_values($p);
  foreach ($arr as $key => $value) {
    print $key . " -> " .  $value . "<br>\n";
  }
  echo "<br>\n";

 } else {
  
  phpinfo();
  print "\n\n";

  /* java_get_server_name() == null means that the backend is not
   running */

  $ext_name="java.so";
  if(PHP_SHLIB_SUFFIX != "so") $ext_name="php_java.dll";

  echo "Error: The PHP/Java Bridge backend is not running.\n";
  echo "\n";
  echo "Please start it and/or check if the directory\n";
  echo "\n\t".ini_get("extension_dir")."\n\n";
  echo "contains \"$ext_name\" and \"JavaBridge.jar\".\n";
  echo "\n";
  echo "Please check that $ext_name is indeed the PHP/Java Bridge and not its\n";
  echo "predecessor, the ext/java extension (check the file size).\n";
  echo "Also check if the following values are correct:\n\n";
  echo "\tjava.java_home = ".ini_get("java.java_home")."\n";
  echo "\tjava.java = ".ini_get("java.java")."\n";
  echo "If you want to start the backend automatically, disable:\n";
  echo "\tjava.socketname = ".ini_get("java.socketname")."\n";
  echo "\tjava.hosts = ".ini_get("java.hosts")."\n";
  echo "\tjava.servlet = ".ini_get("java.servlet")."\n";
  echo "\n";
  echo "If that still doesn't work, please check the \"java command\" above and\n";
  echo "report this problem to:\n\n";
  echo "\tphp-java-bridge-users@lists.sourceforge.net.\n";

 }
?>

