#!/usr/bin/php

<?php
//
// this test must be called twice with a standalone or J2EE back end
//
if (!extension_loaded('java')) {
  if (!(include_once("java/Java.php"))&&!(PHP_SHLIB_SUFFIX=="so" && dl('java.so'))&&!(PHP_SHLIB_SUFFIX=="dll" && dl('php_java.dll'))) {
    echo "java extension not installed.";
    exit(2);
  }
}

@java_reset();
$here=getcwd();
java_require("$here/arrayToString.jar");
$Thread = new JavaClass("java.lang.Thread");
$loader = $Thread->currentThread()->getContextClassLoader();
$Class = new JavaClass("java.lang.Class");
$class = $Class->forName("ArrayToString", false, $loader);
$class2 = $loader->loadClass("ArrayToString");
$System = new JavaClass("java.lang.System");
$hc1 = $System->identityHashCode($class) ;
$hc2 = $System->identityHashCode($class2);
$rc = $hc1==$hc2;
if($rc) echo "=>$hc1, $hc2. test okay\n";
else echo "=>$hc1, $hc2. test failed\n";
return $rc;
?>
