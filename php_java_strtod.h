/*-*- mode: C; tab-width:4 -*-*/

/**\file 
 * strtod implementation which does not depend on the LANG setting
 *
 * It is used to deserialize php values.
 *
 */

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
