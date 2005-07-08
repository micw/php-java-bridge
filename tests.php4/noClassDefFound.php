#!/usr/bin/php

<?php
if(!extension_loaded('java')) {
  dl('java.' . PHP_SHLIB_SUFFIX);
}

$here=trim(`pwd`);
java_require("$here/noClassDefFound.jar");

$v=new java("NoClassDefFound");
echo $v->s . "\n";

if($v->s=="test okay") {
  exit(0);
} else {
  echo "test failed\n";
  exit(1);
}

?>
