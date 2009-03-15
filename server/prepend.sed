$i\
if ($java_script = java_getHeader("X_JAVABRIDGE_INCLUDE", $_SERVER)) { \
  if ($java_script!="@") ((include($java_script)) or trigger_error("Cannot read script. PHP option allow_url_include is Off. Either set this option or remove engine.eval(script) from your Java code.", E_USER_WARNING)); \
  java_context()->call(java_closure()); \
}