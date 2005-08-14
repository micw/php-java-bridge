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
#include <errno.h>

/* php */
#include "php_java.h"
#ifdef ZEND_ENGINE_2
#include "zend_exceptions.h"
#else
#include "zend_stack.h"
#endif

#include "protocol.h"
#include "parser.h"

#include "java_bridge.h"

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
  zend_hash_index_update(Z_OBJPROP_P(presult), 0, &handle, sizeof(pval *), NULL);

}

/*
 * Call a user function. Since our cross-compiler (which creates
 * windows executables) does not allow us to access EG(exception),
 * this is currently a two step process: We first jump into the
 * evaluator in order to handle exceptions for us.  The evaluator will
 * call the _call_with_exception_handler() thunk which calls the user
 * function with our exception handler in place.  If the user function
 * is in the current environment, we call it directly, asking
 * _call_with_exception_handler to provide the parameter array.  If an
 * exception occured, the _exception_handler procedure is called,
 * which sets the JG(exception) and communicates the exception to the
 * server.
 *
 * The current scheme allows us to support exception handling in PHP4:
 * If we detect that the server's java_last_exception carries an
 * exception after executing the thunk (in which case we can be sure
 * that the thunk was aborted), we can call the _exception_handler
 * with the exception. -- After that the communication continues as
 * usual: the server receives the exception as the result of the apply
 * call and might communicate the exception back to us which in turn
 * causes an abortion of the next frame until either the server or the
 * client catches the exception or there are no more frames available.
 */ 
static int call_user_cb(zval**object, zval*func, zval**retval_ptr, zval*func_params TSRMLS_DC) {
  int retval;
  static const char name[] = "call_with_exception_handler";
#if defined(ZEND_ENGINE_2)
  static const char call_with_exception_handler[] =
	"try {"/**/EXT_NAME()/**/"_call_with_exception_handler();} catch (Exception $__JavaException) {"/**/EXT_NAME()/**/"_exception_handler($__JavaException);}";
#else
  static const char call_with_exception_handler[] =
	EXT_NAME()/**/"_call_with_exception_handler();";
#endif	
  JG(object)=object;
  JG(func)=func;
  JG(retval_ptr)=retval_ptr;
  JG(func_params)=func_params;

  JG(exception)=0;
  retval = zend_eval_string((char*)call_with_exception_handler, 0, (char*)name TSRMLS_CC);
  return retval;
}
/*
 * Check for exception and communication the exception back to the
 * server.  Return true if an exception was handled.
 */

static short handle_exception(zval*presult TSRMLS_DC) {
  short has_exception=0;
  if(JG(exception)&&Z_TYPE_P(JG(exception))!=IS_NULL) {
	proxyenv *jenv = EXT_GLOBAL(connect_to_server)(TSRMLS_C);
	long result;
	//php_error(E_WARNING, "php_mod_"/**/EXT_NAME()/**/"(%d): Unhandled exception during callback in user function: %s.", 25, fname);
	EXT_GLOBAL(get_jobject_from_object)(JG(exception), &result TSRMLS_CC);
	if(!result)
	  php_error(E_WARNING, "Exception is not a JavaException object.");
	else {
	  has_exception=1;
	  (*jenv)->writeResultBegin(jenv, presult);
	  (*jenv)->writeException(jenv, result, "php exception.", 0);
	  (*jenv)->writeResultEnd(jenv);
	}
	zval_ptr_dtor(&JG(exception));
#if defined(ZEND_ENGINE_2)
	zend_clear_exception(TSRMLS_C);
#endif
}
  return has_exception;
}

static void setResultFromApply(zval *presult, unsigned char *cname, size_t clen, unsigned char*fname, size_t flen, zval *object, zval *func_params)
{
  short has_exception=0;
  zval *func, *retval_ptr=0;
  char *name;
  zend_class_entry *ce = 0;
  int key_type;
  char *string_key;
  ulong num_key;
  
  TSRMLS_FETCH();
  
  MAKE_STD_ZVAL(func);
  setResultFromString(func, cname, clen);

  if (call_user_cb(&object, func, &retval_ptr, func_params TSRMLS_CC) != SUCCESS) {
	php_error(E_WARNING, "php_mod_"/**/EXT_NAME()/**/"(%d): Could not call user function: %s.", 23, cname);
  }
  if(!handle_exception(presult TSRMLS_CC)) 
	EXT_GLOBAL(result)(retval_ptr, 0, presult TSRMLS_CC);
  if(retval_ptr) 
	zval_ptr_dtor(&retval_ptr);
  zval_ptr_dtor(&func);
}

static  void  setResultFromArray  (pval *presult) {
  array_init( presult );
  INIT_PZVAL( presult );
}

static  pval*nextElement  (pval *handle) {
  pval *result;
  MAKE_STD_ZVAL(result);
  ZVAL_NULL(result);
  zend_hash_next_index_insert(Z_ARRVAL_P(handle), &result, sizeof(zval *), NULL);
  return result;
}

static  pval*hashIndexUpdate  (pval *handle, long key) {
  pval *result;
  MAKE_STD_ZVAL(result);
  ZVAL_NULL(result);
  zend_hash_index_update(Z_ARRVAL_P(handle), (unsigned long)key, &result, sizeof(zval *), NULL);
  return result;
}

static pval*hashUpdate  (pval *handle, unsigned char *key, size_t len) {
  pval *result;
  MAKE_STD_ZVAL(result);
  ZVAL_NULL(result);
  assert(key);
  zend_hash_update(Z_ARRVAL_P(handle), key, len+1, &result, sizeof(zval *), NULL);
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
  zval *container;				/* ctx->id */
  char composite_type;          /* A|H */

  unsigned char *m, *p;						/* see Apply in PROTOCOL.TXT */
  size_t m_length, p_length;
  long v, n;
  zval *retval;
};
struct parse_ctx {
  zval*id;
  zend_stack containers;
};
static void begin(parser_tag_t tag[3], parser_cb_t *cb){
  struct parse_ctx *ctx=(struct parse_ctx*)cb->ctx;
  parser_string_t *st=tag[2].strings;
  
  switch ((*tag[0].strings[0].string)[tag[0].strings[0].off]) {
  case 'A':						/* receive apply args as normal array */
	GET_RESULT(4);
	{
	  /* store array result in tmp_retval, keep ctx->id in retval */
	  zval *tmp_retval;
	  MAKE_STD_ZVAL(tmp_retval);
	  ZVAL_NULL(tmp_retval);

      struct stack_elem stack_elem = 
		{ tmp_retval, 'A', 
		  (unsigned char*)strdup((char*)PARSER_GET_STRING(st, 2)), /* m */
		  (unsigned char*)strdup((char*)PARSER_GET_STRING(st, 1)), /* p */
		  st[2].length,			/* m_length */
		  st[1].length,			/* p_length */
		  strtol((const char*)PARSER_GET_STRING(st, 0), 0, 10), /* v */
		  strtol((const char*)PARSER_GET_STRING(st, 3), 0, 10), /* n */
		  ctx->id
		}; 
	  zend_stack_push(&ctx->containers, &stack_elem, sizeof stack_elem);

	  setResultFromArray(tmp_retval);
	  break;
	}
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
  char c = (*(st[0].string)[st[0].off]);
  switch (c) {
  case 'X': 
	{ 
	  int err;
	  struct parse_ctx *ctx=(struct parse_ctx*)cb->ctx;
	  err=zend_stack_del_top(&ctx->containers);
	  assert(SUCCESS==err);
	}
	break;
  }
}

static const char key_hosts[]="java.hosts";
static const char key_servlet[] = "java.servlet";
static void begin_header(parser_tag_t tag[3], parser_cb_t *cb){
  proxyenv *ctx=(proxyenv*)cb->ctx;
  char *str=(char*)PARSER_GET_STRING(tag[0].strings, 0);

  switch (*str) {
  case 'C'://Content-Length or Connection
	{
	  static const char con_connection[]="Connection", con_close[]="close";
	  if(!strcmp(str, con_connection)&&!strcmp((char*)PARSER_GET_STRING(tag[1].strings, 0), con_close)) {
		(*ctx)->must_reopen = 1;
	  }
	  break;
	}
  case 'X':// Redirect
	{
	  char *key;
	  static const char context[] = "X_JAVABRIDGE_CONTEXT";
	  static const char redirect[]= "X_JAVABRIDGE_REDIRECT";
	  if(!strcmp(str, redirect)) {
		key = (char*)PARSER_GET_STRING(tag[1].strings, 0);
		zend_alter_ini_entry((char*)key_hosts, sizeof key_hosts, 
							 key, tag[1].strings[0].length+1, 
							 ZEND_INI_SYSTEM, PHP_INI_STAGE_RUNTIME);
		(*ctx)->must_reopen = 2;
	  } else if(!strcmp(str, context)) {
		if(!(*ctx)->servlet_ctx) {
		  key = (char*)PARSER_GET_STRING(tag[1].strings, 0);
		  (*ctx)->servlet_ctx = strdup(key);
		}
	  }
	  break;
	}
  }
}

static void handle_request(proxyenv *env) {
  short tail_call;
  struct parse_ctx ctx = {0};
  parser_cb_t cb = {begin, end, &ctx};
  struct stack_elem *stack_elem;

 handle_request:
  if(!(*env)->is_local && EXT_GLOBAL (get_servlet_context) ()) {
	parser_cb_t cb_header = {begin_header, 0, env};
	EXT_GLOBAL (parse_header) (env, &cb_header);
  }

  zend_stack_init(&ctx.containers);
  EXT_GLOBAL (parse) (env, &cb);
  /* pull off A, if any */
  if(SUCCESS==zend_stack_top(&ctx.containers, (void**)&stack_elem))	{ 
	int err;
	assert(stack_elem->m); if(!stack_elem->m) exit(9);
	assert(stack_elem->p); if(!stack_elem->p) exit(9);
	setResultFromApply(stack_elem->retval, stack_elem->p, stack_elem->p_length, stack_elem->m, stack_elem->m_length, (zval*)stack_elem->v, stack_elem->container);
	free(stack_elem->m);
	free(stack_elem->p);
	zval_ptr_dtor(&stack_elem->container);
	err=zend_stack_del_top(&ctx.containers);
	assert(SUCCESS==err);
	tail_call = 1;
  } else {
	tail_call = 0;
  }
  assert(zend_stack_is_empty(&ctx.containers));
  zend_stack_destroy(&ctx.containers);

  /* re-open a closed HTTP connection */
  if((*env)->must_reopen) {
	char*server;

	assert((*env)->peer); if((*env)->peer) close((*env)->peer);
	server = EXT_GLOBAL(test_server)(&(*env)->peer, 0);
	assert(server); if(!server) exit(9);
	free(server);

	if((*env)->must_reopen==2) { // redirect
	  static const char key_sockname[] = "java.socketname";
	  static const char key_off[] = "Off";
	  zend_alter_ini_entry((char*)key_servlet, sizeof key_servlet, 
						   (char*)key_off, sizeof key_off, 
						   ZEND_INI_SYSTEM, PHP_INI_STAGE_RUNTIME);
	  zend_alter_ini_entry((char*)key_sockname, sizeof key_sockname, 
						   (char*)key_off, sizeof key_off, 
						   ZEND_INI_SYSTEM, PHP_INI_STAGE_RUNTIME);
	  EXT_GLOBAL(send_context)(env);
	}

	(*env)->must_reopen = 0;
  }

  if(tail_call) {
	memset(&ctx, 0, sizeof ctx);
	goto handle_request;
  }
}

unsigned char EXT_GLOBAL (get_mode) () {
#ifndef ZEND_ENGINE_2
  // we want arrays as values
  static const unsigned char arraysAsValues = 2;
#else
  static const unsigned char arraysAsValues = 0;
#endif
  unsigned short is_level = ((EXT_GLOBAL (ini_user)&U_LOGLEVEL)!=0);
  unsigned short level = 0;
  if (is_level)
	level = EXT_GLOBAL(cfg)->logLevel_val>7?7:EXT_GLOBAL(cfg)->logLevel_val;
  
  return (is_level<<7)|64|(level<<2)|arraysAsValues;
}

/*
 * adjust the standard environment for the current request.
 */
static proxyenv* adjust_environment(proxyenv *env TSRMLS_DC) {
  static const char context[] = "$_SERVER['X_JAVABRIDGE_CONTEXT'];";
  static const char override[] = "$_SERVER['X_JAVABRIDGE_OVERRIDE_HOSTS'];";
  static const char key_on[] = "On";
  zval val;
  if((SUCCESS==zend_eval_string((char*)context, &val, "context" TSRMLS_CC)) && (Z_TYPE(val)==IS_STRING)) {
	(*env)->servlet_ctx = strdup(Z_STRVAL(val));
  }
  if((SUCCESS==zend_eval_string((char*)override, &val, "override" TSRMLS_CC)) && (Z_TYPE(val)==IS_STRING)) {
	zend_alter_ini_entry((char*)key_hosts, sizeof key_hosts, 
						 Z_STRVAL(val), Z_STRLEN(val), 
						 ZEND_INI_SYSTEM, PHP_INI_STAGE_RUNTIME);
	zend_alter_ini_entry((char*)key_servlet, sizeof key_servlet, 
						 (char*)key_on, sizeof key_on, 
						 ZEND_INI_SYSTEM, PHP_INI_STAGE_RUNTIME);
	
  }
  return env;
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

  return JG(jenv) = adjust_environment(EXT_GLOBAL(createSecureEnvironment)(sock, handle_request, server, is_local) TSRMLS_CC);
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
