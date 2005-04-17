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

ZEND_DECLARE_MODULE_GLOBALS(java)
struct cfg *cfg = 0;

#ifdef __MINGW32__
static int java_errno=0;
int *__errno (void) { return &java_errno; }
#define php_info_print_table_row(a, b, c) \
   php_info_print_table_row_ex(a, "java", b, c)
#endif


PHP_RINIT_FUNCTION(java) 
{
  if(JG(jenv)) {
	php_error(E_ERROR, "php_mod_java(%d): Synchronization problem, rinit with active connection called. Cannot continue, aborting now. Please report this to: php-java-bridge-users@lists.sourceforge.net",59);
  }
  JG(is_closed)=0;
  return SUCCESS;
}
PHP_RSHUTDOWN_FUNCTION(java)
{
  if(JG(jenv)) {
	if(*JG(jenv)) {
	  if((*JG(jenv))->peer) close((*JG(jenv))->peer);
	  if((*JG(jenv))->s) free((*JG(jenv))->s);
	  if((*JG(jenv))->send) free((*JG(jenv))->send);
	  if((*JG(jenv))->server_name) free((*JG(jenv))->server_name);
	  free(*JG(jenv));
	}
	free(JG(jenv));
  }
  JG(jenv) = NULL;
  JG(is_closed)=1;
  return SUCCESS;
}

PHP_FUNCTION(java_last_exception_get)
{
  proxyenv *jenv = java_connect_to_server(TSRMLS_C);
  if(!jenv) {RETURN_NULL();}

  if (ZEND_NUM_ARGS()!=0) WRONG_PARAM_COUNT;

  (*jenv)->writeInvokeBegin(jenv, 0, "lastException", 0, 'P', return_value);
  (*jenv)->writeInvokeEnd(jenv);
}

PHP_FUNCTION(java_last_exception_clear)
{
  proxyenv *jenv = java_connect_to_server(TSRMLS_C);
  if(!jenv) {RETURN_NULL();}

  if (ZEND_NUM_ARGS()!=0) WRONG_PARAM_COUNT;

  (*jenv)->writeInvokeBegin(jenv, 0, "lastException", 0, 'P', return_value);
  (*jenv)->writeObject(jenv, 0);
  (*jenv)->writeInvokeEnd(jenv);
}

PHP_FUNCTION(java_set_library_path)
{
  zval **path;
  proxyenv *jenv = java_connect_to_server(TSRMLS_C);
  if(!jenv) {RETURN_NULL();}

  if (ZEND_NUM_ARGS()!=1 || zend_get_parameters_ex(1, &path) == FAILURE)
	WRONG_PARAM_COUNT;

  convert_to_string_ex(path);

  (*jenv)->writeInvokeBegin(jenv, 0, "setJarLibraryPath", 0, 'I', return_value);
  (*jenv)->writeString(jenv, Z_STRVAL_PP(path), Z_STRLEN_PP(path));
  (*jenv)->writeInvokeEnd(jenv);
}

PHP_FUNCTION(java_instanceof)
{
  zval **pobj, **pclass;
  long obj, class;
  proxyenv *jenv = java_connect_to_server(TSRMLS_C);
  if(!jenv) {RETURN_NULL();}

  if (ZEND_NUM_ARGS()!=2 || zend_get_parameters_ex(2, &pobj, &pclass) == FAILURE)
	WRONG_PARAM_COUNT;

  convert_to_object_ex(pobj);
  convert_to_object_ex(pclass);

  obj = 0;
  java_get_jobject_from_object(*pobj, &obj TSRMLS_CC);
  if(!obj) {
	zend_error(E_ERROR, "Parameter #1 for %s() must be a java object", get_active_function_name(TSRMLS_C));
	return;
  }

  class = 0;
  java_get_jobject_from_object(*pclass, &class TSRMLS_CC);
  if(!class) {
	zend_error(E_ERROR, "Parameter #2 for %s() must be a java object", get_active_function_name(TSRMLS_C));
	return;
  }

  (*jenv)->writeInvokeBegin(jenv, 0, "InstanceOf", 0, 'I', return_value);
  (*jenv)->writeObject(jenv, obj);
  (*jenv)->writeObject(jenv, class);
  (*jenv)->writeInvokeEnd(jenv);
}

PHP_FUNCTION(java_get_session)
{
  zval **argv;
  int argc = ZEND_NUM_ARGS();
  
  if (argc!=2) WRONG_PARAM_COUNT;

  argv = (zval **) safe_emalloc(sizeof(zval *), argc, 0);
  if (zend_get_parameters_array(ht, argc, argv) == FAILURE) {
	php_error(E_ERROR, "Couldn't fetch arguments into array.");
	RETURN_NULL();
  }

  php_java_invoke("getSession", 0, argc, argv, 1, return_value TSRMLS_CC);

  efree(argv);
}

PHP_FUNCTION(java_get_server_name)
{
  proxyenv *jenv;
  if (ZEND_NUM_ARGS()!=0) WRONG_PARAM_COUNT;

  jenv = java_try_connect_to_server(TSRMLS_C);
  if(jenv && (*jenv)->server_name) {
	RETURN_STRING((*jenv)->server_name, 1);
  }
  RETURN_NULL();
}

function_entry java_functions[] = {
	PHP_FE(java_last_exception_get, NULL)
	PHP_FE(java_last_exception_clear, NULL)
	PHP_FE(java_set_library_path, NULL)
	PHP_FE(java_instanceof, NULL)
	PHP_FE(java_get_session, NULL)
	PHP_FE(java_get_server_name, NULL)
	{NULL, NULL, NULL}
};

zend_module_entry java_module_entry = {
	STANDARD_MODULE_HEADER,
	"java",
	java_functions,
	PHP_MINIT(java),
	PHP_MSHUTDOWN(java),
	PHP_RINIT(java),
	PHP_RSHUTDOWN(java),
	PHP_MINFO(java),
	NO_VERSION_YET,
	STANDARD_MODULE_PROPERTIES
};

#ifdef COMPILE_DL_JAVA
ZEND_GET_MODULE(java)
#endif

int java_ini_updated, java_ini_last_updated;
zend_class_entry *php_java_class_entry;
zend_class_entry *php_java_class_class_entry;
zend_class_entry *php_java_exception_class_entry;

#ifdef ZEND_ENGINE_2
zend_object_handlers php_java_handlers;
#endif

static PHP_INI_MH(OnIniHosts)
{
	if (new_value) {
	  cfg->hosts=new_value;
	  java_ini_updated|=U_HOSTS;
	}
	return SUCCESS;
}
static PHP_INI_MH(OnIniSockname)
{
	if (new_value) {
	  cfg->sockname=new_value;
	  java_ini_updated|=U_SOCKNAME;
	}
	return SUCCESS;
}
static PHP_INI_MH(OnIniClassPath)
{
	if (new_value) {
	  cfg->classpath =new_value;
	  java_ini_updated|=U_CLASSPATH;
	}
	return SUCCESS;
}
static PHP_INI_MH(OnIniLibPath)
{
	if (new_value) {
	  cfg->ld_library_path = new_value;
	  java_ini_updated|=U_LIBRARY_PATH;
	}
	return SUCCESS;
}
static PHP_INI_MH(OnIniJava)
{
  if (new_value) {
	cfg->java = new_value;
	java_ini_updated|=U_JAVA;
  }
  return SUCCESS;
}
static PHP_INI_MH(OnIniJavaHome)
{
	if (new_value) {
	  cfg->java_home = new_value;
	  java_ini_updated|=U_JAVA_HOME;
	}
	return SUCCESS;
}
static PHP_INI_MH(OnIniLogLevel)
{
	if (new_value) {
	  cfg->logLevel = new_value;
	  java_ini_updated|=U_LOGLEVEL;
	}
	return SUCCESS;
}
static PHP_INI_MH(OnIniLogFile)
{
	if (new_value) {
	  cfg->logFile = new_value;
	  java_ini_updated|=U_LOGFILE;
	}
	return SUCCESS;
}
PHP_INI_BEGIN()
	 PHP_INI_ENTRY("java.socketname", NULL, PHP_INI_SYSTEM, OnIniSockname)
	 PHP_INI_ENTRY("java.hosts",   NULL, PHP_INI_SYSTEM, OnIniHosts)
	 PHP_INI_ENTRY("java.classpath", NULL, PHP_INI_SYSTEM, OnIniClassPath)
	 PHP_INI_ENTRY("java.libpath",   NULL, PHP_INI_SYSTEM, OnIniLibPath)
	 PHP_INI_ENTRY("java.java",   NULL, PHP_INI_SYSTEM, OnIniJava)
	 PHP_INI_ENTRY("java.java_home",   NULL, PHP_INI_SYSTEM, OnIniJavaHome)

	 PHP_INI_ENTRY("java.log_level",   NULL, PHP_INI_SYSTEM, OnIniLogLevel)
	 PHP_INI_ENTRY("java.log_file",   NULL, PHP_INI_SYSTEM, OnIniLogFile)
PHP_INI_END()


static void php_java_alloc_globals_ctor(zend_java_globals *java_globals TSRMLS_DC)
{
  java_globals->jenv=0;
  java_globals->is_closed=-1;
}

#ifdef ZEND_ENGINE_2

PHP_METHOD(java, java)
{
	zval **argv;
	int argc = ZEND_NUM_ARGS();

	argv = (zval **) safe_emalloc(sizeof(zval *), argc, 0);
	if (zend_get_parameters_array(ht, argc, argv) == FAILURE) {
		php_error(E_ERROR, "Couldn't fetch arguments into array.");
		RETURN_NULL();
	}

	php_java_call_function_handler(INTERNAL_FUNCTION_PARAM_PASSTHRU,
								   "java", 1, 1,
								   getThis(),
								   argc, argv);
	efree(argv);
}

PHP_METHOD(java, mono)
{
	zval **argv;
	int argc = ZEND_NUM_ARGS();

	argv = (zval **) safe_emalloc(sizeof(zval *), argc, 0);
	if (zend_get_parameters_array(ht, argc, argv) == FAILURE) {
		php_error(E_ERROR, "Couldn't fetch arguments into array.");
		RETURN_NULL();
	}

	php_java_call_function_handler(INTERNAL_FUNCTION_PARAM_PASSTHRU,
								   "mono", 1, 1,
								   getThis(),
								   argc, argv);
	efree(argv);
}

PHP_METHOD(java, java_class)
{
	zval **argv;
	int argc = ZEND_NUM_ARGS();

	argv = (zval **) safe_emalloc(sizeof(zval *), argc, 0);
	if (zend_get_parameters_array(ht, argc, argv) == FAILURE) {
		php_error(E_ERROR, "Couldn't fetch arguments into array.");
		RETURN_NULL();
	}

	php_java_call_function_handler(INTERNAL_FUNCTION_PARAM_PASSTHRU,
								   "java", 1, 0, 
								   getThis(),
								   argc, argv);
	efree(argv);
}

PHP_METHOD(java, mono_class)
{
	zval **argv;
	int argc = ZEND_NUM_ARGS();

	argv = (zval **) safe_emalloc(sizeof(zval *), argc, 0);
	if (zend_get_parameters_array(ht, argc, argv) == FAILURE) {
		php_error(E_ERROR, "Couldn't fetch arguments into array.");
		RETURN_NULL();
	}

	php_java_call_function_handler(INTERNAL_FUNCTION_PARAM_PASSTHRU,
								   "mono", 1, 0, 
								   getThis(),
								   argc, argv);
	efree(argv);
}

// exact copy of java_class for jsr223 compatibility
PHP_METHOD(java, javaclass)
{
	zval **argv;
	int argc = ZEND_NUM_ARGS();

	argv = (zval **) safe_emalloc(sizeof(zval *), argc, 0);
	if (zend_get_parameters_array(ht, argc, argv) == FAILURE) {
		php_error(E_ERROR, "Couldn't fetch arguments into array.");
		RETURN_NULL();
	}

	php_java_call_function_handler(INTERNAL_FUNCTION_PARAM_PASSTHRU,
								   "java", 1, 0, 
								   getThis(),
								   argc, argv);
	efree(argv);
}

// exact copy of java_class for jsr223 compatibility
PHP_METHOD(java, monoclass)
{
	zval **argv;
	int argc = ZEND_NUM_ARGS();

	argv = (zval **) safe_emalloc(sizeof(zval *), argc, 0);
	if (zend_get_parameters_array(ht, argc, argv) == FAILURE) {
		php_error(E_ERROR, "Couldn't fetch arguments into array.");
		RETURN_NULL();
	}

	php_java_call_function_handler(INTERNAL_FUNCTION_PARAM_PASSTHRU,
								   "mono", 1, 0, 
								   getThis(),
								   argc, argv);
	efree(argv);
}

PHP_METHOD(java, __call)
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

	php_java_call_function_handler(INTERNAL_FUNCTION_PARAM_PASSTHRU,
								   Z_STRVAL(*argv[0]), 0, 0,
								   getThis(),
								   xargc, xargv);
								   
	efree(argv);
	efree(xargv);
}
PHP_METHOD(java, __tostring)
{
  long result = 0;
  
  if(Z_TYPE_P(getThis()) == IS_OBJECT) {
	java_get_jobject_from_object(getThis(), &result TSRMLS_CC);
  }
  if(result) {
	proxyenv *jenv = java_connect_to_server(TSRMLS_C);
	if(!jenv) {RETURN_NULL();}

	(*jenv)->writeInvokeBegin(jenv, 0, "ObjectToString", 0, 'I', return_value);
	(*jenv)->writeObject(jenv, result);
	(*jenv)->writeInvokeEnd(jenv);
  } else {
	php_java_call_function_handler(INTERNAL_FUNCTION_PARAM_PASSTHRU,
								   "tostring", 0, 0, getThis(), 0, NULL);
  }

}
PHP_METHOD(java, __set)
{
  zval **argv;
  int argc = ZEND_NUM_ARGS();
  
  argv = (zval **) safe_emalloc(sizeof(zval *), argc, 0);
  if (zend_get_parameters_array(ht, argc, argv) == FAILURE) {
	php_error(E_ERROR, "Couldn't fetch arguments into array.");
	RETURN_NULL();
  }
  
  php_java_set_property_handler(Z_STRVAL(*argv[0]), getThis(), argv[1], return_value);
  
  efree(argv);
}
PHP_METHOD(java, __destruct)
{
  zval **argv;
  int argc = ZEND_NUM_ARGS();
  long obj;

  argv = (zval **) safe_emalloc(sizeof(zval *), argc, 0);
  if (zend_get_parameters_array(ht, argc, argv) == FAILURE) {
	php_error(E_ERROR, "Couldn't fetch arguments into array.");
	RETURN_FALSE;
  }
  
  java_get_jobject_from_object(getThis(), &obj TSRMLS_CC);
  if(!obj) RETURN_TRUE;			/* may happen when java is not initalized */

  if(JG(jenv))
	(*JG(jenv))->writeUnref(JG(jenv), obj);
#ifndef __MINGW32__
  else
	if(atoi(cfg->logLevel)>=4)
	  fputs("PHP bug, an object destructor was called after module shutdown\n", stderr);
#endif

  efree(argv);
  RETURN_TRUE;
}
PHP_METHOD(java, __get)
{
  zval **argv;
  int argc = ZEND_NUM_ARGS();
  argv = (zval **) safe_emalloc(sizeof(zval *), argc, 0);
  if (zend_get_parameters_array(ht, argc, argv) == FAILURE) {
	php_error(E_ERROR, "Couldn't fetch arguments into array.");
	RETURN_NULL();
  }
  
  php_java_get_property_handler(Z_STRVAL(*argv[0]), getThis(), return_value);
  efree(argv);
}
PHP_METHOD(java, offsetExists)
{
  proxyenv *jenv = java_connect_to_server(TSRMLS_C);
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
  java_get_jobject_from_object(getThis(), &obj TSRMLS_CC);
  assert(obj);
  (*jenv)->writeInvokeBegin(jenv, 0, "getPhpMap", 0, 'I', return_value);
  (*jenv)->writeObject(jenv, obj);
  (*jenv)->writeInvokeEnd(jenv);
  java_get_jobject_from_object(return_value, &obj TSRMLS_CC);
  assert(obj);
  php_java_invoke("offsetExists", obj, argc, argv, 0, return_value TSRMLS_CC);
  efree(argv);
}
PHP_METHOD(java, offsetGet)
{
  zval **argv;
  int argc;
  long obj;
  proxyenv *jenv = java_connect_to_server(TSRMLS_C);
  if(!jenv) {RETURN_NULL();}

  argc = ZEND_NUM_ARGS();
  argv = (zval **) safe_emalloc(sizeof(zval *), argc, 0);
  if (zend_get_parameters_array(ht, argc, argv) == FAILURE) {
	php_error(E_ERROR, "Couldn't fetch arguments into array.");
	RETURN_NULL();
  }

  java_get_jobject_from_object(getThis(), &obj TSRMLS_CC);
  assert(obj);
  (*jenv)->writeInvokeBegin(jenv, 0, "getPhpMap", 0, 'I', return_value);
  (*jenv)->writeObject(jenv, obj);
  (*jenv)->writeInvokeEnd(jenv);
  java_get_jobject_from_object(return_value, &obj TSRMLS_CC);
  assert(obj);
  php_java_invoke("offsetGet", obj, argc, argv, 0, return_value TSRMLS_CC);
  efree(argv);
}

PHP_METHOD(java, offsetSet)
{
  zval **argv;
  int argc;
  long obj;
  proxyenv *jenv = java_connect_to_server(TSRMLS_C);
  if(!jenv) {RETURN_NULL();}

  argc = ZEND_NUM_ARGS();
  argv = (zval **) safe_emalloc(sizeof(zval *), argc, 0);
  if (zend_get_parameters_array(ht, argc, argv) == FAILURE) {
	php_error(E_ERROR, "Couldn't fetch arguments into array.");
	RETURN_NULL();
  }
  java_get_jobject_from_object(getThis(), &obj TSRMLS_CC);
  assert(obj);
  (*jenv)->writeInvokeBegin(jenv, 0, "getPhpMap", 0, 'I', return_value);
  (*jenv)->writeObject(jenv, obj);
  (*jenv)->writeInvokeEnd(jenv);
  java_get_jobject_from_object(return_value, &obj TSRMLS_CC);
  assert(obj);
  php_java_invoke("offsetSet", obj, argc, argv, 0, return_value TSRMLS_CC);
  efree(argv);
}

PHP_METHOD(java, offsetUnset)
{
  zval **argv;
  int argc;
  long obj;
  proxyenv *jenv = java_connect_to_server(TSRMLS_C);
  if(!jenv) {RETURN_NULL();}

  argc = ZEND_NUM_ARGS();
  argv = (zval **) safe_emalloc(sizeof(zval *), argc, 0);
  if (zend_get_parameters_array(ht, argc, argv) == FAILURE) {
	php_error(E_ERROR, "Couldn't fetch arguments into array.");
	RETURN_NULL();
  }
  java_get_jobject_from_object(getThis(), &obj TSRMLS_CC);
  assert(obj);
  (*jenv)->writeInvokeBegin(jenv, 0, "getPhpMap", 0, 'I', return_value);
  (*jenv)->writeObject(jenv, obj);
  (*jenv)->writeInvokeEnd(jenv);
  java_get_jobject_from_object(return_value, &obj TSRMLS_CC);
  assert(obj);
  php_java_invoke("offsetUnset", obj, argc, argv, 0, return_value TSRMLS_CC);
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

function_entry php_java_class_functions[] = {
  PHP_ME(java, javaclass, NULL, 0)
  PHP_ME(java, java_class, NULL, 0)
  PHP_ME(java, java, NULL, 0)
  PHP_ME(java, __call, arginfo_set, ZEND_ACC_PUBLIC)
  PHP_ME(java, __tostring, arginfo_zero, ZEND_ACC_PUBLIC)
  PHP_ME(java, __get, arginfo_get, ZEND_ACC_PUBLIC)
  PHP_ME(java, __set, arginfo_set, ZEND_ACC_PUBLIC)
  PHP_ME(java, __destruct, arginfo_zero, ZEND_ACC_PUBLIC)
  PHP_ME(java, offsetExists,  arginfo_get, ZEND_ACC_PUBLIC)
  PHP_ME(java, offsetGet,     arginfo_get, ZEND_ACC_PUBLIC)
  PHP_ME(java, offsetSet,     arginfo_set, ZEND_ACC_PUBLIC)
  PHP_ME(java, offsetUnset,   arginfo_get, ZEND_ACC_PUBLIC)
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
  obj.handlers = (zend_object_handlers*)&php_java_handlers;
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
  trace->is_ref = 0;
  trace->refcount = 0;
  zend_fetch_debug_backtrace(trace, 0 TSRMLS_CC);
  
  zend_update_property_string(zend_exception_get_default(), &obj, "file", sizeof("file")-1, zend_get_executed_filename(TSRMLS_C) TSRMLS_CC);
  zend_update_property_long(zend_exception_get_default(), &obj, "line", sizeof("line")-1, zend_get_executed_lineno(TSRMLS_C) TSRMLS_CC);
  zend_update_property(zend_exception_get_default(), &obj, "trace", sizeof("trace")-1, trace TSRMLS_CC);
  
  /* real work */
  obj.value.obj.handlers = (zend_object_handlers*)&php_java_handlers;
  
  return obj.value.obj;
}

static int cast(zval *readobj, zval *writeobj, int type, int should_free TSRMLS_DC)
{
  if (type==IS_STRING) {
	proxyenv *jenv = java_connect_to_server(TSRMLS_C);
	long obj = 0;
	zval free_obj;

	if (should_free)
	  free_obj = *writeobj;

	if(jenv && (Z_TYPE_P(readobj) == IS_OBJECT)) {
	  java_get_jobject_from_object(readobj, &obj TSRMLS_CC);
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
  long java_iterator;
  zval *current_object;
  int type;
} java_iterator;

static void iterator_dtor(zend_object_iterator *iter TSRMLS_DC)
{
  proxyenv *jenv = JG(jenv);
  java_iterator *iterator = (java_iterator *)iter;
  
  zval_ptr_dtor((zval**)&iterator->intern.data);
  if (iterator->current_object) zval_ptr_dtor((zval**)&iterator->current_object);
  
  if(iterator->java_iterator) {
	(*JG(jenv))->writeUnref(JG(jenv), iterator->java_iterator);
	iterator->java_iterator = 0;
  }
  
  efree(iterator);
}

static int iterator_valid(zend_object_iterator *iter TSRMLS_DC)
{
  java_iterator *iterator = (java_iterator *)iter;
  return (iterator->java_iterator && iterator->current_object) ? SUCCESS : FAILURE;
}

static void iterator_current_data(zend_object_iterator *iter, zval ***data TSRMLS_DC)
{
  java_iterator *iterator = (java_iterator *)iter;
  *data = &iterator->current_object;
}

static int iterator_current_key(zend_object_iterator *iter, char **str_key, uint *str_key_len, ulong *int_key TSRMLS_DC)
{
  java_iterator *iterator = (java_iterator *)iter;
  zval *presult;
  
  MAKE_STD_ZVAL(presult);
  ZVAL_NULL(presult);
  
  php_java_invoke("currentKey", iterator->java_iterator, 0, 0, 0, presult TSRMLS_CC);

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

static void init_current_data(java_iterator *iterator TSRMLS_DC) 
{
  MAKE_STD_ZVAL(iterator->current_object);
  ZVAL_NULL(iterator->current_object);

  php_java_invoke("currentData", iterator->java_iterator, 0, 0, 0, iterator->current_object TSRMLS_CC);
}

static void iterator_move_forward(zend_object_iterator *iter TSRMLS_DC)
{
  zval *presult;
  java_iterator *iterator = (java_iterator *)iter;
  proxyenv *jenv = JG(jenv);
  MAKE_STD_ZVAL(presult);
  ZVAL_NULL(presult);

  if (iterator->current_object) {
	zval_ptr_dtor((zval**)&iterator->current_object);
	iterator->current_object = NULL;
  }

  (*jenv)->writeInvokeBegin(jenv, iterator->java_iterator, "moveForward", 0, 'I', presult);
  (*jenv)->writeInvokeEnd(jenv);
  if(Z_BVAL_P(presult))
	init_current_data(iterator TSRMLS_CC);

  zval_ptr_dtor((zval**)&presult);
}

static zend_object_iterator_funcs java_iterator_funcs = {
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
  java_iterator *iterator = emalloc(sizeof *iterator);
  long java_iterator, obj;
  MAKE_STD_ZVAL(presult);
  ZVAL_NULL(presult);

  object->refcount++;
  iterator->intern.data = (void*)object;
  iterator->intern.funcs = &java_iterator_funcs;

  java_get_jobject_from_object(object, &obj TSRMLS_CC);
  assert(obj);

  (*jenv)->writeInvokeBegin(jenv, 0, "getPhpMap", 0, 'I', presult);
  (*jenv)->writeObject(jenv, obj);
  (*jenv)->writeInvokeEnd(jenv);
  java_get_jobject_from_object(presult, &java_iterator TSRMLS_CC);
  if (!java_iterator) return NULL;
  iterator->java_iterator = java_iterator;

  (*jenv)->writeInvokeBegin(jenv, java_iterator, "getType", 0, 'I', presult);
  (*jenv)->writeInvokeEnd(jenv);

  iterator->type = Z_BVAL_P(presult) ? HASH_KEY_IS_STRING : HASH_KEY_IS_LONG;

  (*jenv)->writeInvokeBegin(jenv, java_iterator, "hasMore", 0, 'I', presult);
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
php_java_call_function_handler4(INTERNAL_FUNCTION_PARAMETERS, zend_property_reference *property_reference)
{
  pval *object = property_reference->object;
  zend_overloaded_element *function_name = (zend_overloaded_element *)
    property_reference->elements_list->tail->data;
  char *name = Z_STRVAL(function_name->element);
  int arg_count = ZEND_NUM_ARGS();
  pval **arguments = (pval **) emalloc(sizeof(pval *)*arg_count);
  short createInstance;
  short constructor;
  short is_mono;

  getParametersArray(ht, arg_count, arguments);

  if(!strncmp("mono", name, 4) && arg_count>0) {
	is_mono=1;
	createInstance = strcmp("mono_class", name) && strcmp("monoclass", name);
	constructor = !strcmp("mono", name) || !createInstance;
  } else {
	is_mono=0;
	createInstance = strcmp("java_class", name) && strcmp("javaclass", name);
	constructor = !strcmp("java", name) || !createInstance;
  }
  php_java_call_function_handler(INTERNAL_FUNCTION_PARAM_PASSTHRU, 
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

  php_java_get_property_handler(name, object, &presult);

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

  result = php_java_set_property_handler(name, object, value, &dummy);

  pval_destructor(&property->element);
  return result;
}
#endif

PHP_MINIT_FUNCTION(java)
{
  zend_class_entry *parent;
#ifndef ZEND_ENGINE_2
  zend_class_entry ce;
  INIT_OVERLOADED_CLASS_ENTRY(ce, "java", NULL,
							  php_java_call_function_handler4,
							  get_property_handler,
							  set_property_handler);

  php_java_class_entry = zend_register_internal_class(&ce TSRMLS_CC);

  INIT_CLASS_ENTRY(ce, "java_class", NULL);
  parent = (zend_class_entry *) php_java_class_entry;
  zend_register_internal_class_ex(&ce, parent, NULL TSRMLS_CC);
  INIT_CLASS_ENTRY(ce, "javaclass", NULL);
  parent = (zend_class_entry *) php_java_class_entry;
  zend_register_internal_class_ex(&ce, parent, NULL TSRMLS_CC);

  INIT_CLASS_ENTRY(ce, "mono", NULL);
  parent = (zend_class_entry *) php_java_class_entry;
  zend_register_internal_class_ex(&ce, parent, NULL TSRMLS_CC);
  INIT_CLASS_ENTRY(ce, "mono_class", NULL);
  parent = (zend_class_entry *) php_java_class_entry;
  zend_register_internal_class_ex(&ce, parent, NULL TSRMLS_CC);
  INIT_CLASS_ENTRY(ce, "monoclass", NULL);
  parent = (zend_class_entry *) php_java_class_entry;
  zend_register_internal_class_ex(&ce, parent, NULL TSRMLS_CC);
#else
  zend_class_entry ce;
  zend_internal_function call, get, set;
  
  make_lambda(&call, ZEND_FN(java___call));
  make_lambda(&get, ZEND_FN(java___get));
  make_lambda(&set, ZEND_FN(java___set));
  
  INIT_OVERLOADED_CLASS_ENTRY(ce, "java", 
							  php_java_class_functions, 
							  (zend_function*)&call, 
							  (zend_function*)&get, 
							  (zend_function*)&set);

  memcpy(&php_java_handlers, zend_get_std_object_handlers(), sizeof php_java_handlers);
  //php_java_handlers.clone_obj = clone;
  php_java_handlers.cast_object = cast;

  php_java_class_entry =
	zend_register_internal_class(&ce TSRMLS_CC);
  php_java_class_entry->get_iterator = get_iterator;
  php_java_class_entry->create_object = create_object;
  zend_class_implements(php_java_class_entry TSRMLS_CC, 1, zend_ce_arrayaccess);

  INIT_OVERLOADED_CLASS_ENTRY(ce, "java_exception", 
							  php_java_class_functions, 
							  (zend_function*)&call, 
							  (zend_function*)&get, 
							  (zend_function*)&set);
  
  parent = (zend_class_entry *) zend_exception_get_default();
  php_java_exception_class_entry =
	zend_register_internal_class_ex(&ce, parent, NULL TSRMLS_CC);
  // only cast and clone; no iterator, no array access
  php_java_exception_class_entry->create_object = create_exception_object;
  
  INIT_CLASS_ENTRY(ce, "java_class", php_java_class_functions);
  parent = (zend_class_entry *) php_java_class_entry;

  php_java_class_class_entry = 
	zend_register_internal_class_ex(&ce, parent, NULL TSRMLS_CC);

  /* compatibility with the jsr implementation */
  INIT_CLASS_ENTRY(ce, "javaclass", php_java_class_functions);
  parent = (zend_class_entry *) php_java_class_entry;
  zend_register_internal_class_ex(&ce, parent, NULL TSRMLS_CC);

  INIT_CLASS_ENTRY(ce, "javaexception", php_java_class_functions);
  parent = (zend_class_entry *) php_java_exception_class_entry;
  php_java_exception_class_entry = 
	zend_register_internal_class_ex(&ce, parent, NULL TSRMLS_CC);

  INIT_CLASS_ENTRY(ce, "mono", php_java_class_functions);
  parent = (zend_class_entry *) php_java_class_entry;
  zend_register_internal_class_ex(&ce, parent, NULL TSRMLS_CC);

  INIT_CLASS_ENTRY(ce, "monoclass", php_java_class_functions);
  parent = (zend_class_entry *) php_java_class_entry;
  zend_register_internal_class_ex(&ce, parent, NULL TSRMLS_CC);

#endif
  
  ZEND_INIT_MODULE_GLOBALS(java, php_java_alloc_globals_ctor, NULL);
  
  assert(!cfg);
  if(!cfg) cfg = malloc(sizeof *cfg); if(!cfg) exit(9);
  if(REGISTER_INI_ENTRIES()==SUCCESS) {
	/* set the default values for all undefined */
	extern void java_init_cfg();
	
	java_init_cfg();
#ifndef CFG_JAVA_SOCKET_INET
	cfg->saddr.sun_family = AF_LOCAL;
	memset(cfg->saddr.sun_path, 0, sizeof cfg->saddr.sun_path);
	strcpy(cfg->saddr.sun_path, cfg->sockname);
# ifdef HAVE_ABSTRACT_NAMESPACE
	*cfg->saddr.sun_path=0;
# endif
#else
	cfg->saddr.sin_family = AF_INET;
	cfg->saddr.sin_port=htons(atoi(cfg->sockname));
	cfg->saddr.sin_addr.s_addr = inet_addr( "127.0.0.1" );
#endif
  }
  java_start_server();
  
  assert(!java_ini_last_updated);
  java_ini_last_updated=java_ini_updated;
  java_ini_updated=0;
  
  return SUCCESS;
}
PHP_MINFO_FUNCTION(java)
{
  char*s=java_get_server_string();
  char*server = java_test_server(0, 0);
  
  php_info_print_table_start();
  php_info_print_table_row(2, "java support", "Enabled");
  php_info_print_table_row(2, "java bridge", java_bridge_version);
  php_info_print_table_row(2, "java.libpath", cfg->ld_library_path);
  php_info_print_table_row(2, "java.classpath", cfg->classpath);
  php_info_print_table_row(2, "java.java_home", cfg->java_home);
  php_info_print_table_row(2, "java.java", cfg->java);
  if(strlen(cfg->logFile)==0) 
	php_info_print_table_row(2, "java.log_file", "<stdout>");
  else
	php_info_print_table_row(2, "java.log_file", cfg->logFile);
  php_info_print_table_row(2, "java.log_level", cfg->logLevel);
  php_info_print_table_row(2, "java.hosts", cfg->hosts);
  php_info_print_table_row(2, "java command", s);
  php_info_print_table_row(2, "java status", server?"running":"not running");
  php_info_print_table_row(2, "java server", server?server:"localhost");
  php_info_print_table_end();
  
  free(server);
  free(s);
}

PHP_MSHUTDOWN_FUNCTION(java) 
{
  extern void php_java_shutdown_library();
  extern void java_destroy_cfg(int);
  
  java_destroy_cfg(java_ini_last_updated);
  java_ini_last_updated=0;

  UNREGISTER_INI_ENTRIES();
  php_java_shutdown_library();

  assert(cfg);
  if(cfg) { free(cfg); cfg = 0; }

  return SUCCESS;
}

#ifndef PHP_WRAPPER_H
#error must include php_wrapper.h
#endif
