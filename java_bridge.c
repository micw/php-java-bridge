/*-*- mode: C; tab-width:4 -*-*/

#include <stdlib.h>
#include "php_java.h"
#include "java_bridge.h"
#include <jni.h>

/* miscellaneous */
#include <stdio.h>
#include <assert.h>

/* strings */
#include <string.h>


ZEND_DECLARE_MODULE_GLOBALS(java)

static jobjectArray php_java_makeArray(int argc, pval** argv TSRMLS_DC);
static jobject php_java_makeObject(pval* arg TSRMLS_DC);

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

int java_get_jobject_from_object(pval*object, jobject*obj)
{
  pval **handle;
  int type, n;

  n = zend_hash_index_find(Z_OBJPROP_P(object), 0, (void**) &handle);
  if(n==-1) { *obj=0; return 0; }

  *obj = zend_list_find(Z_LVAL_PP(handle), &type);
  return type;
}

void php_java_invoke(char*name, jobject object, int arg_count, zval**arguments, pval*presult TSRMLS_DC) 
{
  proxyenv *jenv = JG(jenv);
  jlong result = (jlong)(long)presult;
  jstring method;

  BEGIN_TRANSACTION(jenv);
  method = (*jenv)->NewStringUTF(jenv, name);

  assert(method); if(!method) exit(6);

  (*jenv)->Invoke(jenv, JG(php_reflect), JG(invoke),
				  object, method,
				  php_java_makeArray(arg_count, arguments TSRMLS_CC), result);
  END_TRANSACTION(jenv);
}

void php_java_call_function_handler(INTERNAL_FUNCTION_PARAMETERS, char*name, short constructor, short createInstance, pval *object, int arg_count, zval**arguments)
{
  proxyenv *jenv;
  jlong result = 0;

  /* check if we're initialized */
  jenv = JG(jenv);
  if(!jenv) {
	php_error(E_ERROR, "java not initialized");
	return;
  }

  BEGIN_TRANSACTION(jenv);
  if (constructor) {
    /* construct a Java object:
       First argument is the class name.  Any additional arguments will
       be treated as constructor parameters. */

    jstring className;
    result = (jlong)(long)object;

    if (ZEND_NUM_ARGS() < 1) {
      php_error(E_ERROR, "Missing classname in new java() call");
      return;
    }

    className=(*jenv)->NewStringUTF(jenv, Z_STRVAL_P(arguments[0]));
	assert(className);
	/* create a new object */
	(*jenv)->CreateObject(jenv, JG(php_reflect), JG(co),
						  className, createInstance?JNI_TRUE:JNI_FALSE, 
						  php_java_makeArray(arg_count-1, arguments+1 TSRMLS_CC), result);
  } else {

    pval **handle;
    int type;
    jobject obj;
    jstring method;

	java_get_jobject_from_object(object, &obj);
	assert(obj);

    method = (*jenv)->NewStringUTF(jenv, name);
    result = (jlong)(long)return_value;
    /* invoke a method on the given object */
    (*jenv)->Invoke(jenv, JG(php_reflect), JG(invoke),
      obj, method, php_java_makeArray(arg_count, arguments TSRMLS_CC), result);
  }
  checkError((pval*)(long)result TSRMLS_CC);

  END_TRANSACTION(jenv);
}

static jobject php_java_makeObject(pval* arg TSRMLS_DC)
{
  proxyenv *jenv = JG(jenv);
  jobject result;
  pval **handle;
  int type;
  jmethodID makeArg;
  jclass hashClass;
  jvalue args[2];

  switch (Z_TYPE_P(arg)) {
    case IS_STRING:
      result=(*jenv)->NewByteArray(jenv, Z_STRLEN_P(arg));
      (*jenv)->SetByteArrayRegion(jenv, (jbyteArray)result, 0,
        Z_STRLEN_P(arg), Z_STRVAL_P(arg));
      break;

    case IS_OBJECT:
	  java_get_jobject_from_object(arg, &result);
	  assert(result);
      break;

    case IS_BOOL:
      makeArg = (*jenv)->GetMethodID(jenv, JG(reflect_class), "MakeArg",
        "(Z)Ljava/lang/Object;");
	  args[0].z=(jboolean)(Z_LVAL_P(arg));
      result = (*jenv)->CallObjectMethodA(1, jenv, JG(php_reflect), makeArg, args);
      break;

    case IS_LONG:
      makeArg = (*jenv)->GetMethodID(jenv, JG(reflect_class), "MakeArg",
        "(J)Ljava/lang/Object;");
	  args[0].j=(jlong)(Z_LVAL_P(arg));
      result = (*jenv)->CallObjectMethodA(1, jenv, JG(php_reflect), makeArg, args);
      break;

    case IS_DOUBLE:
      makeArg = (*jenv)->GetMethodID(jenv, JG(reflect_class), "MakeArg",
        "(D)Ljava/lang/Object;");
	  args[0].d=(jdouble)(Z_DVAL_P(arg));
      result = (*jenv)->CallObjectMethodA(1, jenv, JG(php_reflect), makeArg, args);
      break;

    case IS_ARRAY:
      {
      jobject jkey, jval;
      zval **value;
      zval key;
      char *string_key;
      ulong num_key;
      jobject jold;
      jmethodID put, init;

      hashClass = (*jenv)->FindClass(jenv, "java/util/Hashtable");
      init = (*jenv)->GetMethodID(jenv, hashClass, "<init>", "()V");
      result = (*jenv)->NewObjectA(0, jenv, hashClass, init, args);

      put = (*jenv)->GetMethodID(jenv, hashClass, "put",
								 "(Ljava/lang/Object;Ljava/lang/Object;)Ljava/lang/Object;");
      result = (*jenv)->NewObjectA(0, jenv, hashClass, init, args);

      /* Iterate through hash */
      zend_hash_internal_pointer_reset(Z_ARRVAL_P(arg));
      while(zend_hash_get_current_data(Z_ARRVAL_P(arg), (void**)&value) == SUCCESS) {
        jval = php_java_makeObject(*value TSRMLS_CC);

        switch (zend_hash_get_current_key(Z_ARRVAL_P(arg), &string_key, &num_key, 0)) {
          case HASH_KEY_IS_STRING:
            Z_TYPE(key) = IS_STRING;
            Z_STRVAL(key) = string_key;
            Z_STRLEN(key) = strlen(string_key);
            jkey = php_java_makeObject(&key TSRMLS_CC);
            break;
          case HASH_KEY_IS_LONG:
            Z_TYPE(key) = IS_LONG;
            Z_LVAL(key) = num_key;
            jkey = php_java_makeObject(&key TSRMLS_CC);
            break;
          default: /* HASH_KEY_NON_EXISTANT */
            jkey = 0;
        }
		args[0].l=jkey;
		args[1].l=jval;
        jold = (*jenv)->CallObjectMethodA(2, jenv, result, put, args);
        zend_hash_move_forward(Z_ARRVAL_P(arg));
      }

      break;
      }

    default:
      result=0;
  }

  return result;
}

static jobjectArray php_java_makeArray(int argc, pval** argv TSRMLS_DC)
{
  jobjectArray result;
  jobject arg;
  int i;
  proxyenv *jenv = JG(jenv);

  jclass objectClass = (*jenv)->FindClass(jenv, "java/lang/Object");
  assert(objectClass);
  result = (*jenv)->NewObjectArray(jenv, argc, objectClass, 0);
  assert(result);
  for (i=0; i<argc; i++) {
    arg = php_java_makeObject(argv[i] TSRMLS_CC);
    (*jenv)->SetObjectArrayElement(jenv, result, i, arg);
  }
  return result;
}

static void
php_java_getset_property (char* name, pval* object, jobjectArray value, zval *presult TSRMLS_DC)
{
  jlong result = 0;
  pval **pobject;
  jobject obj;
  int type;
  jstring propName;
  proxyenv *jenv = JG(jenv);

  propName = (*jenv)->NewStringUTF(jenv, name);

  /* get the object */
  type = java_get_jobject_from_object(object, &obj);
  result = (jlong)(long) presult;

  ZVAL_NULL(presult);

  if (!obj || (type!=le_jobject)) {
    php_error(E_ERROR,
      "Attempt to access a Java property on a non-Java object");
  } else {
    /* invoke the method */
    (*jenv)->GetSetProp(jenv, JG(php_reflect), JG(gsp), obj, propName, value, result);
  }
}

/**
 * php_java_get_property_handler
 */
short php_java_get_property_handler(char*name, zval *object, zval *presult TSRMLS_DC)
{
  proxyenv *jenv = JG(jenv);

  BEGIN_TRANSACTION(jenv);
  php_java_getset_property(name, object, 0, presult TSRMLS_CC);
  END_TRANSACTION(jenv);

  return checkError(presult TSRMLS_CC) ? FAILURE : SUCCESS;
}


/**
 * php_java_set_property_handler
 */
short php_java_set_property_handler(char*name, zval *object, zval *value, zval *presult TSRMLS_DC)
{
  proxyenv *jenv = JG(jenv);

  BEGIN_TRANSACTION(jenv);
  php_java_getset_property(name, object, php_java_makeArray(1, &value TSRMLS_CC), presult TSRMLS_CC);
  END_TRANSACTION(jenv);

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
