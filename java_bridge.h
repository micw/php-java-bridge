/*-*- mode: C; tab-width:4 -*-*/

#ifndef JAVA_BRIDGE_H
#define JAVA_BRIDGE_H

/* PHP Includes */
#include "php_wrapper.h"
#include "zend_compile.h"
#include "php_ini.h"
#include "php_globals.h"

#include "php_java.h"

#define IS_EXCEPTION 86

extern void EXT_GLOBAL(result)(pval* arg, short ignoreNonJava, pval*presult TSRMLS_DC);
extern int EXT_GLOBAL(get_jobject_from_object)(zval *object, long *obj TSRMLS_DC);
extern void EXT_GLOBAL(invoke)(char*name, long object, int arg_count, zval**arguments, short ignoreNonJava, pval*presult TSRMLS_DC) ;
enum constructor {CONSTRUCTOR_NONE, CONSTRUCTOR};
extern void EXT_GLOBAL(call_function_handler)(INTERNAL_FUNCTION_PARAMETERS, char*name, enum constructor constructor, short createInstance, pval *object, int argc, zval**argv);
extern short EXT_GLOBAL(set_property_handler)(char*name, zval *object, zval *value, zval *return_value);
extern short EXT_GLOBAL(get_property_handler)(char*name, zval *object, zval *return_value);

extern void EXT_GLOBAL(destructor)(zend_rsrc_list_entry *rsrc TSRMLS_DC);

extern proxyenv *EXT_GLOBAL(createSecureEnvironment) (int peer, void (*handle_request)(proxyenv *env), char*server, short is_local);
extern void EXT_GLOBAL (protocol_end) (proxyenv *env);

unsigned char EXT_GLOBAL (get_mode) ();

#endif
