#!/usr/bin/php
<?php
try {
  try {
    new java("java.lang.String", null);
  } catch(java_exception $ex) {
    // print the stack trace to $trace
    $trace = new java("java.io.ByteArrayOutputStream");
    $ex->printStackTrace(new java("java.io.PrintStream", $trace));

    print $trace;
   return 0;
  }
} catch (exception $err) {
  print "An error occured: " . $err->toString() . "\n";
  return 1;
}