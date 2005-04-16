#!/usr/bin/php

<?php
if(!extension_loaded('java')) {
  dl('java.' . PHP_SHLIB_SUFFIX);
}

$ar=array(1, 2, 3);
$v=new java("java.util.Vector", $ar);
echo $v->capacity(); 

?>
