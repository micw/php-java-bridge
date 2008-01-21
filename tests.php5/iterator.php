<?php

if(!extension_loaded('java')) 
  require_once("http://127.0.0.1:8080/JavaBridge/java/Java.inc");

$here=getcwd(); java_require("$here/hashSetFactory.jar");

$hash = java("HashSetFactory")->getSet();
$hash->add(1);
$hash->add(3);

foreach($hash as $key=>$val) {
  echo "$key=>$val\n";
}

?>
