/*-*- mode: C; tab-width:4 -*-*/

#ifndef JAVA_BRIDGE_H
#define JAVA_BRIDGE_H

/* PHP Includes */
#include "php_wrapper.h"
#include "zend_compile.h"
#include "php_ini.h"
#include "php_globals.h"

#include "php_java.h"
#include "protocol.h"

#define IS_EXCEPTION 86

extern int java_get_jobject_from_object(zval *object, jobject *obj TSRMLS_DC);
extern void php_java_invoke(char*name, jobject object, int arg_count, zval**arguments, pval*presult TSRMLS_DC) ;
extern void php_java_call_function_handler(INTERNAL_FUNCTION_PARAMETERS, char*name, short constructor, short createInstance, pval *object, int argc, zval**argv);
extern short php_java_set_property_handler(char*name, zval *object, zval *value, zval *return_value);
extern short php_java_get_property_handler(char*name, zval *object, zval *return_value);

extern void php_java_destructor(zend_rsrc_list_entry *rsrc TSRMLS_DC);

#endif
