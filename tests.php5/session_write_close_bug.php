#!/usr/bin/php
<?php
if (!extension_loaded('java')) {
  if (!(require_once("http://127.0.0.1:8080/JavaBridge/java/Java.inc"))) {
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
