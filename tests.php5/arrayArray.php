#!/usr/bin/php

<?php
if (!extension_loaded('java')) {
  if (!(require_once("http://127.0.0.1:8080/JavaBridge/java/Java.inc"))) {
    echo "java extension not installed.";
    exit(2);
  }
}

$here=realpath(dirname($_SERVER["SCRIPT_FILENAME"]));
if(!$here) $here=getcwd();
java_set_library_path("$here/arrayArray.jar");
$Array = new java_class("ArrayArray");
$arrayArray=$Array->create(10);

$String=new java_class("java.lang.String");
for($i=0; $i<10; $i++) {
	$ar = $arrayArray[$i]->array;
	echo java_cast($ar,"S") . " " .java_cast($ar[0],"S") . "\n"; 
}

echo "\n";

foreach($arrayArray as $value) {
	$ar = $value->array;
	echo java_cast($ar,"S") . " " .java_cast($ar[0],"S") ."\n";
}


?>
