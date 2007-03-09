<?php

require_once("java/Java.inc");

$session = java_session();
$System = new JavaClass("java.lang.System");

/* The name of the remote document */
$name = "RMIdocument";

/* continue session? */
if(!$doc=$session->get("$name"))
  $session->put("$name", $doc=createDocument("$name", array()));

try {
  /* add pages to the remote document */
  $doc->addPage(new Java ("Page", 0, "this is page 1"));
  $doc->addPage(new Java ("Page", 0, "this is page 2"));

  /* and print a summary */
  print java_values($doc->analyze()) . "\n";
} catch (JavaException $ex) {
  $cause = $ex->getCause(); if(is_null($ex->getCause())) $cause = $ex;
  echo "Could not access remote document. <br>\n";
  echo "$cause <br>\nin file: {$ex->getFile()}<br>\nline:{$ex->getLine()}\n";
  $session->destroy();
  exit (2);
}
/* destroy the remote document and remove the session */
if($_GET['logout']) {
  echo "bye...\n";
  destroyDocument($doc);
  $session->destroy();
 }

/* Utility procedures */

/*
 * convenience function which connects to the AS server using the URL
 * $url, looks up the service $jndiname and returns a new remote
 * document.
 * @param jndiname The name of the remote document, see sun-ejb-jar.xml
 * @param serverArgs An array describing the connection parameters.
 */
function createDocument($jndiname, $serverArgs) {
  // find initial context
  $initial = new Java("javax.naming.InitialContext", $serverArgs);
  
  try {
    // find the service
    $objref  = $initial->lookup("$jndiname");
    
    // access the home interface
    $DocumentHome = new JavaClass("DocumentHome");
    $PortableRemoteObject = new JavaClass("javax.rmi.PortableRemoteObject");
    $home = $PortableRemoteObject->narrow($objref, $DocumentHome);
    if(is_null($home)) throw new JavaException("java.lang.NullPointerException", "home");

    // create a new remote document and return it
    $doc = $home->create();
  } catch (JavaException $ex) {
    $cause = $ex->getCause(); if(is_null($ex->getCause())) $cause = $ex;
    echo "Could not create remote document. Have you deployed documentBean.jar?<br>\n";
    echo "$cause <br>\nin file: {$ex->getFile()}<br>\nline:{$ex->getLine()}\n";
    exit (1);
  }
  
  return $doc;
}

/*
 * convenience function which destroys the reference to the remote
 * document
 * @param The remote document.
 */
function destroyDocument($doc) {
  $doc->remove();
}

?>
