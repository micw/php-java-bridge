/*-*- mode: C; tab-width:4 -*-*/

/**\file 
 * This file contains most of the internal function
 * prototypes. The public API is in api.h and php_java.h.
 */


#ifndef JAVA_BRIDGE_H
#define JAVA_BRIDGE_H

#include "php_java.h"

#include <unistd.h>

/* PHP Includes */
#include "php_wrapper.h"
#include "zend_compile.h"
#include "php_ini.h"
#include "php_globals.h"

#define IS_EXCEPTION 86

extern void EXT_GLOBAL(result)(pval* arg, short ignoreNonJava, pval*presult TSRMLS_DC);
extern int EXT_GLOBAL(get_jobject_from_object)(zval *object, long *obj TSRMLS_DC);
#ifdef ZEND_ENGINE_2
extern void EXT_GLOBAL(store_jobject)(zval *presult, long id TSRMLS_DC);
extern zend_object_value EXT_GLOBAL(create_object)(zend_class_entry *class_type TSRMLS_DC);
extern zend_object_value EXT_GLOBAL(create_exception_object)(zend_class_entry *class_type TSRMLS_DC);
#endif
extern short EXT_GLOBAL(invoke)(char*name, long object, int arg_count, zval***arguments, short ignoreNonJava, pval*presult TSRMLS_DC) ;
enum constructor {CONSTRUCTOR_NONE, CONSTRUCTOR};
extern short EXT_GLOBAL(call_function_handler)(INTERNAL_FUNCTION_PARAMETERS, char*name, enum constructor constructor, short createInstance, pval *object, int argc, zval***argv);
extern short EXT_GLOBAL(set_property_handler)(char*name, zval *object, zval *value, zval *return_value);
extern short EXT_GLOBAL(get_property_handler)(char*name, zval *object, zval *return_value);

extern void EXT_GLOBAL(destructor)(zend_rsrc_list_entry *rsrc TSRMLS_DC);

extern proxyenv *EXT_GLOBAL(createSecureEnvironment) (int peer, short (*handle_request)(proxyenv *env), short (*handle_cached)(proxyenv *env), char*server, short is_local, struct sockaddr*saddr);
extern void EXT_GLOBAL(redirect_pipe)(proxyenv*env);

extern void EXT_GLOBAL(unlink_channel)(proxyenv*env);
extern const char*EXT_GLOBAL(get_channel)(proxyenv*env);

extern short EXT_GLOBAL (begin_async) (proxyenv*env);
extern void EXT_GLOBAL (end_async) (proxyenv*env);
extern void EXT_GLOBAL (check_session) (proxyenv *env TSRMLS_DC);
extern void EXT_GLOBAL (setResultWith_context) (char*key, char*val, char*path);
extern short EXT_GLOBAL (option_set_by_user) (short option, int where);

extern void EXT_GLOBAL(update_hosts)(const char*new_value);
extern void EXT_GLOBAL(update_servlet)(const char*new_value);
extern void EXT_GLOBAL(update_socketname)(const char*new_value);
extern void EXT_GLOBAL(update_persistent_connections)(const char*new_value);
extern void EXT_GLOBAL(update_compatibility)(const char*new_value);

extern void EXT_GLOBAL (init_cfg) (TSRMLS_D);
extern void EXT_GLOBAL(shutdown_library) (void);
extern void EXT_GLOBAL(destroy_cfg) (int);
extern void EXT_GLOBAL(sys_error)(const char *str, int code);

extern unsigned char EXT_GLOBAL (get_mode) (void);

extern void EXT_GLOBAL(mktmpdir) ();
extern void EXT_GLOBAL(rmtmpdir) ();

extern char *EXT_GLOBAL(getDefaultSessionFactory)(TSRMLS_D);
#endif
