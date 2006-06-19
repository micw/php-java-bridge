/*-*- mode: C; tab-width:4 -*-*/

/**\file
 * snprintf version that does not depend on the LANG setting.
 *
 * It is used to serialize php values.
 */  

#ifndef JAVA_SNPRINTF_H
#define JAVA_SNPRINTF_H

#ifdef snprintf
#undef snprintf
#endif 

#if !defined(ZEND_ENGINE_2) && !defined(__MINGW32__)
extern int EXT_GLOBAL(snprintf) (char *buf, size_t len, const char *format,...);
#else
#ifndef ZEND_ENGINE_2
extern int snprintf (char *buf, size_t len, const char *format,...);
# define ap_php_snprintf snprintf
#endif

# if EXTENSION == JAVA
#  define java_snprintf ap_php_snprintf 
# elif EXTENSION == MONO
#  define mono_snprintf ap_php_snprintf 
# else
#  error unknown EXTENSION
# endif
#endif

#endif
