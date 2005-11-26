/*-*- mode: C; tab-width:4 -*-*/

/**\file
 * snprintf version that does not depend on the LANG setting.
 *
 * It is used to serialize php values.
 */  

#ifndef JAVA_SNPRINTF_H
#define JAVA_SNPRINTF_H

#ifndef ZEND_ENGINE_2
extern int EXT_GLOBAL(snprintf) (char *buf, size_t len, const char *format,...);
#else
# if EXTENSION == JAVA
#  define java_ap_php_snprintf ap_php_snprintf 
# elif EXTENSION == MONO
#  define mono_ap_php_snprintf ap_php_snprintf 
# else
#  error unknown EXTENSION
# endif
#endif

#endif
