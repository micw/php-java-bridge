<?php

include("globals.php");

$doc=createDocument($app_url, "RMIdocument");

$doc->addPage(new java ("Page", 0, "this is page 1"));
$doc->addPage(new java ("Page", 0, "this is page 2"));
print $doc->analyze();

destroyDocument($doc);

?>

		