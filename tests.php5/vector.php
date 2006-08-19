#!/usr/bin/php

<?php
if (!extension_loaded('java')) {
  if (!(include_once("java/Java.php"))&&!(PHP_SHLIB_SUFFIX=="so" && dl('java.so'))&&!(PHP_SHLIB_SUFFIX=="dll" && dl('php_java.dll'))) {
    echo "java extension not installed.";
    exit(2);
  }
}

$v=new java("java.util.Vector");
$v->setSize(10);

foreach($v as $key=>$val) {
  $v[$key]=$key;
}
foreach($v as $key=>$val) {
  echo java_values($val);
}
echo "\n";
for($i=0; $i<10; $i++) {
  echo java_values($v[$i]);
}
echo "\n";
if(java_values($v[9])==9) {
  echo "test okay\n";
  exit(0);
} else {
  echo "ERROR\n";
  exit(1);
}
?>
