#!/usr/bin/php

<?php
if (!extension_loaded('java')) {
  if (!(PHP_SHLIB_SUFFIX=="so" && dl('java.so'))&&!(PHP_SHLIB_SUFFIX=="dll" && dl('php_java.dll'))) {
    echo "java extension not installed.";
    exit(2);
  }
}

$here=realpath(dirname($_SERVER["SCRIPT_FILENAME"]));
java_set_library_path("$here/arrayArray.jar");
$Array = new java_class("ArrayArray");
$arrayArray=$Array->create(10);

$String=new java_class("java.lang.String");
for($i=0; $i<10; $i++) {
	$ar = $arrayArray[$i]->array;
	echo $ar . " " .$ar[0] . "\n"; 
}

echo "\n";

foreach($arrayArray as $value) {
	$ar = $value->array;
	echo $ar . " " .$ar[0] ."\n";
}


?>
