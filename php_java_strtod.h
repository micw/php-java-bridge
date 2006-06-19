/*-*- mode: C; tab-width:4 -*-*/

/**\file 
 * strtod implementation which does not depend on the LANG setting
 *
 * It is used to deserialize php values.
 *
 */

#ifndef JAVA_STRTOD_H
#define JAVA_STRTOD_H

#if !defined(ZEND_ENGINE_2) && !defined(__MINGW32__)
extern double EXT_GLOBAL(strtod)(const char*, char**);
#else
#ifndef ZEND_ENGINE_2
extern double strtod(const char*, char**);
# define zend_strtod strtod
#else
# include "zend_strtod.h"
#endif

# if EXTENSION == JAVA
#  define java_strtod zend_strtod 
# elif EXTENSION == MONO
#  define mono_strtod zend_strtod
# else
#  error unknown EXTENSION
# endif
#endif

#endif
