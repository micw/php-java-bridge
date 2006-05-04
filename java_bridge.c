/*-*- mode: C; tab-width:4 -*-*/

#include <stdlib.h>
#include "php_java.h"
#include "java_bridge.h"

/* miscellaneous */
#include <stdio.h>

/* strings */
#include <string.h>

#ifdef ZEND_ENGINE_2
#include "zend_exceptions.h"
#endif

static void writeArgument(pval* arg, short ignoreNonJava TSRMLS_DC);
static void writeArguments(int argc, pval** argv, short ignoreNonJava TSRMLS_DC);

EXT_EXTERN_MODULE_GLOBALS(EXT)

static short checkError(pval *value TSRMLS_DC)
{
#ifndef ZEND_ENGINE_2
  if (Z_TYPE_P(value) == IS_EXCEPTION) {

	/* display the exception only if we do
	   not abort a callback or if we abort
	   a callback and this callback is not
	   a method.  This is consistent with
	   PHP5 behaviour, where we use
	   call_user_func_array (which also
	   reports the exception) when the
	   callback is not a method. */
    if(!JG(exception)||(JG(exception)&&!(JG(object)&&*JG(object))))
	   php_error(E_WARNING, "%s", Z_STRVAL_P(value));

	efree(Z_STRVAL_P(value));
    ZVAL_FALSE(value);
    return 1;
  };
#endif
  return 0;
}

static short is_type (zval *pobj TSRMLS_DC) {
#ifdef ZEND_ENGINE_2
  zend_class_entry *ce = Z_OBJCE_P(pobj);
  return instanceof_function(ce, EXT_GLOBAL(class_entry) TSRMLS_CC) ||
	instanceof_function(ce, EXT_GLOBAL(exception_class_entry) TSRMLS_CC);
#else
  extern void EXT_GLOBAL(call_function_handler4)(INTERNAL_FUNCTION_PARAMETERS, zend_property_reference *property_reference);
  return pobj->type == IS_OBJECT && pobj->value.obj.ce->handle_function_call==EXT_GLOBAL(call_function_handler4);
#endif
}

#ifdef ZEND_ENGINE_2
struct java_object {
  zend_object parent;
  long id;
};
void EXT_GLOBAL(store_jobject)(zval*object, long id TSRMLS_DC)
{
  struct java_object*jobject = (struct java_object*)zend_objects_get_address(object TSRMLS_CC);
  assert(id!=0);
  jobject->id = id;
}

int EXT_GLOBAL(get_jobject_from_object)(zval*object, long *id TSRMLS_DC)
{
  if(is_type(object TSRMLS_CC)) {
	struct java_object*jobject = (struct java_object*)zend_objects_get_address(object TSRMLS_CC);
	*id = jobject->id;
	return *id!=0;
  }
  *id=0;
  return 0;
}
static void destroy_object(void *object, zend_object_handle handle TSRMLS_DC)
{
  struct java_object*jobject = ((struct java_object*)object);
  if(JG(jenv)&&jobject->id) (*JG(jenv))->writeUnref(JG(jenv), jobject->id);
  jobject->id=0;
}
static void free_object(zend_object *object TSRMLS_DC) 
{
  ((struct java_object*)object)->id=0;
  zend_hash_destroy(object->properties);
  FREE_HASHTABLE(object->properties);
  efree(object);
}
static zend_object_value objects_new(struct java_object **object, zend_class_entry *class_type TSRMLS_DC)
{	
	zend_object_value retval;

	*object = emalloc(sizeof(struct java_object));
	memset(*object, 0, sizeof(struct java_object));
	(*object)->parent.ce = class_type;
	retval.handle = zend_objects_store_put(*object, (zend_objects_store_dtor_t) destroy_object, (zend_objects_free_object_storage_t) free_object, NULL TSRMLS_CC);
	retval.handlers = (zend_object_handlers*)&EXT_GLOBAL(handlers);
	return retval;
}
zend_object_value EXT_GLOBAL(create_object)(zend_class_entry *class_type TSRMLS_DC)
{
  zval tmp;
  zend_object_value obj;
  struct java_object *object;
  
  obj = objects_new(&object, class_type TSRMLS_CC);
  
  ALLOC_HASHTABLE(object->parent.properties);
  zend_hash_init(object->parent.properties, 0, NULL, ZVAL_PTR_DTOR, 0);
  zend_hash_copy(object->parent.properties, &class_type->default_properties, (copy_ctor_func_t) zval_add_ref, (void *) &tmp, sizeof(zval *));

  return obj;
}
zend_object_value EXT_GLOBAL(create_exception_object)(zend_class_entry *class_type TSRMLS_DC)
{
  zval tmp;
  zend_object_value obj;
  struct java_object *object;

  zend_object *temp_exception_object;

  obj = objects_new(&object, class_type TSRMLS_CC);

  ALLOC_HASHTABLE(object->parent.properties);
  zend_hash_init(object->parent.properties, 0, NULL, ZVAL_PTR_DTOR, 0);
  
  /* since the signature of zend_exception_get_default changes from day to day: */
  /* create a standard exception object */
  tmp.value.obj= zend_exception_get_default()->create_object(class_type TSRMLS_CC);
  temp_exception_object=zend_objects_get_address(&tmp TSRMLS_CC);

  /* and copy the trace from there */
  zend_hash_copy(object->parent.properties, 
				 temp_exception_object->properties, (copy_ctor_func_t) zval_add_ref, (void *) &tmp, sizeof(zval *));
  return obj;
}  
#else
int EXT_GLOBAL(get_jobject_from_object)(pval*object, long *obj TSRMLS_DC)
{
  pval **handle;
  int n=-1;

  if(is_type(object TSRMLS_CC))
	n = zend_hash_index_find(Z_OBJPROP_P(object), 0, (void**) &handle);
  if(n==-1) { *obj=0; return 0; }

  *obj=**(long**)handle;
  return 1;
}
#endif

void EXT_GLOBAL(result) (pval* arg, short ignoreNonJava, pval*presult TSRMLS_DC) {
  proxyenv *jenv = EXT_GLOBAL(connect_to_server)(TSRMLS_C);
  (*jenv)->writeResultBegin(jenv, presult);
  if(arg)
	writeArgument(arg, ignoreNonJava TSRMLS_CC);
  else
	(*jenv)->writeObject(jenv, 0);
  (*jenv)->writeResultEnd(jenv);
}

void EXT_GLOBAL(invoke)(char*name, long object, int arg_count, zval**arguments, short ignoreNonJava, pval*presult TSRMLS_DC) 
{
  proxyenv *jenv = EXT_GLOBAL(connect_to_server)(TSRMLS_C);

  (*jenv)->writeInvokeBegin(jenv, object, name, 0, 'I', (void*)presult);
  writeArguments(arg_count, arguments, ignoreNonJava TSRMLS_CC);
  (*jenv)->writeInvokeEnd(jenv);
}

void EXT_GLOBAL(call_function_handler)(INTERNAL_FUNCTION_PARAMETERS, char*name, enum constructor constructor, short createInstance, pval *object, int arg_count, zval**arguments)
{
  long result = 0;
  proxyenv *jenv;

  if (constructor) {
    /* construct a Java object:
       First argument is the class name.  Any additional arguments will
       be treated as constructor parameters. */

    result = (long)object;

    if (ZEND_NUM_ARGS() < 1) {
      php_error(E_ERROR, "Missing classname in new "/**/EXT_NAME()/**/" call");
      return;
    }
	
#if EXTENSION == JAVA
	/* create a new vm object */
	jenv = EXT_GLOBAL(connect_to_server)(TSRMLS_C);
	if(!jenv) {ZVAL_NULL(object); return;}
	
	(*jenv)->writeCreateObjectBegin(jenv, Z_STRVAL_P(arguments[0]), Z_STRLEN_P(arguments[0]), createInstance?'I':'C', (void*)result);
	writeArguments(--arg_count, ++arguments, 0 TSRMLS_CC);
	(*jenv)->writeCreateObjectEnd(jenv);
#elif EXTENSION == MONO
	/* create a new vm object, prepend .cli */
	char *cname, *mname;
	size_t clen;
	jenv = EXT_GLOBAL(connect_to_server)(TSRMLS_C);
	if(!jenv) {ZVAL_NULL(object); return;}
	
	cname = Z_STRVAL_P(arguments[0]);
	clen = Z_STRLEN_P(arguments[0]);
	mname = emalloc(clen+5);
	assert(mname); if(!mname) {ZVAL_NULL(object); return;}
	strcpy(mname, "cli.");
	memcpy(mname+4,cname, clen);
	mname[clen+4] = 0;
	(*jenv)->writeCreateObjectBegin(jenv, mname, clen+4, createInstance?'I':'C', (void*)result);
	writeArguments(--arg_count, ++arguments, 0 TSRMLS_CC);
	(*jenv)->writeCreateObjectEnd(jenv);
	efree(mname);
#endif
  } else {

    long obj;

	jenv = EXT_GLOBAL(connect_to_server)(TSRMLS_C);
	if(!jenv) {ZVAL_NULL(object); return;}

	EXT_GLOBAL(get_jobject_from_object)(object, &obj TSRMLS_CC);
	assert(obj);

    result = (long)return_value;
    /* invoke a method on the given object */
	(*jenv)->writeInvokeBegin(jenv, obj, name, 0, 'I', (void*)result);
	writeArguments(arg_count, arguments, 0 TSRMLS_CC);
	(*jenv)->writeInvokeEnd(jenv);
  }
  checkError((pval*)result TSRMLS_CC);
}

static void writeArgument(pval* arg, short ignoreNonJava TSRMLS_DC)
{
  proxyenv *jenv = JG(jenv);
  long result;

  switch (Z_TYPE_P(arg)) {
    case IS_STRING:
      (*jenv)->writeString(jenv, Z_STRVAL_P(arg), Z_STRLEN_P(arg));
      break;

    case IS_OBJECT:
	  EXT_GLOBAL(get_jobject_from_object)(arg, &result TSRMLS_CC);
	  if(!ignoreNonJava && !result) 
		php_error(E_WARNING, "Argument is not (or does not contain) Java object(s).");
	  (*jenv)->writeObject(jenv, result);
      break;

    case IS_BOOL:
      (*jenv)->writeBoolean(jenv, Z_LVAL_P(arg));
      break;

    case IS_LONG:
	  (*jenv)->writeLong(jenv, Z_LVAL_P(arg));
      break;

    case IS_DOUBLE:
	  (*jenv)->writeDouble(jenv, Z_DVAL_P(arg));
      break;

    case IS_ARRAY:
      {
      zval **value;
      char *string_key;
      ulong num_key;
	  short wrote_begin=0;

      /* Iterate through hash */
      zend_hash_internal_pointer_reset(Z_ARRVAL_P(arg));
      while(zend_hash_get_current_data(Z_ARRVAL_P(arg), (void**)&value) == SUCCESS) {
        switch (zend_hash_get_current_key(Z_ARRVAL_P(arg), &string_key, &num_key, 0)) {
          case HASH_KEY_IS_STRING:
			if(!wrote_begin) { 
			  wrote_begin=1; 
			  (*jenv)->writeCompositeBegin_h(jenv); 
			}
			(*jenv)->writePairBegin_s(jenv, string_key, strlen(string_key));
			writeArgument(*value, ignoreNonJava TSRMLS_CC);
			(*jenv)->writePairEnd(jenv);
            break;
          case HASH_KEY_IS_LONG:
			if(!wrote_begin) { 
			  wrote_begin=1; 
			  (*jenv)->writeCompositeBegin_h(jenv); 
			}
			(*jenv)->writePairBegin_n(jenv, num_key);
			writeArgument(*value, ignoreNonJava TSRMLS_CC);
			(*jenv)->writePairEnd(jenv);
            break;
          default: /* HASH_KEY_NON_EXISTANT */
			if(!wrote_begin) { 
			  wrote_begin=1; 
			  (*jenv)->writeCompositeBegin_a(jenv); 
			}
			(*jenv)->writePairBegin(jenv);
			writeArgument(*value, ignoreNonJava TSRMLS_CC);
			(*jenv)->writePairEnd(jenv);
        }
        zend_hash_move_forward(Z_ARRVAL_P(arg));
      }
	  if(!wrote_begin) (*jenv)->writeCompositeBegin_a(jenv); 
	  (*jenv)->writeCompositeEnd(jenv);
      break;
      }
  default:
	(*jenv)->writeObject(jenv, 0);
  }
}

static void writeArguments(int argc, pval** argv, short ignoreNonJava TSRMLS_DC)
{
  int i;

  for (i=0; i<argc; i++) {
    writeArgument(argv[i], ignoreNonJava TSRMLS_CC);
  }
}

/**
 * get_property_handler
 */
short EXT_GLOBAL(get_property_handler)(char*name, zval *object, zval *presult)
{
  long obj;
  proxyenv *jenv;

  TSRMLS_FETCH();

  jenv = EXT_GLOBAL(connect_to_server)(TSRMLS_C);
  if(!jenv) {ZVAL_NULL(presult); return FAILURE;}

  /* get the object */
  EXT_GLOBAL(get_jobject_from_object)(object, &obj TSRMLS_CC);

  ZVAL_NULL(presult);

  if (!obj) {
    php_error(E_ERROR,
      "Attempt to access a Java property on a non-Java object");
  } else {
    /* invoke the method */
	(*jenv)->writeInvokeBegin(jenv, obj, name, 0, 'P', (void*)presult);
	(*jenv)->writeInvokeEnd(jenv);
  }
  return checkError(presult TSRMLS_CC) ? FAILURE : SUCCESS;
}


/**
 * set_property_handler
 */
short EXT_GLOBAL(set_property_handler)(char*name, zval *object, zval *value, zval *presult)
{
  long obj;
  proxyenv *jenv;

  TSRMLS_FETCH();

  jenv = EXT_GLOBAL(connect_to_server)(TSRMLS_C);
  if(!jenv) {ZVAL_NULL(presult); return FAILURE; }

  /* get the object */
  EXT_GLOBAL(get_jobject_from_object)(object, &obj TSRMLS_CC);

  ZVAL_NULL(presult);

  if (!obj) {
    php_error(E_ERROR,
      "Attempt to access a Java property on a non-Java object");
  } else {
    /* invoke the method */
	(*jenv)->writeInvokeBegin(jenv, obj, name, 0, 'P', (void*)presult);
	writeArgument(value, 0 TSRMLS_CC);
	(*jenv)->writeInvokeEnd(jenv);
  }
  return checkError(presult TSRMLS_CC) ? FAILURE : SUCCESS;
}

#ifndef PHP_WRAPPER_H
#error must include php_wrapper.h
#endif
