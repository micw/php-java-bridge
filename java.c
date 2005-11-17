/*-*- mode: C; tab-width:4 -*-*/

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
#include "zend_builtin_functions.h"
#endif

EXT_DECLARE_MODULE_GLOBALS(EXT)
	 struct cfg *EXT_GLOBAL (cfg)  = 0;

#ifdef __MINGW32__
static const int java_errno=0;
int *__errno (void) { return &java_errno; }
#define php_info_print_table_row(a, b, c)		\
  php_info_print_table_row_ex(a, "java", b, c)
#endif

static void clone_cfg(TSRMLS_D) {
  if(!JG(ini_user)) {
	JG(ini_user)=EXT_GLOBAL(ini_user);
	JG(hosts)=estrdup(EXT_GLOBAL(cfg)->hosts);
	JG(servlet)=estrdup(EXT_GLOBAL(cfg)->servlet);
  }
}
static void destroy_cloned_cfg(TSRMLS_D) {
  if(JG(hosts)) efree(JG(hosts));
  if(JG(servlet)) efree(JG(servlet));
  JG(ini_user)=0;
  JG(hosts)=0;
  JG(servlet)=0;
}

PHP_RINIT_FUNCTION(EXT) 
{
  if(EXT_GLOBAL(cfg)) clone_cfg(TSRMLS_C);

  if(JG(jenv)) {
	php_error(E_ERROR, "php_mod_"/**/EXT_NAME()/**/"(%d): Synchronization problem, rinit with active connection called. Cannot continue, aborting now. Please report this to: php-java-bridge-users@lists.sourceforge.net",59);
  }
  JG(is_closed)=0;
  return SUCCESS;
}

PHP_RSHUTDOWN_FUNCTION(EXT)
{
  destroy_cloned_cfg(TSRMLS_C);

  if(JG(jenv)) {
	if(*JG(jenv)) {
	  if((*JG(jenv))->peer!=-1) {
		/* end servlet session */
		EXT_GLOBAL(protocol_end)(JG(jenv));
		close((*JG(jenv))->peer);
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
  JG(is_closed)=1;
  return SUCCESS;
}

static void last_exception_get(proxyenv *jenv, zval**return_value)
{
  (*jenv)->writeInvokeBegin(jenv, 0, "lastException", 0, 'P', *return_value);
  (*jenv)->writeInvokeEnd(jenv);
}
EXT_FUNCTION(EXT_GLOBAL(last_exception_get))
{
  proxyenv *jenv;
  if (ZEND_NUM_ARGS()!=0) WRONG_PARAM_COUNT;
  jenv = EXT_GLOBAL(connect_to_server)(TSRMLS_C);
  if(!jenv) {RETURN_NULL();}

  last_exception_get(jenv, &return_value);
}

static void last_exception_clear(proxyenv*jenv, zval**return_value) {
  (*jenv)->writeInvokeBegin(jenv, 0, "lastException", 0, 'P', *return_value);
  (*jenv)->writeObject(jenv, 0);
  (*jenv)->writeInvokeEnd(jenv);
}
EXT_FUNCTION(EXT_GLOBAL(last_exception_clear))
{
  proxyenv *jenv;
  if (ZEND_NUM_ARGS()!=0) WRONG_PARAM_COUNT;
  jenv = EXT_GLOBAL(connect_to_server)(TSRMLS_C);
  if(!jenv) {RETURN_NULL();}

  last_exception_clear(jenv, &return_value);
}

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
EXT_FUNCTION(EXT_GLOBAL(set_library_path))
{
  require(INTERNAL_FUNCTION_PARAM_PASSTHRU);
}
EXT_FUNCTION(EXT_GLOBAL(require))
{
  require(INTERNAL_FUNCTION_PARAM_PASSTHRU);
}

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

static void session(INTERNAL_FUNCTION_PARAMETERS)
{
  proxyenv *jenv;
  zval **session=0, **is_new=0;
  int argc=ZEND_NUM_ARGS();
  
  if (argc>2 || zend_get_parameters_ex(argc, &session, &is_new) == FAILURE)
	WRONG_PARAM_COUNT;

  jenv=EXT_GLOBAL(connect_to_server)(TSRMLS_C);
  if(!jenv) RETURN_NULL();
 
  assert(EXT_GLOBAL(cfg)->is_cgi_servlet && (*jenv)->servlet_ctx ||!EXT_GLOBAL(cfg)->is_cgi_servlet);
  EXT_GLOBAL(check_context) (jenv TSRMLS_CC); /* re-direct if no
												 context was found */

  (*jenv)->writeInvokeBegin(jenv, 0, "getSession", 0, 'I', return_value);
  if(argc>0 && Z_TYPE_PP(session)!=IS_NULL) {
	convert_to_string_ex(session);
	(*jenv)->writeString(jenv, Z_STRVAL_PP(session), Z_STRLEN_PP(session)); 
  } else {
	(*jenv)->writeObject(jenv, 0);
  }
  (*jenv)->writeBoolean(jenv, (argc<2||Z_TYPE_PP(is_new)==IS_NULL)?0:Z_BVAL_PP(is_new)); 

  (*jenv)->writeLong(jenv, 1440); // FIXME: use session.gc_maxlifetime

  (*jenv)->writeInvokeEnd(jenv);
}

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
  EXT_GLOBAL(check_context) (jenv TSRMLS_CC); /* re-direct if no
												 context was found */

  (*jenv)->writeInvokeBegin(jenv, 0, "getContext", 0, 'I', return_value);
  (*jenv)->writeInvokeEnd(jenv);
}

EXT_FUNCTION(EXT_GLOBAL(get_context))
{
  context(INTERNAL_FUNCTION_PARAM_PASSTHRU);
}

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


static void values(INTERNAL_FUNCTION_PARAMETERS)
{
  proxyenv *jenv;
  zval **pobj;
  long obj;

  jenv = EXT_GLOBAL(connect_to_server)(TSRMLS_C);
  if(!jenv) RETURN_NULL();

  if (ZEND_NUM_ARGS()!=1 || zend_get_parameters_ex(1, &pobj) == FAILURE)
	WRONG_PARAM_COUNT;

#ifdef ZEND_ENGINE_2
  convert_to_object_ex(pobj);
  obj = 0;
  EXT_GLOBAL(get_jobject_from_object)(*pobj, &obj TSRMLS_CC);
  if(!obj) {
	zend_error(E_ERROR, "Argument for %s() must be a "/**/EXT_NAME()/**/" object", get_active_function_name(TSRMLS_C));
	RETURN_NULL();
  }

  (*jenv)->writeInvokeBegin(jenv, 0, "getValues", 0, 'I', return_value);
  (*jenv)->writeObject(jenv, obj);
  (*jenv)->writeInvokeEnd(jenv);
#else
  *return_value = **pobj;
  zval_copy_ctor(return_value);
#endif
}

static const char identity[] = "serialID";
static void serialize(INTERNAL_FUNCTION_PARAMETERS)
{
  long obj;
  zval *handle, *id;

  proxyenv *jenv = EXT_GLOBAL(connect_to_server)(TSRMLS_C);
  if(!jenv) {
	php_error(E_WARNING, EXT_NAME()/**/" cannot be serialized.");
	RETURN_NULL();
  }
  EXT_GLOBAL(get_jobject_from_object)(getThis(), &obj TSRMLS_CC);
  assert(obj);
  if(!obj) {
	php_error(E_WARNING, EXT_NAME()/**/" cannot be serialized.");
	RETURN_NULL();
  }

  MAKE_STD_ZVAL(handle);
  ZVAL_NULL(handle);
  (*jenv)->writeInvokeBegin(jenv, 0, "serialize", 0, 'I', handle);
  (*jenv)->writeObject(jenv, obj);
  (*jenv)->writeLong(jenv, 1440); // FIXME: use session.gc_maxlifetime
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
	php_error(E_ERROR, EXT_NAME()/**/" cannot be de-serialized.");
  }

  err = zend_hash_find(Z_OBJPROP_P(getThis()), (char*)identity, sizeof identity, (void**)&id);
  assert(SUCCESS==err);
  if(FAILURE==err) {
	php_error(E_WARNING, EXT_NAME()/**/" cannot be deserialized.");
	RETURN_NULL();
  }
  
  MAKE_STD_ZVAL(handle);
  ZVAL_NULL(handle);
  (*jenv)->writeInvokeBegin(jenv, 0, "deserialize", 0, 'I', handle);
  (*jenv)->writeString(jenv, Z_STRVAL_PP(id), Z_STRLEN_PP(id));
  (*jenv)->writeLong(jenv, 1440); // FIXME: use session.gc_maxlifetime
  (*jenv)->writeInvokeEnd(jenv);
  if(Z_TYPE_P(handle)!=IS_LONG) {
#ifndef ZEND_ENGINE_2
	php_error(E_WARNING, EXT_NAME()/**/" cannot be deserialized, session expired.");
#endif
	ZVAL_NULL(getThis());
  }	else {
	zend_hash_index_update(Z_OBJPROP_P(getThis()), 0, &handle, sizeof(pval *), NULL);
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

EXT_FUNCTION(EXT_GLOBAL(values))
{
  values(INTERNAL_FUNCTION_PARAM_PASSTHRU);
}

EXT_FUNCTION(EXT_GLOBAL(get_values))
{
  values(INTERNAL_FUNCTION_PARAM_PASSTHRU);
}

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
 * Exception handler for php5
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
 * Exception handler for php4
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
  if(allocate_php4_exception(TSRMLS_C))
	if(zend_eval_string((char*)handler, *JG(retval_ptr), (char*)name TSRMLS_CC)!=SUCCESS) { 
	  php_error(E_WARNING, "php_mod_"/**/EXT_NAME()/**/"(%d): Could not call user function: %s.", 22, Z_STRVAL_P(JG(func)));
	}
}
static void call_with_params(int count, zval ***func_params TSRMLS_DC) {
  if(allocate_php4_exception(TSRMLS_C))	/* checked and destroyed in client. handle_exception() */
	if (call_user_function_ex(0, JG(object), JG(func), JG(retval_ptr), count, func_params, 0, NULL TSRMLS_CC) != SUCCESS) {
	  php_error(E_WARNING, "php_mod_"/**/EXT_NAME()/**/"(%d): Could not call user function: %s.", 23, Z_STRVAL_P(JG(func)));
	}
}
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

	 int EXT_GLOBAL(ini_updated), EXT_GLOBAL(ini_user), EXT_GLOBAL(ini_set);
zend_class_entry *EXT_GLOBAL(class_entry);
zend_class_entry *EXT_GLOBAL(class_class_entry);
zend_class_entry *EXT_GLOBAL(class_class_entry_jsr);
zend_class_entry *EXT_GLOBAL(exception_class_entry);

#ifdef ZEND_ENGINE_2
zend_object_handlers EXT_GLOBAL(handlers);
#endif

static const char on[]="On";
static const char on2[]="1";
static const char off[]="Off";
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
static PHP_INI_MH(OnIniServlet)
{
  if (new_value) {
	if((EXT_GLOBAL (ini_set) &U_SERVLET)) free(EXT_GLOBAL(cfg)->servlet);
	if(!strncasecmp(on, new_value, 2) || !strncasecmp(on2, new_value, 1))
	  EXT_GLOBAL(cfg)->servlet=strdup(DEFAULT_SERVLET);
	else
	  EXT_GLOBAL(cfg)->servlet=strdup(new_value);
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
  PHP_INI_ENTRY(EXT_NAME()/**/".classpath", NULL, PHP_INI_SYSTEM, OnIniClassPath)
  PHP_INI_ENTRY(EXT_NAME()/**/".libpath",   NULL, PHP_INI_SYSTEM, OnIniLibPath)
  PHP_INI_ENTRY(EXT_NAME()/**/"."/**/EXT_NAME()/**/"",   NULL, PHP_INI_SYSTEM, OnIniJava)
  PHP_INI_ENTRY(EXT_NAME()/**/"."/**/EXT_NAME()/**/"_home",   NULL, PHP_INI_SYSTEM, OnIniJavaHome)

  PHP_INI_ENTRY(EXT_NAME()/**/".log_level",   NULL, PHP_INI_ALL, OnIniLogLevel)
  PHP_INI_ENTRY(EXT_NAME()/**/".log_file",   NULL, PHP_INI_SYSTEM, OnIniLogFile)
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

EXT_METHOD(EXT, EXT)
{
  zval **argv;
  int argc = ZEND_NUM_ARGS();

  argv = (zval **) safe_emalloc(sizeof(zval *), argc, 0);
  if (zend_get_parameters_array(ht, argc, argv) == FAILURE) {
	php_error(E_ERROR, "Couldn't fetch arguments into array.");
	RETURN_NULL();
  }

  EXT_GLOBAL(call_function_handler)(INTERNAL_FUNCTION_PARAM_PASSTHRU,
									EXT_NAME(), CONSTRUCTOR, 1,
									getThis(),
									argc, argv);
  efree(argv);
}

EXT_METHOD(EXT, EXT_GLOBAL(class))
{
  zval **argv;
  int argc = ZEND_NUM_ARGS();

  argv = (zval **) safe_emalloc(sizeof(zval *), argc, 0);
  if (zend_get_parameters_array(ht, argc, argv) == FAILURE) {
	php_error(E_ERROR, "Couldn't fetch arguments into array.");
	RETURN_NULL();
  }

  EXT_GLOBAL(call_function_handler)(INTERNAL_FUNCTION_PARAM_PASSTHRU,
									EXT_NAME(), CONSTRUCTOR, 0, 
									getThis(),
									argc, argv);
  efree(argv);
}

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
EXT_METHOD(EXT, __tostring)
{
  long result = 0;
  
  if(Z_TYPE_P(getThis()) == IS_OBJECT) {
	EXT_GLOBAL(get_jobject_from_object)(getThis(), &result TSRMLS_CC);
  }
  if(result) {
	proxyenv *jenv = EXT_GLOBAL(connect_to_server)(TSRMLS_C);
	if(!jenv) {RETURN_NULL();}

	(*jenv)->writeInvokeBegin(jenv, 0, "ObjectToString", 0, 'I', return_value);
	(*jenv)->writeObject(jenv, result);
	(*jenv)->writeInvokeEnd(jenv);

  } else {
	EXT_GLOBAL(call_function_handler)(INTERNAL_FUNCTION_PARAM_PASSTHRU,
									  "tostring", CONSTRUCTOR_NONE, 0, getThis(), 0, NULL);
  }

}
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
EXT_METHOD(EXT, __destruct)
{
  long obj;

  EXT_GLOBAL(get_jobject_from_object)(getThis(), &obj TSRMLS_CC);
  if(!obj) RETURN_TRUE;			/* may happen when vm is not initalized */

  if(JG(jenv))
	(*JG(jenv))->writeUnref(JG(jenv), obj);

  RETURN_TRUE;
}
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
EXT_METHOD(EXT, __sleep)
{
  serialize(INTERNAL_FUNCTION_PARAM_PASSTHRU);
}
EXT_METHOD(EXT, __wakeup)
{
  deserialize(INTERNAL_FUNCTION_PARAM_PASSTHRU);
}

EXT_METHOD(EXT, offsetExists)
{
  proxyenv *jenv = EXT_GLOBAL(connect_to_server)(TSRMLS_C);
  zval **argv;
  int argc;
  long obj;

  if(!jenv) {RETURN_NULL();}

  argc = ZEND_NUM_ARGS();
  argv = (zval **) safe_emalloc(sizeof(zval *), argc, 0);
  if (zend_get_parameters_array(ht, argc, argv) == FAILURE) {
	php_error(E_ERROR, "Couldn't fetch arguments into array.");
	RETURN_NULL();
  }
  EXT_GLOBAL(get_jobject_from_object)(getThis(), &obj TSRMLS_CC);
  assert(obj);
  (*jenv)->writeInvokeBegin(jenv, 0, "getPhpMap", 0, 'I', return_value);
  (*jenv)->writeObject(jenv, obj);
  (*jenv)->writeInvokeEnd(jenv);
  EXT_GLOBAL(get_jobject_from_object)(return_value, &obj TSRMLS_CC);
  assert(obj);
  EXT_GLOBAL(invoke)("offsetExists", obj, argc, argv, 0, return_value TSRMLS_CC);
  efree(argv);
}
EXT_METHOD(EXT, offsetGet)
{
  zval **argv;
  int argc;
  long obj;
  proxyenv *jenv = EXT_GLOBAL(connect_to_server)(TSRMLS_C);
  if(!jenv) {RETURN_NULL();}

  argc = ZEND_NUM_ARGS();
  argv = (zval **) safe_emalloc(sizeof(zval *), argc, 0);
  if (zend_get_parameters_array(ht, argc, argv) == FAILURE) {
	php_error(E_ERROR, "Couldn't fetch arguments into array.");
	RETURN_NULL();
  }

  EXT_GLOBAL(get_jobject_from_object)(getThis(), &obj TSRMLS_CC);
  assert(obj);
  (*jenv)->writeInvokeBegin(jenv, 0, "getPhpMap", 0, 'I', return_value);
  (*jenv)->writeObject(jenv, obj);
  (*jenv)->writeInvokeEnd(jenv);
  EXT_GLOBAL(get_jobject_from_object)(return_value, &obj TSRMLS_CC);
  assert(obj);
  EXT_GLOBAL(invoke)("offsetGet", obj, argc, argv, 0, return_value TSRMLS_CC);
  efree(argv);
}

EXT_METHOD(EXT, offsetSet)
{
  zval **argv;
  int argc;
  long obj;
  proxyenv *jenv = EXT_GLOBAL(connect_to_server)(TSRMLS_C);
  if(!jenv) {RETURN_NULL();}

  argc = ZEND_NUM_ARGS();
  argv = (zval **) safe_emalloc(sizeof(zval *), argc, 0);
  if (zend_get_parameters_array(ht, argc, argv) == FAILURE) {
	php_error(E_ERROR, "Couldn't fetch arguments into array.");
	RETURN_NULL();
  }
  EXT_GLOBAL(get_jobject_from_object)(getThis(), &obj TSRMLS_CC);
  assert(obj);
  (*jenv)->writeInvokeBegin(jenv, 0, "getPhpMap", 0, 'I', return_value);
  (*jenv)->writeObject(jenv, obj);
  (*jenv)->writeInvokeEnd(jenv);
  EXT_GLOBAL(get_jobject_from_object)(return_value, &obj TSRMLS_CC);
  assert(obj);
  EXT_GLOBAL(invoke)("offsetSet", obj, argc, argv, 0, return_value TSRMLS_CC);
  efree(argv);
}

EXT_METHOD(EXT, offsetUnset)
{
  zval **argv;
  int argc;
  long obj;
  proxyenv *jenv = EXT_GLOBAL(connect_to_server)(TSRMLS_C);
  if(!jenv) {RETURN_NULL();}

  argc = ZEND_NUM_ARGS();
  argv = (zval **) safe_emalloc(sizeof(zval *), argc, 0);
  if (zend_get_parameters_array(ht, argc, argv) == FAILURE) {
	php_error(E_ERROR, "Couldn't fetch arguments into array.");
	RETURN_NULL();
  }
  EXT_GLOBAL(get_jobject_from_object)(getThis(), &obj TSRMLS_CC);
  assert(obj);
  (*jenv)->writeInvokeBegin(jenv, 0, "getPhpMap", 0, 'I', return_value);
  (*jenv)->writeObject(jenv, obj);
  (*jenv)->writeInvokeEnd(jenv);
  EXT_GLOBAL(get_jobject_from_object)(return_value, &obj TSRMLS_CC);
  assert(obj);
  EXT_GLOBAL(invoke)("offsetUnset", obj, argc, argv, 0, return_value TSRMLS_CC);
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

function_entry EXT_GLOBAL(class_functions)[] = {
  EXT_ME(EXT, EXT, NULL, 0)
  EXT_MALIAS(EXT, EXT_GLOBAL_N(exception), EXT, NULL, 0)
  EXT_MALIAS(EXT, EXT_GLOBAL(exception), EXT, NULL, 0)
  EXT_ME(EXT, EXT_GLOBAL(class), NULL, 0)
  EXT_MALIAS(EXT, EXT_GLOBAL_N(class), EXT_GLOBAL(class), NULL, 0)
  //EXT_MALIAS(EXT, __construct, EXT, arginfo_set, ZEND_ACC_PUBLIC)
  EXT_ME(EXT, __call, arginfo_set, ZEND_ACC_PUBLIC)
  EXT_ME(EXT, __tostring, arginfo_zero, ZEND_ACC_PUBLIC)
  EXT_ME(EXT, __get, arginfo_get, ZEND_ACC_PUBLIC)
  EXT_ME(EXT, __set, arginfo_set, ZEND_ACC_PUBLIC)
  EXT_ME(EXT, __sleep, arginfo_zero, ZEND_ACC_PUBLIC)
  EXT_ME(EXT, __wakeup, arginfo_zero, ZEND_ACC_PUBLIC)
  EXT_ME(EXT, __destruct, arginfo_zero, ZEND_ACC_PUBLIC)
  EXT_ME(EXT, offsetExists,  arginfo_get, ZEND_ACC_PUBLIC)
  EXT_ME(EXT, offsetGet,     arginfo_get, ZEND_ACC_PUBLIC)
  EXT_ME(EXT, offsetSet,     arginfo_set, ZEND_ACC_PUBLIC)
  EXT_ME(EXT, offsetUnset,   arginfo_get, ZEND_ACC_PUBLIC)
  {NULL, NULL, NULL}
};



static zend_object_value create_object(zend_class_entry *class_type TSRMLS_DC)
{
  /* standard initialization, copied from parent zend_API.c */
  zval *tmp;
  zend_object *object;
  zend_object_value obj = zend_objects_new(&object, class_type TSRMLS_CC);
  ALLOC_HASHTABLE(object->properties);
  zend_hash_init(object->properties, 0, NULL, ZVAL_PTR_DTOR, 0);
  zend_hash_copy(object->properties, &class_type->default_properties, (copy_ctor_func_t) zval_add_ref, (void *) &tmp, sizeof(zval *));

  /* real work */
  obj.handlers = (zend_object_handlers*)&EXT_GLOBAL(handlers);
  return obj;
}

static zend_object_value create_exception_object(zend_class_entry *class_type TSRMLS_DC)
{
  /* standard initialization, copied from parent zend_exceptions.c */
  zval tmp, obj;
  zend_object *object;
  zval *trace;
  obj.value.obj = zend_objects_new(&object, class_type TSRMLS_CC);

  ALLOC_HASHTABLE(object->properties);
  zend_hash_init(object->properties, 0, NULL, ZVAL_PTR_DTOR, 0);
  zend_hash_copy(object->properties, &class_type->default_properties, (copy_ctor_func_t) zval_add_ref, (void *) &tmp, sizeof(zval *));
  
  ALLOC_ZVAL(trace);
  ZVAL_NULL(trace);
  trace->is_ref = 0;
  trace->refcount = 0;
  zend_fetch_debug_backtrace(trace, 0 TSRMLS_CC);
  
  zend_update_property_string(zend_exception_get_default(), &obj, "file", sizeof("file")-1, zend_get_executed_filename(TSRMLS_C) TSRMLS_CC);
  zend_update_property_long(zend_exception_get_default(), &obj, "line", sizeof("line")-1, zend_get_executed_lineno(TSRMLS_C) TSRMLS_CC);
  zend_update_property(zend_exception_get_default(), &obj, "trace", sizeof("trace")-1, trace TSRMLS_CC);
  
  /* real work */
  obj.value.obj.handlers = (zend_object_handlers*)&EXT_GLOBAL(handlers);
  
  return obj.value.obj;
}

static int cast(zval *readobj, zval *writeobj, int type, int should_free TSRMLS_DC)
{
  proxyenv *jenv = EXT_GLOBAL(connect_to_server)(TSRMLS_C);
  long obj = 0;
  zval free_obj;

  if(!jenv) return FAILURE;

  if (should_free)
	free_obj = *writeobj;

  if(jenv && (Z_TYPE_P(readobj) == IS_OBJECT)) {
	EXT_GLOBAL(get_jobject_from_object)(readobj, &obj TSRMLS_CC);
  }

  if(obj) {

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
		  EXT_GLOBAL(get_jobject_from_object)(writeobj, &obj2 TSRMLS_CC);
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
	  (*jenv)->writeInvokeBegin(jenv, 0, "getValues", 0, 'I', writeobj);
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

void 
EXT_GLOBAL(call_function_handler4)(INTERNAL_FUNCTION_PARAMETERS, zend_property_reference *property_reference)
{
  pval *object = property_reference->object;
  zend_overloaded_element *function_name = (zend_overloaded_element *)
    property_reference->elements_list->tail->data;
  char *name = Z_STRVAL(function_name->element);
  int arg_count = ZEND_NUM_ARGS();
  pval **arguments = (pval **) emalloc(sizeof(pval *)*arg_count);
  short createInstance = 1;
  enum constructor constructor = CONSTRUCTOR_NONE;
  zend_class_entry *ce = Z_OBJCE_P(getThis()), *parent;

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

/*
 * check for CGI environment and set hosts so that we can connect back
 * to the sever from which we were called.
 */
static void override_ini_from_cgi(void) {
  static const char key_hosts[]="java.hosts";
  static const char key_servlet[] = "java.servlet";
  char *hosts;
  EXT_GLOBAL(cfg)->is_cgi_servlet=0;
  
  if ((hosts=getenv("X_JAVABRIDGE_OVERRIDE_HOSTS"))) {
	switch(*hosts) {
	case '/': 				/* this is fast cgi, override
							   information will be passed via
							   X_JAVABRIDGE_REDIRECT header (see
							   override_ini_for_redirect()). */
	  zend_alter_ini_entry((char*)key_servlet, sizeof key_servlet,
						   (char*)on, sizeof on,
						   ZEND_INI_SYSTEM, PHP_INI_STAGE_STARTUP);
	  break;

	default:					/* cgi binary with redirect
								   information */
	  {
		char *kontext, *host = estrdup(hosts);
		kontext = strchr(host, '/');
		if(kontext) *kontext++=0;
		zend_alter_ini_entry((char*)key_hosts, sizeof key_hosts,
							 host, strlen(host)+1,
							 ZEND_INI_SYSTEM, PHP_INI_STAGE_STARTUP);
		if(!kontext) {
		  zend_alter_ini_entry((char*)key_servlet, sizeof key_servlet,
							   (char*)on, sizeof on,
							   ZEND_INI_SYSTEM, PHP_INI_STAGE_STARTUP);
		} else {
		  zend_alter_ini_entry((char*)key_servlet, sizeof key_servlet,
							   (char*)kontext, strlen(kontext)+1,
							   ZEND_INI_SYSTEM, PHP_INI_STAGE_STARTUP);
		}
		efree(host);
	  }
	  /* fall through */
	case 0:					/* cgi binary, but redirect is off */
	  EXT_GLOBAL(cfg)->is_cgi_servlet=1;
	}
  }
}

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

  EXT_GLOBAL(cfg)->can_fork = 
	!(EXT_GLOBAL (option_set_by_user) (U_SOCKNAME, EXT_GLOBAL(ini_user))) &&
	!(EXT_GLOBAL (option_set_by_user) (U_HOSTS, EXT_GLOBAL(ini_user))) &&
	!(EXT_GLOBAL (option_set_by_user) (U_SERVLET, EXT_GLOBAL(ini_user)));
}

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
  parent = (zend_class_entry *) EXT_GLOBAL(class_entry);
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
  
  INIT_OVERLOADED_CLASS_ENTRY(ce, EXT_NAME(), 
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
  EXT_GLOBAL(class_entry)->create_object = create_object;
  zend_class_implements(EXT_GLOBAL(class_entry) TSRMLS_CC, 1, zend_ce_arrayaccess);

  INIT_OVERLOADED_CLASS_ENTRY(ce, EXT_NAME()/**/"_exception", 
							  EXT_GLOBAL(class_functions), 
							  (zend_function*)&call, 
							  (zend_function*)&get, 
							  (zend_function*)&set);
  
  parent = (zend_class_entry *) zend_exception_get_default();
  EXT_GLOBAL(exception_class_entry) =
	zend_register_internal_class_ex(&ce, parent, NULL TSRMLS_CC);
  // only cast and clone; no iterator, no array access
  EXT_GLOBAL(exception_class_entry)->create_object = create_exception_object;
  
  INIT_CLASS_ENTRY(ce, EXT_NAME()/**/"_class", EXT_GLOBAL(class_functions));
  parent = (zend_class_entry *) EXT_GLOBAL(class_entry);

  EXT_GLOBAL(class_class_entry) = 
	zend_register_internal_class_ex(&ce, parent, NULL TSRMLS_CC);

  /* compatibility with the jsr implementation */
  INIT_CLASS_ENTRY(ce, EXT_NAME()/**/"class", EXT_GLOBAL(class_functions));
  parent = (zend_class_entry *) EXT_GLOBAL(class_entry);
  EXT_GLOBAL(class_class_entry_jsr) = 
	zend_register_internal_class_ex(&ce, parent, NULL TSRMLS_CC);

  INIT_CLASS_ENTRY(ce, EXT_NAME()/**/"exception", EXT_GLOBAL(class_functions));
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
	override_ini_from_cgi();
	EXT_GLOBAL(ini_user)|=EXT_GLOBAL(ini_updated);
	EXT_GLOBAL(ini_updated)=0;

	make_local_socket_info(TSRMLS_C);
	clone_cfg(TSRMLS_C);
	EXT_GLOBAL(start_server) (TSRMLS_C);
  } 
  return SUCCESS;
}
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
	php_info_print_table_row(2, EXT_NAME()/**/".libpath", EXT_GLOBAL(cfg)->ld_library_path);
	php_info_print_table_row(2, EXT_NAME()/**/".classpath", EXT_GLOBAL(cfg)->classpath);
  }
#endif
  if(is_local) {
	php_info_print_table_row(2, EXT_NAME()/**/"."/**/EXT_NAME()/**/"_home", EXT_GLOBAL(cfg)->vm_home);
	php_info_print_table_row(2, EXT_NAME()/**/"."/**/EXT_NAME(), EXT_GLOBAL(cfg)->vm);
	if(strlen(EXT_GLOBAL(cfg)->logFile)==0) 
	  php_info_print_table_row(2, EXT_NAME()/**/".log_file", "<stdout>");
	else
	  php_info_print_table_row(2, EXT_NAME()/**/".log_file", EXT_GLOBAL(cfg)->logFile);
  }
  php_info_print_table_row(2, EXT_NAME()/**/".log_level", is_level ? EXT_GLOBAL(cfg)->logLevel : "no value (use backend's default level)");
  php_info_print_table_row(2, EXT_NAME()/**/".hosts", JG(hosts));
#if EXTENSION == JAVA
  php_info_print_table_row(2, EXT_NAME()/**/".servlet", JG(servlet)?JG(servlet):off);
#endif
  if(!server || is_local) {
	php_info_print_table_row(2, EXT_NAME()/**/" command", s);
  }
  php_info_print_table_row(2, EXT_NAME()/**/" status", server?"running":"not running");
  php_info_print_table_row(2, EXT_NAME()/**/" server", server?server:"localhost");
  php_info_print_table_end();
  
  free(server);
  free(s);
}

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
