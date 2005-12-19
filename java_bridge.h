/*-*- mode: C; tab-width:4 -*-*/

/**\file
 * Mediates between php and the server.
 *
 * Creates the proxyenv structure and handles invoke, get/set
 * property.
 */


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
#ifdef ZEND_ENGINE_2
extern void EXT_GLOBAL(store_jobject)(zval *presult, long id TSRMLS_DC);
extern zend_object_value EXT_GLOBAL(create_object)(zend_class_entry *class_type TSRMLS_DC);
extern zend_object_value EXT_GLOBAL(create_exception_object)(zend_class_entry *class_type TSRMLS_DC);
#endif
extern void EXT_GLOBAL(invoke)(char*name, long object, int arg_count, zval**arguments, short ignoreNonJava, pval*presult TSRMLS_DC) ;
enum constructor {CONSTRUCTOR_NONE, CONSTRUCTOR};
extern void EXT_GLOBAL(call_function_handler)(INTERNAL_FUNCTION_PARAMETERS, char*name, enum constructor constructor, short createInstance, pval *object, int argc, zval**argv);
extern short EXT_GLOBAL(set_property_handler)(char*name, zval *object, zval *value, zval *return_value);
extern short EXT_GLOBAL(get_property_handler)(char*name, zval *object, zval *return_value);

extern void EXT_GLOBAL(destructor)(zend_rsrc_list_entry *rsrc TSRMLS_DC);

extern proxyenv *EXT_GLOBAL(createSecureEnvironment) (int peer, void (*handle_request)(proxyenv *env), char*server, short is_local, struct sockaddr*saddr);
extern void EXT_GLOBAL(redirect)(proxyenv*env, char*redirect_port, char*channel_in, char*channel_out TSRMLS_DC);

extern void EXT_GLOBAL(init_channel)(TSRMLS_D);
extern void EXT_GLOBAL(destroy_channel)(TSRMLS_D);
extern const char*EXT_GLOBAL(get_channel)(TSRMLS_D);

extern void EXT_GLOBAL (protocol_end) (proxyenv *env);
extern void EXT_GLOBAL (check_context) (proxyenv *env TSRMLS_DC);
extern void EXT_GLOBAL (setResultWith_context) (char*key, char*val, char*path);
extern short EXT_GLOBAL (option_set_by_user) (short option, int where);
extern void EXT_GLOBAL (init_cfg) (TSRMLS_D);
extern void EXT_GLOBAL(shutdown_library) (void);
extern void EXT_GLOBAL(destroy_cfg) (int);
extern void EXT_GLOBAL(sys_error)(const char *str, int code);
  

extern unsigned char EXT_GLOBAL (get_mode) (void);

#endif
