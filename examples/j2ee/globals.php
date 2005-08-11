<?php

// please adjust the following line if your server runs on a different host
$app_url="iiop://localhost:3700"; // e.g.: "iiop://192.168.5.1:3700";

$here=getcwd();
$client="$here/documentBeanClient.jar";
$j2ee_jar="$here/j2ee.jar";
java_set_library_path("$client;$j2ee_jar");

/*
 * convenience function which connects to the AS server using the URL
 * $url, looks up the service $jndiname and returns a new remote
 * document.
 */
function createDocument($url, $jndiname) {

        /*
         * Set up the initial context.  The following code is AS
         * specific, WebLogic for example uses a different URL.
         * Please consult your AS documentation for details.
         */
	$env = new java("java.util.Properties");
	$env->put("java.naming.factory.initial", "com.sun.jndi.cosnaming.CNCtxFactory");
	$env->put("java.naming.provider.url", $url);
	$initial = new java("javax.naming.InitialContext", $env);

        // find the service
	$objref  = $initial->lookup($jndiname);

        // access the home interface
	$DocumentHome = new java_class("DocumentHome");
	$PortableRemoteObject = new java_class("javax.rmi.PortableRemoteObject");
	$home = $PortableRemoteObject->narrow($objref, $DocumentHome);

        // create a new remote document and return it
	$doc = $home->create();
	return $doc;
}

/*
 * convenience function which destroys the reference to the remote
 * document
 */
function destroyDocument($doc) {
	$doc->remove();
}

?>
