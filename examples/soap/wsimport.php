<?php

$HOST="192.168.6.203";
$PORT=8080;
$SERVICE="jaxws-fromwsdl/addnumbers?wsdl";

$java=ini_get("java.java"); if(!$java) $java="java";
$wsimport_dir=getcwd(); $ws_libs="-Djava.ext.dirs=/home/jostb/SUNWappserver/lib";
system("$java $ws_libs com.sun.tools.ws.WsImport -keep http://$HOST:$PORT/$SERVICE");

?>
