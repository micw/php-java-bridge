#!/usr/bin/php

<?php
if (!extension_loaded('java')) {
  if (!(PHP_SHLIB_SUFFIX=="so" && dl('java.so'))&&!(PHP_SHLIB_SUFFIX=="dll" && dl('php_java.dll'))) {
    echo "java extension not installed.";
    exit(2);
  }
}

class c {
  function toString() {
    return "class c";
  }
}
function toString() {
  return "top-level";
}
function getClosure() {
  $cl = java_closure(new c());
  $res = (string)$cl;
  if($res != "class c") throw new JavaException ("java.lang.Exception", "test failed");
  return $cl;
}

try {
  $cl = java_closure();
  $Proxy = new JavaClass("java.lang.reflect.Proxy");
  $proc = $Proxy->getInvocationHandler($cl);
  $ncl = $proc->invoke($cl, "getClosure", array());
  $res = (string)$ncl;
  if($res != "class c") throw new Exception ("test failed");
  echo "test okay<br>\n";
} catch (Exception $e) {
  echo "test failed: $e<br>\n";
  exit(1);
}

