#!/usr/bin/php

<?php

if(!extension_loaded('java')) {
  dl('java.' . PHP_SHLIB_SUFFIX);
}

try {
  try {
    new java("java.lang.String", null);
  } catch(java_exception $ex) {
    // print the stack trace to $trace
    $trace = new java("java.io.ByteArrayOutputStream");
    $ex->printStackTrace(new java("java.io.PrintStream", $trace));

    echo "Exception occured:" . $trace . "\n";
    return 0;
  }
} catch (exception $err) {
  print "An error occured: $err\n";
  return 1;
}