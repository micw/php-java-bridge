#!/usr/bin/php

<?php 
if(!extension_loaded('java')) {
  dl('java.' . PHP_SHLIB_SUFFIX);
}

$n=500;
for ($i = 0; $i < $n; $i++) { 
  $temp_array[$i]="$i";
}

// post temp array to java (as hash)
$hash = new java("java.util.Hashtable", $temp_array);

// post temp array to java (as arrayList)
$hashMap = new java("java.util.HashMap", $temp_array);

// receive Hashtable and Hashmap in one request
$php_hash=java_get_values($hash);
$php_hashMap=java_get_values($hashMap);


echo "array from java_get_values:\n";
for ($i = 0; $i < $n; $i++) { 
  echo "($php_hash[$i],$php_hashMap[$i]) ";
}
echo "\n\n";

echo "the same, but slower (uses $n*4 round trips):\n";
for ($i = 0; $i < $n; $i++) { 
  echo "($hash[$i],$hashMap[$i])";
}
echo "\n";
?>
