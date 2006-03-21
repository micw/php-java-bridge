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

#ifdef ZEND_ENGINE_2
#include "zend_interfaces.h"
#include "zend_exceptions.h"
#endif

EXT_DECLARE_MODULE_GLOBALS(EXT)

/**
 * Holds the global configuration.
 * This structure is shared by all php instances
 */
struct cfg *EXT_GLOBAL (cfg)  = 0;

#ifdef __MINGW32__
static const int java_errno=0;
int *__errno (void) { return (int*)&java_errno; }
#define php_info_print_table_row(a, b, c)		\
  php_info_print_table_row_ex(a, "v", b, c)
#endif

static void clone_cfg(TSRMLS_D) {
  JG(ini_user)=EXT_GLOBAL(ini_user);
  JG(hosts)=strdup(EXT_GLOBAL(cfg)->hosts);
  JG(servlet)=strdup(EXT_GLOBAL(cfg)->servlet);
}
static void destroy_cloned_cfg(TSRMLS_D) {
  if(JG(hosts)) free(JG(hosts));
  if(JG(servlet)) free(JG(servlet));
  JG(ini_user)=0;
  JG(hosts)=0;
  JG(servlet)=0;
}

/**
 * Called when a new request starts.  Opens a connection to the
 * backend, creates an instance of the proxyenv structure and clones
 * the servlet, hosts and ini_user flags.
 */
PHP_RINIT_FUNCTION(EXT) 
{
  if(EXT_GLOBAL(cfg)) {
	clone_cfg(TSRMLS_C);
  }

  EXT_GLOBAL(init_channel)(TSRMLS_C);

  if(JG(jenv)) {
	php_error(E_ERROR, "php_mod_"/**/EXT_NAME()/**/"(%d): Synchronization problem, rinit with active connection called. Cannot continue, aborting now. Please report this to: php-java-bridge-users@lists.sourceforge.net",59);
  }
  JG(is_closed)=0;
  return SUCCESS;
}

/**
 * Called when the request terminates. Closes the connection to the
 * backend, destroys the proxyenv instance.
 */
PHP_RSHUTDOWN_FUNCTION(EXT)
{
  destroy_cloned_cfg(TSRMLS_C);

  if(JG(jenv)) {
	if(*JG(jenv)) {
	  if((*JG(jenv))->peer!=-1) {
		/* end servlet session */
		EXT_GLOBAL(protocol_end)(JG(jenv));
		close((*JG(jenv))->peer);
		if((*JG(jenv))->peerr!=-1) close((*JG(jenv))->peerr);
		if((*JG(jenv))->peer0!=-1) close((*JG(jenv))->peer0);
	  }
	  if((*JG(jenv))->s) free((*JG(jenv))->s);
	  if((*JG(jenv))->send) free((*JG(jenv))->send);
	  if((*JG(jenv))->server_name) free((*JG(jenv))->server_name);
	  if((*JG(jenv))->servlet_ctx) free((*JG(jenv))->servlet_ctx);
	  if((*JG(jenv))->servlet_context_string) free((*JG(jenv))->servlet_context_string);
	  free(*JG(jenv));
	}
	free(JG(jenv));
  }

  JG(jenv) = NULL;
  EXT_GLOBAL(destroy_channel)(TSRMLS_C);

  JG(is_closed)=1;
  return SUCCESS;
}

static void last_exception_get(proxyenv *jenv, zval**return_value)
{
  (*jenv)->writeInvokeBegin(jenv, 0, "lastException", 0, 'P', *return_value);
  (*jenv)->writeInvokeEnd(jenv);
}

/**
 * Proto: object java_last_exception_get(void)
 *
 * Get last Java exception
 * \deprecated Use PHP5 try/catch instead.
 */
EXT_FUNCTION(EXT_GLOBAL(last_exception_get))
{
  proxyenv *jenv;
  if (ZEND_NUM_ARGS()!=0) WRONG_PARAM_COUNT;
  jenv = EXT_GLOBAL(connect_to_server)(TSRMLS_C);
  if(!jenv) {RETURN_NULL();}
  if((*jenv)->handle==(*jenv)->async_ctx.handle_request) { /* async protocol */
	php_error(E_ERROR, "php_mod_"/**/EXT_NAME()/**/"(%d): last_exception_get() invalid while in stream mode", 21);
	RETURN_NULL();
  }

  last_exception_get(jenv, &return_value);
}


static void last_exception_clear(proxyenv*jenv, zval**return_value) {
  (*jenv)->writeInvokeBegin(jenv, 0, "lastException", 0, 'P', *return_value);
  (*jenv)->writeObject(jenv, 0);
  (*jenv)->writeInvokeEnd(jenv);
}

/**
 * Proto: void java_last_exception_clear(void)
 *
 * Clear last java extension.
 * \deprecated Use PHP5 try/catch instead.
*/
EXT_FUNCTION(EXT_GLOBAL(last_exception_clear))
{
  proxyenv *jenv;
  if (ZEND_NUM_ARGS()!=0) WRONG_PARAM_COUNT;
  jenv = EXT_GLOBAL(connect_to_server)(TSRMLS_C);
  if(!jenv) {RETURN_NULL();}

  last_exception_clear(jenv, &return_value);
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
  zval **enc;
  proxyenv *jenv = EXT_GLOBAL(connect_to_server)(TSRMLS_C);
  if(!jenv) {RETURN_NULL();}

  if (ZEND_NUM_ARGS()!=1 || zend_get_parameters_ex(1, &enc) == FAILURE)
	WRONG_PARAM_COUNT;

  convert_to_string_ex(enc);

  (*jenv)->writeInvokeBegin(jenv, 0, "setFileEncoding", 0, 'I', return_value);
  (*jenv)->writeString(jenv, Z_STRVAL_PP(enc), Z_STRLEN_PP(enc));
  (*jenv)->writeInvokeEnd(jenv);
}


static void require(INTERNAL_FUNCTION_PARAMETERS) {
  static const char ext_dir[] = "extension_dir";
  char *ext = php_ini_string((char*)ext_dir, sizeof ext_dir, 0);
  zval **path;
  proxyenv *jenv = EXT_GLOBAL(connect_to_server)(TSRMLS_C);
  if(!jenv) {RETURN_NULL();}

  if (ZEND_NUM_ARGS()!=1 || zend_get_parameters_ex(1, &path) == FAILURE)
	WRONG_PARAM_COUNT;

  convert_to_string_ex(path);

#if EXTENSION == JAVA
  (*jenv)->writeInvokeBegin(jenv, 0, "setJarLibraryPath", 0, 'I', return_value);
#else
  (*jenv)->writeInvokeBegin(jenv, 0, "setLibraryPath", 0, 'I', return_value);
#endif
  (*jenv)->writeString(jenv, Z_STRVAL_PP(path), Z_STRLEN_PP(path));
  (*jenv)->writeString(jenv, ext, strlen(ext));
  (*jenv)->writeInvokeEnd(jenv);
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
 * access the library you have loaded, you must reset the backend to
 * clear the loader cache and load your classes and the library in one
 * java_require() call.
 */
EXT_FUNCTION(EXT_GLOBAL(require))
{
  require(INTERNAL_FUNCTION_PARAM_PASSTHRU);
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
  zval **pobj, **pclass;
  long obj, class;
  proxyenv *jenv = EXT_GLOBAL(connect_to_server)(TSRMLS_C);
  if(!jenv) {RETURN_NULL();}

  if (ZEND_NUM_ARGS()!=2 || zend_get_parameters_ex(2, &pobj, &pclass) == FAILURE)
	WRONG_PARAM_COUNT;

  convert_to_object_ex(pobj);
  convert_to_object_ex(pclass);

  obj = 0;
  EXT_GLOBAL(get_jobject_from_object)(*pobj, &obj TSRMLS_CC);
  if(!obj) {
	zend_error(E_ERROR, "Argument #1 for %s() must be a "/**/EXT_NAME()/**/" object", get_active_function_name(TSRMLS_C));
	return;
  }

  class = 0;
  EXT_GLOBAL(get_jobject_from_object)(*pclass, &class TSRMLS_CC);
  if(!class) {
	zend_error(E_ERROR, "Argument #2 for %s() must be a "/**/EXT_NAME()/**/" object", get_active_function_name(TSRMLS_C));
	return;
  }

  (*jenv)->writeInvokeBegin(jenv, 0, "InstanceOf", 0, 'I', return_value);
  (*jenv)->writeObject(jenv, obj);
  (*jenv)->writeObject(jenv, class);
  (*jenv)->writeInvokeEnd(jenv);
}

static long session_get_default_lifetime() {
  static const char session_max_lifetime[]="session.gc_maxlifetime";
  long l = zend_ini_long((char*)session_max_lifetime, sizeof(session_max_lifetime), 0);
  return l==0?1440:l;
}
static void session(INTERNAL_FUNCTION_PARAMETERS)
{
  proxyenv *jenv;
  zval **session=0, **is_new=0;
  int argc=ZEND_NUM_ARGS();
  
  if (argc>2 || zend_get_parameters_ex(argc, &session, &is_new) == FAILURE)
	WRONG_PARAM_COUNT;

  jenv=EXT_GLOBAL(connect_to_server)(TSRMLS_C);
  if(!jenv) RETURN_NULL();
  if((*jenv)->handle==(*jenv)->async_ctx.handle_request) { /* async protocol */
	php_error(E_ERROR, "php_mod_"/**/EXT_NAME()/**/"(%d): get_session() invalid while in stream mode", 21);
	RETURN_NULL();
  }

  assert(EXT_GLOBAL(cfg)->is_cgi_servlet && (*jenv)->servlet_ctx ||!EXT_GLOBAL(cfg)->is_cgi_servlet);
								/* create a new connection to the
								   backend if java_session() is not
								   the first statement in a script */
  EXT_GLOBAL(check_session) (jenv TSRMLS_CC);

  (*jenv)->writeInvokeBegin(jenv, 0, "getSession", 0, 'I', return_value);
  if(argc>0 && Z_TYPE_PP(session)!=IS_NULL) {
	convert_to_string_ex(session);
	(*jenv)->writeString(jenv, Z_STRVAL_PP(session), Z_STRLEN_PP(session)); 
  } else {
	(*jenv)->writeObject(jenv, 0);
  }
  (*jenv)->writeBoolean(jenv, (argc<2||Z_TYPE_PP(is_new)==IS_NULL)?0:Z_BVAL_PP(is_new)); 

  (*jenv)->writeLong(jenv, session_get_default_lifetime()); // session.gc_maxlifetime

  (*jenv)->writeInvokeEnd(jenv);
  (*jenv)->backend_has_session_proxy=1;
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
 * java_get_session("myPrivateApplicationStore")->put("key", "value");
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
  session(INTERNAL_FUNCTION_PARAM_PASSTHRU);
}

static void context(INTERNAL_FUNCTION_PARAMETERS)
{
  proxyenv *jenv;
  int argc=ZEND_NUM_ARGS();
  
  if (argc!=0)
	WRONG_PARAM_COUNT;

  jenv=EXT_GLOBAL(connect_to_server)(TSRMLS_C);
  if(!jenv) RETURN_NULL();
 
  assert(EXT_GLOBAL(cfg)->is_cgi_servlet && (*jenv)->servlet_ctx ||!EXT_GLOBAL(cfg)->is_cgi_servlet);
  (*jenv)->writeInvokeBegin(jenv, 0, "getContext", 0, 'I', return_value);
  (*jenv)->writeInvokeEnd(jenv);
}

/**
 * Proto: object java_context(void) or object java_get_context(void)
 *
 * Returns the jsr223 script context handle.
 *
 * Example which closes over the current environment and pass it back to java:
 * \code
 * java_get_context()->call(java_closure()) || die "Script should be called from java";
 * \endcode
 *
 * It is possible to access implicit web objects (the session, the
 * application store etc.) from the context. Please see the JSR223
 * documentation or for details. Example:
 * \code
 * java_get_context()->getHttpServletRequest();
 * \endcode
 * @see get_session()
 */
EXT_FUNCTION(EXT_GLOBAL(get_context))
{
  context(INTERNAL_FUNCTION_PARAM_PASSTHRU);
}

/**
 * Proto: string java_server_name(void) or string java_get_server_name(void)
 *
 * Returns the name of the backend or null if the backend is not running. Example:
 * \code
 * $backend = java_get_server_name();
 * if(!$backend) wakeup_administrator("backend not running");
 * echo "Connected to the backend: $backend\n";
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
 * Tries to reset the backent to
 * its initial state. If the call succeeds, all session handles and
 * caches are gone. 
 *
 * Example:
 * \code echo "Resetting backend to initial state\n";
 * java_reset();
 * \endcode
 *
 * This procedure does nothing when the backend runs
 * in a servlet environment or an application server. 
 */
EXT_FUNCTION(EXT_GLOBAL(reset))
{
  proxyenv *jenv;
  if (ZEND_NUM_ARGS()!=0) WRONG_PARAM_COUNT;

  jenv = EXT_GLOBAL(connect_to_server)(TSRMLS_C);
  if(!jenv) RETURN_NULL();

  (*jenv)->writeInvokeBegin(jenv, 0, "reset", 0, 'I', return_value);
  (*jenv)->writeInvokeEnd(jenv);
  php_error(E_WARNING, "php_mod_"/**/EXT_NAME()/**/"(%d): Your script has called the privileged procedure \""/**/EXT_NAME()/**/"_reset()\" which resets the "/**/EXT_NAME()/**/" backend to its initial state. Therefore all "/**/EXT_NAME()/**/" session variables and all caches are gone.", 18);
}

/**
 * Proto: void java_begin_document(void)
 *
 * Enters stream mode (asynchronuous protocol). The statements are
 * sent to the backend in one XML stream.
*/
EXT_FUNCTION(EXT_GLOBAL(begin_document))
{
  static const char begin[] = "beginDocument";
  proxyenv *jenv;
  if (ZEND_NUM_ARGS()!=0) WRONG_PARAM_COUNT;
  jenv = EXT_GLOBAL(connect_to_server)(TSRMLS_C);
  if(!jenv) {RETURN_NULL();}
  if((*jenv)->handle==(*jenv)->async_ctx.handle_request) { /* async protocol */
	php_error(E_ERROR, "php_mod_"/**/EXT_NAME()/**/"(%d): begin_document() invalid while in stream mode", 21);
	RETURN_NULL();
  }

  (*jenv)->writeInvokeBegin(jenv, 0, (char*)begin, sizeof(begin)-1, 'I', return_value);
  (*jenv)->writeInvokeEnd(jenv);
  EXT_GLOBAL(begin_async)(jenv);
}
/**
 * Proto: void java_end_document(void)
 *
 * Ends stream mode.
*/
EXT_FUNCTION(EXT_GLOBAL(end_document))
{
  static const char end[] = "endDocument";
  proxyenv *jenv;
  if (ZEND_NUM_ARGS()!=0) WRONG_PARAM_COUNT;
  jenv = EXT_GLOBAL(connect_to_server)(TSRMLS_C);
  if(!jenv) {RETURN_NULL();}
  if((*jenv)->handle!=(*jenv)->async_ctx.handle_request) { /* async protocol */
	php_error(E_ERROR, "php_mod_"/**/EXT_NAME()/**/"(%d): end_document() invalid when not in stream mode", 21);
	RETURN_NULL();
  }

  (*jenv)->writeInvokeBegin(jenv, 0, (char*)end, sizeof(end)-1, 'I', return_value);
  EXT_GLOBAL(end_async)(jenv);
  (*jenv)->writeInvokeEnd(jenv);
}

static void values(INTERNAL_FUNCTION_PARAMETERS)
{
  proxyenv *jenv;
  zval **pobj;
  long obj;

  jenv = EXT_GLOBAL(connect_to_server)(TSRMLS_C);
  if(!jenv) RETURN_NULL();
  if((*jenv)->handle==(*jenv)->async_ctx.handle_request) { /* async protocol */
	php_error(E_ERROR, "php_mod_"/**/EXT_NAME()/**/"(%d): values() invalid while in stream mode", 21);
	RETURN_NULL();
  }

  if (ZEND_NUM_ARGS()!=1 || zend_get_parameters_ex(1, &pobj) == FAILURE)
	WRONG_PARAM_COUNT;

  convert_to_object_ex(pobj);
  obj = 0;
  EXT_GLOBAL(get_jobject_from_object)(*pobj, &obj TSRMLS_CC);
  if(!obj) {
	*return_value = **pobj;
	zval_copy_ctor(return_value);
	return;
  }

  (*jenv)->writeInvokeBegin(jenv, 0, "getValues", 0, 'I', return_value);
  (*jenv)->writeObject(jenv, obj);
  (*jenv)->writeInvokeEnd(jenv);
}
static const char warn_session[] = 
"the session module's session_write_close() tried to write garbage, aborted. \
-- Have you loaded the session module before the java module? \n Use \
java_session(session_id())->put(key,val) instead of the \
\"$_SESSION[key]=val\" syntax, if you don't want to depend on the \
session module. Else if \"session_write_close();\" at the end of \
your script fixes this problem, please report this bug \
to the PHP release team.";
static const char identity[] = "serialID";
static void serialize(INTERNAL_FUNCTION_PARAMETERS)
{
  long obj;
  zval *handle, *id;

  proxyenv *jenv = EXT_GLOBAL(connect_to_server)(TSRMLS_C);
  if(!jenv) {
	php_error(E_WARNING, EXT_NAME()/**/" cannot be serialized. %s", warn_session);
	RETURN_NULL();
  }
  if((*jenv)->handle==(*jenv)->async_ctx.handle_request) { /* async protocol */
	php_error(E_ERROR, "php_mod_"/**/EXT_NAME()/**/"(%d): serialize() invalid while in stream mode", 21);
	RETURN_NULL();
  }

  EXT_GLOBAL(get_jobject_from_object)(getThis(), &obj TSRMLS_CC);
  if(!obj) {
	/* set a breakpoint in java_bridge.c destroy_object, in rshutdown
	   and get_jobject_from_object */
	php_error(E_WARNING, EXT_NAME()/**/" cannot be serialized. %s", warn_session);
	RETURN_NULL();
  }

  MAKE_STD_ZVAL(handle);
  ZVAL_NULL(handle);
  (*jenv)->writeInvokeBegin(jenv, 0, "serialize", 0, 'I', handle);
  (*jenv)->writeObject(jenv, obj);
  (*jenv)->writeLong(jenv, session_get_default_lifetime()); // session.gc_maxlifetime
  (*jenv)->writeInvokeEnd(jenv);
  zend_hash_update(Z_OBJPROP_P(getThis()), (char*)identity, sizeof identity, &handle, sizeof(pval *), NULL);

  /* Return the field that should be serialized ("serialID") */
  array_init(return_value);
  INIT_PZVAL(return_value);

  MAKE_STD_ZVAL(id);
  Z_TYPE_P(id)=IS_STRING;
  Z_STRLEN_P(id)=sizeof(identity)-1;
  Z_STRVAL_P(id)=estrdup(identity);
  zend_hash_index_update(Z_ARRVAL_P(return_value), 0, &id, sizeof(pval*), NULL);
}
static void deserialize(INTERNAL_FUNCTION_PARAMETERS)
{
  zval *handle, **id;
  int err;

  proxyenv *jenv = EXT_GLOBAL(connect_to_server)(TSRMLS_C);
  if(!jenv) {
	php_error(E_ERROR, EXT_NAME()/**/" cannot be de-serialized. %s", warn_session);
  }
  if((*jenv)->handle==(*jenv)->async_ctx.handle_request) { /* async protocol */
	php_error(E_ERROR, "php_mod_"/**/EXT_NAME()/**/"(%d): deserialize() invalid while in stream mode", 21);
	RETURN_NULL();
  }

  err = zend_hash_find(Z_OBJPROP_P(getThis()), (char*)identity, sizeof identity, (void**)&id);
  if(FAILURE==err) {
	/* set a breakpoint in java_bridge.c destroy_object, in rshutdown
	   and get_jobject_from_object */
	php_error(E_WARNING, EXT_NAME()/**/" cannot be deserialized. %s", warn_session);
  }
  
  MAKE_STD_ZVAL(handle);
  ZVAL_NULL(handle);
  (*jenv)->writeInvokeBegin(jenv, 0, "deserialize", 0, 'I', handle);
  (*jenv)->writeString(jenv, Z_STRVAL_PP(id), Z_STRLEN_PP(id));
  (*jenv)->writeLong(jenv, session_get_default_lifetime()); // use session.gc_maxlifetime
  (*jenv)->writeInvokeEnd(jenv);
  if(Z_TYPE_P(handle)!=IS_LONG) {
#ifndef ZEND_ENGINE_2
	php_error(E_WARNING, EXT_NAME()/**/" cannot be deserialized, session expired.");
#endif
	ZVAL_NULL(getThis());
  }	else {
#ifndef ZEND_ENGINE_2
	zend_hash_index_update(Z_OBJPROP_P(getThis()), 0, &handle, sizeof(pval *), NULL);
#else
	EXT_GLOBAL(store_jobject)(getThis(), Z_LVAL_P(handle) TSRMLS_CC);
#endif
  }
  
  RETURN_NULL();
}
#ifndef ZEND_ENGINE_2
EXT_FUNCTION(EXT_GLOBAL(__sleep))
{
  serialize(INTERNAL_FUNCTION_PARAM_PASSTHRU);
}
EXT_FUNCTION(EXT_GLOBAL(__wakeup))
{
  deserialize(INTERNAL_FUNCTION_PARAM_PASSTHRU);
}
#endif

/** Proto: mixed java_values(object ob) or mixed java_get_values(object ob)
 *
 * Fetches the value(s) of the java object into a php variable. ob
 * must be a java object. A java array, Map or Collection is returned
 * as a php array. Please make sure that the values do not not exceed
 * php's memory limit. Example:
 *
 * \code
 * function fetchValues($obj) { 
 *  if($obj instanceof Java) return java_values($obj);
 *  return $obj;
 * }
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
  values(INTERNAL_FUNCTION_PARAM_PASSTHRU);
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
 * When a php instance is supplied as a argument, that environment will be used
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
  char *string_key;
  ulong num_key;
  zval **pobj, **pfkt, **pclass, **val;
  long class = 0;
  int key_type;
  proxyenv *jenv;
  int argc = ZEND_NUM_ARGS();

  if (argc>3 || zend_get_parameters_ex(argc, &pobj, &pfkt, &pclass) == FAILURE)
	WRONG_PARAM_COUNT;

  jenv = EXT_GLOBAL(connect_to_server)(TSRMLS_C);
  if(!jenv) RETURN_NULL();


  if (argc>0 && *pobj && Z_TYPE_PP(pobj) == IS_OBJECT) {
	zval_add_ref(pobj);
  }

  (*jenv)->writeInvokeBegin(jenv, 0, "makeClosure", 0, 'I', return_value);
  (*jenv)->writeLong(jenv, (argc==0||Z_TYPE_PP(pobj)==IS_NULL)?0:(long)*pobj);

  /* fname -> cname Map */
  if(argc>1) {
	if (Z_TYPE_PP(pfkt) == IS_ARRAY) {
	  (*jenv)->writeCompositeBegin_h(jenv);
	  zend_hash_internal_pointer_reset(Z_ARRVAL_PP(pfkt));
	  while ((key_type = zend_hash_get_current_key(Z_ARRVAL_PP(pfkt), &string_key, &num_key, 1)) != HASH_KEY_NON_EXISTANT) {
		if ((zend_hash_get_current_data(Z_ARRVAL_PP(pfkt), (void**)&val) == SUCCESS)) {
		  if(Z_TYPE_PP(val) == IS_STRING && key_type==HASH_KEY_IS_STRING) { 
			size_t len = strlen(string_key);
			(*jenv)->writePairBegin_s(jenv, string_key, len);
			(*jenv)->writeString(jenv, Z_STRVAL_PP(val), Z_STRLEN_PP(val));
			(*jenv)->writePairEnd(jenv);
		  } else {
			zend_error(E_ERROR, "Argument #2 for %s() must be null, a string, or a map of java => php function names.", get_active_function_name(TSRMLS_C));
		  }
		}
		zend_hash_move_forward(Z_ARRVAL_PP(pfkt));
	  }
	  (*jenv)->writeCompositeEnd(jenv);
	} else if (Z_TYPE_PP(pfkt) == IS_STRING) {
	  (*jenv)->writeString(jenv, Z_STRVAL_PP(pfkt), Z_STRLEN_PP(pfkt));
	} else {
	  (*jenv)->writeCompositeBegin_h(jenv);
	  (*jenv)->writeCompositeEnd(jenv);
	}
  }

  /* interfaces */
  if(argc>2) {
	(*jenv)->writeCompositeBegin_a(jenv);
	if(Z_TYPE_PP(pclass) == IS_ARRAY) {
	  zend_hash_internal_pointer_reset(Z_ARRVAL_PP(pclass));
	  while ((key_type = zend_hash_get_current_data(Z_ARRVAL_PP(pclass), (void**)&val)) == SUCCESS) {
		EXT_GLOBAL(get_jobject_from_object)(*val, &class TSRMLS_CC);
		if(class) { 
		  (*jenv)->writePairBegin(jenv);
		  (*jenv)->writeObject(jenv, class);
		  (*jenv)->writePairEnd(jenv);
		} else {
		  zend_error(E_ERROR, "Argument #3 for %s() must be a "/**/EXT_NAME()/**/" interface or an array of interfaces.", get_active_function_name(TSRMLS_C));
		}
		zend_hash_move_forward(Z_ARRVAL_PP(pclass));
	  }
	} else {
	  EXT_GLOBAL(get_jobject_from_object)(*pclass, &class TSRMLS_CC);
	  if(class) { 
		(*jenv)->writePairBegin(jenv);
		(*jenv)->writeObject(jenv, class);
		(*jenv)->writePairEnd(jenv);
	  } else {
		zend_error(E_ERROR, "Argument #3 for %s() must be a "/**/EXT_NAME()/**/" interface or an array of interfaces.", get_active_function_name(TSRMLS_C));
	  }
	}
	(*jenv)->writeCompositeEnd(jenv);
  }
  (*jenv)->writeInvokeEnd(jenv);
}


/**
 * Only for internal use.
 */
EXT_FUNCTION(EXT_GLOBAL(exception_handler))
{
  zval **pobj;
  if (ZEND_NUM_ARGS()!=1 || zend_get_parameters_ex(1, &pobj) == FAILURE) WRONG_PARAM_COUNT;
  MAKE_STD_ZVAL(JG(exception)); 
  *JG(exception)=**pobj;
  zval_copy_ctor(JG(exception));

  RETURN_NULL();
}

/**
 * Only for internal use
 */
static void check_php4_exception(TSRMLS_D) {
#ifndef ZEND_ENGINE_2
  last_exception_get(JG(jenv), &JG(exception));
#endif
}
static int allocate_php4_exception(TSRMLS_D) {
#ifndef ZEND_ENGINE_2
  MAKE_STD_ZVAL(JG(exception));
  ZVAL_NULL(JG(exception));
  last_exception_clear(JG(jenv), &JG(exception));
#endif
  return 1;
}
static void call_with_handler(char*handler, const char*name TSRMLS_DC) {
  if(allocate_php4_exception(TSRMLS_C)) {
	int err;

#ifdef ZEND_ENGINE_2
	php_set_error_handling(EH_THROW, zend_exception_get_default() TSRMLS_CC);
#endif
	err = 
	  zend_eval_string((char*)handler, *JG(retval_ptr), (char*)name TSRMLS_CC);

#ifdef ZEND_ENGINE_2
	php_std_error_handling();
#endif
	
	if (err != SUCCESS) { 
	  php_error(E_WARNING, "php_mod_"/**/EXT_NAME()/**/"(%d): Could not call user function: %s.", 22, Z_STRVAL_P(JG(func)));
	}
  }
}
static void call_with_params(int count, zval ***func_params TSRMLS_DC) {
  if(allocate_php4_exception(TSRMLS_C)) {/* checked and destroyed in client. handle_exception() */
	int err;
#ifdef ZEND_ENGINE_2
	php_set_error_handling(EH_THROW, zend_exception_get_default() TSRMLS_CC);
#endif
	err = call_user_function_ex(0, JG(object), JG(func), JG(retval_ptr), count, func_params, 0, NULL TSRMLS_CC);
#ifdef ZEND_ENGINE_2
	php_std_error_handling();
#endif
	
	if (err != SUCCESS) {
	  php_error(E_WARNING, "php_mod_"/**/EXT_NAME()/**/"(%d): Could not call user function: %s.", 23, Z_STRVAL_P(JG(func)));
	}
  }
}

/**
 * Only for internal use.
 */
EXT_FUNCTION(EXT_GLOBAL(call_with_exception_handler))
{
  zval ***func_params;
  HashTable *func_params_ht;
  int count, current;
  if (ZEND_NUM_ARGS()==1) {
	*return_value=*JG(func_params);
	zval_copy_ctor(return_value);
	return;
  }
  /* for functions in the global environment */
  if(!*JG(object)) {
	static const char name[] = "call_global_func_with_exception_handler";
	static const char call_user_funcH[] = "call_user_func_array('";
	static const char call_user_funcT[] = "',"/**/EXT_NAME()/**/"_call_with_exception_handler(true));";
	char *handler=emalloc(sizeof(call_user_funcH)-1+Z_STRLEN_P(JG(func))+sizeof(call_user_funcT));
	assert(handler); if(!handler) exit(9);
	strcpy(handler, call_user_funcH); 
	strcat(handler, Z_STRVAL_P(JG(func))); 
	strcat(handler, call_user_funcT);

	MAKE_STD_ZVAL(*JG(retval_ptr)); ZVAL_NULL(*JG(retval_ptr)); 
	call_with_handler(handler, name TSRMLS_CC);
	check_php4_exception(TSRMLS_C);
	efree(handler);
	RETURN_NULL();
  }
  /* for methods */
  current=0;
  func_params_ht = Z_ARRVAL_P(JG(func_params));
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
  zval **pobj;
  long obj;
  proxyenv *jenv = EXT_GLOBAL(connect_to_server)(TSRMLS_C);
  if(!jenv) {RETURN_NULL();}

  if (ZEND_NUM_ARGS()!=1 || zend_get_parameters_ex(1, &pobj) == FAILURE)
	WRONG_PARAM_COUNT;

  convert_to_object_ex(pobj);
  obj = 0;
  EXT_GLOBAL(get_jobject_from_object)(*pobj, &obj TSRMLS_CC);
  if(!obj) {
	zend_error(E_ERROR, "Argument for %s() must be a "/**/EXT_NAME()/**/" object", get_active_function_name(TSRMLS_C));
	return;
  }
  (*jenv)->writeInvokeBegin(jenv, 0, "inspect", 0, 'I', return_value);
  (*jenv)->writeObject(jenv, obj);
  (*jenv)->writeInvokeEnd(jenv);
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

static const char on[]="On";
static const char on2[]="1";
static const char off[]="Off";
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
  if (new_value) {
	if((EXT_GLOBAL (ini_set) &U_HOSTS)) free(EXT_GLOBAL(cfg)->hosts);
	EXT_GLOBAL(cfg)->hosts=strdup(new_value);
	assert(EXT_GLOBAL(cfg)->hosts); if(!EXT_GLOBAL(cfg)->hosts) exit(6);
	EXT_GLOBAL(ini_updated)|=U_HOSTS;
  }
  return SUCCESS;
}
static PHP_INI_MH(OnIniExtJavaCompatibility)
{
	if (new_value) {
	  if(!strncasecmp(on, new_value, 2) || !strncasecmp(on2, new_value, 1))
		EXT_GLOBAL(cfg)->extJavaCompatibility=1;
	  else
		EXT_GLOBAL(cfg)->extJavaCompatibility=0;
	  EXT_GLOBAL(ini_updated)|=U_EXT_JAVA_COMPATIBILITY;
	}
	return SUCCESS;
}
static PHP_INI_MH(OnIniServlet)
{
  if (new_value) {
	if((EXT_GLOBAL (ini_set) &U_SERVLET)) free(EXT_GLOBAL(cfg)->servlet);
	if(!strncasecmp(on, new_value, 2) || !strncasecmp(on2, new_value, 1)) {
	  EXT_GLOBAL(cfg)->servlet=strdup(DEFAULT_SERVLET);
	  EXT_GLOBAL(cfg)->servlet_is_default=1;
	}
	else {
	  EXT_GLOBAL(cfg)->servlet=strdup(new_value);
	  EXT_GLOBAL(cfg)->servlet_is_default=0;
	}
	assert(EXT_GLOBAL(cfg)->servlet); if(!EXT_GLOBAL(cfg)->servlet) exit(6);
	EXT_GLOBAL(ini_updated)|=U_SERVLET;
  }
  return SUCCESS;
}

static PHP_INI_MH(OnIniSockname)
{
  if (new_value) {
	if((EXT_GLOBAL (ini_set) &U_SOCKNAME)) free(EXT_GLOBAL(cfg)->sockname);
	EXT_GLOBAL(cfg)->sockname=strdup(new_value);
	EXT_GLOBAL(cfg)->socketname_set=1;
	assert(EXT_GLOBAL(cfg)->sockname); if(!EXT_GLOBAL(cfg)->sockname) exit(6);
	EXT_GLOBAL(ini_updated)|=U_SOCKNAME;
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
  PHP_INI_ENTRY(EXT_NAME()/**/".classpath", NULL, PHP_INI_SYSTEM, OnIniClassPath)
  PHP_INI_ENTRY(EXT_NAME()/**/".libpath",   NULL, PHP_INI_SYSTEM, OnIniLibPath)
  PHP_INI_ENTRY(EXT_NAME()/**/"."/**/EXT_NAME()/**/"",   NULL, PHP_INI_SYSTEM, OnIniJava)
  PHP_INI_ENTRY(EXT_NAME()/**/"."/**/EXT_NAME()/**/"_home",   NULL, PHP_INI_SYSTEM, OnIniJavaHome)

  PHP_INI_ENTRY(EXT_NAME()/**/".log_level",   NULL, PHP_INI_ALL, OnIniLogLevel)
  PHP_INI_ENTRY(EXT_NAME()/**/".log_file",   NULL, PHP_INI_SYSTEM, OnIniLogFile)
  PHP_INI_ENTRY(EXT_NAME()/**/".ext_java_compatibility",   NULL, PHP_INI_SYSTEM, OnIniExtJavaCompatibility)
  PHP_INI_END()

/* vm_alloc_globals_ctor(zend_vm_globals *vm_globals) */
  static void EXT_GLOBAL(alloc_globals_ctor)(EXT_GLOBAL_EX(zend_,globals,_) *EXT_GLOBAL(globals) TSRMLS_DC)
{
  EXT_GLOBAL(globals)->jenv=0;
  EXT_GLOBAL(globals)->is_closed=-1;

  EXT_GLOBAL(globals)->ini_user=0;

  EXT_GLOBAL(globals)->hosts=0;
  EXT_GLOBAL(globals)->servlet=0;
}

#ifdef ZEND_ENGINE_2

/**
 * Proto: object Java::Java (string classname [, string argument1, .\ .\ .\ ]) or object Java::java_exception (string classname [, string argument1, .\ .\ .\ ]) or object Java::JavaException (string classname [, string argument1, .\ .\ .\ ]);
 *
 * Java constructor. Example:
 * \code
 * $object = new Java("java.lang.String", "hello world"); 
 * echo (string)$object;
 * \endcode
 * \code
 * $ex = new JavaException("java.lang.NullPointerException");
 * throw $ex;
 *
 * \endcode
 */
EXT_FUNCTION(EXT_GLOBAL(construct))
{
  zval **argv;
  int argc = ZEND_NUM_ARGS();

  argv = (zval **) safe_emalloc(sizeof(zval *), argc, 0);
  if (zend_get_parameters_array(ht, argc, argv) == FAILURE) {
	php_error(E_ERROR, "Couldn't fetch arguments into array.");
	RETURN_NULL();
  }

  if(argc<1 || Z_TYPE_P(argv[0])!=IS_STRING) WRONG_PARAM_COUNT;

  EXT_GLOBAL(call_function_handler)(INTERNAL_FUNCTION_PARAM_PASSTHRU,
									EXT_NAME(), CONSTRUCTOR, 1,
									getThis(),
									argc, argv);
  efree(argv);
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
  zval **argv;
  int argc = ZEND_NUM_ARGS();

  argv = (zval **) safe_emalloc(sizeof(zval *), argc, 0);
  if (zend_get_parameters_array(ht, argc, argv) == FAILURE) {
	php_error(E_ERROR, "Couldn't fetch arguments into array.");
	RETURN_NULL();
  }

  if(argc<1 || Z_TYPE_P(argv[0])!=IS_STRING) WRONG_PARAM_COUNT;

  EXT_GLOBAL(call_function_handler)(INTERNAL_FUNCTION_PARAM_PASSTHRU,
									EXT_NAME(), CONSTRUCTOR, 0, 
									getThis(),
									argc, argv);
  efree(argv);
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
  zval **xargv, **argv;
  int i = 0, xargc, argc = ZEND_NUM_ARGS();
  HashPosition pos;
  zval **param;


  argv = (zval **) safe_emalloc(sizeof(zval *), argc, 0);
  if (zend_get_parameters_array(ht, argc, argv) == FAILURE) {
	php_error(E_ERROR, "Couldn't fetch arguments into array.");
	RETURN_NULL();
  }

  /* function arguments in arg#2 */
  xargc = zend_hash_num_elements(Z_ARRVAL_P(argv[1]));
  xargv = safe_emalloc(sizeof(zval *), xargc, 0);
  for (zend_hash_internal_pointer_reset_ex(Z_ARRVAL_P(argv[1]), &pos);
	   zend_hash_get_current_data_ex(Z_ARRVAL_P(argv[1]), (void **) &param, &pos) == SUCCESS;
	   zend_hash_move_forward_ex(Z_ARRVAL_P(argv[1]), &pos)) {
	/*zval_add_ref(param);*/
	xargv[i++] = *param;
  }

  EXT_GLOBAL(call_function_handler)(INTERNAL_FUNCTION_PARAM_PASSTHRU,
									Z_STRVAL(*argv[0]), CONSTRUCTOR_NONE, 0,
									getThis(),
									xargc, xargv);
								   
  efree(argv);
  efree(xargv);
}

/** Proto: object Java::__toString (void)
 *
 * Displays the java object as a string. Note: it doesn't cast the
 * object to a string, thus echo $ob displays a string
 * representation of $ob, e.g.: \code [o(String)"hello"]\endcode
 *
 * Use a string cast if you want to display the java string as a php
 * string, e.g.:
 * \code 
 * echo (string)$string; // explicit cast
 * echo "$string"; // implicit cast
 * \endcode
 */
EXT_METHOD(EXT, __tostring)
{
  long result = 0;
  
  if(Z_TYPE_P(getThis()) == IS_OBJECT) {
	EXT_GLOBAL(get_jobject_from_object)(getThis(), &result TSRMLS_CC);
  }
  if(result) {
	proxyenv *jenv = EXT_GLOBAL(connect_to_server)(TSRMLS_C);
	if(!jenv) {RETURN_NULL();}
	if((*jenv)->handle==(*jenv)->async_ctx.handle_request){/* async protocol */
	  php_error(E_ERROR, "php_mod_"/**/EXT_NAME()/**/"(%d): __tostring() invalid while in stream mode", 21);
	  RETURN_NULL();
	}

	(*jenv)->writeInvokeBegin(jenv, 0, "ObjectToString", 0, 'I', return_value);
	(*jenv)->writeObject(jenv, result);
	(*jenv)->writeInvokeEnd(jenv);

  } else {
	EXT_GLOBAL(call_function_handler)(INTERNAL_FUNCTION_PARAM_PASSTHRU,
									  "tostring", CONSTRUCTOR_NONE, 0, getThis(), 0, NULL);
  }

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
  zval **argv;
  int argc = ZEND_NUM_ARGS();
  
  argv = (zval **) safe_emalloc(sizeof(zval *), argc, 0);
  if (zend_get_parameters_array(ht, argc, argv) == FAILURE) {
	php_error(E_ERROR, "Couldn't fetch arguments into array.");
	RETURN_NULL();
  }
  
  EXT_GLOBAL(set_property_handler)(Z_STRVAL(*argv[0]), getThis(), argv[1], return_value);
  
  efree(argv);
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
  zval **argv;
  int argc = ZEND_NUM_ARGS();
  argv = (zval **) safe_emalloc(sizeof(zval *), argc, 0);
  if (zend_get_parameters_array(ht, argc, argv) == FAILURE) {
	php_error(E_ERROR, "Couldn't fetch arguments into array.");
	RETURN_NULL();
  }
  
  EXT_GLOBAL(get_property_handler)(Z_STRVAL(*argv[0]), getThis(), return_value);
  efree(argv);
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
  serialize(INTERNAL_FUNCTION_PARAM_PASSTHRU);
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
  deserialize(INTERNAL_FUNCTION_PARAM_PASSTHRU);
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
  proxyenv *jenv = EXT_GLOBAL(connect_to_server)(TSRMLS_C);
  zval **argv;
  int argc;

  if(!jenv) {RETURN_NULL();}

  argc = ZEND_NUM_ARGS();
  argv = (zval **) safe_emalloc(sizeof(zval *), argc+1, 0);
  if (zend_get_parameters_array(ht, argc, argv+1) == FAILURE) {
	php_error(E_ERROR, "Couldn't fetch arguments into array.");
	RETURN_NULL();
  }
  argv[0]=getThis();
  EXT_GLOBAL(invoke)("offsetExists", 0, argc+1, argv, 0, return_value TSRMLS_CC);
  efree(argv);
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
  zval **argv;
  int argc;
  proxyenv *jenv = EXT_GLOBAL(connect_to_server)(TSRMLS_C);
  if(!jenv) {RETURN_NULL();}

  argc = ZEND_NUM_ARGS();
  argv = (zval **) safe_emalloc(sizeof(zval *), argc+1, 0);
  if (zend_get_parameters_array(ht, argc, argv+1) == FAILURE) {
	php_error(E_ERROR, "Couldn't fetch arguments into array.");
	RETURN_NULL();
  }

  argv[0]=getThis();
  EXT_GLOBAL(invoke)("offsetGet", 0, argc+1, argv, 0, return_value TSRMLS_CC);
  efree(argv);
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
  zval **argv;
  int argc;
  proxyenv *jenv = EXT_GLOBAL(connect_to_server)(TSRMLS_C);
  if(!jenv) {RETURN_NULL();}

  argc = ZEND_NUM_ARGS();
  argv = (zval **) safe_emalloc(sizeof(zval *), argc+1, 0);
  if (zend_get_parameters_array(ht, argc, argv+1) == FAILURE) {
	php_error(E_ERROR, "Couldn't fetch arguments into array.");
	RETURN_NULL();
  }

  argv[0]=getThis();
  EXT_GLOBAL(invoke)("offsetSet", 0, argc+1, argv, 0, return_value TSRMLS_CC);
  efree(argv);
}

/** Proto: string Java::offsetUnset()
 * 
 * Remove the entry at a given position. Used internally.
 */
EXT_METHOD(EXT_ARRAY, offsetUnset)
{
  zval **argv;
  int argc;
  long obj;
  proxyenv *jenv = EXT_GLOBAL(connect_to_server)(TSRMLS_C);
  if(!jenv) {RETURN_NULL();}

  argc = ZEND_NUM_ARGS();
  argv = (zval **) safe_emalloc(sizeof(zval *), argc+1, 0);
  if (zend_get_parameters_array(ht, argc, argv+1) == FAILURE) {
	php_error(E_ERROR, "Couldn't fetch arguments into array.");
	RETURN_NULL();
  }

  argv[0]=getThis();
  EXT_GLOBAL(invoke)("offsetUnset", 0, argc+1, argv, 0, return_value TSRMLS_CC);
  efree(argv);
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



static int cast(zval *readobj, zval *writeobj, int type, int should_free TSRMLS_DC)
{
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
    ZVAL_NULL(writeobj);
	switch(type) {

	case IS_STRING:
	  (*jenv)->writeInvokeBegin(jenv, 0, "castToString", 0, 'I', writeobj);
	  (*jenv)->writeObject(jenv, obj);
	  (*jenv)->writeInvokeEnd(jenv);
	  break;
	case IS_BOOL:
	  (*jenv)->writeInvokeBegin(jenv, 0, "castToBoolean", 0, 'I', writeobj);
	  (*jenv)->writeObject(jenv, obj);
	  (*jenv)->writeInvokeEnd(jenv);
	  break;
	case IS_LONG:
	  (*jenv)->writeInvokeBegin(jenv, 0, "castToExact", 0, 'I', writeobj);
	  (*jenv)->writeObject(jenv, obj);
	  (*jenv)->writeInvokeEnd(jenv);
	  break;
	case IS_DOUBLE:
	  (*jenv)->writeInvokeBegin(jenv, 0, "castToInexact", 0, 'I', writeobj);
	  (*jenv)->writeObject(jenv, obj);
	  (*jenv)->writeInvokeEnd(jenv);
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
		  (*jenv)->writeInvokeEnd(jenv);
		} else {
		  obj = 0; //failed
		}
	  }
	  break;
	case IS_ARRAY: 
#ifdef ZEND_ENGINE_2
	  (*jenv)->writeInvokeBegin(jenv, 0, "castToArray", 0, 'I', writeobj);
	  (*jenv)->writeObject(jenv, obj);
	  (*jenv)->writeInvokeEnd(jenv);
#else
	  obj = 0; // failed
#endif
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
  
  EXT_GLOBAL(invoke)("currentKey", iterator->vm_iterator, 0, 0, 0, presult TSRMLS_CC);

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

  EXT_GLOBAL(invoke)("currentData", iterator->vm_iterator, 0, 0, 0, iterator->current_object TSRMLS_CC);
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
  (*jenv)->writeInvokeEnd(jenv);
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

static zend_object_iterator *get_iterator(zend_class_entry *ce, zval *object TSRMLS_DC)
{
  zval *presult;
  proxyenv *jenv = JG(jenv);
  vm_iterator *iterator = emalloc(sizeof *iterator);
  long vm_iterator, obj;
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
  (*jenv)->writeInvokeEnd(jenv);

  iterator->type = Z_BVAL_P(presult) ? HASH_KEY_IS_STRING : HASH_KEY_IS_LONG;

  (*jenv)->writeInvokeBegin(jenv, vm_iterator, "hasMore", 0, 'I', presult);
  (*jenv)->writeInvokeEnd(jenv);
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
  pval **arguments = (pval **) emalloc(sizeof(pval *)*arg_count);
  enum constructor constructor = CONSTRUCTOR_NONE;
  zend_class_entry *ce = Z_OBJCE_P(getThis());
								/* Do not create an instance for new
								   java_class or new JavaClass */
  short createInstance = 1;
  zend_class_entry *parent;

  for(parent=ce; parent->parent; parent=parent->parent)
	if ((parent==EXT_GLOBAL(class_class_entry)) || ((parent==EXT_GLOBAL(class_class_entry_jsr)))) {
	  createInstance = 0;		/* do not create an instance for new java_class or new JavaClass */
	  break;
	}

  getParametersArray(ht, arg_count, arguments);

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

static void make_local_socket_info(TSRMLS_D) {
  memset(&EXT_GLOBAL(cfg)->saddr, 0, sizeof EXT_GLOBAL(cfg)->saddr);
#ifndef CFG_JAVA_SOCKET_INET
  EXT_GLOBAL(cfg)->saddr.sun_family = AF_LOCAL;
  memset(EXT_GLOBAL(cfg)->saddr.sun_path, 0, sizeof EXT_GLOBAL(cfg)->saddr.sun_path);
  strcpy(EXT_GLOBAL(cfg)->saddr.sun_path, EXT_GLOBAL(get_sockname)(TSRMLS_C));
# ifdef HAVE_ABSTRACT_NAMESPACE
  *EXT_GLOBAL(cfg)->saddr.sun_path=0;
# endif
#else
  EXT_GLOBAL(cfg)->saddr.sin_family = AF_INET;
  EXT_GLOBAL(cfg)->saddr.sin_port=htons(atoi(EXT_GLOBAL(get_sockname)(TSRMLS_C)));
  EXT_GLOBAL(cfg)->saddr.sin_addr.s_addr = inet_addr( "127.0.0.1" );
#endif
}

/**
 * Called when the module is initialized. Creates the Java and
 * JavaClass structures and tries to start the backend if
 * java.socketname, java.servlet or java.hosts are not set.  The
 * backend is not started if the environment variable
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
							  get_property_handler,
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
  
  make_lambda(&call, EXT_FN(EXT_GLOBAL(__call)));
  make_lambda(&get, EXT_FN(EXT_GLOBAL(__get)));
  make_lambda(&set, EXT_FN(EXT_GLOBAL(__set)));
  
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
  if(!EXT_GLOBAL (cfg) ) EXT_GLOBAL (cfg) = malloc(sizeof *EXT_GLOBAL (cfg) ); if(!EXT_GLOBAL (cfg) ) exit(9);

  if(REGISTER_INI_ENTRIES()==SUCCESS) {
	/* set the default values for all undefined */
	
	EXT_GLOBAL(init_cfg) (TSRMLS_C);

	make_local_socket_info(TSRMLS_C);
	clone_cfg(TSRMLS_C);
	EXT_GLOBAL(start_server) (TSRMLS_C);
	destroy_cloned_cfg(TSRMLS_C);
  } 
  return SUCCESS;
}
/**
 * Displays the module info.
 */
PHP_MINFO_FUNCTION(EXT)
{
  short is_local;
  char*s=EXT_GLOBAL(get_server_string) (TSRMLS_C);
  char*server = EXT_GLOBAL(test_server) (0, &is_local, 0 TSRMLS_CC);
  short is_level = ((EXT_GLOBAL (ini_user)&U_LOGLEVEL)!=0);

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
	  php_info_print_table_row(2, EXT_NAME()/**/".log_file", "<stdout>");
	else
	  php_info_print_table_row(2, EXT_NAME()/**/".log_file", EXT_GLOBAL(cfg)->logFile);
  }
  php_info_print_table_row(2, EXT_NAME()/**/".log_level", is_level ? EXT_GLOBAL(cfg)->logLevel : "no value (use backend's default level)");
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
  php_info_print_table_row(2, EXT_NAME()/**/" command", s);
  php_info_print_table_row(2, EXT_NAME()/**/" status", server?"running":"not running");
  php_info_print_table_row(2, EXT_NAME()/**/" server", server?server:"localhost");
  php_info_print_table_end();
  
  free(server);
  free(s);
}

/**
 * Called when the module terminates. Stops the backend, if it is running.
 * When running in Apache/IIS or as a FastCGI binary, this procedure is 
 * called only once. When running as a CGI binary this is called whenever
 * the CGI binary terminates.
 */
PHP_MSHUTDOWN_FUNCTION(EXT) 
{
  EXT_GLOBAL(destroy_cfg) (EXT_GLOBAL(ini_set));
  EXT_GLOBAL(ini_user) = EXT_GLOBAL(ini_set) = 0;

  UNREGISTER_INI_ENTRIES();
  EXT_GLOBAL(shutdown_library) ();

  assert(EXT_GLOBAL (cfg));
  if(EXT_GLOBAL (cfg) ) { free(EXT_GLOBAL (cfg) ); EXT_GLOBAL (cfg) = 0; }

  return SUCCESS;
}

#ifndef PHP_WRAPPER_H
#error must include php_wrapper.h
#endif
