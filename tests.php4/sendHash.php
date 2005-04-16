#!/usr/bin/php

<?php
if(!extension_loaded('java')) {
  dl('java.' . PHP_SHLIB_SUFFIX);
}

$h=array("k"=>"v", "k2"=>"v2");
$m=new java("java.util.HashMap",$h);
echo $m->size(); 
?>
