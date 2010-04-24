<?php
$s = <<<EOF
<?php 
/* wrapper for Java.inc */ 

if(!function_exists("java_get_base")) require_once("Java.inc"); 

if (\$java_script = java_getHeader("X_JAVABRIDGE_INCLUDE", \$_SERVER)) {
  if (\$java_script!="@" && ((\$_SERVER['REMOTE_ADDR']=='127.0.0.1') || (!strncmp(\$_SERVER['DOCUMENT_ROOT'], \$java_script, strlen(\$_SERVER['DOCUMENT_ROOT']))))) {
    chdir (dirname (\$java_script));
    require_once(\$java_script);
  }
  java_call_with_continuation();
}
?>

EOF;

file_put_contents($argv[1], $s);
?>
