#!/usr/bin/php

<?php
if(!extension_loaded('java')) {
  dl('java.' . PHP_SHLIB_SUFFIX);
}

$here=trim(`pwd`);
java_set_library_path("$here/arrayArray.jar");
$ReflectArray = new java_class("java.lang.reflect.Array");
$Array = new java_class("ArrayArray");
$arrayArray=$Array->create(10);

$String=new java_class("java.lang.String");
for($i=0; $i<$ReflectArray->getLength($arrayArray); $i++)
	echo $arrayArray[$i]->array . " " .$arrayArray[$i]->array[0] . "\n";

echo "\n";

foreach($arrayArray as $value)
	echo $value->array . " " . $value->array[0] . "\n";


?>
