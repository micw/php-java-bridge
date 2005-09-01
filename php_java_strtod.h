#ifndef JAVA_STRTOD_H
#define JAVA_STRTOD_H

#ifndef ZEND_ENGINE_2
extern double EXT_GLOBAL(strtod)(const char*, char**);
#else
#include "zend_strtod.h"

# if EXTENSION == JAVA
#  define java_strtod zend_strtod 
# elif EXTENSION == MONO
#  define mono_strtod zend_strtod
# else
#  error unknown EXTENSION
# endif
#endif

#endif
