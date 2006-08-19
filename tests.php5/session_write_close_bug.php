#!/usr/bin/php
<?php
if (!extension_loaded('java')) {
  if (!(include_once("java/Java.php"))&&!(PHP_SHLIB_SUFFIX=="so" && dl('java.so'))&&!(PHP_SHLIB_SUFFIX=="dll" && dl('php_java.dll'))) {
    echo "java extension not installed.";
    exit(2);
  }
}

session_id("test");
session_start();
$a=$_SESSION['a'];
if(!$a) {
  echo "new";
  $a=new java("java.lang.StringBuffer");
  $_SESSION['a']=$a;
 }
$a=$_SESSION['a'];
echo $a;
//session_write_close();
?>
