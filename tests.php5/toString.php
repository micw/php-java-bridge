#!/usr/bin/php
<?php

if(!extension_loaded('java')) {
  dl('java.' . PHP_SHLIB_SUFFIX);
}

$Object = new java_class ("java.lang.Object");
$object = $Object->newInstance();

// test __toString()
// should display "class java.lang.Object"
echo $Object; echo "\n";

// test cast to string
// should display "class java.lang.Object"
echo "" . $Object . "\n";

echo "$object\n";
?>
