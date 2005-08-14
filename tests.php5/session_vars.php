#!/usr/bin/php

<?php

if (!extension_loaded('java')) {
  if (!(PHP_SHLIB_SUFFIX=="so" && dl('java.so'))&&!(PHP_SHLIB_SUFFIX=="dll" && dl('php_java.dll'))) {
    echo "java extension not installed.";
    exit(2);
  }
}

include("../php_java_lib/JSession.php");
// Stores java values in the _$SESSION variable.  (In order to run
// this test from the console we use serialize and unserialize
// instead of using $_SESSION).

$ser="vector.ser";

// load the session from the disc
if(file_exists($ser)) {
  $file=fopen($ser,"r");
  $id=fgets($file);
  fclose($file);
  $v=unserialize($id);
  if(!$v->getJava())  $v=null; // because java backend was restarted
}

// either a new session or previous session destroyed
if(!$v) {
  echo "creating new session\n";
  $v=new JSessionAdapter(new java("java.util.Vector", array(1, true, -1.345e99, "hello", new java("java.lang.Object"))));
  $v->addElement($v->getJava());
  $id=serialize($v);
  $file=fopen($ser,"w");
  fwrite($file, $id);
  fclose($file);
}

echo $v;
?>

