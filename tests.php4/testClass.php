#!/usr/bin/php

<?php
if(!extension_loaded('java')) {
  dl('java.' . PHP_SHLIB_SUFFIX);
}

$class = new java_class("java.lang.Class");
$arr = $class->getConstructors();
if(0==sizeof($array)) {
     echo "test okay\n";
     exit(0);
}
echo "error\n";
exit(1);

?>
