#!/usr/bin/php

<?php
if (!extension_loaded('java')) {
  if (!(require_once("http://127.0.0.1:8080/JavaBridge/java/Java.inc"))) {
    echo "java extension not installed.";
    exit(2);
  }
}

$v=new java("java.util.HashMap");
for($i=0; $i<100; $i++) {
  $v->put($i, $i);
}

foreach($v as $key=>$val) {
  if($key!=java_values($val)) { echo "ERROR\n"; exit(1); }
}
echo "test okay\n";
exit(0);

?>
