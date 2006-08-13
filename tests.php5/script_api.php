<?php

define("SCRIPT_NAME", "/tmp/t.php");
java_require("php-script.jar;script-api.jar");

$IRunnable = new JavaClass("java.lang.Runnable");
java_context()->call(java_closure(new Runnable(), null, $IRunnable)) || test();

/**
 * This class implements IRunnable
 */
class Runnable {
  function run() {
    $out = new java("java.io.FileOutputStream", SCRIPT_NAME.".out", true);
    $Thread = new JavaClass("java.lang.Thread");
    $nr = java_context()->getAttribute("nr", 100); //engine scope
    for($i=0; $i<10; $i++) {
      $out->write(ord("$nr"));
      $Thread->yield();
    }
    $out->close();
  }
}

/**
 * Allocate a thread and an associated PHP continuation
 * @arg nr The data will be passed to the PHP run method
 * @return the PHP continuation.
 */
function createRunnable($nr) {
  global $IRunnable;
  $r = new java("php.java.script.PhpScriptEngine");
  $r->eval(new java("java.io.FileReader", SCRIPT_NAME));
  $r->put("nr", $nr);
  $r->put("thread",new java("java.lang.Thread",$r->getInterface($IRunnable)));
  return $r;
}

/**
 * Creates and starts two PHP threads 
 * concurrently writing to SCRIPTNAME.out
 */
function test() {
  $runnables = array();
  for($i=1; $i<=2; $i++) {
    $runnables[$i]=createRunnable($i);
  }
  for($i=1; $i<=2; $i++) {
    $runnables[$i]->get("thread")->start();
  }
  for($i=1; $i<=2; $i++) {
    $runnables[$i]->get("thread")->join();
    $runnables[$i]->release(); // release the PHP continuation
  }
}

?>
