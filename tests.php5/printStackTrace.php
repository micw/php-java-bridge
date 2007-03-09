#!/usr/bin/php

<?php

if (!extension_loaded('java')) {
  if (!(require_once("http://127.0.0.1:8080/JavaBridge/java/Java.inc"))) {
    echo "java extension not installed.";
    exit(2);
  }
}

try {
  try {
    new java("java.lang.String", null);
  } catch(JavaException $ex) {
    if(!($ex instanceof java_exception)) {
      echo "TEST FAILED: The exception is not a java exception!\n";
      return 2;
    }
  }

  try {
    new java("java.lang.String", null);
  } catch(java_exception $ex) {
    // print the stack trace to $trace
    // note that a simple "echo (string)$ex" also prints the stack trace
    $trace = new java("java.io.ByteArrayOutputStream");
    $ex->printStackTrace(new java("java.io.PrintStream", $trace));

    echo "Exception occured:" . $trace->__toString() . "\n";
    return 0;
  }
} catch (exception $err) {
  print "An error occured: $err\n";
  return 1;
}
