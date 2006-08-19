#!/usr/bin/php

<?php
if (!extension_loaded('java')) {
  if (!(include_once("java/Java.php"))&&!(PHP_SHLIB_SUFFIX=="so" && dl('java.so'))&&!(PHP_SHLIB_SUFFIX=="dll" && dl('php_java.dll'))) {
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
