#!/usr/bin/php

<?php

if (!extension_loaded('java')) {
  if (!(require_once("http://127.0.0.1:8080/JavaBridge/java/Java.inc"))) {
    echo "java extension not installed.";
    exit(2);
  }
}

try {
  $here=realpath(dirname($_SERVER["SCRIPT_FILENAME"]));
if(!$here) $here=getcwd();
  java_set_library_path("$here/exception.jar");
  $e = new java("Exception");

  // trigger ID=42
  $i = $e->inner;
  $i->o = new java("java.lang.Integer", 42);

  // should return 33
  $e->inner->meth(33);

  try {
    // should throw exception "Exception$Ex"
    $e->inner->meth(42);
    return 2;
  } catch (java_exception $exception) {
    echo "An exception occured: ".java_cast($exception, "S")."\n";

    $cause = $exception->getCause();
    echo "exception ".java_cast($cause,"S")." --> " . $cause->getID() . "\n";
    return (java_values($cause->getID()) == 42) ? 0 : 3; 
  }
} catch (exception $err) {
  print "unexpected: ".java_cast($err, "S")." \n";
  return 4;
}
