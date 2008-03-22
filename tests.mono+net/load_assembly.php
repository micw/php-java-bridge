<?php require_once("mono/Mono.inc");

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
