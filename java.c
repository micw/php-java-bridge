/*-*- mode: C; tab-width:4 -*-*/

/* wait */
#include <sys/types.h>
#include <sys/wait.h>
/* miscellaneous */
#include <stdio.h>
#include <stdlib.h>
#include <assert.h>
#include <errno.h>

#include "php.h"
#include "php_globals.h"
#include "ext/standard/info.h"

#include "php_java.h"
#include "java_bridge.h"
#include "protocol.h"

#ifdef ZEND_ENGINE_2
#include "zend_interfaces.h"
#endif

ZEND_DECLARE_MODULE_GLOBALS(java)

PHP_RINIT_FUNCTION(java) 
{
    extern int java_connect_to_server(struct cfg*cfg TSRMLS_DC);
	assert(!JG(jenv));
	java_connect_to_server(&JG(cfg) TSRMLS_CC);
	return SUCCESS;
}
PHP_RSHUTDOWN_FUNCTION(java)
{
  if (JG(php_reflect)) (*JG(jenv))->DeleteGlobalRef(JG(jenv), JG(php_reflect));
  if(JG(jenv)&&*JG(jenv)&&(*JG(jenv))->peer) SFCLOSE((*JG(jenv))->peer);
  if(JG(jenv)&&*JG(jenv)) free(*JG(jenv));
  if(JG(jenv)) free(JG(jenv));

  JG(php_reflect) = NULL;
  JG(jenv) = NULL;
  return SUCCESS;
}

PHP_FUNCTION(java_last_exception_get)
{
  jlong result = 0;

  if (ZEND_NUM_ARGS()!=0) WRONG_PARAM_COUNT;

  result = (jlong)(long)return_value;
  
  if(!JG(jenv)) {
	php_error(E_ERROR, "java not initialized");
	return;
  }

  (*JG(jenv))->LastException(JG(jenv), JG(php_reflect), 
							 JG(lastEx), result);
}

PHP_FUNCTION(java_last_exception_clear)
{
  jlong result = 0;

  if (ZEND_NUM_ARGS()!=0) WRONG_PARAM_COUNT;

  result = (jlong)(long)return_value;
  
  if(!JG(jenv)) {
	php_error(E_ERROR, "java not initialized");
	return;
  }

  (*JG(jenv))->CallVoidMethod(1, JG(jenv), JG(php_reflect), 
							  JG(clearEx));
}

PHP_FUNCTION(java_set_library_path)
{
  zval **path;
  jlong result = 0;
  jstring p;

  if (ZEND_NUM_ARGS()!=1 || zend_get_parameters_ex(1, &path) == FAILURE) 
	WRONG_PARAM_COUNT;

  convert_to_string_ex(path);

  result = (jlong)(long)return_value;
  
  if(!JG(jenv)) {
	php_error(E_ERROR, "java not initialized");
	return;
  }

  p = (*JG(jenv))->NewStringUTF(JG(jenv), Z_STRVAL_PP(path));
  (*JG(jenv))->CallVoidMethod(2, JG(jenv), JG(php_reflect), 
							  JG(setJarPath), p);
}

static short check_type (zval *pobj, zend_class_entry *class) {
#ifdef ZEND_ENGINE_2
  if (zend_get_class_entry(pobj) != php_java_class_entry)
	return 0;
  else
#endif
	return 1;
}

PHP_FUNCTION(java_instanceof)
{
  zval **pobj, **pclass;
  jobject obj, class;
  jboolean result;

  if (ZEND_NUM_ARGS()!=2 || zend_get_parameters_ex(2, &pobj, &pclass) == FAILURE) 
	WRONG_PARAM_COUNT;

  convert_to_object_ex(pobj);
  convert_to_object_ex(pclass);

  if(!JG(jenv)) {
	php_error(E_ERROR, "java not initialized");
	return;
  }

  obj = NULL;
  if((Z_TYPE_PP(pobj) == IS_OBJECT) && check_type(*pobj, php_java_class_entry)){
	java_get_jobject_from_object(*pobj, &obj);
  }
  if(!obj) {
	zend_error(E_WARNING, "Parameter #1 for %s() must be a java object", get_active_function_name(TSRMLS_C));
	return;
  }

  class = NULL;
  if((Z_TYPE_PP(pclass) == IS_OBJECT) && check_type(*pclass, php_java_class_entry)){
	java_get_jobject_from_object(*pclass, &class);
  }
  if(!class) {
	zend_error(E_WARNING, "Parameter #2 for %s() must be a java object", get_active_function_name(TSRMLS_C));
	return;
  }

  result = (*JG(jenv))->IsInstanceOf(JG(jenv), obj, class);
  if(result == JNI_TRUE) {
	RETURN_TRUE;
  } else {
	RETURN_FALSE;
  }
}

function_entry java_functions[] = {
	PHP_FE(java_last_exception_get, NULL)
	PHP_FE(java_last_exception_clear, NULL)
	PHP_FE(java_set_library_path, NULL)
	PHP_FE(java_instanceof, NULL)
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

int  le_jobject;
int java_ini_updated, java_ini_last_updated;
zend_class_entry *php_java_class_entry, *php_java_exception_class_entry;
#ifdef ZEND_ENGINE_2
zend_object_handlers php_java_handlers;
#endif

static PHP_INI_MH(OnIniSockname)
{
	if (new_value) {
	  JG(cfg).sockname=new_value;
	  java_ini_updated|=U_SOCKNAME;
	}
	return SUCCESS;
}
static PHP_INI_MH(OnIniClassPath)
{
	if (new_value) {
	  JG(cfg).classpath =new_value;
	  java_ini_updated|=U_CLASSPATH;
	}
	return SUCCESS;
}
static PHP_INI_MH(OnIniLibPath)
{
	if (new_value) {
	  JG(cfg).ld_library_path = new_value;
	  java_ini_updated|=U_LIBRARY_PATH;
	}
	return SUCCESS;
}
static PHP_INI_MH(OnIniJava)
{
  if (new_value) {
	JG(cfg).java = new_value;
	java_ini_updated|=U_JAVA;
  }
  return SUCCESS;
}
static PHP_INI_MH(OnIniJavaHome)
{
	if (new_value) {
	  JG(cfg).java_home = new_value;
	  java_ini_updated|=U_JAVA_HOME;
	}
	return SUCCESS;
}
static PHP_INI_MH(OnIniLogLevel)
{
	if (new_value) {
	  JG(cfg).logLevel = new_value;
	  java_ini_updated|=U_LOGLEVEL;
	}
	return SUCCESS;
}
static PHP_INI_MH(OnIniLogFile)
{
	if (new_value) {
	  JG(cfg).logFile = new_value;
	  java_ini_updated|=U_LOGFILE;
	}
	return SUCCESS;
}
PHP_INI_BEGIN()
	 PHP_INI_ENTRY("java.socketname", NULL, PHP_INI_SYSTEM, OnIniSockname)
	 PHP_INI_ENTRY("java.classpath", NULL, PHP_INI_SYSTEM, OnIniClassPath)
	 PHP_INI_ENTRY("java.libpath",   NULL, PHP_INI_SYSTEM, OnIniLibPath)
	 PHP_INI_ENTRY("java.java",   NULL, PHP_INI_SYSTEM, OnIniJava)
	 PHP_INI_ENTRY("java.java_home",   NULL, PHP_INI_SYSTEM, OnIniJavaHome)

	 PHP_INI_ENTRY("java.log_level",   NULL, PHP_INI_SYSTEM, OnIniLogLevel)
	 PHP_INI_ENTRY("java.log_file",   NULL, PHP_INI_SYSTEM, OnIniLogFile)
PHP_INI_END()


static void php_java_alloc_globals_ctor(zend_java_globals *java_globals TSRMLS_DC)
{
  java_globals->php_reflect=0;
  java_globals->jenv=0;
  java_globals->reflect_class=0;
}

static void init_server()
{
  extern int java_test_server(struct cfg*cfg TSRMLS_DC);
  extern void java_start_server(struct cfg*cfg TSRMLS_DC);

  if(java_test_server(&JG(cfg) TSRMLS_CC)==FAILURE) 
	java_start_server(&JG(cfg) TSRMLS_CC);
  java_test_server(&JG(cfg) TSRMLS_CC);
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
	php_java_call_function_handler(INTERNAL_FUNCTION_PARAM_PASSTHRU,
								   "tostring", 0, 0, getThis(), 0, NULL);
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
  zval **argv, **handle;
  int type;
  int argc = ZEND_NUM_ARGS();
  jobject obj;
  
  argv = (zval **) safe_emalloc(sizeof(zval *), argc, 0);
  if (zend_get_parameters_array(ht, argc, argv) == FAILURE) {
	php_error(E_ERROR, "Couldn't fetch arguments into array.");
	RETURN_NULL();
  }
  
  java_get_jobject_from_object(getThis(), &obj);
  assert(obj);
  if(JG(jenv))
	(*JG(jenv))->DeleteGlobalRef(JG(jenv), obj);
  else
	fputs("PHP bug, an object destructor was called after module shutdown", stderr);

  efree(argv);
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
  zval **argv;
  int argc;
  jobject obj, map;

  argc = ZEND_NUM_ARGS();
  argv = (zval **) safe_emalloc(sizeof(zval *), argc, 0);
  if (zend_get_parameters_array(ht, argc, argv) == FAILURE) {
	php_error(E_ERROR, "Couldn't fetch arguments into array.");
	RETURN_NULL();
  }
  java_get_jobject_from_object(getThis(), &obj);
  assert(obj);
  map = (*JG(jenv))->CallObjectMethod(1, JG(jenv), JG(php_reflect), JG(getPhpMap), obj);

  php_java_invoke("offsetExists", map, argc, argv, return_value);

}
PHP_METHOD(java, offsetGet)
{
  zval **argv;
  int argc;
  jobject obj, map;
  proxyenv *jenv =JG(jenv);

  argc = ZEND_NUM_ARGS();
  argv = (zval **) safe_emalloc(sizeof(zval *), argc, 0);
  if (zend_get_parameters_array(ht, argc, argv) == FAILURE) {
	php_error(E_ERROR, "Couldn't fetch arguments into array.");
	RETURN_NULL();
  }
  java_get_jobject_from_object(getThis(), &obj);
  assert(obj);
  BEGIN_TRANSACTION(jenv);
  map = (*JG(jenv))->CallObjectMethod(1, JG(jenv), JG(php_reflect), JG(getPhpMap), obj);

  php_java_invoke("offsetGet", map, argc, argv, return_value);
  END_TRANSACTION(jenv);
}

PHP_METHOD(java, offsetSet)
{
  zval **argv;
  int argc;
  jobject obj, map;
  proxyenv *jenv = JG(jenv);

  argc = ZEND_NUM_ARGS();
  argv = (zval **) safe_emalloc(sizeof(zval *), argc, 0);
  if (zend_get_parameters_array(ht, argc, argv) == FAILURE) {
	php_error(E_ERROR, "Couldn't fetch arguments into array.");
	RETURN_NULL();
  }
  java_get_jobject_from_object(getThis(), &obj);
  assert(obj);

  BEGIN_TRANSACTION(jenv);
  map = (*jenv)->CallObjectMethod(1, jenv, JG(php_reflect), JG(getPhpMap), obj);

  php_java_invoke("offsetSet", map, argc, argv, return_value);
  END_TRANSACTION(jenv);
}

PHP_METHOD(java, offsetUnset)
{
  zval **argv, **handle;
  int type, argc;
  jobject obj, map;
  proxyenv *jenv = JG(jenv);

  argc = ZEND_NUM_ARGS();
  argv = (zval **) safe_emalloc(sizeof(zval *), argc, 0);
  if (zend_get_parameters_array(ht, argc, argv) == FAILURE) {
	php_error(E_ERROR, "Couldn't fetch arguments into array.");
	RETURN_NULL();
  }
  java_get_jobject_from_object(getThis(), &obj);
  assert(obj);

  BEGIN_TRANSACTION(jenv);
  map = (*jenv)->CallObjectMethod(1, jenv, JG(php_reflect), JG(getPhpMap), obj);

  php_java_invoke("offsetUnset", map, argc, argv, return_value);
  END_TRANSACTION(jenv);
}

static
ZEND_BEGIN_ARG_INFO(arginfo_array_offsetGet, 0)
	 ZEND_ARG_INFO(0, index)
ZEND_END_ARG_INFO();

static
ZEND_BEGIN_ARG_INFO(arginfo_array_offsetSet, 0)
	 ZEND_ARG_INFO(0, index)
	 ZEND_ARG_INFO(0, newval)
ZEND_END_ARG_INFO();

static function_entry java_class_functions[] = {
  PHP_ME(java, java_class, NULL, 0)
  PHP_ME(java, java, NULL, 0)
  PHP_ME(java, __call, NULL, 0)
  PHP_ME(java, __tostring, NULL, 0)
  PHP_ME(java, __get, NULL, 0)
  PHP_ME(java, __set, NULL, 0)
  PHP_ME(java, __destruct, NULL, 0)
  PHP_ME(java, offsetExists,  arginfo_array_offsetGet, ZEND_ACC_PUBLIC)
  PHP_ME(java, offsetGet,     arginfo_array_offsetGet, ZEND_ACC_PUBLIC)
  PHP_ME(java, offsetSet,     arginfo_array_offsetSet, ZEND_ACC_PUBLIC)
  PHP_ME(java, offsetUnset,   arginfo_array_offsetGet, ZEND_ACC_PUBLIC)
};



static zend_object_value create_object(zend_class_entry *class_type TSRMLS_DC)
{
  /* standard initialization, copied from parent zend_API.c */
  zval *tmp;
  zend_object *object;
  zend_object_value obj = zend_objects_new(&object, class_type TSRMLS_CC);
  ALLOC_HASHTABLE_REL(object->properties);
  zend_hash_init(object->properties, 0, NULL, ZVAL_PTR_DTOR, 0);
  zend_hash_copy(object->properties, &class_type->default_properties, (copy_ctor_func_t) zval_add_ref, (void *) &tmp, sizeof(zval *));

  /* real work */
  obj.handlers = (zend_object_handlers*)&php_java_handlers;
  return obj;
}

static int cast(zval *readobj, zval *writeobj, int type, int should_free TSRMLS_DC)
{
  if (type==IS_STRING) {
	jobject obj;
	zval free_obj;

	if (should_free)
	  free_obj = *writeobj;

	java_get_jobject_from_object(readobj, &obj);
	assert(obj);
	php_java_invoke("tostring", obj,  0, 0, writeobj);
	if (should_free)
	  zval_dtor(&free_obj);
	return SUCCESS;
  }
  return FAILURE;
}


typedef struct {
  zend_object_iterator intern;
  jobject java_iterator;
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
	(*jenv)->DeleteGlobalRef(jenv, iterator->java_iterator);
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
  
  php_java_invoke("currentKey", iterator->java_iterator, 0, 0, presult);

  if(ZVAL_IS_NULL(presult)) {
	zval_ptr_dtor((zval**)&presult);
	return HASH_KEY_NON_EXISTANT;
  }

  if(iterator->type == HASH_KEY_IS_STRING) {
	*str_key_len = Z_STRLEN_P(presult);
	*str_key = estrndup(Z_STRVAL_P(presult), *str_key_len);
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
  object_init_ex(iterator->current_object, php_java_class_entry);
  iterator->current_object->is_ref=1;
  iterator->current_object->refcount=1;
  php_java_invoke("currentData", iterator->java_iterator, 0, 0, iterator->current_object);
}

static void iterator_move_forward(zend_object_iterator *iter TSRMLS_DC)
{
  java_iterator *iterator = (java_iterator *)iter;
  proxyenv *jenv = JG(jenv);
  if (iterator->current_object) {
	zval_ptr_dtor((zval**)&iterator->current_object);
	iterator->current_object = NULL;
  }
  BEGIN_TRANSACTION(jenv);
  if((*jenv)->CallObjectMethod(0, jenv, iterator->java_iterator, JG(moveForward)))
	init_current_data(iterator TSRMLS_CC);
  END_TRANSACTION(jenv);
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
  proxyenv *jenv = JG(jenv);
  java_iterator *iterator = emalloc(sizeof *iterator);

  jobject java_iterator, obj;

  object->refcount++;
  iterator->intern.data = (void*)object;
  iterator->intern.funcs = &java_iterator_funcs;

  java_get_jobject_from_object(object, &obj);
  assert(obj);

  BEGIN_TRANSACTION(jenv);
  java_iterator = (*jenv)->CallObjectMethod(1, jenv, JG(php_reflect), JG(getPhpMap), obj);
  if (!java_iterator) return NULL;

  java_iterator = (*jenv)->NewGlobalRef(jenv, java_iterator);
  assert(java_iterator);
  iterator->java_iterator = java_iterator;
  iterator->type = 
	(*jenv)->CallObjectMethod(0, jenv, java_iterator, JG(getType))
	? HASH_KEY_IS_STRING
	: HASH_KEY_IS_LONG;

  if((*jenv)->CallObjectMethod(0, jenv, java_iterator, JG(hasMore))) {
	init_current_data(iterator TSRMLS_CC);
  }
  END_TRANSACTION(jenv);

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

static void 
call_function_handler(INTERNAL_FUNCTION_PARAMETERS, zend_property_reference *property_reference)
{
  pval *object = property_reference->object;
  zend_overloaded_element *function_name = (zend_overloaded_element *)
    property_reference->elements_list->tail->data;
  char *name = Z_STRVAL(function_name->element);
  int arg_count = ZEND_NUM_ARGS();
  pval **arguments = (pval **) emalloc(sizeof(pval *)*arg_count);
  short createInstance = strcmp("java_class", name);
  short constructor = !strcmp("java", name) || !createInstance;

  getParametersArray(ht, arg_count, arguments);

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

  TSRMLS_FETCH();

  element = property_reference->elements_list->head;
  property=(zend_overloaded_element *)element->data;
  name =  Z_STRVAL(property->element);
  object = property_reference->object;

  php_java_get_property_handler(name, object, &presult TSRMLS_CC);

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

  TSRMLS_FETCH();

  element = property_reference->elements_list->head;
  property=(zend_overloaded_element *)element->data;
  name =  Z_STRVAL(property->element);
  object = property_reference->object;

  result = php_java_set_property_handler(name, object, value, &dummy TSRMLS_CC);

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
							  call_function_handler,
							  get_property_handler,
							  set_property_handler);

  php_java_class_entry = zend_register_internal_class(&ce TSRMLS_CC);

  INIT_CLASS_ENTRY(ce, "java_class", NULL);
  parent = (zend_class_entry *) php_java_class_entry;
  zend_register_internal_class_ex(&ce, parent, NULL TSRMLS_CC);
#else
  zend_class_entry ce;
  zend_internal_function call, get, set;
  
  make_lambda(&call, ZEND_FN(java___call));
  make_lambda(&get, ZEND_FN(java___get));
  make_lambda(&set, ZEND_FN(java___set));
  
  INIT_OVERLOADED_CLASS_ENTRY(ce, "java", 
							  java_class_functions, 
							  (zend_function*)&call, 
							  (zend_function*)&get, 
							  (zend_function*)&set);

  memcpy(&php_java_handlers, zend_get_std_object_handlers(), sizeof php_java_handlers);
  //php_java_handlers.clone_obj = clone;
  php_java_handlers.cast_object = cast;
  ce.get_iterator = get_iterator;
  ce.create_object = create_object;

  php_java_class_entry =
	zend_register_internal_class(&ce TSRMLS_CC);
  zend_class_implements(php_java_class_entry TSRMLS_CC, 1, zend_ce_arrayaccess);
  INIT_OVERLOADED_CLASS_ENTRY(ce, "java_exception", 
							  java_class_functions, 
							  (zend_function*)&call, 
							  (zend_function*)&get, 
							  (zend_function*)&set);
  
  parent = (zend_class_entry *) zend_exception_get_default();
  php_java_exception_class_entry =
	zend_register_internal_class_ex(&ce, parent, NULL TSRMLS_CC);
  
  INIT_CLASS_ENTRY(ce, "java_class", java_class_functions);
  parent = (zend_class_entry *) php_java_class_entry;

  zend_register_internal_class_ex(&ce, parent, NULL TSRMLS_CC);
#endif
  
  /* Register the resource, with destructor (arg 1) and text
	 description (arg 3), the other arguments are just standard
	 placeholders */
  le_jobject = zend_register_list_destructors_ex(php_java_destructor, NULL, "java", module_number);
  
  ZEND_INIT_MODULE_GLOBALS(java, php_java_alloc_globals_ctor, NULL);
  
  if(REGISTER_INI_ENTRIES()==SUCCESS) {
	/* set the default values for all undefined */
	extern void java_init_cfg(struct cfg *cfg);
	
	java_init_cfg(&JG(cfg));
#ifndef CFG_JAVA_SOCKET_INET
	JG(cfg).saddr.sun_family = AF_LOCAL;
	memset(JG(cfg).saddr.sun_path, 0, sizeof JG(cfg).saddr.sun_path);
	strcpy(JG(cfg).saddr.sun_path, JG(cfg).sockname);
#else
	JG(cfg).saddr.sin_family = AF_INET;
	JG(cfg).saddr.sin_port=htons(atoi(JG(cfg).sockname));
	JG(cfg).saddr.sin_addr.s_addr = inet_addr( "127.0.0.1" );
#endif
  }
  init_server();
  
  assert(!java_ini_last_updated);
  java_ini_last_updated=java_ini_updated;
  java_ini_updated=0;
  
  return SUCCESS;
}
static char*get_server_args(struct cfg*cfg) {
  int i;
  char*s;
  char*env[N_SENV];
  char*args[N_SARGS];
  unsigned int length = 0;

  java_get_server_args(cfg, env, args);

  for(i=0; i< (sizeof env)/(sizeof*env); i++) {
	if(!env[i]) break;
	length+=strlen(env[i])+1;
  }
  for(i=0; i< (sizeof args)/(sizeof*args); i++) {
	size_t l;
	if(!args[i]) break;
	l=strlen(args[i]);
	length+=(l?l:2)+1;
  }
  s=malloc(length+1);
  assert(s);
  *s=0;
  for(i=0; i< (sizeof env)/(sizeof*env); i++) {
	if(!env[i]) break;
	strcat(s, env[i]); strcat(s, " ");
	free(env[i]);
  }
  for(i=0; i< (sizeof args)/(sizeof*args); i++) {
	if(!args[i]) break;
	if(!strlen(args[i])) strcat(s,"'");
	strcat(s, args[i]);
	if(!strlen(args[i])) strcat(s,"'");
	strcat(s, " ");
	free(args[i]);
  }
  s[length]=0;
  return s;
}
PHP_MINFO_FUNCTION(java)
{
  extern int java_test_server(struct cfg*cfg TSRMLS_DC);
  char*s=get_server_args(&JG(cfg));
  int status = java_test_server(&JG(cfg) TSRMLS_CC);
  
  php_info_print_table_start();
  php_info_print_table_row(2, "java support", "Enabled");
  php_info_print_table_row(2, "java bridge", java_bridge_version);
#ifndef CFG_JAVA_SOCKET_ANON
  php_info_print_table_row(2, "java command", s);
#endif
  php_info_print_table_row(2, "java.libpath", JG(cfg).ld_library_path);
  php_info_print_table_row(2, "java.classpath", JG(cfg).classpath);
  php_info_print_table_row(2, "java.java_home", JG(cfg).java_home);
  php_info_print_table_row(2, "java.java", JG(cfg).java);
  if(strlen(JG(cfg).logFile)==0) 
	php_info_print_table_row(2, "java.log_file", "<stdout>");
  else
	php_info_print_table_row(2, "java.log_file", JG(cfg).logFile);
  php_info_print_table_row(2, "java.log_level", JG(cfg).logLevel);
  php_info_print_table_row(2, "java status", (status==SUCCESS)?"running":"not running");
  php_info_print_table_end();
  
  free(s);
}

PHP_MSHUTDOWN_FUNCTION(java) 
{
  extern void php_java_shutdown_library(struct cfg*cfg TSRMLS_DC);
  extern void java_destroy_cfg(int, struct cfg*cfg TSRMLS_DC);
  
  java_destroy_cfg(java_ini_last_updated, &JG(cfg) TSRMLS_CC);
  java_ini_last_updated=0;

  UNREGISTER_INI_ENTRIES();
  php_java_shutdown_library(&JG(cfg) TSRMLS_CC);
  return SUCCESS;
}
