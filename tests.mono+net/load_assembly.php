#!/usr/bin/php
<?php

if(!extension_loaded("mono")) {
  $n = php_sapi_name();
  if($n=="cgi"||$n=="cgi-fcgi"||$n=="cli") @dl("mono.so")||@dl('php_mono.dll');
 }
if(!extension_loaded("mono")) {
  require_once("Mono.inc");
 }

$here=getcwd();
mono_require("$here/sample_lib.dll");

$ArrayToString=new MonoClass("sample.ArrayToString");


// create long array ...
$length=10;
for($i=0; $i<$length; $i++) {
  $arr[$i]=$i;
}

// ... and post it to the VM.  Should print integers
print "integer array: ". $ArrayToString->Convert($arr) . "\n";


// double
for($i=0; $i<$length; $i++) {
  $arr[$i]=$i +1.23;
}

// should print doubles
print "double array: ". $ArrayToString->Convert($arr) . "\n";


// boolean
for($i=0; $i<$length; $i++) {
  $arr[$i]=$i%2?true:false;
}

// should print booleans
print "boolean array: ". $ArrayToString->Convert($arr) ."\n";


?>
