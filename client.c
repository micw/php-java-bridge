/*-*- mode: C; tab-width:4 -*-*/

/* execve */
#include <unistd.h>
#include <sys/types.h>

/* strings */
#include <string.h>
/* setenv */
#include <stdlib.h>

/* miscellaneous */
#include <stdio.h>
#include <assert.h>
#include <errno.h>

/* php */
#include "php_wrapper.h"
#ifdef ZEND_ENGINE_2
#include "zend_exceptions.h"
#endif

#include "protocol.h"
#include "parser.h"

#include "java_bridge.h"
#include "php_java.h"

ZEND_EXTERN_MODULE_GLOBALS(java)


static void setResultFromString (pval *presult, char*s, size_t len){
  Z_TYPE_P(presult)=IS_STRING;
  Z_STRLEN_P(presult)=len;
  Z_STRVAL_P(presult)=emalloc(Z_STRLEN_P(presult)+1);
  memcpy(Z_STRVAL_P(presult), s, Z_STRLEN_P(presult));
  Z_STRVAL_P(presult)[Z_STRLEN_P(presult)]=0;
}
static  void  setResultFromLong  (pval *presult, long value) {
  Z_TYPE_P(presult)=IS_LONG;
  Z_LVAL_P(presult)=value;
}


static void  setResultFromDouble  (pval *presult, double value) {
  Z_TYPE_P(presult)=IS_DOUBLE;
  Z_DVAL_P(presult)=value;
}

static  void  setResultFromBoolean  (pval *presult, short value) {
  Z_TYPE_P(presult)=IS_BOOL;
  Z_LVAL_P(presult)=value;
}

#ifdef ZEND_ENGINE_2
static  void  setResultFromException  (pval *presult, long value) {
  /* wrap the java object in a pval object */
  pval *handle;
  TSRMLS_FETCH();
  
  if (Z_TYPE_P(presult) != IS_OBJECT) {
	object_init_ex(presult, php_java_exception_class_entry);
	presult->is_ref=1;
    presult->refcount=1;
  }

  ALLOC_ZVAL(handle);
  Z_TYPE_P(handle) = IS_LONG;
  Z_LVAL_P(handle) = zend_list_insert(value, le_jobject);
  pval_copy_constructor(handle);
  INIT_PZVAL(handle);
#ifndef ZEND_ENGINE_2
  zval_add_ref(&handle); // FIXME, this should be unnecessary
#endif
  zend_hash_index_update(Z_OBJPROP_P(presult), 0, &handle, sizeof(pval *), NULL);
}
#endif

static  void  setResultFromObject  (pval *presult, long value) {
  /* wrap the java object in a pval object */
  pval *handle;
  TSRMLS_FETCH();
  
  if (Z_TYPE_P(presult) != IS_OBJECT) {
	object_init_ex(presult, php_java_class_entry);
	presult->is_ref=1;
    presult->refcount=1;
  }

  ALLOC_ZVAL(handle);
  Z_TYPE_P(handle) = IS_LONG;
  Z_LVAL_P(handle) = zend_list_insert((void*)value, le_jobject);
  pval_copy_constructor(handle);
  INIT_PZVAL(handle);
#ifndef ZEND_ENGINE_2
  zval_add_ref(&handle); // FIXME, this should be unnecessary
#endif
  zend_hash_index_update(Z_OBJPROP_P(presult), 0, &handle, sizeof(pval *), NULL);

}

#ifndef ZEND_ENGINE_2
static  void  setResultFromArray  (pval *presult) {
  array_init( presult );
  INIT_PZVAL( presult );
}

static  pval*nextElement  (pval *handle) {
  pval *result;
  zval_add_ref(&handle);
  ALLOC_ZVAL(result);
  zval_add_ref(&result);
  zend_hash_next_index_insert(Z_ARRVAL_P(handle), &result, sizeof(zval *), NULL);
  return result;
}

static  pval*hashIndexUpdate  (pval *handle, long key) {
  pval *result;
  zval_add_ref(&handle);
  ALLOC_ZVAL(result);
  zval_add_ref(&result);
  zend_hash_index_update(Z_ARRVAL_P(handle), (unsigned long)key, &result, sizeof(zval *), NULL);
  return result;
}

static pval*hashUpdate  (pval *handle, char *key, size_t len) {
  pval *result;
  pval pkey;
  zval_add_ref(&handle);
  ALLOC_ZVAL(result);
  setResultFromString(&pkey, key, len);
  assert(key);
  zval_add_ref(&result);
  zend_hash_update(Z_ARRVAL_P(handle), Z_STRVAL(pkey), Z_STRLEN(pkey)+1, &result, sizeof(zval *), NULL);
  return result;
}
#endif

static  void  setException  (pval *presult, long value, char *strValue, size_t len) {
#ifndef ZEND_ENGINE_2
  setResultFromString(presult, strValue, len);
  Z_TYPE_P(presult)=IS_EXCEPTION;
#else
  zval *exception;

  TSRMLS_FETCH();

  setResultFromObject(presult, value); /* discarded */
  MAKE_STD_ZVAL(exception); 
  setResultFromException(exception, value); 
  zend_throw_exception_object(exception TSRMLS_CC);
#endif
}

struct parse_ctx {
  char composite_type;			/* A|H */
  zval*id;
  pval*val;
};
void begin(parser_tag_t tag[3], parser_cb_t *cb){
  struct parse_ctx *ctx=(struct parse_ctx*)cb->ctx;
  parser_string_t *st=tag[2].strings;
  pval *id;

  switch ((*tag[0].strings[0].string)[tag[0].strings[0].off]) {
  case 'X':
	ctx->id=(zval*)strtol(PARSER_GET_STRING(st, 2), 0, 10);
	setResultFromArray(ctx->id);
	ctx->composite_type=*PARSER_GET_STRING(st, 0);
	ctx->id=(zval*)strtol(PARSER_GET_STRING(st, 1), 0, 10);
	break;
  case 'P':
	if(ctx->composite_type=='H') { /* hash table */
	  ctx->id=(zval*)strtol(PARSER_GET_STRING(st, 2), 0, 10);
	  if(*PARSER_GET_STRING(st, 0)=='N')	/* number */
		ctx->val=hashIndexUpdate(ctx->id, strtol(PARSER_GET_STRING(st, 1), 0, 10));
	  else
		ctx->val=hashUpdate(ctx->id, PARSER_GET_STRING(st, 1), st[1].length);
	}
	else {						/* array */
	  ctx->id=(zval*)strtol(PARSER_GET_STRING(st, 1), 0, 10);
	  ctx->id=(zval*)strtol(PARSER_GET_STRING(st, 0), 0, 10);
	  ctx->id=nextElement(ctx->id);
	}
	break;
  case 'S':
	ctx->id=(zval*)strtol(PARSER_GET_STRING(st, 1), 0, 10);
	setResultFromString(ctx->id, PARSER_GET_STRING(st, 0), st[0].length);
	break;
  case 'B':
	ctx->id=(zval*)strtol(PARSER_GET_STRING(st, 1), 0, 10);
	setResultFromBoolean(ctx->id, *PARSER_GET_STRING(st, 0)!='0');
	break;
  case 'L':
	ctx->id=(zval*)strtol(PARSER_GET_STRING(st, 1), 0, 10);
	setResultFromLong(ctx->id, strtol(PARSER_GET_STRING(st, 0), 0, 10));
	break;
  case 'D':
	ctx->id=(zval*)strtol(PARSER_GET_STRING(st, 1), 0, 10);
	setResultFromDouble(ctx->id, atof(PARSER_GET_STRING(st, 0)));
	break;
  case 'O':
	ctx->id=(zval*)strtol(PARSER_GET_STRING(st, 1), 0, 10);
	if(!st[0].length) {
	  ZVAL_NULL(ctx->id);
	} else {
	  setResultFromObject(ctx->id, strtol(PARSER_GET_STRING(st, 0), 0, 10));
	}
	break;
  }
}
static int handle_request(proxyenv *env) {
  struct parse_ctx ctx = {0,0,0};
  parser_cb_t cb = {begin, 0, &ctx};
  parse(env, &cb);
}

proxyenv *java_connect_to_server(TSRMLS_D) {
  int sock, n=-1;
  proxyenv *jenv =JG(jenv);

  if(jenv) return jenv;

#ifndef CFG_JAVA_SOCKET_INET
  sock = socket (PF_LOCAL, SOCK_STREAM, 0);
#else
  sock = socket (PF_INET, SOCK_STREAM, 0);
#endif
  if(sock!=-1) {
	n = connect(sock,(struct sockaddr*)&cfg->saddr, sizeof cfg->saddr);
  }
  if(n==-1) { 
	php_error(E_ERROR, "php_mod_java(%d): Could not connect to server: %s -- Have you started the java bridge?",52, strerror(errno));
	return 0;
  }

  return JG(jenv) = java_createSecureEnvironment(sock, handle_request);
}

#ifndef PHP_WRAPPER_H
#error must include php_wrapper.h
#endif
