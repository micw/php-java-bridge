/*-*- mode: C; tab-width:4 -*-*/

/* execve */
#include <unistd.h>
#include <sys/types.h>

/* strings */
#include <string.h>
/* setenv */
#include <stdlib.h>

/* wait */
#include <sys/types.h>
#include <sys/wait.h>

/* miscellaneous */
#include <stdio.h>
#include <assert.h>
#include <errno.h>

/* jni */
#include <jni.h>

/* php */
#include "php.h"


#include "protocol.h"
#include "java_bridge.h"
#include "php_java.h"

ZEND_DECLARE_MODULE_GLOBALS(java)


static int check_error(proxyenv *jenv, int nr TSRMLS_DC) {
  jthrowable error = (*(jenv))->ExceptionOccurred(jenv);
  jclass errClass;
  jmethodID toString;
  jobject errString;
  const char *errAsUTF;
  jboolean isCopy;
  if(!error) return 0;
  (*jenv)->ExceptionClear(jenv);
  errClass = (*jenv)->GetObjectClass(jenv, error);
  toString = (*jenv)->GetMethodID(jenv, errClass, "toString", "()Ljava/lang/String;");
  errString = (*jenv)->CallObjectMethod(0, jenv, error, toString);
  errAsUTF = (*jenv)->GetStringUTFChars(jenv, errString, &isCopy);
  fprintf(stdout, "php_mod_java(%d): %s",nr, errAsUTF);
  php_error(E_ERROR, "php_mod_java(%d): %s",nr, errAsUTF);
  if(isCopy) (*jenv)->ReleaseStringUTFChars(jenv, errString, errAsUTF);
  return 1;
}

#define swrite java_swrite
extern void java_swrite(const  void  *ptr,  size_t  size,  size_t  nmemb,  SFILE *stream);

#define sread java_sread
extern void java_sread(void *ptr, size_t size, size_t nmemb, SFILE *stream);

#define id java_id
extern void java_id(proxyenv *env, char id);

static void setResultFromString (proxyenv *jenv,  pval *presult, jbyteArray jvalue){
  jboolean isCopy;
  jbyte *value = (*jenv)->GetByteArrayElements(jenv, jvalue, &isCopy);
  Z_TYPE_P(presult)=IS_STRING;
  Z_STRLEN_P(presult)=(*jenv)->GetArrayLength(jenv, jvalue);
  Z_STRVAL_P(presult)=emalloc(Z_STRLEN_P(presult)+1);
  memcpy(Z_STRVAL_P(presult), value, Z_STRLEN_P(presult));
  Z_STRVAL_P(presult)[Z_STRLEN_P(presult)]=0;
  if (isCopy) (*jenv)->ReleaseByteArrayElements(jenv, jvalue, value, 0);
}
static  void  setResultFromLong  (proxyenv *jenv,  pval *presult, jlong value) {
  Z_TYPE_P(presult)=IS_LONG;
  Z_LVAL_P(presult)=(long)value;
}


static void  setResultFromDouble  (proxyenv *jenv,  pval *presult, jdouble value) {
  Z_TYPE_P(presult)=IS_DOUBLE;
  Z_DVAL_P(presult)=value;
}

static  void  setResultFromBoolean  (proxyenv *jenv, pval *presult, jboolean value) {
  Z_TYPE_P(presult)=IS_BOOL;
  Z_LVAL_P(presult)=value;
}

static  void  setResultFromObject  (proxyenv *jenv,  pval *presult, jobject value) {
  /* wrap the java object in a pval object */
  jobject _ob;
  pval *handle;
  TSRMLS_FETCH();
  
  if (Z_TYPE_P(presult) != IS_OBJECT) {
	object_init_ex(presult, php_java_class_entry);
	presult->is_ref=1;
    presult->refcount=1;
  }

  ALLOC_ZVAL(handle);
  Z_TYPE_P(handle) = IS_LONG;
  _ob= (*jenv)->NewGlobalRef(jenv, value);
  Z_LVAL_P(handle) = zend_list_insert(_ob, le_jobject);
  pval_copy_constructor(handle);
  INIT_PZVAL(handle);
  zval_add_ref(&handle);
  zend_hash_index_update(Z_OBJPROP_P(presult), 0, &handle, sizeof(pval *), NULL);

}

static  void  setResultFromArray  (proxyenv *jenv,  pval *presult) {
  array_init( presult );
  INIT_PZVAL( presult );
}

static  pval*nextElement  (proxyenv *jenv,  pval *handle) {
  pval *result;
  zval_add_ref(&handle);
  ALLOC_ZVAL(result);
  zval_add_ref(&result);
  zend_hash_next_index_insert(Z_ARRVAL_P(handle), &result, sizeof(zval *), NULL);
  return result;
}

static  pval*hashIndexUpdate  (proxyenv *jenv,  pval *handle, jlong key) {
  pval *result;
  zval_add_ref(&handle);
  ALLOC_ZVAL(result);
  zval_add_ref(&result);
  zend_hash_index_update(Z_ARRVAL_P(handle), (unsigned long)key, &result, sizeof(zval *), NULL);
  return result;
}

static pval*hashUpdate  (proxyenv *jenv, pval *handle, jbyteArray key) {
  pval *result;
  pval pkey;
  zval_add_ref(&handle);
  ALLOC_ZVAL(result);
  setResultFromString(jenv, &pkey, key);
  assert(key);
  zval_add_ref(&result);
  zend_hash_update(Z_ARRVAL_P(handle), Z_STRVAL(pkey), Z_STRLEN(pkey)+1, &result, sizeof(zval *), NULL);
  return result;
}

static  void  setException  (proxyenv *jenv,  pval *presult, jbyteArray value) {
  setResultFromString(jenv, presult, value);
  Z_TYPE_P(presult)=IS_EXCEPTION;
}


static int handle_request(proxyenv *env) {
  jlong result;
  char c;
  SFILE *peer=(*env)->peer;
  sread(&c, 1, 1, peer);

  switch(c) {
  case PROTOCOL_END: {
	return 0;
  }
  case SETRESULTFROMSTRING: {
	jbyteArray jvalue;
	sread(&result, sizeof result, 1, peer);
	sread(&jvalue, sizeof jvalue, 1, peer);
	setResultFromString(env, (pval*)(long)result, jvalue);
	break;
  }
  case SETRESULTFROMLONG: {
	jlong jvalue;
	sread(&result, sizeof result, 1, peer);
	sread(&jvalue, sizeof jvalue, 1, peer);
	setResultFromLong(env, (pval*)(long)result, jvalue);
	break;
  }
  case SETRESULTFROMDOUBLE: {
	jdouble jvalue;
	sread(&result, sizeof result, 1, peer);
	sread(&jvalue, sizeof jvalue, 1, peer);
	setResultFromDouble(env, (pval*)(long)result, jvalue);
	break;
  }
  case SETRESULTFROMBOOLEAN: {
	jboolean jvalue;
	sread(&result, sizeof result, 1, peer);
	sread(&jvalue, sizeof jvalue, 1, peer);
	setResultFromBoolean(env, (pval*)(long)result, jvalue);
	break;
  }
  case SETRESULTFROMOBJECT: {
	jobject jvalue;
	sread(&result, sizeof result, 1, peer);
	sread(&jvalue, sizeof jvalue, 1, peer);
	setResultFromObject(env, (pval*)(long)result, jvalue);
	break;
  }
  case SETRESULTFROMARRAY: {
 	sread(&result, sizeof result, 1, peer);
	setResultFromArray(env, (pval*)(long)result);
	break;
  }
  case NEXTELEMENT: {
	sread(&result, sizeof result, 1, peer);
	result=(jlong)(long)nextElement(env, (pval*)(long)result);
	id(env, 0);
	swrite(&result, sizeof result, 1, peer);
	break;
  }
  case HASHINDEXUPDATE: {
	jlong jvalue;
	sread(&result, sizeof result, 1, peer);
	sread(&jvalue, sizeof jvalue, 1, peer);
	result=(jlong)(long)hashIndexUpdate(env, (pval*)(long)result, jvalue);
	id(env, 0);
	swrite(&result, sizeof result, 1, peer);
	break;
  }
  case HASHUPDATE: {
	jbyteArray jvalue;
	sread(&result, sizeof result, 1, peer);
	sread(&jvalue, sizeof jvalue, 1, peer);
	result=(jlong)(long)hashUpdate(env, (pval*)(long)result, jvalue);
	id(env, 0);
	swrite(&result, sizeof result, 1, peer);
	break;
  }
  case SETEXCEPTION: {
	jbyteArray jvalue;
	sread(&result, sizeof result, 1, peer);
	sread(&jvalue, sizeof jvalue, 1, peer);
	setException(env, (pval*)(long)result, jvalue);
	break;
  }
  default: {
	php_error(E_ERROR, "php_mod_java(%d): %s, %i",61, "protocol error: ", (unsigned int)c);
	id(env, 0);
	return 0;
  }
  }

  // acknowledge
  id(env, 0); 
  
  return 1;
}
static int handle_requests(proxyenv *env) {
  // continue to handle server requests until the server says the
  // packet is finished (one of the three main methods has sent 0)
  while(handle_request(env));
  return 0;
}
static int java_do_test_server(struct cfg*cfg TSRMLS_DC) {
  char term=0;
  int sock;
  int n, c, e;
  jobject ob;

#ifdef CFG_JAVA_SOCKET_INET
  sock = socket (PF_LOCAL, SOCK_STREAM, 0);
#else
  sock = socket (PF_INET, SOCK_STREAM, 0);
#endif
  if(sock==-1) return FAILURE;
#ifdef CFG_JAVA_SOCKET_ANON
  *cfg->saddr.sun_path=0;
#endif
  n = connect(sock,(struct sockaddr*)&cfg->saddr, sizeof cfg->saddr);
#ifdef CFG_JAVA_SOCKET_ANON
  *cfg->saddr.sun_path='@';
#endif
  if(n!=-1) {
	c = read(sock, &ob, sizeof ob);
	c = (c==sizeof ob) ? write(sock, &term, sizeof term) : 0;
  }
  e = close(sock);

  return (n!=-1 && e!=-1 && c==1)?SUCCESS:FAILURE;
}
int java_test_server(struct cfg*cfg TSRMLS_DC) {
  int count=15;
  if(java_do_test_server(cfg TSRMLS_CC)==SUCCESS) return SUCCESS;
  /* wait for the server that has just started */
  while(cfg->cid && (java_do_test_server(cfg TSRMLS_CC)==FAILURE) && count--) {
	php_error(E_NOTICE, "php_mod_java(%d): waiting for server another %d seconds",57, count);
	sleep(1);
  }
  return (cfg->cid && count)?SUCCESS:FAILURE;
}
int java_connect_to_server(struct cfg*cfg TSRMLS_DC) {
  jobject local_php_reflect;
  jmethodID init;
  int sock, s, i, n=-1, len;
  SFILE *peer;

#ifndef CFG_JAVA_SOCKET_INET
  sock = socket (PF_LOCAL, SOCK_STREAM, 0);
#else
  sock = socket (PF_INET, SOCK_STREAM, 0);
#endif
  if(sock!=-1) {
#ifdef CFG_JAVA_SOCKET_ANON	
	*cfg->saddr.sun_path=0;
#endif
	n = connect(sock,(struct sockaddr*)&cfg->saddr, sizeof cfg->saddr);
#ifdef CFG_JAVA_SOCKET_ANON	
	*cfg->saddr.sun_path='@';
#endif
  }
  if(n==-1) { 
	php_error(E_WARNING, "php_mod_java(%d): Could not connect to server: %s -- Have you started the java bridge?",52, strerror(errno));
	return FAILURE;
  }
  peer = SFDOPEN(sock, "r+");
  assert(peer);
  if(!peer) return FAILURE;

  JG(jenv)=java_createSecureEnvironment(peer, handle_requests);

  if(SFREAD(&local_php_reflect, sizeof local_php_reflect, 1, peer)!=1) {
	php_error(E_WARNING, "php_mod_java(%d): Could not connect to server: %s -- Have you started the java bridge?",58, strerror(errno));
	return FAILURE;
  }
  JG(reflect_class) = (*JG(jenv))->FindClass(JG(jenv), "JavaBridge");
  if(check_error(JG(jenv), 3 TSRMLS_CC)) return FAILURE;
  
  JG(php_reflect) = (*JG(jenv))->NewGlobalRef(JG(jenv), local_php_reflect);
  if(check_error(JG(jenv), 5 TSRMLS_CC)) return FAILURE;

   return SUCCESS;
}
