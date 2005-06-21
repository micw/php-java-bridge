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

EXT_EXTERN_MODULE_GLOBALS(EXT)


static void setResultFromString (pval *presult, unsigned char*s, size_t len){
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
  /* wrap the vm object in a pval object */
  pval *handle;
  TSRMLS_FETCH();
  
  if (Z_TYPE_P(presult) != IS_OBJECT) {
	object_init_ex(presult, EXT_GLOBAL(exception_class_entry));
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
  /* wrap the vm object in a pval object */
  pval *handle;
  TSRMLS_FETCH();
  
  if (Z_TYPE_P(presult) != IS_OBJECT) {
	object_init_ex(presult, EXT_GLOBAL(class_entry));
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

static  void  setResultFromArray  (pval *presult) {
  array_init( presult );
  INIT_PZVAL( presult );
}

static  pval*nextElement  (pval *handle) {
  pval *result;
  zval_add_ref(&handle);
  ALLOC_ZVAL(result);
  ZVAL_NULL(result);
  zval_add_ref(&result);
  zend_hash_next_index_insert(Z_ARRVAL_P(handle), &result, sizeof(zval *), NULL);
  return result;
}

static  pval*hashIndexUpdate  (pval *handle, long key) {
  pval *result;
  zval_add_ref(&handle);
  ALLOC_ZVAL(result);
  ZVAL_NULL(result);
  zval_add_ref(&result);
  zend_hash_index_update(Z_ARRVAL_P(handle), (unsigned long)key, &result, sizeof(zval *), NULL);
  return result;
}

static pval*hashUpdate  (pval *handle, unsigned char *key, size_t len) {
  pval *result;
  pval pkey;
  zval_add_ref(&handle);
  ALLOC_ZVAL(result);
  ZVAL_NULL(result);
  setResultFromString(&pkey, key, len);
  assert(key);
  zval_add_ref(&result);
  zend_hash_update(Z_ARRVAL_P(handle), Z_STRVAL(pkey), Z_STRLEN(pkey)+1, &result, sizeof(zval *), NULL);
  return result;
}

static  void  setException  (pval *presult, long value, unsigned char *strValue, size_t len) {
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

#define GET_RESULT(pos) if(!ctx->id) {ctx->id=(zval*)strtol((const char*)PARSER_GET_STRING(st, pos), 0, 10);}
struct stack_elem { 
  zval *container;
  char composite_type;          /* A|H */
};
struct parse_ctx {
  zval*id;
  zend_stack containers;
};
static void begin(parser_tag_t tag[3], parser_cb_t *cb){
  struct parse_ctx *ctx=(struct parse_ctx*)cb->ctx;
  parser_string_t *st=tag[2].strings;
  
  switch ((*tag[0].strings[0].string)[tag[0].strings[0].off]) {
  case 'X':
	GET_RESULT(1);
	{
      struct stack_elem stack_elem = { ctx->id, *PARSER_GET_STRING(st, 0) };
	  zend_stack_push(&ctx->containers, &stack_elem, sizeof stack_elem);
	  setResultFromArray(ctx->id);
	  break;
	}
  case 'P':
	{ 
      struct stack_elem *stack_elem;
	  zend_stack_top(&ctx->containers, (void**)&stack_elem);
	  if(stack_elem->composite_type=='H') { /* hash table */
		if(*PARSER_GET_STRING(st, 0)=='N')	/* number */
		  ctx->id=hashIndexUpdate(stack_elem->container, strtol((const char*)PARSER_GET_STRING(st, 1), 0, 10));
		else
		  ctx->id=hashUpdate(stack_elem->container, PARSER_GET_STRING(st, 1), st[1].length);
	  }
	  else {						/* array */
		ctx->id=nextElement(stack_elem->container);
	  }
	  break;
	}
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
	setResultFromLong(ctx->id, strtol((const char*)PARSER_GET_STRING(st, 0), 0, 10));
	break;
  case 'D':
	GET_RESULT(1);
	setResultFromDouble(ctx->id, zend_string_to_double((const char*)PARSER_GET_STRING(st, 0), st[0].length));
	break;
  case 'O':
	GET_RESULT(1);
	if(!st[0].length) {
	  ZVAL_NULL(ctx->id);
	} else {
	  setResultFromObject(ctx->id, strtol((const char*)PARSER_GET_STRING(st, 0), 0, 10));
	}
	break;
  case 'E':
	{
	  unsigned char *stringRepresentation=PARSER_GET_STRING(st, 1);
	  size_t len=st[1].length;
	  long obj = strtol((const char*)PARSER_GET_STRING(st, 0), 0, 10);
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
	{ 
	  int err;
	  struct parse_ctx *ctx=(struct parse_ctx*)cb->ctx;
	  err=zend_stack_del_top(&ctx->containers);
	  assert(SUCCESS==err);
	}
  }
}

static void begin_header(parser_tag_t tag[3], parser_cb_t *cb){
  proxyenv *ctx=(proxyenv*)cb->ctx;
  char *str=(char*)PARSER_GET_STRING(tag[0].strings, 0);

  switch (*str) {
  case 'S'://Set-Cookie:
	{
	  char *cookie, *path;
	  static const char setcookie[]="Set-Cookie";
	  if(strcmp(str, setcookie) || ((*ctx)->cookie_name)) return;
	  (*ctx)->cookie_name = strdup((char*)PARSER_GET_STRING(tag[1].strings, 0));
	  cookie = (char*)PARSER_GET_STRING(tag[2].strings, 0);
	  if((path=strchr(cookie, ';'))) *path=0;	/* strip off path */
	  (*ctx)->cookie_value = strdup(cookie);
	  assert((*ctx)->cookie_name && (*ctx)->cookie_value);
	  if(!(*ctx)->cookie_name || !(*ctx)->cookie_value) exit(6);
	  break;
	}

  case 'C'://Content-Length or Connection
	{
	  static const char con_connection[]="Connection", con_close[]="close";
	  if(!strcmp(str, con_connection)&&!strcmp((char*)PARSER_GET_STRING(tag[1].strings, 0), con_close)) {
		(*ctx)->must_reopen = 1;
	  }
	  break;
	}
  }
}
static void handle_request(proxyenv *env) {
  struct parse_ctx ctx = {0};
  parser_cb_t cb = {begin, end, &ctx};

  if(!(*env)->is_local && EXT_GLOBAL (get_servlet_context) ()) {
	parser_cb_t cb_header = {begin_header, 0, env};
	EXT_GLOBAL (parse_header) (env, &cb_header);
  }

  zend_stack_init(&ctx.containers);
  EXT_GLOBAL (parse) (env, &cb);
  assert(zend_stack_is_empty(&ctx.containers));
  zend_stack_destroy(&ctx.containers);

  /* re-open a closed HTTP connection */
  if((*env)->must_reopen) {
	char*server;
	(*env)->must_reopen = 0;
	assert((*env)->peer); if((*env)->peer) close((*env)->peer);
	server = EXT_GLOBAL(test_server)(&(*env)->peer, 0); /* FIXME: this is not very efficient */
	assert(server); if(!server) exit(9);
	free(server);
  }

}

unsigned char EXT_GLOBAL (get_mode) () {
#ifndef ZEND_ENGINE_2
  // we want arrays as values
  static const unsigned char arraysAsValues = 2;
#else
  static const unsigned char arraysAsValues = 0;
#endif
	unsigned short is_level = ((EXT_GLOBAL (ini_last_updated)&U_LOGLEVEL)!=0);
	unsigned short level = 0;
	if (is_level)
	  level = EXT_GLOBAL(cfg)->logLevel_val>4?4:EXT_GLOBAL(cfg)->logLevel_val;

	return (is_level<<7)|64|(level<<2)|arraysAsValues;
}

static proxyenv *try_connect_to_server(short bail TSRMLS_DC) {
  char *server;
  int sock;
  short is_local;
  proxyenv *jenv =JG(jenv);
  if(jenv) return jenv;

  if(JG(is_closed)) {
	php_error(E_ERROR, "php_mod_"/**/EXT_NAME()/**/"(%d): Could not connect to server: Session is closed. -- This usually means that you have tried to access the server in your class' __destruct() method.",51);
	return 0;
  }
  if(!(server=EXT_GLOBAL(test_server)(&sock, &is_local))) {
	if (bail) 
	  php_error(E_ERROR, "php_mod_"/**/EXT_NAME()/**/"(%d): Could not connect to server: %s -- Have you started the "/**/EXT_NAME()/**/" bridge and set the "/**/EXT_NAME()/**/".socketname option?",52, strerror(errno));
	return 0;
  }

  if(is_local || !EXT_GLOBAL (get_servlet_context) ()) {
	unsigned char mode = EXT_GLOBAL (get_mode) ();
	send(sock, &mode, sizeof mode, 0); 
  }

  return JG(jenv) = EXT_GLOBAL(createSecureEnvironment)(sock, handle_request, server, is_local);
}
proxyenv *EXT_GLOBAL(connect_to_server)(TSRMLS_D) {
  return try_connect_to_server(1 TSRMLS_CC);
}
proxyenv *EXT_GLOBAL(try_connect_to_server)(TSRMLS_D) {
  
  return try_connect_to_server(0 TSRMLS_CC);
}

#ifndef PHP_WRAPPER_H
#error must include php_wrapper.h
#endif
