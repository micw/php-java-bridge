#!/usr/bin/php

<?php
if (!extension_loaded('java')) {
  if (!(require_once("http://127.0.0.1:8080/JavaBridge/java/Java.inc"))) {
    echo "java extension not installed.";
    exit(2);
  }
}

$Thread = new JavaClass("java.lang.Thread");
$current= $Thread->currentThread();
try {
  $thread = new java("java.lang.Thread", $current->getThreadGroup(), "simple");
  echo "Test failed or no javabridge.policy installed\n";
  exit(1);
} catch (JavaException $e) {
  $cause = $e->getCause();
  if(!java_instanceof($cause, new JavaClass("java.lang.SecurityException"))) {
    echo "test failed\n";
    exit(2);
  }
}
try {
  $thread = new java("java.lang.Thread");
  echo "Test failed or no javabridge.policy installed\n";
  exit(3);
} catch (JavaException $e) {
  $cause = $e->getCause();
  if(!java_instanceof($cause, new JavaClass("java.lang.SecurityException"))) {
    echo "test failed\n";
    exit(4);
  }
}
echo "test okay\n";
exit(0);
?>
