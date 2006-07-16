/*-*- mode: C; tab-width:4 -*-*/

/**\file 
 * This is the main entry point for the java extension. 

 * It contains the global structures and the callbacks required for
 * zend engine 1 and 2.
 *
 */

/* wait */
#include <sys/types.h>
#include <sys/wait.h>
/* miscellaneous */
#include <stdio.h>
#include <stdlib.h>
#include <assert.h>
#include <errno.h>

#include "php_java.h"
#include "php_globals.h"
#include "ext/standard/info.h"

#include "java_bridge.h"
#include "api.h"

#ifdef ZEND_ENGINE_2
#include "zend_interfaces.h"
#include "zend_exceptions.h"
#endif
#include "zend_extensions.h"

EXT_DECLARE_MODULE_GLOBALS(EXT)

/**
 * Holds the global configuration.
 * This structure is shared by all php instances
 */
struct cfg *EXT_GLOBAL (cfg)  = 0;

#ifdef __MINGW32__
static const int java_errno=0;
int *__errno (void) { return (int*)&java_errno; }
#ifdef ZEND_ENGINE_2
#define php_info_print_table_row(a, b, c) php_info_print_table_row_ex(a, "v", b, c)
#else
#define php_info_print_table_end() php_printf("</table><br />\n")
#endif
#endif
/**
 * Called when a new request starts.  Opens a connection to the
 * back-end, creates an instance of the proxyenv structure and clones
 * the servlet, hosts and ini_user flags.
 */
PHP_RINIT_FUNCTION(EXT) 
{
  if(!EXT_GLOBAL(cfg)->persistent_connections && JG(jenv)) {
	php_error(E_ERROR, "php_mod_"/**/EXT_NAME()/**/"(%d): Synchronization problem, rinit with active connection called. Cannot continue, aborting now. Please report this to: php-java-bridge-users@lists.sourceforge.net",59);
  }
  JG(is_closed)=0;
  return SUCCESS;
}

/**
 * Close or recycle the current connection.
 * If that failed, shut down all other connections as well.
 * @return always true
 */
static short shutdown_connections(TSRMLS_D) {
  proxyenv *current = JG(jenv);
  HashTable *connections = &JG(connections);
  short success = EXT_GLOBAL(close_connection) (JG(jenv), EXT_GLOBAL(cfg)->persistent_connections TSRMLS_CC);
  if(!success) {				/* error: close all connections */
	proxyenv **env;
	zend_hash_internal_pointer_reset(connections);
	while(SUCCESS==zend_hash_get_current_data(connections, (void**)&env)) {
	  if(*env!=current) {
		EXT_GLOBAL(activate_connection)(*env TSRMLS_CC);
		EXT_GLOBAL(close_connection) (*env, 0 TSRMLS_CC);
	  }
	  zend_hash_move_forward(connections);
	}
	zend_hash_clean(connections);
  }
  JG(jenv)=0;
  return 1;
}

/**
 * Called when the request terminates. Closes the connection to the
 * back-end, destroys the proxyenv instance.
 */
PHP_RSHUTDOWN_FUNCTION(EXT)
{
  shutdown_connections(TSRMLS_C);
  if(JG(cb_stack)) { 
	zend_stack_destroy(JG(cb_stack));
	efree(JG(cb_stack)); JG(cb_stack) = 0; 
  }
  JG(is_closed)=1;
  return SUCCESS;
}

static short can_reconnect(TSRMLS_D) {
  return EXT_GLOBAL(cfg)->persistent_connections &&
	!(*JG(jenv))->peer_redirected;
}
	
/** try calling the procedure again with a new connection, if
	persistent connections are enabled */
#define API_CALL(proc) \
  EXT_GLOBAL(proc)(INTERNAL_FUNCTION_PARAM_PASSTHRU) ||	\
  (can_reconnect(TSRMLS_C) &&							\
   shutdown_connections(TSRMLS_C) &&					\
   EXT_GLOBAL(proc)(INTERNAL_FUNCTION_PARAM_PASSTHRU))

/**
 * Proto: object java_last_exception_get(void)
 *
 * Get last Java exception
 * \deprecated Use PHP5 try/catch instead.
 */
EXT_FUNCTION(EXT_GLOBAL(last_exception_get))
{
  API_CALL(last_exception_get);
}


/**
 * Proto: void java_last_exception_clear(void)
 *
 * Clear last java extension.
 * \deprecated Use PHP5 try/catch instead.
*/
EXT_FUNCTION(EXT_GLOBAL(last_exception_clear))
{
  API_CALL(last_exception_clear);
}

/**
 * Proto: void java_set_file_encoding(string)
 *
 * Set the java file encoding, for example UTF-8 or ASCII. Needed
 * because php does not support unicode. All string to byte array
 * conversions use this encoding. Example: \code
 * java_set_file_encoding("ISO-8859-1"); \endcode
 */
EXT_FUNCTION(EXT_GLOBAL(set_file_encoding))
{
  API_CALL(set_file_encoding);
}


/**
 * Proto: void java_require(string path) or java_set_library_path(string path)
 *
 * Set the library path. Example: 
 * \code
 * java_require("foo.jar;bar.jar"); 
 * \endcode
 *
 * The .jar files should be stored in /usr/share/java or
 * extension_dir/lib one of its sub-directories. However, it is also
 * possible to fetch .jar files from a remote server, for example:
 * \code
 * java_require("http://php-java-bridge.sf.net/kawa.jar;...");
 * \endcode
 *
 * Note that the classloader isolates the loaded libraries: When you
 * call java_require("foo.jar"); java_require("bar.jar"), the classes
 * from foo cannot see the classes loaded from bar. If you get a
 * NoClassDefFound error saying that one of your classes cannot
 * access the library you have loaded, you must reset the back-end to
 * clear the loader cache and load your classes and the library in one
 * java_require() call.
 */
EXT_FUNCTION(EXT_GLOBAL(require))
{
  API_CALL(require);
}

/**
 * Proto:  bool java_instanceof(object object, object clazz)
 *
 * Tests if object is an instance of clazz. 
 * Example: 
 * \code
 * return($o instanceof Java && $c instanceof Java && java_instanceof($o, $c)); 
 * \endcode
 */
EXT_FUNCTION(EXT_GLOBAL(instanceof))
{
  API_CALL(instanceof);
}

/**
 * Proto: object java_session([string], [bool]) or object java_get_session([string], [bool])
 *
 * Return a session handle.  When java_session() is called without 
 * arguments, the session is shared with java.
 * Example: 
 * \code
 * java_get_session()->put("key", new Java("java.lang.Object"));
 * [...]
 * \endcode
 * The java components (jsp, servlets) can retrieve the value, for
 * example with:
 * \code getSession().getAttribute("key"); \endcode
 *
 * When java_get_session() is called with a session handle, the session
 * is not shared with java and no cookies are set. Example:
 * \code
 * java_get_session("myPublicApplicationStore")->put("key", "value");
 * \endcode
 *
 * When java_get_session() is called with a second argument set to true,
 * a new session is allocated, the old session is destroyed if necessary.
 * Example:
 * \code
 * java_get_session(null, true)->put("key", "val");
 * \endcode.
 * @see get_context()
 */
EXT_FUNCTION(EXT_GLOBAL(get_session))
{
  API_CALL(session);
}

/**
 * Proto: object java_context(void) or object java_get_context(void)
 *
 * Returns the jsr223 script context handle.
 *
 * Example which closes over the current environment and passes it back to java:
 * \code
 * java_get_context()->call(java_closure()) || die "Script should be called from java";
 * \endcode
 *
 * It is possible to access implicit web objects (the session, the
 * application store etc.) from the context. Please see the JSR223
 * documentation for details. Example:
 * \code
 * java_get_context()->getHttpServletRequest();
 * \endcode
 * @see get_session()
 */
EXT_FUNCTION(EXT_GLOBAL(get_context))
{
  API_CALL(context);
}

/**
 * Proto: string java_server_name(void) or string java_get_server_name(void)
 *
 * Returns the name of the back-end or null, if the back-end is not running. Example:
 * \code
 * $backend = java_get_server_name();
 * if(!$backend) wakeup_administrator("back-end not running");
 * echo "Connected to the back-end: $backend\n";
 * \endcode
 */
EXT_FUNCTION(EXT_GLOBAL(get_server_name))
{
  proxyenv *jenv;
  if (ZEND_NUM_ARGS()!=0) WRONG_PARAM_COUNT;

  jenv = EXT_GLOBAL(try_connect_to_server)(TSRMLS_C);
  if(jenv && (*jenv)->server_name) {
	RETURN_STRING((*jenv)->server_name, 1);
  }
  RETURN_NULL();
}

/**
 * Proto: void java_reset(void);
 *
 * Tries to reset the back-end to
 * its initial state. If the call succeeds, all 
 * caches are gone. 
 *
 * Example:
 * \code echo "Resetting back-end to initial state\n";
 * java_reset();
 * \endcode
 *
 * This procedure does nothing when the back-end runs
 * in a servlet environment or an application server. 
 */
EXT_FUNCTION(EXT_GLOBAL(reset))
{
  API_CALL(reset);
}

/**
 * Proto: void java_begin_document(void)
 *
 * Enters stream mode (asynchronuous protocol). The statements are
 * sent to the back-end in one XML stream.
*/
EXT_FUNCTION(EXT_GLOBAL(begin_document))
{
  API_CALL(begin_document);
}

/**
 * Proto: void java_end_document(void)
 *
 * Ends stream mode.
*/
EXT_FUNCTION(EXT_GLOBAL(end_document))
{
  API_CALL(end_document);
}

#ifndef ZEND_ENGINE_2
EXT_FUNCTION(EXT_GLOBAL(__sleep))
{
  API_CALL(serialize);
}
EXT_FUNCTION(EXT_GLOBAL(__wakeup))
{
  API_CALL(deserialize);
}
#endif

/** Proto: mixed java_values(val) or mixed java_get_values(object ob)
 * Evaluates the object and fetches its content, if possible.
 * A java array, Map or Collection object is returned
 * as a php array. An array, Map or Collection proxy is returned as a java array, Map or Collection object, and a null proxy is returned as null. All values of java types for which a primitive php type exists are returned as php values. Everything else is returned unevaluated. Please make sure that the values do not not exceed
 * php's memory limit. Example:
 *
 * \code
 * $str = new java("java.lang.String", "hello");
 * echo $str;
 * => [o(String):"hello"]
 * echo java_values($str);
 * => hello
 * $chr = $str->toCharArray();
 * echo $chr;
 * => [o(array_of-C):"[C@1b10d42"]
 * $ar = java_values($chr);
 * print $ar;
 * => Array
 * print $ar[0];
 * => [o(Character):"h"]
 * print java_values($ar[0]);
 * => h
 * \endcode
 */
EXT_FUNCTION(EXT_GLOBAL(get_values))
{
  API_CALL(values);
}

/**
 * Proto: object java_closure([object],[array|string],[object]) or object java_get_closure([object],[array|string],[object])
 *
 * Closes over the php environment and packages it up as a java
 * class. Example: 
 * \code
 * function toString() {return "helloWorld";};
 * $object = java_get_closure();
 * echo "Java says that PHP says: $object\n";
 * \endcode
 *
 * When a php instance is supplied as an argument, the environment will be used
 * instead. When a string or key/value map is supplied as a second argument,
 * the java procedure names are mapped to the php procedure names. Example:
 * \code
 * function hello() {return "hello";};
 * echo (string)java_get_closure(null, "hello");
 * \endcode
 * 
 * When an array of java interfaces is supplied as a third argument,
 * the environment must implement these interfaces.
 * Example:
 * \code
 * class Listener {
 *   function actionPerformed($actionEvent) {
 *   ...
 *   }
 * }
 * function getListener() {
 *   return java_get_closure(new Listener(), null, array(new Java("java.awt.event.ActionListener")));
 * }
 * \endcode
 */
EXT_FUNCTION(EXT_GLOBAL(get_closure))
{
  API_CALL(get_closure);
}

/**
 * Only for internal use.
 */
EXT_FUNCTION(EXT_GLOBAL(exception_handler))
{
  zval **pobj;
  struct cb_stack_elem *stack_elem;
  int err = zend_stack_top(JG(cb_stack), (void**)&stack_elem); assert(SUCCESS==err);

  if (ZEND_NUM_ARGS()!=1 || zend_get_parameters_ex(1, &pobj) == FAILURE) WRONG_PARAM_COUNT;
  MAKE_STD_ZVAL(stack_elem->exception);
  *stack_elem->exception=**pobj;
  zval_copy_ctor(stack_elem->exception);

  RETURN_NULL();
}

/**
 * Only for internal use
 */
static void check_php4_exception(TSRMLS_D) {
#ifndef ZEND_ENGINE_2
  proxyenv*jenv = JG(jenv);
  struct cb_stack_elem *stack_elem;
  int err = zend_stack_top(JG(cb_stack), (void**)&stack_elem); assert(SUCCESS==err);
  (*jenv)->writeInvokeBegin(jenv, 0, "lastException", 0, 'P', stack_elem->exception);
  (*jenv)->writeInvokeEnd(jenv);
#endif
}
static int allocate_php4_exception(TSRMLS_D) {
#ifndef ZEND_ENGINE_2
  proxyenv*jenv = JG(jenv);
  struct cb_stack_elem *stack_elem;
  int err = zend_stack_top(JG(cb_stack), (void**)&stack_elem); assert(SUCCESS==err);
  MAKE_STD_ZVAL(stack_elem->exception);
  ZVAL_NULL(stack_elem->exception);
  (*jenv)->writeInvokeBegin(jenv, 0, "lastException", 0, 'P', stack_elem->exception);
  (*jenv)->writeObject(jenv, 0);
  (*jenv)->writeInvokeEnd(jenv);
#endif
  return 1;
}
static void call_with_handler(char*handler, const char*name TSRMLS_DC) {
  if(allocate_php4_exception(TSRMLS_C)) {
	int err, e;
	struct cb_stack_elem *stack_elem;
#ifdef ZEND_ENGINE_2
	php_set_error_handling(EH_THROW, zend_exception_get_default() TSRMLS_CC);
#endif
	e = zend_stack_top(JG(cb_stack), (void**)&stack_elem); assert(SUCCESS==e);
	err = 
	  zend_eval_string((char*)handler, *stack_elem->retval_ptr, (char*)name TSRMLS_CC);

#ifdef ZEND_ENGINE_2
	php_std_error_handling();
#endif
	
	if (err != SUCCESS) { 
	  php_error(E_WARNING, "php_mod_"/**/EXT_NAME()/**/"(%d): Could not call user function: %s.", 22, Z_STRVAL_P(stack_elem->func));
	}
  }
}

static int java_call_user_function_ex(HashTable *function_table, zval **object_pp, zval *function_name, zval **retval_ptr_ptr, int param_count, zval **params[], int no_separation, HashTable *symbol_table TSRMLS_DC) {
#if !defined(ZEND_ENGINE_2) && defined(__MINGW32__)
  int i, err;
  zval *local_retval;
  zval **params_array = (zval **) emalloc(sizeof(zval *)*param_count);
  for (i=0; i<param_count; i++) {
	params_array[i] = *params[i];
	zval_copy_ctor(params_array[i]);
  }
  MAKE_STD_ZVAL(local_retval);
  err = call_user_function(function_table, object_pp, function_name, local_retval, param_count, params_array TSRMLS_CC);
  *retval_ptr_ptr=local_retval;
  zval_copy_ctor(*retval_ptr_ptr);
  efree(params_array);

  /* this is a dummy which is never called. It is here so that gcc
	 enables auto-import. */
  assert(!function_table);
  if(function_table) call_user_function_ex(function_table, object_pp, function_name, retval_ptr_ptr, param_count, params, no_separation, symbol_table TSRMLS_CC);

  return err;
#else
  return call_user_function_ex(function_table, object_pp, function_name, retval_ptr_ptr, param_count, params, no_separation, symbol_table TSRMLS_CC);
#endif
}
static void call_with_params(int count, zval ***func_params TSRMLS_DC) {
  if(allocate_php4_exception(TSRMLS_C)) {/* checked and destroyed in client. handle_exception() */
	struct cb_stack_elem *stack_elem;
	int err, e;
#ifdef ZEND_ENGINE_2
	php_set_error_handling(EH_THROW, zend_exception_get_default() TSRMLS_CC);
#endif
	e = zend_stack_top(JG(cb_stack), (void**)&stack_elem);
	assert(SUCCESS==e);
	err = java_call_user_function_ex(0, stack_elem->object, stack_elem->func, stack_elem->retval_ptr, count, func_params, 1, 0 TSRMLS_CC);
#ifdef ZEND_ENGINE_2
	php_std_error_handling();
#endif
	if (err != SUCCESS) {
	  php_error(E_WARNING, "php_mod_"/**/EXT_NAME()/**/"(%d): Could not call user function: %s.", 23, Z_STRVAL_P(stack_elem->func));
	}
  }
}

/**
 * Only for internal use.
 */
EXT_FUNCTION(EXT_GLOBAL(call_with_exception_handler))
{
  int count, current, err;
  struct cb_stack_elem *stack_elem;
  err = zend_stack_top(JG(cb_stack), (void**)&stack_elem); assert(SUCCESS==err);
  if (ZEND_NUM_ARGS()==1) {
	*return_value=*stack_elem->func_params;
	zval_copy_ctor(return_value);
	return;
  }
  /* for functions in the global environment */
  if(!*stack_elem->object) {
	static const char name[] = "call_global_func_with_exception_handler";
	static const char call_user_funcH[] = "call_user_func_array('";
	static const char call_user_funcT[] = "',"/**/EXT_NAME()/**/"_call_with_exception_handler(true));";
	char *handler=emalloc(sizeof(call_user_funcH)-1+Z_STRLEN_P(stack_elem->func)+sizeof(call_user_funcT));
	assert(handler); if(!handler) exit(9);
	strcpy(handler, call_user_funcH); 
	strcat(handler, Z_STRVAL_P(stack_elem->func));
	strcat(handler, call_user_funcT);

	MAKE_STD_ZVAL(*stack_elem->retval_ptr); ZVAL_NULL(*stack_elem->retval_ptr);
	call_with_handler(handler, name TSRMLS_CC);
	check_php4_exception(TSRMLS_C);
	efree(handler);
  } else {
	zval ***func_params;
	HashTable *func_params_ht;
	/* for methods */
	current=0;
	func_params_ht = Z_ARRVAL_P(stack_elem->func_params);
	count = zend_hash_num_elements(func_params_ht);
	func_params = safe_emalloc(sizeof(zval **), count, 0);
	for (zend_hash_internal_pointer_reset(func_params_ht);
		 zend_hash_get_current_data(func_params_ht, (void **) &func_params[current]) == SUCCESS;
		 zend_hash_move_forward(func_params_ht)
		 ) {
	  current++;
	}
	
	call_with_params(count, func_params TSRMLS_CC);
	check_php4_exception(TSRMLS_C);
	efree(func_params);
  }
	
  RETURN_NULL();
}

/**
 * Proto: void java_inspect(object);
 *
 * Returns the contents (public fields, public methods, public
 * classes) of object as a string.
 * Example:
 * \code
 * echo java_inspect(java_get_context());
 * \endcode
 */
EXT_FUNCTION(EXT_GLOBAL(inspect)) {
  API_CALL(inspect);
}

function_entry EXT_GLOBAL(functions)[] = {
  EXT_FE(EXT_GLOBAL(last_exception_get), NULL)
  EXT_FE(EXT_GLOBAL(last_exception_clear), NULL)
  EXT_FE(EXT_GLOBAL(set_file_encoding), NULL)
  EXT_FE(EXT_GLOBAL(instanceof), NULL)

  EXT_FE(EXT_GLOBAL(require),  NULL)
  EXT_FALIAS(EXT_GLOBAL(set_library_path), EXT_GLOBAL(require),  NULL)

  EXT_FE(EXT_GLOBAL(get_session), NULL)
  EXT_FALIAS(EXT_GLOBAL(session), EXT_GLOBAL(get_session), NULL)

  EXT_FE(EXT_GLOBAL(get_context), NULL)
  EXT_FALIAS(EXT_GLOBAL(context), EXT_GLOBAL(get_context), NULL)

  EXT_FE(EXT_GLOBAL(get_server_name), NULL)
  EXT_FALIAS(EXT_GLOBAL(server_name), EXT_GLOBAL(get_server_name), NULL)

  EXT_FE(EXT_GLOBAL(get_values), NULL)
  EXT_FALIAS(EXT_GLOBAL(values), EXT_GLOBAL(get_values), NULL)

  EXT_FE(EXT_GLOBAL(get_closure), NULL)
  EXT_FALIAS(EXT_GLOBAL(closure), EXT_GLOBAL(get_closure), NULL)

  EXT_FE(EXT_GLOBAL(call_with_exception_handler), NULL)
  EXT_FE(EXT_GLOBAL(exception_handler), NULL)
  EXT_FE(EXT_GLOBAL(inspect), NULL)
  EXT_FE(EXT_GLOBAL(reset), NULL)

  EXT_FE(EXT_GLOBAL(begin_document), NULL)
  EXT_FE(EXT_GLOBAL(end_document), NULL)
  {NULL, NULL, NULL}
};

zend_module_entry EXT_GLOBAL(module_entry) = {
  STANDARD_MODULE_HEADER,
  EXT_NAME(),
  EXT_GLOBAL(functions),
  EXT_MINIT(EXT),
  EXT_MSHUTDOWN(EXT),
  EXT_RINIT(EXT),
  EXT_RSHUTDOWN(EXT),
  EXT_MINFO(EXT),
  NO_VERSION_YET,
  STANDARD_MODULE_PROPERTIES
};

#if defined(COMPILE_DL_JAVA) || defined(COMPILE_DL_MONO)
EXT_GET_MODULE(EXT)
#endif

/**
 * Holds the flags set/unset for all overridden java ini entries
 * these are U_HOST, U_SERVLET and U_SOCKNAME
 * @see X_JAVABRIDGE_OVERRIDE_HOSTS
 */
int EXT_GLOBAL(ini_override);

/**
 * Holds the flags set/unset for all java ini entries
 */
int EXT_GLOBAL(ini_updated);

/**
 * The options set by the user.
 */
int EXT_GLOBAL(ini_user);

/**
 * The options which carry a value.
 */
int EXT_GLOBAL(ini_set);

/**
 * Represents the java class struct
 */
zend_class_entry *EXT_GLOBAL(class_entry);

/**
 * Represents the java class array struct
 */
zend_class_entry *EXT_GLOBAL(array_entry);

/**
 * Represents the java_class class struct
 */
zend_class_entry *EXT_GLOBAL(class_class_entry);

/**
 * Represents the javaclass class struct
 */
zend_class_entry *EXT_GLOBAL(class_class_entry_jsr);

/**
 * Represents the javaexception class struct
 */
zend_class_entry *EXT_GLOBAL(exception_class_entry);

#ifdef ZEND_ENGINE_2
/**
 * The object handlers, see create_object.
 */
zend_object_handlers EXT_GLOBAL(handlers);
#endif

static PHP_INI_MH(OnIniPolicy)
{
  if (new_value) {
	if((EXT_GLOBAL (ini_set) &U_POLICY)) free(EXT_GLOBAL(cfg)->policy);
	EXT_GLOBAL(cfg)->policy=strdup(new_value);
	assert(EXT_GLOBAL(cfg)->policy); if(!EXT_GLOBAL(cfg)->policy) exit(6);
	EXT_GLOBAL(ini_updated)|=U_POLICY;
  }
  return SUCCESS;
}
static PHP_INI_MH(OnIniWrapper)
{
  if (new_value) {
	if((EXT_GLOBAL (ini_set) &U_WRAPPER)) free(EXT_GLOBAL(cfg)->wrapper);
	EXT_GLOBAL(cfg)->wrapper=strdup(new_value);
	assert(EXT_GLOBAL(cfg)->wrapper); if(!EXT_GLOBAL(cfg)->wrapper) exit(6);
	EXT_GLOBAL(ini_updated)|=U_WRAPPER;
  }
  return SUCCESS;
}
static PHP_INI_MH(OnIniHosts)
{
  if (new_value && !(EXT_GLOBAL(ini_override)&U_HOSTS)) {
	EXT_GLOBAL(update_hosts)(new_value);
  }
  return SUCCESS;
}
static PHP_INI_MH(OnIniExtJavaCompatibility)
{
  if (new_value) {
	EXT_GLOBAL(update_compatibility)(new_value);
  }
  return SUCCESS;
}
static PHP_INI_MH(OnIniPersistentConnections)
{
  if(new_value) {
	EXT_GLOBAL(update_persistent_connections)(new_value);
  }
  return SUCCESS;
}
static PHP_INI_MH(OnIniServlet)
{
  if (new_value && !(EXT_GLOBAL(ini_override)&U_SERVLET)) {
	EXT_GLOBAL(update_servlet)(new_value);
  }
  return SUCCESS;
}

static PHP_INI_MH(OnIniSockname)
{
  if (new_value && !(EXT_GLOBAL(ini_override)&U_SOCKNAME)) {
	EXT_GLOBAL(update_socketname)(new_value);
  }
  return SUCCESS;
}
static PHP_INI_MH(OnIniClassPath)
{
  if (new_value) {
	if((EXT_GLOBAL (ini_set) &U_CLASSPATH)) free(EXT_GLOBAL(cfg)->classpath);
	EXT_GLOBAL(cfg)->classpath =strdup(new_value);
	assert(EXT_GLOBAL(cfg)->classpath); if(!EXT_GLOBAL(cfg)->classpath) exit(6);
	EXT_GLOBAL(ini_updated)|=U_CLASSPATH;
  }
  return SUCCESS;
}
static PHP_INI_MH(OnIniLibPath)
{
  if (new_value) {
	if((EXT_GLOBAL (ini_set) &U_LIBRARY_PATH)) free(EXT_GLOBAL(cfg)->ld_library_path);
	EXT_GLOBAL(cfg)->ld_library_path = strdup(new_value);
	assert(EXT_GLOBAL(cfg)->ld_library_path); if(!EXT_GLOBAL(cfg)->ld_library_path) exit(6);
	EXT_GLOBAL(ini_updated)|=U_LIBRARY_PATH;
  }
  return SUCCESS;
}
static PHP_INI_MH(OnIniJava)
{
  if (new_value) {
	if((EXT_GLOBAL (ini_set) &U_JAVA)) free(EXT_GLOBAL(cfg)->vm);
	EXT_GLOBAL(cfg)->vm = strdup(new_value);
	assert(EXT_GLOBAL(cfg)->vm); if(!EXT_GLOBAL(cfg)->vm) exit(6);
	EXT_GLOBAL(ini_updated)|=U_JAVA;
  }
  return SUCCESS;
}
static PHP_INI_MH(OnIniJavaHome)
{
  if (new_value) {
	if((EXT_GLOBAL (ini_set) &U_JAVA_HOME)) free(EXT_GLOBAL(cfg)->vm_home);
	EXT_GLOBAL(cfg)->vm_home = strdup(new_value);
	assert(EXT_GLOBAL(cfg)->vm_home); if(!EXT_GLOBAL(cfg)->vm_home) exit(6);
	EXT_GLOBAL(ini_updated)|=U_JAVA_HOME;
  }
  return SUCCESS;
}
static PHP_INI_MH(OnIniLogLevel)
{
  if (new_value) {
	if((EXT_GLOBAL (ini_set) &U_LOGLEVEL)) free(EXT_GLOBAL(cfg)->logLevel);
	EXT_GLOBAL(cfg)->logLevel = strdup(new_value);
	assert(EXT_GLOBAL(cfg)->logLevel); if(!EXT_GLOBAL(cfg)->logLevel) exit(6);
	EXT_GLOBAL(cfg)->logLevel_val=atoi(EXT_GLOBAL(cfg)->logLevel);
	EXT_GLOBAL(ini_updated)|=U_LOGLEVEL;
  }
  return SUCCESS;
}
static PHP_INI_MH(OnIniLogFile)
{
  if (new_value) {
	if((EXT_GLOBAL (ini_set) &U_LOGFILE)) free(EXT_GLOBAL(cfg)->logFile);
	EXT_GLOBAL(cfg)->logFile = strdup(new_value);
	assert(EXT_GLOBAL(cfg)->logFile); if(!EXT_GLOBAL(cfg)->logFile) exit(6);
	EXT_GLOBAL(ini_updated)|=U_LOGFILE;
  }
  return SUCCESS;
}
PHP_INI_BEGIN()
  PHP_INI_ENTRY(EXT_NAME()/**/".servlet", NULL, PHP_INI_SYSTEM, OnIniServlet)
  PHP_INI_ENTRY(EXT_NAME()/**/".socketname", NULL, PHP_INI_SYSTEM, OnIniSockname)
  PHP_INI_ENTRY(EXT_NAME()/**/".hosts",   NULL, PHP_INI_SYSTEM, OnIniHosts)
  PHP_INI_ENTRY(EXT_NAME()/**/".wrapper",   NULL, PHP_INI_SYSTEM, OnIniWrapper)
  PHP_INI_ENTRY(EXT_NAME()/**/".security_policy",   NULL, PHP_INI_SYSTEM, OnIniPolicy)
  PHP_INI_ENTRY(EXT_NAME()/**/".classpath", NULL, PHP_INI_SYSTEM, OnIniClassPath)
  PHP_INI_ENTRY(EXT_NAME()/**/".libpath",   NULL, PHP_INI_SYSTEM, OnIniLibPath)
  PHP_INI_ENTRY(EXT_NAME()/**/"."/**/EXT_NAME()/**/"",   NULL, PHP_INI_SYSTEM, OnIniJava)
  PHP_INI_ENTRY(EXT_NAME()/**/"."/**/EXT_NAME()/**/"_home",   NULL, PHP_INI_SYSTEM, OnIniJavaHome)

  PHP_INI_ENTRY(EXT_NAME()/**/".log_level",   NULL, PHP_INI_SYSTEM, OnIniLogLevel)
  PHP_INI_ENTRY(EXT_NAME()/**/".log_file",   NULL, PHP_INI_SYSTEM, OnIniLogFile)
  PHP_INI_ENTRY(EXT_NAME()/**/".ext_java_compatibility",   NULL, PHP_INI_SYSTEM, OnIniExtJavaCompatibility)
  PHP_INI_ENTRY(EXT_NAME()/**/".persistent_connections",   NULL, PHP_INI_SYSTEM, OnIniPersistentConnections)
  PHP_INI_END()

/* PREFORK calls this once. All childs receive cloned values. However,
   the WORKER MPM calls this for the master and for all childs */
  static void EXT_GLOBAL(alloc_globals_ctor)(EXT_GLOBAL_EX(zend_,globals,_) *EXT_GLOBAL(globals) TSRMLS_DC)
{
  EXT_GLOBAL(globals)->jenv=0;
  EXT_GLOBAL(globals)->is_closed=-1;

  EXT_GLOBAL(globals)->ini_user=0;

  EXT_GLOBAL(globals)->hosts=0;
  EXT_GLOBAL(globals)->servlet=0;

  zend_hash_init(&EXT_GLOBAL(globals)->connections, 0, 0, 0, 1);
  EXT_GLOBAL(globals)->cb_stack=0;

  EXT_GLOBAL(globals)->peer = EXT_GLOBAL(globals)->peerr = -1;
  EXT_GLOBAL(globals)->servlet_ctx = 0;
}

#ifdef ZEND_ENGINE_2

/**
 * Proto: object Java::Java (string classname [, string argument1, .\ .\ .\ ]) or object Java::Java (array arguments)  or object Java::java_exception (string classname [, string argument1, .\ .\ .\ ]) or object Java::JavaException (string classname [, string argument1, .\ .\ .\ ]);
 *
 * Java constructor. Example:
 * \code
 * $object = new Java("java.lang.String", "hello world"); 
 * echo (string)$object;
 * \endcode
 * \code
 * $ex = new JavaException("java.lang.NullPointerException");
 * throw $ex;
 * \endcode
 * \code
 * require_once("rt/java_util_LinkedList.php");
 * class org_apache_lucene_search_IndexSearcher extends php_Java {
 *   __construct() { 
 *     $args = func_get_args();
 *     array_unshift($args, "org.apache.lucene.search.IndexSearcher");
 *     $java = new Java($args); 
 *   }
 * }
 * class org_apache_lucene_search_PhraseQuery extends php_Java {
 *   __construct() { 
 *     $args = func_get_args();
 *     array_unshift($args, "org.apache.lucene.search.PhraseQuery");
 *     $java = new Java($args); 
 *   }
 * }
 * class org_apache_lucene_index_Term extends php_Java {
 *   __construct() { 
 *     $args = func_get_args();
 *     array_unshift($args, "org.apache.lucene.index.Term");
 *     $java = new Java($args); 
 *   }
 * }
 * $searcher = new org_apache_lucene_search_IndexSearcher(getcwd());
 * $term = new org_apache_lucene_index_Term("name", "test.php");
 * $phrase = new org_apache_lucene_search_PhraseQuery();
 * phrase->add($term);
 * $hits = $searcher->search($phrase);
 * $iter = $hits->iterator();
 * $list = new java_util_LinkedList();
 * while($iter->hasNext()) {
 * $next = $iter->next();
 * $name = $next->get("name");
 * $list->append($name);
 * }
 * echo $list;
 *
 * \endcode 
 */
EXT_FUNCTION(EXT_GLOBAL(construct))
{
  API_CALL(construct);
}

/** 
 * Proto: object Java::JavaClass ( string classname) or object java::java_class ( string classname);
 *
 * References a java class. Example: 
 * \code
 * $Object = new JavaClass("java.lang.Object");
 * $object = $Object->newInstance();
 * \endcode
 * \code
 * $Thread = new JavaClass("java.lang.Thread");
 * $Thread->sleep(1000);

 * \endcode
 */
EXT_FUNCTION(EXT_GLOBAL(construct_class))
{
  API_CALL(construct_class);
}

/** 
 * Proto: mixed Java::__call ( string procedure_name [, array arguments ])
 *
 * Calls a Java procedure
 * Example:
 * \code
 * # The JPersistenceAdapter makes it possible to serialize java values.
 * #
 * # Example:
 * # $v=new JPersistenceAdapter(new Java("java.lang.StringBuffer", "hello"));
 * # $id=serialize($v);
 * # $file=fopen("file.out","w");
 * # fwrite($file, $id);
 * # fclose($file);
 * #
 *
 * class JPersistenceProxy {
 *  var $java;
 *  var $serialID;
 *
 *  function __construct($java){ 
 *    $this->java=$java; 
 *    $this->serialID; 
 *  }
 *  function __sleep() {
 *    $buf = new Java("java.io.ByteArrayOutputStream");
 *    $out = new Java("java.io.ObjectOutputStream", $buf);
 *    $out->writeObject($this->java);
 *    $out->close();
 *    $this->serialID = base64_encode((string)$buf->toByteArray());
 *    return array("serialID");
 *  }
 *  function __wakeup() {
 *    $buf = new Java("java.io.ByteArrayInputStream",base64_decode($this->serialID));
 *    $in = new Java("java.io.ObjectInputStream", $buf);
 *    $this->java = $in->readObject();
 *    $in->close();
 *  }
 *  function getJava() {
 *    return $this->java;
 *  }
 *  function __destruct() { 
 *    if($this->java) return $this->java->__destruct(); 
 *  }
 * }
 *
 * class JPersistenceAdapter extends JPersistenceProxy {
 *  function __get($arg)       { if($this->java) return $this->java->__get($arg); }
 *  function __put($key, $val) { if($this->java) return $this->java->__put($key, $val); }
 *  function __call($m, $a)    { if($this->java) return $this->java->__call($m,$a); }
 *  function __toString()      { if($this->java) return $this->java->__toString(); }
 * }
 * \endcode
 */
EXT_METHOD(EXT, __call)
{
  API_CALL(call);
}


/** Proto: object Java::__toString (void)
 *
 * Displays the java object as a string. Note: it doesn't cast the
 * object to a string, thus echo $ob displays a string
 * representation of $ob, e.g.: \code [o(String)"hello"]\endcode
 *
 * Use a string cast or java_values(), if you want to display the java string as a php
 * string, e.g.:
 * \code 
 * echo (string)$string; // explicit cast
 * echo "$string"; // implicit cast
 * \endcode
 */
EXT_METHOD(EXT, __tostring)
{
  API_CALL(toString);
}


/**
 * Proto: void Java::__set(object, object)
 *
 * The setter
 * 
 * Example: \code $obj->property = "value"; \endcode If no property
 * exists, the bean properties are examined and a setter is called:
 * \code $object->setProperty(value)\endcode
 */
EXT_METHOD(EXT, __set)
{
  API_CALL(set);
}

/** 
 * Proto: void Java::__destruct()
 *
 * Example:
 * \code
 * 
 * # The JSessionAdapter makes it possible to store java values into the
 * # $_SESSION variable. 
 * 
 * # Example:
 * # $vector = new JSessionAdapter(new Java("java.util.Vector"));
 * # $vector->addElement(...);
 * # $_SESSION["v"]=$vector;
 *
 *
 * class JSessionProxy {
 *  var $java;
 *  var $serialID;
 *
 *  function __construct($java){ 
 *    $this->java=$java; 
 *    $this->serialID = uniqid(""); 
 *  }
 *  function __sleep() {
 *    $session=java_get_session("PHPSESSION".session_id());
 *    $session->put($this->serialID, $this->java);
 *    return array("serialID");
 *  }
 *  function __wakeup() {
 *    $session=java_get_session("PHPSESSION".session_id());
 *    $this->java = $session->get($this->serialID);
 *  }
 *  function getJava() {
 *    return $this->java;
 *  }
 *  function __destruct() { 
 *    if($this->java) return $this->java->__destruct(); 
 *  }
 * }
 *
 * class JSessionAdapter extends JSessionProxy {
 *  function __get($arg)       { if($this->java) return $this->java->__get($arg); }
 *  function __put($key, $val) { if($this->java) return $this->java->__put($key, $val); }
 *  function __call($m, $a)    { if($this->java) return $this->java->__call($m,$a); }
 *  function __toString()      { if($this->java) return $this->java->__toString(); }
 * }
 * \endcode
 */
EXT_METHOD(EXT, __destruct)
{
  /* dummy, see destroy_object in java_bridge.c */
}

/** 
 * Proto: object Java::__get(object)
 *
 * The getter. Example: \code echo (string) $object->property;
 * \endcode If no property exists, the bean properties are examined
 * and the getter is called, example: \code $object->getProperty()
 * \endcode.
 */
EXT_METHOD(EXT, __get)
{
  API_CALL(get);
}

/**
 * Proto: string Java::__sleep()
 *
 * Serializes the object. 
 * Example:
 * \code
 *   $vector=new JPersistenceAdapter(new Java("java.lang.StringBuffer", "hello"));
 *  $v=array (
 *	"test",
 *	$vector,
 *	3.14);
 *  $id=serialize($v);
 *  $file=fopen("test.ser","w");
 *  fwrite($file, $id);
 *  fclose($file);
 * \endcode
 */
EXT_METHOD(EXT, __sleep)
{
  API_CALL(serialize);
}

/** Proto: string Java::__wakeup()
 * 
 * Deserializes the object. 
 * Example: 
 * \code
 *  try {
 *    $v=unserialize($id);
 *  } catch (JavaException $e) {
 *    echo "Warning: Could not deserialize: ". $e->getCause() . "\n";
 *  }
 * \endcode
 */
EXT_METHOD(EXT, __wakeup)
{
  API_CALL(deserialize);
}


#define EXT_ARRAY EXTC##Array

/** Proto: bool Java::offsetExists()
 * 
 * Checks if an object exists at the given position.
 * Example:
 * \code
 * $System = new Java("java.lang.System");
 * $props = $System->getProperties();
 * if(!$props["user.home"]) die("No home dir!?!");
 * \endcode
 */
EXT_METHOD(EXT_ARRAY, offsetExists)
{
  API_CALL(offsetExists);
}

/** 
 * Proto: object Java::offsetGet()
 *
 * Get the object at a given position.
 *
 * Example:
 * \code
 * $System = new Java("java.lang.System");
 * $props = $System->getProperties();
 * echo $props["user.home"]);
 * \endcode
 *
 */
EXT_METHOD(EXT_ARRAY, offsetGet)
{
  API_CALL(offsetGet);
}

/** Proto: void Java::offsetSet(object, object);
 *
 * Set the object at a given position. Example:
 * \code
 * $Array = new JavaClass("java.lang.reflect.Array");
 * $testobj=$Array->newInstance(new JavaClass("java.lang.String"), array(2, 2, 2, 2, 2, 2));
 *
 * $testobj[0][0][0][0][0][1] = 1;
 * $testobj[0][0][0][0][1][0] = 2;
 * $testobj[0][0][0][1][0][0] = 3;
 * $testobj[0][0][1][0][0][0] = 4;
 * $testobj[0][1][0][0][0][0] = 5;
 * $testobj[1][0][0][0][0][0] = 6;
 * \endcode
 */
EXT_METHOD(EXT_ARRAY, offsetSet)
{
  API_CALL(offsetSet);
}

/** Proto: string Java::offsetUnset()
 * 
 * Remove the entry at a given position. Used internally.
 */
EXT_METHOD(EXT_ARRAY, offsetUnset)
{
  API_CALL(offsetUnset);
}

static
ZEND_BEGIN_ARG_INFO(arginfo_zero, 0)
	 ZEND_END_ARG_INFO();

static
ZEND_BEGIN_ARG_INFO(arginfo_get, 0)
	 ZEND_ARG_INFO(0, index)
	 ZEND_END_ARG_INFO();

static
ZEND_BEGIN_ARG_INFO(arginfo_set, 0)
	 ZEND_ARG_INFO(0, index)
	 ZEND_ARG_INFO(0, newval)
	 ZEND_END_ARG_INFO();

#ifndef ZEND_ENGINE_2
function_entry EXT_GLOBAL(class_functions)[] = {
  EXT_ME(EXT, EXT, NULL, 0)
  EXT_ME(EXT, EXT_GLOBAL(class), NULL, 0)
  EXT_MALIAS(EXT, EXT_GLOBAL_N(class), EXT_GLOBAL(class), NULL, 0)
  EXT_ME(EXT, __call, arginfo_set, ZEND_ACC_PUBLIC)
  EXT_ME(EXT, __tostring, arginfo_zero, ZEND_ACC_PUBLIC)
  EXT_ME(EXT, __get, arginfo_get, ZEND_ACC_PUBLIC)
  EXT_ME(EXT, __set, arginfo_set, ZEND_ACC_PUBLIC)
  EXT_ME(EXT, __sleep, arginfo_zero, ZEND_ACC_PUBLIC)
  EXT_ME(EXT, __wakeup, arginfo_zero, ZEND_ACC_PUBLIC)
  EXT_ME(EXT, __destruct, arginfo_zero, ZEND_ACC_PUBLIC)
  {NULL, NULL, NULL}
};
#else
function_entry EXT_GLOBAL(class_functions)[] = {
  ZEND_FENTRY(__construct, EXT_FN(EXT_GLOBAL(construct)), NULL, 0)
  EXT_ME(EXT, __call, arginfo_set, ZEND_ACC_PUBLIC)
  EXT_ME(EXT, __tostring, arginfo_zero, ZEND_ACC_PUBLIC)
  EXT_ME(EXT, __get, arginfo_get, ZEND_ACC_PUBLIC)
  EXT_ME(EXT, __set, arginfo_set, ZEND_ACC_PUBLIC)
  EXT_ME(EXT, __sleep, arginfo_zero, ZEND_ACC_PUBLIC)
  EXT_ME(EXT, __wakeup, arginfo_zero, ZEND_ACC_PUBLIC)
  EXT_ME(EXT, __destruct, arginfo_zero, ZEND_ACC_PUBLIC)
  //PHP_ME(EXT, __createReflectionInstance, NULL, 0)
  {NULL, NULL, NULL}
};
static function_entry (array_class_functions)[] = {
  ZEND_FENTRY(__construct, EXT_FN(EXT_GLOBAL(construct)), NULL, 0)
  EXT_ME(EXT_ARRAY, offsetExists,  arginfo_get, ZEND_ACC_PUBLIC)
  EXT_ME(EXT_ARRAY, offsetGet,     arginfo_get, ZEND_ACC_PUBLIC)
  EXT_ME(EXT_ARRAY, offsetSet,     arginfo_set, ZEND_ACC_PUBLIC)
  EXT_ME(EXT_ARRAY, offsetUnset,   arginfo_get, ZEND_ACC_PUBLIC)
  {NULL, NULL, NULL}
};
static function_entry (class_class_functions)[] = {
  ZEND_FENTRY(__construct, EXT_FN(EXT_GLOBAL(construct_class)), NULL, 0)
  {NULL, NULL, NULL}
};
#endif



#if ZEND_EXTENSION_API_NO >= 220060519 
static int cast(zval *readobj, zval *writeobj, int type TSRMLS_DC)
{
  int should_free = 0;
#else
static int cast(zval *readobj, zval *writeobj, int type, int should_free TSRMLS_DC)
{
#endif
  proxyenv *jenv = EXT_GLOBAL(connect_to_server)(TSRMLS_C);
  long obj = 0;
  zval free_obj;

  if(!jenv) return FAILURE;
  if((*jenv)->handle==(*jenv)->async_ctx.handle_request) { /* async protocol */
	php_error(E_ERROR, "php_mod_"/**/EXT_NAME()/**/"(%d): cast() invalid while in stream mode", 21);
	return FAILURE;
  }

  if (should_free)
	free_obj = *writeobj;

  if(jenv && (Z_TYPE_P(readobj) == IS_OBJECT)) {
	EXT_GLOBAL(get_jobject_from_object)(readobj, &obj TSRMLS_CC);
  }

  if(obj) {
	INIT_PZVAL(writeobj);
    ZVAL_NULL(writeobj);
	switch(type) {

	case IS_STRING:
	  (*jenv)->writeInvokeBegin(jenv, 0, "castToString", 0, 'I', writeobj);
	  (*jenv)->writeObject(jenv, obj);
	  obj = (*jenv)->writeInvokeEnd(jenv);
	  break;
	case IS_BOOL:
	  (*jenv)->writeInvokeBegin(jenv, 0, "castToBoolean", 0, 'I', writeobj);
	  (*jenv)->writeObject(jenv, obj);
	  obj = (*jenv)->writeInvokeEnd(jenv);
	  break;
	case IS_LONG:
	  (*jenv)->writeInvokeBegin(jenv, 0, "castToExact", 0, 'I', writeobj);
	  (*jenv)->writeObject(jenv, obj);
	  obj = (*jenv)->writeInvokeEnd(jenv);
	  break;
	case IS_DOUBLE:
	  (*jenv)->writeInvokeBegin(jenv, 0, "castToInexact", 0, 'I', writeobj);
	  (*jenv)->writeObject(jenv, obj);
	  obj = (*jenv)->writeInvokeEnd(jenv);
	  break;
	case IS_OBJECT: 
	  {
		long obj2;
		if(jenv && (Z_TYPE_P(readobj) == IS_OBJECT)) {
		  EXT_GLOBAL(get_jobject_from_object)(readobj, &obj2 TSRMLS_CC);
		}
		if(obj2) {
		  (*jenv)->writeInvokeBegin(jenv, 0, "cast", 0, 'I', writeobj);
		  (*jenv)->writeObject(jenv, obj);
		  (*jenv)->writeObject(jenv, obj2);
		  obj = (*jenv)->writeInvokeEnd(jenv);
		} else {
		  obj = 0; //failed
		}
	  }
	  break;
	case IS_ARRAY: 
#ifdef ZEND_ENGINE_2
	  (*jenv)->writeInvokeBegin(jenv, 0, "castToArray", 0, 'I', writeobj);
	  (*jenv)->writeObject(jenv, obj);
	  obj = (*jenv)->writeInvokeEnd(jenv);
#else
	  obj = 0; // failed
#endif
	  break;
	default:
	  obj = 0; // failed
	  break;
	}
  }

  if (should_free)
	zval_dtor(&free_obj);

  return obj?SUCCESS:FAILURE;
}


typedef struct {
  zend_object_iterator intern;
  long vm_iterator;
  zval *current_object;
  int type;
} vm_iterator;

static void iterator_dtor(zend_object_iterator *iter TSRMLS_DC)
{
  proxyenv *jenv = JG(jenv);
  vm_iterator *iterator = (vm_iterator *)iter;
  
  zval_ptr_dtor((zval**)&iterator->intern.data);
  if (iterator->current_object) zval_ptr_dtor((zval**)&iterator->current_object);
  
  if(iterator->vm_iterator) {
	/* check jenv because destructor may be called after request
	   shutdown */
	if(jenv) (*jenv)->writeUnref(jenv, iterator->vm_iterator);
	iterator->vm_iterator = 0;
  }
  
  efree(iterator);
}

static int iterator_valid(zend_object_iterator *iter TSRMLS_DC)
{
  vm_iterator *iterator = (vm_iterator *)iter;
  return (iterator->vm_iterator && iterator->current_object) ? SUCCESS : FAILURE;
}

static void iterator_current_data(zend_object_iterator *iter, zval ***data TSRMLS_DC)
{
  vm_iterator *iterator = (vm_iterator *)iter;
  *data = &iterator->current_object;
}

static int iterator_current_key(zend_object_iterator *iter, char **str_key, uint *str_key_len, ulong *int_key TSRMLS_DC)
{
  vm_iterator *iterator = (vm_iterator *)iter;
  zval *presult;
  
  MAKE_STD_ZVAL(presult);
  ZVAL_NULL(presult);
  
  if(!EXT_GLOBAL(invoke)("currentKey", iterator->vm_iterator, 0, 0, 0, presult TSRMLS_CC)) ZVAL_NULL(presult);

  if(ZVAL_IS_NULL(presult)) {
	zval_ptr_dtor((zval**)&presult);
	return HASH_KEY_NON_EXISTANT;
  }

  if(iterator->type == HASH_KEY_IS_STRING) {
	size_t strlen = Z_STRLEN_P(presult);
	*str_key = emalloc(strlen+1);
	memcpy(*str_key, Z_STRVAL_P(presult), strlen);
	(*str_key)[strlen]=0;

	// len+1 is due to a bug in php. It assignes the len with
	// key->value.str.len = str_key_len-1; In the evaluator the
	// obtained length is always increased by one, except for the
	// return value from iterator_current_key.  So we must do this
	// ourselfs.  The author's intention was probably to discard the
	// termination character, but that's pointless, if php expects our
	// string to be null terminated why does it ask for the string
	// length?  And if it doesn't expect a null terminated string, why
	// does it decrease the obtained length by one?
	*str_key_len = strlen+1;

  } else {
	ulong i =(unsigned long)atol((char*)Z_STRVAL_P(presult));
	*int_key = i;
  }
  zval_ptr_dtor((zval**)&presult);
  return iterator->type;
}

static void init_current_data(vm_iterator *iterator TSRMLS_DC) 
{
  MAKE_STD_ZVAL(iterator->current_object);
  ZVAL_NULL(iterator->current_object);

  if(!EXT_GLOBAL(invoke)("currentData", iterator->vm_iterator, 0, 0, 0, iterator->current_object TSRMLS_CC)) ZVAL_NULL(iterator->current_object);
}

static void iterator_move_forward(zend_object_iterator *iter TSRMLS_DC)
{
  zval *presult;
  vm_iterator *iterator = (vm_iterator *)iter;
  proxyenv *jenv = JG(jenv);
  MAKE_STD_ZVAL(presult);
  ZVAL_NULL(presult);

  if (iterator->current_object) {
	zval_ptr_dtor((zval**)&iterator->current_object);
	iterator->current_object = NULL;
  }

  (*jenv)->writeInvokeBegin(jenv, iterator->vm_iterator, "moveForward", 0, 'I', presult);
  if(!(*jenv)->writeInvokeEnd(jenv)) ZVAL_NULL(presult);
  if(Z_BVAL_P(presult))
	init_current_data(iterator TSRMLS_CC);

  zval_ptr_dtor((zval**)&presult);
}

static zend_object_iterator_funcs EXT_GLOBAL(iterator_funcs) = {
  iterator_dtor,
  iterator_valid,
  iterator_current_data,
  iterator_current_key,
  iterator_move_forward,
  NULL
};

#if ZEND_EXTENSION_API_NO >= 220060519 
static zend_object_iterator *get_iterator(zend_class_entry *ce, zval *object, int by_ref TSRMLS_DC)
{
#else
static zend_object_iterator *get_iterator(zend_class_entry *ce, zval *object TSRMLS_DC)
{
  int by_ref = 0;
#endif
  zval *presult;
  proxyenv *jenv;
  vm_iterator *iterator;
  long vm_iterator, obj;

  if (by_ref) {					/* WTF?! */
	zend_error(E_ERROR, "An iterator cannot be used with foreach by reference");
  }

  iterator = emalloc(sizeof *iterator);
  jenv = JG(jenv);
  if((*jenv)->handle==(*jenv)->async_ctx.handle_request) { /* async protocol */
	php_error(E_ERROR, "php_mod_"/**/EXT_NAME()/**/"(%d): object iterator invalid while in stream mode, use $arr=java_values($java_obj); java_begin_document(); foreach($arr as ...) ...; java_end_document(); instead.", 21);
	return 0;
  }

  MAKE_STD_ZVAL(presult);
  ZVAL_NULL(presult);

  object->refcount++;
  iterator->intern.data = (void*)object;
  iterator->intern.funcs = &EXT_GLOBAL(iterator_funcs);

  EXT_GLOBAL(get_jobject_from_object)(object, &obj TSRMLS_CC);
  assert(obj);

  (*jenv)->writeInvokeBegin(jenv, 0, "getPhpMap", 0, 'I', presult);
  (*jenv)->writeObject(jenv, obj);
  (*jenv)->writeInvokeEnd(jenv);
  EXT_GLOBAL(get_jobject_from_object)(presult, &vm_iterator TSRMLS_CC);
  if (!vm_iterator) return NULL;
  iterator->vm_iterator = vm_iterator;

  (*jenv)->writeInvokeBegin(jenv, vm_iterator, "getType", 0, 'I', presult);
  if(!(*jenv)->writeInvokeEnd(jenv)) return NULL;

  iterator->type = Z_BVAL_P(presult) ? HASH_KEY_IS_STRING : HASH_KEY_IS_LONG;

  (*jenv)->writeInvokeBegin(jenv, vm_iterator, "hasMore", 0, 'I', presult);
  if(!(*jenv)->writeInvokeEnd(jenv)) return NULL;
  if(Z_BVAL_P(presult)) 
	init_current_data(iterator TSRMLS_CC);
  else
	iterator->current_object = NULL;

  zval_ptr_dtor((zval**)&presult);
  return (zend_object_iterator*)iterator;
}
static void make_lambda(zend_internal_function *f,
						void (*handler)(INTERNAL_FUNCTION_PARAMETERS))
{
  f->type = ZEND_INTERNAL_FUNCTION;
  f->handler = handler;
  f->function_name = NULL;
  f->scope = NULL;
  f->fn_flags = 0;
  f->prototype = NULL;
  f->num_args = 0;
  f->arg_info = NULL;
  f->pass_rest_by_reference = 0;
}

#else

static int make_lambda(zend_internal_function *f,
					   void (*handler)(INTERNAL_FUNCTION_PARAMETERS))
{
  f->type = ZEND_INTERNAL_FUNCTION;
  f->handler = handler;
  f->function_name = NULL;
  f->arg_types = NULL;
}

/**
 * Call function handler for php4
 */
void 
EXT_GLOBAL(call_function_handler4)(INTERNAL_FUNCTION_PARAMETERS, zend_property_reference *property_reference)
{
  pval *object = property_reference->object;
  zend_overloaded_element *function_name = (zend_overloaded_element *)
    property_reference->elements_list->tail->data;
  char *name = Z_STRVAL(function_name->element);
  int arg_count = ZEND_NUM_ARGS();
  pval ***arguments = (pval ***) safe_emalloc(sizeof(zval **), arg_count, 0);
  enum constructor constructor = CONSTRUCTOR_NONE;
  zend_class_entry *ce = Z_OBJCE_P(getThis());
								/* Do not create an instance for new
								   java_class or new JavaClass */
  short createInstance = 1;
  zend_class_entry *parent;
  short rc;

  for(parent=ce; parent->parent; parent=parent->parent)
	if ((parent==EXT_GLOBAL(class_class_entry)) || ((parent==EXT_GLOBAL(class_class_entry_jsr)))) {
	  createInstance = 0;		/* do not create an instance for new java_class or new JavaClass */
	  break;
	}

  zend_get_parameters_array_ex(arg_count, arguments);

  if(!strcmp(name, ce->name)) constructor = CONSTRUCTOR;
  EXT_GLOBAL(call_function_handler)(INTERNAL_FUNCTION_PARAM_PASSTHRU, 
									name, constructor, createInstance, 
									object, 
									arg_count, arguments);

  efree(arguments);
  pval_destructor(&function_name->element);
}

static pval 
get_property_handler(zend_property_reference *property_reference)
{
  pval presult, *object;
  zend_llist_element *element;
  zend_overloaded_element *property;
  char *name;

  element = property_reference->elements_list->head;
  property=(zend_overloaded_element *)element->data;
  name =  Z_STRVAL(property->element);
  object = property_reference->object;

  EXT_GLOBAL(get_property_handler)(name, object, &presult);

  pval_destructor(&property->element);
  return presult;
}

static int 
set_property_handler(zend_property_reference *property_reference, pval *value)
{
  int result;
  pval dummy, *object;
  zend_llist_element *element;
  zend_overloaded_element *property;
  char *name;

  element = property_reference->elements_list->head;
  property=(zend_overloaded_element *)element->data;
  name =  Z_STRVAL(property->element);
  object = property_reference->object;

  result = EXT_GLOBAL(set_property_handler) (name, object, value, &dummy);

  pval_destructor(&property->element);
  return result;
}
#endif

#if !defined(ZEND_ENGINE_2) && defined(__MINGW32__)
static void*return_msc_structure(void*mem, zend_property_reference *property_reference) {
   register zval res = get_property_handler(property_reference);
   return memcpy(mem, &res, sizeof res);
}
#endif

/**
 * Called when the module is initialized. Creates the Java and
 * JavaClass structures and tries to start the back-end if
 * java.socketname, java.servlet or java.hosts are not set.  The
 * back-end is not started if the environment variable
 * X_JAVABRIDGE_OVERRIDE_HOSTS exists and contains either "/" or
 * "host:port//context/servlet".  When running as a Apache/IIS module
 * or Fast CGI, this procedure is called only once. When running as a
 * CGI binary, it is called whenever the CGI binary is called.
 */
PHP_MINIT_FUNCTION(EXT)
{
  zend_class_entry *parent;
#ifndef ZEND_ENGINE_2
  static const char nserialize[]="__sleep", ndeserialize[]="__wakeup";
  zend_internal_function serialize, deserialize;
  zend_class_entry ce;
  INIT_OVERLOADED_CLASS_ENTRY(ce, EXT_NAME(), NULL,
							  EXT_GLOBAL(call_function_handler4),
#if !defined(ZEND_ENGINE_2) && defined(__MINGW32__)
							  return_msc_structure,
#else
							  get_property_handler,
#endif
							  set_property_handler);

  EXT_GLOBAL(class_entry) = zend_register_internal_class(&ce TSRMLS_CC);

  INIT_CLASS_ENTRY(ce, EXT_NAME()/**/"_class", NULL);
  parent = (zend_class_entry *) EXT_GLOBAL(class_entry);
  EXT_GLOBAL(class_class_entry) = 
	zend_register_internal_class_ex(&ce, parent, NULL TSRMLS_CC);
  INIT_CLASS_ENTRY(ce, EXT_NAME()/**/"class", NULL);
  parent = (zend_class_entry *) EXT_GLOBAL(class_class_entry);
  EXT_GLOBAL(class_class_entry_jsr) = 
	zend_register_internal_class_ex(&ce, parent, NULL TSRMLS_CC);

  make_lambda(&serialize, EXT_FN(EXT_GLOBAL(__sleep)));
  make_lambda(&deserialize, EXT_FN(EXT_GLOBAL(__wakeup)));

  if((FAILURE == (zend_hash_add(&EXT_GLOBAL(class_entry)->function_table, 
								(char*)nserialize, sizeof(nserialize), &serialize, sizeof(zend_function), NULL))) ||
	 (FAILURE == (zend_hash_add(&EXT_GLOBAL(class_entry)->function_table, 
								(char*)ndeserialize, sizeof(ndeserialize), &deserialize, sizeof(zend_function), NULL))))
	{
	  php_error(E_ERROR, "Could not register __sleep/__wakeup methods.");
	  return FAILURE;
	}

#else
  zend_class_entry ce;
  zend_internal_function call, get, set;
  
  make_lambda(&call, EXT_MN(EXT_GLOBAL(__call)));
  make_lambda(&get, EXT_MN(EXT_GLOBAL(__get)));
  make_lambda(&set, EXT_MN(EXT_GLOBAL(__set)));
  
  INIT_OVERLOADED_CLASS_ENTRY(ce, EXT_NAMEC(), 
							  EXT_GLOBAL(class_functions), 
							  (zend_function*)&call, 
							  (zend_function*)&get, 
							  (zend_function*)&set);

  memcpy(&EXT_GLOBAL(handlers), zend_get_std_object_handlers(), sizeof EXT_GLOBAL(handlers));
  //EXT_GLOBAL(handlers).clone_obj = clone;
  EXT_GLOBAL(handlers).cast_object = cast;

  EXT_GLOBAL(class_entry) =
	zend_register_internal_class(&ce TSRMLS_CC);
  EXT_GLOBAL(class_entry)->get_iterator = get_iterator;
  EXT_GLOBAL(class_entry)->create_object = EXT_GLOBAL(create_object);

  INIT_CLASS_ENTRY(ce, EXT_NAMEC()/**/"Array", 
				   (array_class_functions));
  parent = (zend_class_entry *) EXT_GLOBAL(class_entry);
  EXT_GLOBAL(array_entry) =
	zend_register_internal_class_ex(&ce, parent, NULL TSRMLS_CC);

  zend_class_implements(EXT_GLOBAL(array_entry) TSRMLS_CC, 1, 
						zend_ce_arrayaccess);

  INIT_OVERLOADED_CLASS_ENTRY(ce, EXT_NAME()/**/"_exception", 
							  EXT_GLOBAL(class_functions), 
							  (zend_function*)&call, 
							  (zend_function*)&get, 
							  (zend_function*)&set);
  
  parent = (zend_class_entry *) zend_exception_get_default();
  EXT_GLOBAL(exception_class_entry) =
	zend_register_internal_class_ex(&ce, parent, NULL TSRMLS_CC);
  // only cast and clone; no iterator, no array access
  EXT_GLOBAL(exception_class_entry)->create_object = EXT_GLOBAL(create_exception_object);
  
  INIT_CLASS_ENTRY(ce, EXT_NAME()/**/"_class", (class_class_functions));
  parent = (zend_class_entry *) EXT_GLOBAL(class_entry);

  EXT_GLOBAL(class_class_entry) = 
	zend_register_internal_class_ex(&ce, parent, NULL TSRMLS_CC);

  /* compatibility with the jsr implementation */
  INIT_CLASS_ENTRY(ce, EXT_NAMEC()/**/"Class", (class_class_functions));
  parent = (zend_class_entry *) EXT_GLOBAL(class_entry);
  EXT_GLOBAL(class_class_entry_jsr) = 
	zend_register_internal_class_ex(&ce, parent, NULL TSRMLS_CC);

  INIT_CLASS_ENTRY(ce, EXT_NAMEC()/**/"Exception", EXT_GLOBAL(class_functions));
  parent = (zend_class_entry *) EXT_GLOBAL(exception_class_entry);
  EXT_GLOBAL(exception_class_entry) = 
	zend_register_internal_class_ex(&ce, parent, NULL TSRMLS_CC);

#endif
  
  EXT_INIT_MODULE_GLOBALS(EXT, EXT_GLOBAL(alloc_globals_ctor), NULL);
  
  assert(!EXT_GLOBAL (cfg) );
  if(!EXT_GLOBAL (cfg) ) EXT_GLOBAL (cfg) = malloc(sizeof *EXT_GLOBAL (cfg) ); 
  if(!EXT_GLOBAL (cfg) ) exit(9);

  if(REGISTER_INI_ENTRIES()==SUCCESS) {
	char *tmpdir, sockname_shm[] = SOCKNAME_SHM, sockname[] = SOCKNAME;
	/* set the default values for all undefined */
	
	EXT_GLOBAL(init_cfg) (TSRMLS_C);

	EXT_GLOBAL(clone_cfg)(TSRMLS_C);
	EXT_GLOBAL(start_server) (TSRMLS_C);
	EXT_GLOBAL(destroy_cloned_cfg)(TSRMLS_C);
	EXT_GLOBAL(mktmpdir)();
	
#ifndef __MINGW32__
	EXT_GLOBAL(cfg)->pid = getpid();
#endif

  } 
  return SUCCESS;
}
struct save_cfg {
  int ini_user;
  char *servlet, *hosts;
};
static void push_cfg(struct save_cfg*cfg TSRMLS_DC) {
  cfg->ini_user = JG(ini_user);
  cfg->servlet = JG(servlet);
  cfg->hosts = JG(hosts);
  JG(ini_user) = EXT_GLOBAL(ini_user);
  if(!(JG(hosts)=strdup(EXT_GLOBAL(cfg)->hosts))) exit(9);
  if(!(JG(servlet)=strdup(EXT_GLOBAL(cfg)->servlet))) exit(9);
}
static void pop_cfg(struct save_cfg*cfg TSRMLS_DC) {
  JG(ini_user) = cfg->ini_user;
  if(JG(servlet)) free(JG(servlet)); 
  JG(servlet) = cfg->servlet;
  if(JG(hosts)) free(JG(hosts)); 
  JG(hosts) = cfg->hosts;
}
/**
 * Displays the module info.
 */
PHP_MINFO_FUNCTION(EXT)
{
  static const char on[]="On";
  static const char off[]="Off";
  short is_local, is_level;
  char*s, *server;
  struct save_cfg saved_cfg;

  push_cfg(&saved_cfg TSRMLS_CC);
  EXT_GLOBAL(override_ini_for_redirect)(TSRMLS_C);
  s = EXT_GLOBAL(get_server_string) (TSRMLS_C);
  server = EXT_GLOBAL(test_server) (0, &is_local, 0 TSRMLS_CC);
  is_level = ((EXT_GLOBAL (ini_user)&U_LOGLEVEL)!=0);

  php_info_print_table_start();
  php_info_print_table_row(2, EXT_NAME()/**/" support", "Enabled");
  php_info_print_table_row(2, EXT_NAME()/**/" bridge", EXT_GLOBAL(bridge_version));
#if EXTENSION == JAVA
  if(is_local) {
								/* don't show default value, they may
								   not be used anyway */
	if((EXT_GLOBAL(option_set_by_user) (U_LIBRARY_PATH, EXT_GLOBAL(ini_user))))
	  php_info_print_table_row(2, EXT_NAME()/**/".libpath", EXT_GLOBAL(cfg)->ld_library_path);

								/* don't show default value, they may
								   not be used anyway */
	if((EXT_GLOBAL(option_set_by_user) (U_CLASSPATH, EXT_GLOBAL(ini_user))))
	  php_info_print_table_row(2, EXT_NAME()/**/".classpath", EXT_GLOBAL(cfg)->classpath);
  }
#endif
  if(!server || is_local) {
	php_info_print_table_row(2, EXT_NAME()/**/"."/**/EXT_NAME()/**/"_home", EXT_GLOBAL(cfg)->vm_home);
	php_info_print_table_row(2, EXT_NAME()/**/"."/**/EXT_NAME(), EXT_GLOBAL(cfg)->vm);
	if((EXT_GLOBAL(option_set_by_user) (U_WRAPPER, EXT_GLOBAL(ini_user))))
	  php_info_print_table_row(2, EXT_NAME()/**/".wrapper", EXT_GLOBAL(cfg)->wrapper);
	if(strlen(EXT_GLOBAL(cfg)->logFile)==0) 
	  php_info_print_table_row(2, EXT_NAME()/**/".log_file", "<stderr>");
	else
	  php_info_print_table_row(2, EXT_NAME()/**/".log_file", EXT_GLOBAL(cfg)->logFile);
  }
  php_info_print_table_row(2, EXT_NAME()/**/".log_level", is_level ? EXT_GLOBAL(cfg)->logLevel : "no value (use back-end's default level)");
  if(EXT_GLOBAL(option_set_by_user) (U_HOSTS, EXT_GLOBAL(ini_user)))  
	php_info_print_table_row(2, EXT_NAME()/**/".hosts", JG(hosts));
#if EXTENSION == JAVA
  if(EXT_GLOBAL(option_set_by_user) (U_SERVLET, EXT_GLOBAL(ini_user)))  
	php_info_print_table_row(2, EXT_NAME()/**/".servlet", JG(servlet)?JG(servlet):off);
#endif
#ifndef ZEND_ENGINE_2
  php_info_print_table_row(2, EXT_NAME()/**/".ext_java_compatibility", on);
#else
  php_info_print_table_row(2, EXT_NAME()/**/".ext_java_compatibility", EXT_GLOBAL(cfg)->extJavaCompatibility?on:off);
#endif
  php_info_print_table_row(2, EXT_NAME()/**/".persistent_connections", EXT_GLOBAL(cfg)->persistent_connections?on:off);
  if(!server || is_local) {
	if(!EXT_GLOBAL(cfg)->policy) {
	  php_info_print_table_row(2, EXT_NAME()/**/".security_policy", "Off");
	} else {
	  /* set by user */
	  if(EXT_GLOBAL(option_set_by_user) (U_POLICY, EXT_GLOBAL(ini_user)))
		php_info_print_table_row(2, EXT_NAME()/**/".security_policy", EXT_GLOBAL(cfg)->policy);
	  else
		php_info_print_table_row(2, EXT_NAME()/**/".security_policy", "Off");
	}
  }
  php_info_print_table_row(2, EXT_NAME()/**/" command", s);
  php_info_print_table_row(2, EXT_NAME()/**/" status", server?"running":"not running");
  php_info_print_table_row(2, EXT_NAME()/**/" server", server?server:"localhost");
  php_info_print_table_end();
  
  free(server);
  free(s);
  pop_cfg(&saved_cfg TSRMLS_CC);
}

/**
 * Called when the module terminates. Stops the back-end, if it is running.
 * When running in Apache/IIS or as a FastCGI binary, this procedure is 
 * called only once. When running as a CGI binary this is called whenever
 * the CGI binary terminates.
 */
PHP_MSHUTDOWN_FUNCTION(EXT) 
{
  proxyenv **env;
  HashTable *connections = &JG(connections);
  zend_hash_internal_pointer_reset(connections);
  while(SUCCESS==zend_hash_get_current_data(connections, (void**)&env)) {
	EXT_GLOBAL(activate_connection)(*env TSRMLS_CC);
	EXT_GLOBAL(close_connection) (*env, 0 TSRMLS_CC);
	zend_hash_move_forward(connections);
  }
  zend_hash_destroy(connections);
  
  EXT_GLOBAL(destroy_cfg) (EXT_GLOBAL(ini_set));
  EXT_GLOBAL(ini_user) = EXT_GLOBAL(ini_set) = 0;

  UNREGISTER_INI_ENTRIES();
  EXT_GLOBAL(shutdown_library) ();

  assert(EXT_GLOBAL (cfg));
  if(EXT_GLOBAL (cfg) ) { 
	EXT_GLOBAL(rmtmpdir)();
	free(EXT_GLOBAL (cfg) ); EXT_GLOBAL (cfg) = 0; 
  }
	
  return SUCCESS;
}

#ifndef PHP_WRAPPER_H
#error must include php_wrapper.h
#endif
