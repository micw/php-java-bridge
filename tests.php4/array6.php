#!/usr/bin/php

<?php
if(!extension_loaded('java')) {
  dl('java.' . PHP_SHLIB_SUFFIX);
}

$here=trim(`pwd`);
java_set_library_path("$here/array6.jar");

$testvar = new Java('Array6');
$testobj = $testvar->test();
echo $testobj[0][0][0][0][0][1];
echo $testobj[0][0][0][0][1][0];
echo $testobj[0][0][0][1][0][0];
echo $testobj[0][0][1][0][0][0];
echo $testobj[0][1][0][0][0][0];
echo $testobj[1][0][0][0][0][0];
echo "\n";
?>
