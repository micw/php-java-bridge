#!/usr/bin/php

<?php
if(!extension_loaded('java')) {
  dl('java.' . PHP_SHLIB_SUFFIX);
}

$val=123456789123456.789;
$v=new java("java.lang.Double", $val);
echo $val . "\n";
echo $v->doubleValue();
echo "\n";

exit ($v->doubleValue() == $val);
?>
