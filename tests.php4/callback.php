#!/usr/bin/php

<?php
if (!extension_loaded('java')) {
  if (!(PHP_SHLIB_SUFFIX=="so" && dl('java.so'))&&!(PHP_SHLIB_SUFFIX=="dll" && dl('php_java.dll'))) {
    echo "java extension not installed.";
    exit(2);
  }
}
function c1($s1, $o1) {
  echo "c1: $s1, $o1\n";
  // must display a warning
  $var=new java("java.lang.String", null);

  // not reached 
  echo "ERROR.\n"; 
  exit(3); 
}
function c2($b) {
  echo "c2: $b\n";
  return $b;
}
function c3 ($e) {
  echo "c3: $e\n";
  return 2;
}
$here=getcwd();
java_require("$here/../tests.php5/callback.jar");

$closure=java_closure();
$callbackTest=new java('Callback$Test', $closure);

if($callbackTest->test()) {
  echo "test okay\n";
  exit(0);
} else {
  echo "test failed\n";
  exit(1);
}
?>
