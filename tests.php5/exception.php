#!/usr/bin/php
<?php
try {
  $here=trim(`pwd`);
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
  } catch (java_exception $ex) {
    echo "exception ". $ex->toString() ." --> " . $ex->getID() . "\n";
    return ($ex->getID() == 42) ? 0 : 3; 
  }
} catch (exception $err) {
  print "$err \n";
  return 4;
}