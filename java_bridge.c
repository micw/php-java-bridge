/*-*- mode: C; tab-width:4 -*-*/

#include <stdlib.h>
#include "php_java.h"
#include "java_bridge.h"

/* miscellaneous */
#include <stdio.h>
#include <assert.h>

/* strings */
#include <string.h>


static void writeArgument(pval* arg TSRMLS_DC);
static void writeArguments(int argc, pval** argv TSRMLS_DC);

ZEND_EXTERN_MODULE_GLOBALS(java)

static short checkError(pval *value TSRMLS_DC)
{
#ifndef ZEND_ENGINE_2
  if (Z_TYPE_P(value) == IS_EXCEPTION) {
    php_error(E_WARNING, "%s", Z_STRVAL_P(value));
	efree(Z_STRVAL_P(value));
    ZVAL_FALSE(value);
    return 1;
  };
#endif
  return 0;
}

int java_get_jobject_from_object(pval*object, long *obj TSRMLS_DC)
{
  pval **handle;
  int type, n;

  n = zend_hash_index_find(Z_OBJPROP_P(object), 0, (void**) &handle);
  if(n==-1) { *obj=0; return 0; }

  *obj = (long)zend_list_find(Z_LVAL_PP(handle), &type);
  return type;
}

void php_java_invoke(char*name, long object, int arg_count, zval**arguments, pval*presult TSRMLS_DC) 
{
  proxyenv *jenv = java_connect_to_server(TSRMLS_C);

  (*jenv)->writeInvokeBegin(jenv, object, name, 0, 'I', (void*)presult);
  writeArguments(arg_count, arguments TSRMLS_CC);
  (*jenv)->writeInvokeEnd(jenv);
}

void php_java_call_function_handler(INTERNAL_FUNCTION_PARAMETERS, char*name, short constructor, short createInstance, pval *object, int arg_count, zval**arguments)
{
  long result = 0;
  proxyenv *jenv = java_connect_to_server(TSRMLS_C);
  if(!jenv) {ZVAL_NULL(object); return;}

  if (constructor) {
    /* construct a Java object:
       First argument is the class name.  Any additional arguments will
       be treated as constructor parameters. */

    result = (long)object;

    if (ZEND_NUM_ARGS() < 1) {
      php_error(E_ERROR, "Missing classname in new java() call");
      return;
    }

	/* create a new object */
	(*jenv)->writeCreateObjectBegin(jenv, Z_STRVAL_P(arguments[0]), Z_STRLEN_P(arguments[0]), createInstance?'I':'C', (void*)result);
	writeArguments(--arg_count, ++arguments TSRMLS_CC);
	(*jenv)->writeCreateObjectEnd(jenv);

  } else {

    long obj;

	java_get_jobject_from_object(object, &obj TSRMLS_CC);
	assert(obj);

    result = (long)return_value;
    /* invoke a method on the given object */
	(*jenv)->writeInvokeBegin(jenv, obj, name, 0, 'I', (void*)result);
	writeArguments(arg_count, arguments TSRMLS_CC);
	(*jenv)->writeInvokeEnd(jenv);
  }
  checkError((pval*)result TSRMLS_CC);
}

static void writeArgument(pval* arg TSRMLS_DC)
{
  proxyenv *jenv = JG(jenv);
  long result;

  switch (Z_TYPE_P(arg)) {
    case IS_STRING:
      (*jenv)->writeString(jenv, Z_STRVAL_P(arg), Z_STRLEN_P(arg));
      break;

    case IS_OBJECT:
	  java_get_jobject_from_object(arg, &result TSRMLS_CC);
	  assert(result);
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
			writeArgument(*value TSRMLS_CC);
			(*jenv)->writePairEnd(jenv);
            break;
          case HASH_KEY_IS_LONG:
			if(!wrote_begin) { 
			  wrote_begin=1; 
			  (*jenv)->writeCompositeBegin_h(jenv); 
			}
			(*jenv)->writePairBegin_n(jenv, num_key);
			writeArgument(*value TSRMLS_CC);
			(*jenv)->writePairEnd(jenv);
            break;
          default: /* HASH_KEY_NON_EXISTANT */
			if(!wrote_begin) { 
			  wrote_begin=1; 
			  (*jenv)->writeCompositeBegin_a(jenv); 
			}
			(*jenv)->writePairBegin(jenv);
			writeArgument(*value TSRMLS_CC);
			(*jenv)->writePairEnd(jenv);
        }
        zend_hash_move_forward(Z_ARRVAL_P(arg));
      }
	  if(wrote_begin) (*jenv)->writeCompositeEnd(jenv);
      break;
      }
  default:
	(*jenv)->writeObject(jenv, 0);
  }
}

static void writeArguments(int argc, pval** argv TSRMLS_DC)
{
  int i;

  for (i=0; i<argc; i++) {
    writeArgument(argv[i] TSRMLS_CC);
  }
}

/**
 * php_java_get_property_handler
 */
short php_java_get_property_handler(char*name, zval *object, zval *presult)
{
  long obj;
  int type;
  proxyenv *jenv;

  TSRMLS_FETCH();

  jenv = java_connect_to_server(TSRMLS_C);
  if(!jenv) {ZVAL_NULL(presult); return FAILURE;}

  /* get the object */
  type = java_get_jobject_from_object(object, &obj TSRMLS_CC);

  ZVAL_NULL(presult);

  if (!obj || (type!=le_jobject)) {
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
 * php_java_set_property_handler
 */
short php_java_set_property_handler(char*name, zval *object, zval *value, zval *presult)
{
  long obj;
  int type;
  proxyenv *jenv;

  TSRMLS_FETCH();

  jenv = java_connect_to_server(TSRMLS_C);
  if(!jenv) {ZVAL_NULL(presult); return FAILURE; }

  /* get the object */
  type = java_get_jobject_from_object(object, &obj TSRMLS_CC);

  ZVAL_NULL(presult);

  if (!obj || (type!=le_jobject)) {
    php_error(E_ERROR,
      "Attempt to access a Java property on a non-Java object");
  } else {
    /* invoke the method */
	(*jenv)->writeInvokeBegin(jenv, obj, name, 0, 'P', (void*)presult);
	writeArgument(value TSRMLS_CC);
	(*jenv)->writeInvokeEnd(jenv);
  }
  return checkError(presult TSRMLS_CC) ? FAILURE : SUCCESS;
}

/*
 * delete the object we've allocated during setResultFromObject
 */
void php_java_destructor(zend_rsrc_list_entry *rsrc TSRMLS_DC)
{
  // Disabled. In PHP 4 this was called *after* connection shutdown,
  // which is much too late.  The server part now does its own
  // resource tracking.
/* 	void *jobject = (void *)rsrc->ptr; */
/* 	assert(JG(jenv)); */
/* 	if (JG(jenv)) (*JG(jenv))->DeleteGlobalRef(JG(jenv), jobject); */
}

#ifndef PHP_WRAPPER_H
#error must include php_wrapper.h
#endif
