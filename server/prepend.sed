$i\
if ($java_script = java_getHeader("X_JAVABRIDGE_INCLUDE", $_SERVER)) { \
  if ($java_script!="@") require($java_script); \
  if(!java_getHeader("X_JAVABRIDGE_INCLUDE_ONLY", $_SERVER)) java_context()->call(java_closure()); \
}
$a\
?>
