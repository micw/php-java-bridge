/*-*- mode: C; tab-width:4 -*-*/

/* wait */
#include <sys/types.h>
#include <sys/wait.h>
/* miscellaneous */
#include <stdio.h>
#include <stdlib.h>
#include <assert.h>
#include <errno.h>

#include "php_wrapper.h"
#include "php_globals.h"
#include "ext/standard/info.h"

#include "php_java.h"
#include "java_bridge.h"

#ifdef ZEND_ENGINE_2
#include "zend_interfaces.h"
#include "zend_exceptions.h"
#endif

EXT_DECLARE_MODULE_GLOBALS(EXT)
struct cfg *EXT_GLOBAL (cfg)  = 0;

#ifdef __MINGW32__
static int EXT_GLOBAL(errno)=0;
int *__errno (void) { return &EXT_GLOBAL(errno); }
#define php_info_print_table_row(a, b, c) \
   php_info_print_table_row_ex(a, EXT_NAME(), b, c)
#endif


PHP_RINIT_FUNCTION(EXT) 
{
  if(JG(jenv)) {
	php_error(E_ERROR, "php_mod_"/**/EXT_NAME()/**/"(%d): Synchronization problem, rinit with active connection called. Cannot continue, aborting now. Please report this to: php-java-bridge-users@lists.sourceforge.net",59);
  }
  JG(is_closed)=0;
  return SUCCESS;
}
PHP_RSHUTDOWN_FUNCTION(EXT)
{
  if(JG(jenv)) {
	if(*JG(jenv)) {
	  if((*JG(jenv))->peer) close((*JG(jenv))->peer);
	  if((*JG(jenv))->s) free((*JG(jenv))->s);
	  if((*JG(jenv))->send) free((*JG(jenv))->send);
	  if((*JG(jenv))->server_name) free((*JG(jenv))->server_name);
	  if((*JG(jenv))->cookie_name) free((*JG(jenv))->cookie_name);
	  if((*JG(jenv))->cookie_value) free((*JG(jenv))->cookie_value);
	  free(*JG(jenv));
	}
	free(JG(jenv));
  }
  JG(jenv) = NULL;
  JG(is_closed)=1;
  return SUCCESS;
}

EXT_FUNCTION(EXT_GLOBAL(last_exception_get))
{
  proxyenv *jenv = EXT_GLOBAL(connect_to_server)(TSRMLS_C);
  if(!jenv) {RETURN_NULL();}

  if (ZEND_NUM_ARGS()!=0) WRONG_PARAM_COUNT;

  (*jenv)->writeInvokeBegin(jenv, 0, "lastException", 0, 'P', return_value);
  (*jenv)->writeInvokeEnd(jenv);
}

EXT_FUNCTION(EXT_GLOBAL(last_exception_clear))
{
  proxyenv *jenv = EXT_GLOBAL(connect_to_server)(TSRMLS_C);
  if(!jenv) {RETURN_NULL();}

  if (ZEND_NUM_ARGS()!=0) WRONG_PARAM_COUNT;

  (*jenv)->writeInvokeBegin(jenv, 0, "lastException", 0, 'P', return_value);
  (*jenv)->writeObject(jenv, 0);
  (*jenv)->writeInvokeEnd(jenv);
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
  zval **path;
  proxyenv *jenv = EXT_GLOBAL(connect_to_server)(TSRMLS_C);
  if(!jenv) {RETURN_NULL();}

  if (ZEND_NUM_ARGS()!=1 || zend_get_parameters_ex(1, &path) == FAILURE)
	WRONG_PARAM_COUNT;

  convert_to_string_ex(path);

  (*jenv)->writeInvokeBegin(jenv, 0, "setJarLibraryPath", 0, 'I', return_value);
  (*jenv)->writeString(jenv, Z_STRVAL_PP(path), Z_STRLEN_PP(path));
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
	zend_error(E_ERROR, "Parameter #1 for %s() must be a "/**/EXT_NAME()/**/" object", get_active_function_name(TSRMLS_C));
	return;
  }

  class = 0;
  EXT_GLOBAL(get_jobject_from_object)(*pclass, &class TSRMLS_CC);
  if(!class) {
	zend_error(E_ERROR, "Parameter #2 for %s() must be a "/**/EXT_NAME()/**/" object", get_active_function_name(TSRMLS_C));
	return;
  }

  (*jenv)->writeInvokeBegin(jenv, 0, "InstanceOf", 0, 'I', return_value);
  (*jenv)->writeObject(jenv, obj);
  (*jenv)->writeObject(jenv, class);
  (*jenv)->writeInvokeEnd(jenv);
}

EXT_FUNCTION(EXT_GLOBAL(get_session))
{
  proxyenv *jenv;
  zval **session;
  
  if (ZEND_NUM_ARGS()!=1 || zend_get_parameters_ex(1, &session) == FAILURE)
	WRONG_PARAM_COUNT;

  if(JG(jenv)) {
	php_error(E_ERROR, "This script has already selected a backend.  Please call "/**/EXT_NAME()/**/"_get_session() before calling any of the "/**/EXT_NAME()/**/"functions.");
  }

  convert_to_string_ex(session);

  jenv=EXT_GLOBAL(connect_to_server)(TSRMLS_C);
  if(!jenv) RETURN_NULL();
  (*jenv)->writeInvokeBegin(jenv, 0, "getSession", 0, 'I', return_value);
  (*jenv)->writeString(jenv, Z_STRVAL_PP(session), Z_STRLEN_PP(session)); 
  (*jenv)->writeBoolean(jenv, 0); 
  (*jenv)->writeLong(jenv, 1400); 
  (*jenv)->writeInvokeEnd(jenv);
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
}

function_entry EXT_GLOBAL(functions)[] = {
	PHP_FE(EXT_GLOBAL(last_exception_get), NULL)
	PHP_FE(EXT_GLOBAL(last_exception_clear), NULL)
	PHP_FE(EXT_GLOBAL(set_file_encoding), NULL)
	PHP_FE(EXT_GLOBAL(require), NULL)
	PHP_FE(EXT_GLOBAL(set_library_path), NULL)
	PHP_FE(EXT_GLOBAL(instanceof), NULL)
	PHP_FE(EXT_GLOBAL(get_session), NULL)
	PHP_FE(EXT_GLOBAL(get_server_name), NULL)
	PHP_FE(EXT_GLOBAL(reset), NULL)
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

int EXT_GLOBAL(ini_updated), EXT_GLOBAL(ini_last_updated);
zend_class_entry *EXT_GLOBAL(class_entry);
zend_class_entry *EXT_GLOBAL(class_class_entry);
zend_class_entry *EXT_GLOBAL(exception_class_entry);

#ifdef ZEND_ENGINE_2
zend_object_handlers EXT_GLOBAL(handlers);
#endif

static PHP_INI_MH(OnIniHosts)
{
	if (new_value) {
	  EXT_GLOBAL(cfg)->hosts=new_value;
	  EXT_GLOBAL(ini_updated)|=U_HOSTS;
	}
	return SUCCESS;
}
static PHP_INI_MH(OnIniServlet)
{
	if (new_value) {
	  EXT_GLOBAL(cfg)->servlet=new_value;
	  EXT_GLOBAL(ini_updated)|=U_SERVLET;
	}
	return SUCCESS;
}
static PHP_INI_MH(OnIniSockname)
{
	if (new_value) {
	  EXT_GLOBAL(cfg)->sockname=new_value;
	  EXT_GLOBAL(ini_updated)|=U_SOCKNAME;
	}
	return SUCCESS;
}
static PHP_INI_MH(OnIniClassPath)
{
	if (new_value) {
	  EXT_GLOBAL(cfg)->classpath =new_value;
	  EXT_GLOBAL(ini_updated)|=U_CLASSPATH;
	}
	return SUCCESS;
}
static PHP_INI_MH(OnIniLibPath)
{
	if (new_value) {
	  EXT_GLOBAL(cfg)->ld_library_path = new_value;
	  EXT_GLOBAL(ini_updated)|=U_LIBRARY_PATH;
	}
	return SUCCESS;
}
static PHP_INI_MH(OnIniJava)
{
  if (new_value) {
	EXT_GLOBAL(cfg)->vm = new_value;
	EXT_GLOBAL(ini_updated)|=U_JAVA;
  }
  return SUCCESS;
}
static PHP_INI_MH(OnIniJavaHome)
{
	if (new_value) {
	  EXT_GLOBAL(cfg)->vm_home = new_value;
	  EXT_GLOBAL(ini_updated)|=U_JAVA_HOME;
	}
	return SUCCESS;
}
static PHP_INI_MH(OnIniLogLevel)
{
	if (new_value) {
	  EXT_GLOBAL(cfg)->logLevel = new_value;
	  EXT_GLOBAL(cfg)->logLevel_val=atoi(EXT_GLOBAL(cfg)->logLevel);
	  EXT_GLOBAL(ini_updated)|=U_LOGLEVEL;
	}
	return SUCCESS;
}
static PHP_INI_MH(OnIniLogFile)
{
	if (new_value) {
	  EXT_GLOBAL(cfg)->logFile = new_value;
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

	 PHP_INI_ENTRY(EXT_NAME()/**/".log_level",   NULL, PHP_INI_SYSTEM, OnIniLogLevel)
	 PHP_INI_ENTRY(EXT_NAME()/**/".log_file",   NULL, PHP_INI_SYSTEM, OnIniLogFile)
PHP_INI_END()

/* vm_alloc_globals_ctor(zend_vm_globals *vm_globals) */
static void EXT_GLOBAL(alloc_globals_ctor)(EXT_GLOBAL_EX(zend_,globals,_) *EXT_GLOBAL(globals) TSRMLS_DC)
{
  EXT_GLOBAL(globals)->jenv=0;
  EXT_GLOBAL(globals)->is_closed=-1;
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

// exact copy of vm_class for jsr223 compatibility
EXT_METHOD(EXT, EXT_GLOBAL_N(class))
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
  zval **argv;
  int argc = ZEND_NUM_ARGS();
  long obj;

  argv = (zval **) safe_emalloc(sizeof(zval *), argc, 0);
  if (zend_get_parameters_array(ht, argc, argv) == FAILURE) {
	php_error(E_ERROR, "Couldn't fetch arguments into array.");
	RETURN_FALSE;
  }
  
  EXT_GLOBAL(get_jobject_from_object)(getThis(), &obj TSRMLS_CC);
  if(!obj) RETURN_TRUE;			/* may happen when vm is not initalized */

  if(JG(jenv))
	(*JG(jenv))->writeUnref(JG(jenv), obj);

  efree(argv);
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
  EXT_ME(EXT, EXT_GLOBAL_N(class), NULL, 0)
  EXT_ME(EXT, EXT_GLOBAL(class), NULL, 0)
  EXT_ME(EXT, EXT, NULL, 0)
  EXT_ME(EXT, __call, arginfo_set, ZEND_ACC_PUBLIC)
  EXT_ME(EXT, __tostring, arginfo_zero, ZEND_ACC_PUBLIC)
  EXT_ME(EXT, __get, arginfo_get, ZEND_ACC_PUBLIC)
  EXT_ME(EXT, __set, arginfo_set, ZEND_ACC_PUBLIC)
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
  if (type==IS_STRING) {
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
	  (*jenv)->writeInvokeBegin(jenv, 0, "ObjectToString", 0, 'I', writeobj);
	  (*jenv)->writeObject(jenv, obj);
	  (*jenv)->writeInvokeEnd(jenv);
	}

	if (should_free)
	  zval_dtor(&free_obj);
	return obj?SUCCESS:FAILURE;
  }
  return FAILURE;
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

void 
EXT_GLOBAL(call_function_handler4)(INTERNAL_FUNCTION_PARAMETERS, zend_property_reference *property_reference)
{
  pval *object = property_reference->object;
  zend_overloaded_element *function_name = (zend_overloaded_element *)
    property_reference->elements_list->tail->data;
  char *name = Z_STRVAL(function_name->element);
  int arg_count = ZEND_NUM_ARGS();
  pval **arguments = (pval **) emalloc(sizeof(pval *)*arg_count);
  short createInstance;
  enum constructor constructor = CONSTRUCT_NONE;

  getParametersArray(ht, arg_count, arguments);

  createInstance = strcmp(EXT_NAME()/**/"_class", name) && strcmp(EXT_NAME()/**/"class", name);
  if(!strcmp(EXT_NAME(), name) || !createInstance) constructor = CONSTRUCTOR;
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

PHP_MINIT_FUNCTION(EXT)
{
  zend_class_entry *parent;
#ifndef ZEND_ENGINE_2
  zend_class_entry ce;
  INIT_OVERLOADED_CLASS_ENTRY(ce, EXT_NAME(), NULL,
							  EXT_GLOBAL(call_function_handler4),
							  get_property_handler,
							  set_property_handler);

  EXT_GLOBAL(class_entry) = zend_register_internal_class(&ce TSRMLS_CC);

  INIT_CLASS_ENTRY(ce, EXT_NAME()/**/"_class", NULL);
  parent = (zend_class_entry *) EXT_GLOBAL(class_entry);
  zend_register_internal_class_ex(&ce, parent, NULL TSRMLS_CC);
  INIT_CLASS_ENTRY(ce, EXT_NAME()/**/"class", NULL);
  parent = (zend_class_entry *) EXT_GLOBAL(class_entry);
  zend_register_internal_class_ex(&ce, parent, NULL TSRMLS_CC);

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
	extern void EXT_GLOBAL(init_cfg) ();
	
	EXT_GLOBAL(init_cfg) ();
	memset(&EXT_GLOBAL(cfg)->saddr, 0, sizeof EXT_GLOBAL(cfg)->saddr);
#ifndef CFG_JAVA_SOCKET_INET
	EXT_GLOBAL(cfg)->saddr.sun_family = AF_LOCAL;
	memset(EXT_GLOBAL(cfg)->saddr.sun_path, 0, sizeof EXT_GLOBAL(cfg)->saddr.sun_path);
	strcpy(EXT_GLOBAL(cfg)->saddr.sun_path, EXT_GLOBAL(cfg)->sockname);
# ifdef HAVE_ABSTRACT_NAMESPACE
	*EXT_GLOBAL(cfg)->saddr.sun_path=0;
# endif
#else
	EXT_GLOBAL(cfg)->saddr.sin_family = AF_INET;
	EXT_GLOBAL(cfg)->saddr.sin_port=htons(atoi(EXT_GLOBAL(cfg)->sockname));
	EXT_GLOBAL(cfg)->saddr.sin_addr.s_addr = inet_addr( "127.0.0.1" );
#endif
  }
  EXT_GLOBAL(start_server) ();
  
  assert(!EXT_GLOBAL(ini_last_updated));
  EXT_GLOBAL(ini_last_updated)=EXT_GLOBAL(ini_updated);
  EXT_GLOBAL(ini_updated)=0;
  
  return SUCCESS;
}
PHP_MINFO_FUNCTION(EXT)
{
  char*s=EXT_GLOBAL(get_server_string) ();
  char*server = EXT_GLOBAL(test_server) (0, 0);
  short is_level = ((EXT_GLOBAL (ini_last_updated)&U_LOGLEVEL)!=0);

  php_info_print_table_start();
  php_info_print_table_row(2, EXT_NAME()/**/" support", "Enabled");
  php_info_print_table_row(2, EXT_NAME()/**/" bridge", EXT_GLOBAL(bridge_version));
#if !defined(__MINGW32__) && EXTENSION == JAVA
  php_info_print_table_row(2, EXT_NAME()/**/".libpath", EXT_GLOBAL(cfg)->ld_library_path);
  php_info_print_table_row(2, EXT_NAME()/**/".classpath", EXT_GLOBAL(cfg)->classpath);
  php_info_print_table_row(2, EXT_NAME()/**/"."/**/EXT_NAME()/**/"_home", EXT_GLOBAL(cfg)->vm_home);
  php_info_print_table_row(2, EXT_NAME()/**/"."/**/EXT_NAME(), EXT_GLOBAL(cfg)->vm);
#endif
#ifndef __MINGW32__
  if(strlen(EXT_GLOBAL(cfg)->logFile)==0) 
	php_info_print_table_row(2, EXT_NAME()/**/".log_file", "<stdout>");
  else
	php_info_print_table_row(2, EXT_NAME()/**/".log_file", EXT_GLOBAL(cfg)->logFile);
  php_info_print_table_row(2, EXT_NAME()/**/".log_level", is_level ? EXT_GLOBAL(cfg)->logLevel : "no value (use backend's default level)");
#endif
  php_info_print_table_row(2, EXT_NAME()/**/".hosts", EXT_GLOBAL(cfg)->hosts);
#ifndef __MINGW32__
  php_info_print_table_row(2, EXT_NAME()/**/" command", s);
#endif
  php_info_print_table_row(2, EXT_NAME()/**/" status", server?"running":"not running");
  php_info_print_table_row(2, EXT_NAME()/**/" server", server?server:"localhost");
  php_info_print_table_end();
  
  free(server);
  free(s);
}

PHP_MSHUTDOWN_FUNCTION(EXT) 
{
  extern void EXT_GLOBAL(shutdown_library) ();
  extern void EXT_GLOBAL(destroy_cfg) (int);
  
  EXT_GLOBAL(destroy_cfg) (EXT_GLOBAL(ini_last_updated));
  EXT_GLOBAL(ini_last_updated)=0;

  UNREGISTER_INI_ENTRIES();
  EXT_GLOBAL(shutdown_library) ();

  assert(cfg);
  if(EXT_GLOBAL (cfg) ) { free(EXT_GLOBAL (cfg) ); EXT_GLOBAL (cfg) = 0; }

  return SUCCESS;
}

#ifndef PHP_WRAPPER_H
#error must include php_wrapper.h
#endif
