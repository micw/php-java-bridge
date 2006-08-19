#!/usr/bin/php

<?php 
if (!extension_loaded('java')) {
  if (!(include_once("java/Java.php"))&&!(PHP_SHLIB_SUFFIX=="so" && dl('java.so'))&&!(PHP_SHLIB_SUFFIX=="dll" && dl('php_java.dll'))) {
    echo "java extension not installed.";
    exit(2);
  }
}
ini_set("max_execution_time", 0);

$System=new JavaClass("java.lang.System");
$n=10000;
for ($i = 0; $i < $n; $i++) { 
  $temp_array[$i]="$i";
}

// post temp array to java (as hash)
$hash = new java("java.util.Hashtable", $temp_array);

// post temp array to java (as arrayList)
$hashMap = new java("java.util.HashMap", $temp_array);

$now = $System->currentTimeMillis();
// receive Hashtable and Hashmap in one request
$php_hash=java_get_values($hash);
$php_hashMap=java_get_values($hashMap);


echo "array from java_get_values:\n";
for ($i = 0; $i < $n; $i++) { 
  $val = "($php_hash[$i],$php_hashMap[$i]) ";
}
$now=$System->currentTimeMillis()-$now;
echo "$now (ms)\n\n";

$now = $System->currentTimeMillis();
echo "the same, but slower (uses $n*4 round trips):\n";
for ($i = 0; $i < $n; $i++) { 
  $val = "($hash[$i],$hashMap[$i])";
}
$now=$System->currentTimeMillis()-$now;
echo "$now (ms)\n";
?>
