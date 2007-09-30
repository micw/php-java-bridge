<?php
if(!extension_loaded("mono")) {
  $n = php_sapi_name();
  if($n=="cgi"||$n=="cgi-fcgi"||$n=="cli") @dl("mono.so")||@dl('php_mono.dll');
 }
if(!extension_loaded("mono")) {
  $port= (isset($_SERVER['SERVER_PORT']) && (($_SERVER['SERVER_PORT'])>1024)) ? $_SERVER['SERVER_PORT'] : '8080';
  require_once("http://localhost:${port}/JavaBridge/java/Mono.inc");
 }

phpinfo();
print "\n\n";

$v = new Mono("java.lang.System");
$arr=$v->getProperties();

foreach ($arr as $key => $value) {
  print $key . " -> " .  $value . "<br>\n";
}
?>

