#!/usr/bin/php

<?php
if(!extension_loaded('java')) {
  dl('java.' . PHP_SHLIB_SUFFIX);
}

$here=trim(`pwd`);
java_set_library_path("$here/arrayArray.jar");
$ReflectArray = new java_class("java.lang.reflect.Array");
$Array = new java_class("ArrayArray");
$n=60;
$arrayArray=$Array->create($n);

// Keep-Alive default is 15s, the following tests if the client
// re-opens the connection (the test will dump core if not).
$String=new java_class("java.lang.String");
for($i=0; $i<$n; $i++) {
	$ar = $arrayArray[$i]->array;
	echo $n-$i-1 . " ";
	sleep(1);
}

echo "\n";

?>
