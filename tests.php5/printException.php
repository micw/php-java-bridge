#!/usr/bin/php

<?php
if (!extension_loaded('java')) {
  if (!(require_once("http://127.0.0.1:8080/JavaBridge/java/Java.inc"))) {
    echo "java extension not installed.";
    exit(2);
  }
}

function fault() {
  new java("java.lang.String", null);
}

function me() {
  $environment = java_closure();
  $Proxy = new JavaClass("java.lang.reflect.Proxy");
  $proc = $Proxy->getInvocationHandler($environment);

  $proc->invoke($environment, "fault", array());
}  

function call() {
  me();
}
call();

?>
