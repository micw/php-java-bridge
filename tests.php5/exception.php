#!/usr/bin/php
<?php

$here=trim(`pwd`);
java_set_library_path("$here/exception.jar");
$e = new java("Exception");

$i = $e->inner;
$i->o = new java("java.lang.Integer", 42);

$e->inner->meth(33);
try {
  $e->inner->meth(42);
  return 0;
} catch (Exception $ex) {
  echo "--> " . $ex->getID() . "\n";
  return $ex->getID();
}
