$i\
include(array_key_exists("HTTP_X_JAVABRIDGE_INCLUDE", $$_SERVER)?$$_SERVER["HTTP_X_JAVABRIDGE_INCLUDE"]:$$_SERVER["X_JAVABRIDGE_INCLUDE"]);java_context()->call(java_closure());
