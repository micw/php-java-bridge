<?php

class SERVER {const JBOSS=1, WEBSPHERE=2, SUN=3, ORACLE=4;}
$server=SERVER::JBOSS;

echo "connecting to server: ";

$here = getcwd();
switch($server) {
 case SERVER::JBOSS: 
   echo "jboss\n";
   $name = "DocumentEJB";
   $JBOSS_HOME=isset($JBOSS_HOME) ? $JBOSS_HOME : "/opt/jboss-4.0.2/";
   java_require("$JBOSS_HOME/client/;$here/documentBean.jar");
   $server=array("java.naming.factory.initial"=>
		 "org.jnp.interfaces.NamingContextFactory",
		 "java.naming.provider.url"=>
		 "jnp://127.0.0.1:1099");
   break;
 case SERVER::WEBSPHERE: 
   echo "websphere\n";
   $name = "RMIdocument";
   $WAS_HOME=isset($WAS_HOME) ? $WAS_HOME : "/opt/IBM/WebSphere/AppServer";
   java_require("$WAS_HOME/lib/;$here/documentBean.jar");
   $server=array("java.naming.factory.initial"=>
		 "com.ibm.websphere.naming.WsnInitialContextFactory",
		 "java.naming.provider.url"=>
		 "iiop://localhost:2809");
   break;
 case SERVER::SUN:
   echo "sun\n";
   $name = "RMIdocument";
   $app_server=isset($app_server) ? $app_server : "~/SUNWappserver";
   java_require("$app_server/lib/;$here/documentBean.jar");
   $server=array("java.naming.factory.initial"=>
		 "com.sun.jndi.cosnaming.CNCtxFactory",
		 "java.naming.provider.url"=>
		 "iiop://localhost:3700");
   break;
 }

try {
  $doc=createDocument("$name", $server);
} catch (JavaException $e) {
  echo "Could not create remote document. Have you deployed documentBean.jar?\n";
  echo $e->getCause() ."\n";
  exit (1);
}

/* add pages to the remote document */
$doc->addPage(new java ("Page", 0, "this is page 1"));
$doc->addPage(new java ("Page", 0, "this is page 2"));
/* and print a summary */
print $doc->analyze() . "\n";

destroyDocument($doc);


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
  
  // find the service
  $objref  = $initial->lookup("$jndiname");
  
  // access the home interface
  $DocumentHome = new JavaClass("DocumentHome");
  $PortableRemoteObject = new JavaClass("javax.rmi.PortableRemoteObject");
  $home = $PortableRemoteObject->narrow($objref, $DocumentHome);
  
  // create a new remote document and return it
  $doc = $home->create();
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
