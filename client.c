/*-*- mode: C; tab-width:4 -*-*/

/* execve */
#include <unistd.h>
#include <sys/types.h>

/* strings */
#include <string.h>
#include <ctype.h>

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
#else
#include "zend_stack.h"
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
  Z_LVAL_P(handle) = value;
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
  Z_LVAL_P(handle) = value;
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
  ZVAL_NULL(presult);
  MAKE_STD_ZVAL(exception); 
  ZVAL_NULL(exception);
  setResultFromException(exception, value); 
  zend_throw_exception_object(exception TSRMLS_CC);
#endif
}

#define GET_RESULT(pos) if(!ctx->id) {ctx->id=(zval*)strtol(PARSER_GET_STRING(st, pos), 0, 10);}
struct stack_elem { 
  zval *container;
  char composite_type;          /* A|H */
};
struct parse_ctx {
  zval*id;
  zend_stack containers;
};
void begin(parser_tag_t tag[3], parser_cb_t *cb){
  struct parse_ctx *ctx=(struct parse_ctx*)cb->ctx;
  parser_string_t *st=tag[2].strings;

  switch ((*tag[0].strings[0].string)[tag[0].strings[0].off]) {
  case 'X':
#ifndef ZEND_ENGINE_2
	GET_RESULT(1);
	{
      struct stack_elem stack_elem = { ctx->id, *PARSER_GET_STRING(st, 0) };
	  zend_stack_push(&ctx->containers, &stack_elem, sizeof stack_elem);
	  setResultFromArray(ctx->id);
	  break;
	}
#else
	  assert(0);
#endif
  case 'P':
#ifndef ZEND_ENGINE_2
	{ 
      struct stack_elem *stack_elem;
	  zend_stack_top(&ctx->containers, (void**)&stack_elem);
	  if(stack_elem->composite_type=='H') { /* hash table */
		if(*PARSER_GET_STRING(st, 0)=='N')	/* number */
		  ctx->id=hashIndexUpdate(stack_elem->container, strtol(PARSER_GET_STRING(st, 1), 0, 10));
		else
		  ctx->id=hashUpdate(stack_elem->container, PARSER_GET_STRING(st, 1), st[1].length);
	  }
	  else {						/* array */
		ctx->id=nextElement(stack_elem->container);
	  }
	  break;
	}
#else
	  assert(0);
#endif
  case 'S':
	GET_RESULT(1);
	setResultFromString(ctx->id, PARSER_GET_STRING(st, 0), st[0].length);
	break;
  case 'B':
	GET_RESULT(1);
	setResultFromBoolean(ctx->id, *PARSER_GET_STRING(st, 0)=='T');
	break;
  case 'L':
	GET_RESULT(1);
	setResultFromLong(ctx->id, strtol(PARSER_GET_STRING(st, 0), 0, 10));
	break;
  case 'D':
	GET_RESULT(1);
	setResultFromDouble(ctx->id, zend_string_to_double(PARSER_GET_STRING(st, 0), st[0].length));
	break;
  case 'O':
	GET_RESULT(1);
	if(!st[0].length) {
	  ZVAL_NULL(ctx->id);
	} else {
	  setResultFromObject(ctx->id, strtol(PARSER_GET_STRING(st, 0), 0, 10));
	}
	break;
  case 'E':
	{
	  char *stringRepresentation=PARSER_GET_STRING(st, 1);
	  size_t len=st[1].length;
	  long obj = strtol(PARSER_GET_STRING(st, 0), 0, 10);
	  GET_RESULT(2);
	  setException(ctx->id, obj, stringRepresentation, len);
	  break;
	}
  default:
	assert(0);
  }
}
static void end(parser_string_t st[1], parser_cb_t *cb){
  switch (*(st[0].string)[st[0].off]) {
  case 'X': 
#ifndef ZEND_ENGINE_2
  { 
    int err;
	struct parse_ctx *ctx=(struct parse_ctx*)cb->ctx;
    err=zend_stack_del_top(&ctx->containers);
    assert(SUCCESS==err);
  }
#else
	assert(0);
#endif
  }
}
static void handle_request(proxyenv *env) {
  struct parse_ctx ctx = {0};
  parser_cb_t cb = {begin, end, &ctx};

  zend_stack_init(&ctx.containers);
  parse(env, &cb);
  assert(zend_stack_is_empty(&ctx.containers));
  zend_stack_destroy(&ctx.containers);
}

static proxyenv *try_connect_to_server(short bail, unsigned char spec TSRMLS_DC) {
  char *server;
  int sock;
  short no_multicast;
  proxyenv *jenv =JG(jenv);
  if(jenv) return jenv;

  if(JG(is_closed)) {
	php_error(E_ERROR, "php_mod_java(%d): Could not connect to server: Session is closed. -- This usually means that you have tried to access the server in your class' __destruct() method.",51);
	return 0;
  }
  no_multicast = (spec=='m' || spec=='j');
  if(!(server=no_multicast?java_test_server_no_multicast(&sock, spec TSRMLS_CC):java_test_server(&sock, spec))) {
	if (bail) 
	  php_error(E_ERROR, "php_mod_java(%d): Could not connect to server: %s -- Have you started the java bridge and set the java.socketname option?",52, strerror(errno));
	return 0;
  }
#ifndef ZEND_ENGINE_2
  // we want arrays as values
  { char c=2; send(sock, &c, sizeof c, 0); }
#endif

  return JG(jenv) = java_createSecureEnvironment(sock, handle_request, server);
}
proxyenv *java_connect_to_server(TSRMLS_D) {
  return try_connect_to_server(1, 0 TSRMLS_CC);
}
proxyenv *java_try_connect_to_server(TSRMLS_D) {
  
  return try_connect_to_server(0, 0 TSRMLS_CC);
}
proxyenv *java_connect_to_mono(TSRMLS_D) {
  
  return try_connect_to_server(1, 'M' TSRMLS_CC);
}
proxyenv *java_connect_to_server_no_multicast(TSRMLS_D) {
  
  return try_connect_to_server(1, 'j' TSRMLS_CC);
}
proxyenv *java_connect_to_mono_no_multicast(TSRMLS_D) {
  
  return try_connect_to_server(1, 'm' TSRMLS_CC);
}

#ifndef PHP_WRAPPER_H
#error must include php_wrapper.h
#endif
