<?php
// check if tomcat can close a re-directed connection.
// The client must send the context with the close
// command, otherwise the context will not be destroyed
$o=new java("java.lang.Object");
echo "Check that the log contains \"waiting for context\" and \"context finished\"\n";
?>

