<?php

// please adjust the following line if your server runs on a different host
$app_url="iiop://localhost:3700"; // e.g.: "iiop://192.168.5.1:3700";

$here=trim("`pwd`");
$app_server=getenv("app_server");
if(!$app_server || strlen($app_server)==0) {
	echo ('app_server environment variable not set. Assuming that you have copied the j2ee.jar and the documentClient.jar from the remote application server into the local directory.');
}

// add the J2EE classes and the documentClient.jar generated by the AS
// to our classpath
$location1="$app_server/domains/domain1/applications/j2ee-modules/documentBean/documentClient.jar";
$location2="$here/documentClient.jar";
$j2ee_jar1="$app_server/lib/j2ee.jar";
$j2ee_jar2="$here/j2ee.jar";
java_set_library_path("$location1;$location2;$j2ee_jar1;j2ee_jar2");

// convenience function which connects to the AS server using the URL
// $url, looks up the service $jndiname and returns a new remote
// document.
function createDocument($url, $jndiname) {

        // Set up the initial context.  The following code is AS
        // specific, WebLogic for example uses a different URL.
        // Please consult your AS documentation for details.
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

// convenience function which destroys the reference to the remote
// document
function destroyDocument($doc) {
	$doc->remove();
}

?>
