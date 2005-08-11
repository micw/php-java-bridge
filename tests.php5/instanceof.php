#!/usr/bin/php

<?php

if (!extension_loaded('java')) {
  if (!(PHP_SHLIB_SUFFIX=="so" && dl('java.so'))&&!(PHP_SHLIB_SUFFIX=="dll" && dl('php_java.dll'))) {
    echo "java extension not installed.";
    exit(2);
  }
}

$ListClass=new java_class("java.util.ArrayList");
$list = new java("java.util.ArrayList");
$list->add(0);
$list->add("one");
$list->add(null);
$list->add(new java("java.lang.Object"));
$list->add(new java("java.lang.Long", 4));
$list->add($list); // the list now contains itself
$list->add(new java("java.lang.String", "last entry"));

foreach ($list as $key=>$value) {               
  echo "$key => ";
  if($value instanceof java) {
    if(java_instanceof($value, $ListClass)) {

      // second argument may also be an instance of a class
      if(!java_instanceof($value, $list)) exit(1);

      echo "[I have found myself!] ";
    } else {
      echo "[found java object: " .$value->toString() . "] ";
    }
  }
  echo "$value\n";
}
?>
