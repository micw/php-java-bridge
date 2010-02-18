#!/bin/sh

echo '<?php /* wrapper for Java.inc */ if(!function_exists("java_get_base")) {require_once("Java.inc"); if ($java_script = java_getHeader("X_JAVABRIDGE_INCLUDE", $_SERVER)) {if ($java_script!="@") {chdir (dirname ($java_script)); require_once($java_script);}}; java_call_with_continuation();} ?>' >META-INF/java/JavaProxy.php
