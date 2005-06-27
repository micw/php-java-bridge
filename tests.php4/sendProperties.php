#!/usr/bin/php

<?php
if(!extension_loaded('java')) {
  dl('java.' . PHP_SHLIB_SUFFIX);
}

$h=array("k"=>"v", "k2"=>"v2");
$m=new java("java.util.Properties",$h);
echo $m->size() . " " . $m->getProperty("k", "ERROR") . " \n";
if($m->getProperty("k2", "ERROR") != "v2") {
  echo "ERROR\n";
  exit(1);
}
echo "test okay\n";
exit(0);
?>
