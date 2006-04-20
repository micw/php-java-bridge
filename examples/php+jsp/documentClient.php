<?php

$session = java_session();
$System = new JavaClass("java.lang.System");

/* The name of the remote document */
$name = "RMIdocument";

/* continue session? */
if(!$doc=$session->get("$name"))
  $session->put("$name", $doc=createDocument("$name", array()));

/* add pages to the remote document */
$doc->addPage(new java ("Page", 0, "this is page 1"));
$doc->addPage(new java ("Page", 0, "this is page 2"));

/* and print a summary */
print $doc->analyze() . "\n";

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
  $initial = new java("javax.naming.InitialContext", $serverArgs);
  
  try {
    // find the service
    $objref  = $initial->lookup("$jndiname");
    
    // access the home interface
    $DocumentHome = new JavaClass("DocumentHome");
    $PortableRemoteObject = new JavaClass("javax.rmi.PortableRemoteObject");
    $home = $PortableRemoteObject->narrow($objref, $DocumentHome);
    
    // create a new remote document and return it
    $doc = $home->create();
  } catch (JavaException $ex) {
    echo "Could not create remote document. Have you deployed documentBean.jar?<br>\n";
    echo "{$ex->getCause()} <br>\nin file: {$ex->getFile()}<br>\nline:{$ex->getLine()}\n";
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
