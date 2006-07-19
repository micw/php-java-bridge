/*-*- mode: C; tab-width:4 -*-*/

/* execve, mkfifo */
#include <unistd.h>
#include <sys/types.h>
#include <sys/stat.h>

/* strings */
#include <string.h>
#include <strings.h>
#include <ctype.h>

/* setenv */
#include <stdlib.h>

/* signal */
#include <signal.h>

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
#include "php_java_strtod.h"

#include "java_bridge.h"

EXT_EXTERN_MODULE_GLOBALS(EXT)

#ifdef DISABLE_HEX
#define GET_LONG(val) strtol(val, 0, 10)
#else
#define GET_LONG(val) strtoul(val, 0, 16)
#endif


static void setResultFromString (pval *presult, unsigned char*s, size_t len){
  Z_TYPE_P(presult)=IS_STRING;
  Z_STRLEN_P(presult)=len;
  Z_STRVAL_P(presult)=emalloc(Z_STRLEN_P(presult)+1);
  memcpy(Z_STRVAL_P(presult), s, Z_STRLEN_P(presult));
  Z_STRVAL_P(presult)[Z_STRLEN_P(presult)]=0;
}
#ifdef DISABLE_HEX
static  void  setResultFromLong  (pval *presult, long value) {
  Z_TYPE_P(presult)=IS_LONG;
  Z_LVAL_P(presult)=value;
}
#else
static void setResultFromLong (pval *presult, unsigned long value, short flag){
  Z_TYPE_P(presult)=IS_LONG;
  if(flag)
	Z_LVAL_P(presult)=value*-1;
  else
	Z_LVAL_P(presult)=value;
}
#endif

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
  TSRMLS_FETCH();
  
  assert(Z_TYPE_P(presult) == IS_NULL);
  if (Z_TYPE_P(presult) != IS_OBJECT) {
	object_init_ex(presult, EXT_GLOBAL(exception_class_entry));
	presult->is_ref=1;
    presult->refcount=1;
  }
  EXT_GLOBAL(store_jobject)(presult, value TSRMLS_CC);
}

static void setResultFromObject (pval *presult, long value, char type) {
  /* wrap the vm object in a pval object */
  TSRMLS_FETCH();
  
  if (Z_TYPE_P(presult) != IS_OBJECT) {
	switch(type) {
	case 'A': 
	  object_init_ex(presult, EXT_GLOBAL(array_entry));
	  break;
	default: 
	  assert(0);
	case 'O':
	  object_init_ex(presult, EXT_GLOBAL(class_entry));
	  break;
	}
	presult->is_ref=1;
	presult->refcount=1;

  } else {
	if(type=='A' && 
	   !instanceof_function(Z_OBJCE_P(presult), EXT_GLOBAL(array_entry) TSRMLS_CC)) {
	  object_init_ex(presult, EXT_GLOBAL(array_entry));
	}
  }

  EXT_GLOBAL(store_jobject)(presult, value TSRMLS_CC);
}
#else
static void setResultFromObject (pval *presult, long value, char type) {
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
#endif


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
  int retval, err;
  struct cb_stack_elem stack_elem = {0, object, func, retval_ptr, func_params};
  
  static const char name[] = "call_with_exception_handler";
#if defined(ZEND_ENGINE_2)
  static const char call_with_exception_handler[] =
	"try {"/**/EXT_NAME()/**/"_call_with_exception_handler();} catch (Exception $__JavaException) {"/**/EXT_NAME()/**/"_exception_handler($__JavaException);}";
#else
  static const char call_with_exception_handler[] =
	EXT_NAME()/**/"_call_with_exception_handler();";
#endif	
  if(!JG(cb_stack)){
	JG(cb_stack)=emalloc(sizeof*JG(cb_stack));
	if(!JG(cb_stack))exit(9);
	zend_stack_init(JG(cb_stack));
  }
  zend_stack_push(JG(cb_stack), &stack_elem, sizeof stack_elem);
  
  retval = zend_eval_string((char*)call_with_exception_handler, 0, (char*)name TSRMLS_CC);
  return retval;
}

/*
 * Check for exception and communicate the exception back to the
 * server.  Return true if an exception was handled.
 */
static short handle_exception(zval*presult TSRMLS_DC) {
  short has_exception=0;
  struct cb_stack_elem *stack_elem;
  int err = zend_stack_top(JG(cb_stack), (void**)&stack_elem); assert(SUCCESS==err);
  if(stack_elem->exception&&Z_TYPE_P(stack_elem->exception)!=IS_NULL) {
	proxyenv *jenv = EXT_GLOBAL(connect_to_server)(TSRMLS_C);
	long result;
	//php_error(E_WARNING, "php_mod_"/**/EXT_NAME()/**/"(%d): Unhandled exception during callback in user function: %s.", 25, fname);
	EXT_GLOBAL(get_jobject_from_object)(stack_elem->exception, &result TSRMLS_CC);
	has_exception=1;
	(*jenv)->writeResultBegin(jenv, presult);
	(*jenv)->writeException(jenv, result, "php exception", 0);
	(*jenv)->writeResultEnd(jenv);
	zval_ptr_dtor(&stack_elem->exception);
#if defined(ZEND_ENGINE_2)
	zend_clear_exception(TSRMLS_C);
#endif
}
  return has_exception;
}

static void setResultFromApply(zval *presult, unsigned char *cname, size_t clen, unsigned char*fname, size_t flen, zval *object, zval *func_params)
{
  zval *func, *retval_ptr=0;
  
  TSRMLS_FETCH();

  MAKE_STD_ZVAL(func);
  setResultFromString(func, cname, clen);

  if (call_user_cb(&object, func, &retval_ptr, func_params TSRMLS_CC) != SUCCESS) {
	php_error(E_WARNING, "php_mod_"/**/EXT_NAME()/**/"(%d): Could not call user function: %s.", 23, cname);
  } else {
	int err;
	if(!handle_exception(presult TSRMLS_CC)) EXT_GLOBAL(result)(retval_ptr, 0, presult TSRMLS_CC);
	err = zend_stack_del_top(JG(cb_stack));
	assert(SUCCESS==err);
  }

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
  zend_hash_update(Z_ARRVAL_P(handle), (char*)key, len+1, &result, sizeof(zval *), NULL);
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

#define GET_RESULT(pos) if(!ctx->id) {ctx->id=(zval*)GET_LONG((const char*)PARSER_GET_STRING(st, pos));}
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

      {
      struct stack_elem stack_elem = 
		{ tmp_retval, 'A', 
		  (unsigned char*)strdup((char*)PARSER_GET_STRING(st, 2)), /* m */
		  (unsigned char*)strdup((char*)PARSER_GET_STRING(st, 1)), /* p */
		  st[2].length,			/* m_length */
		  st[1].length,			/* p_length */
		  (*cb->env)->async_ctx.nextValue =
		  GET_LONG((const char*)PARSER_GET_STRING(st, 0)), /* v */
		  GET_LONG((const char*)PARSER_GET_STRING(st, 3)), /* n */
		  ctx->id
		}; 
	  zend_stack_push(&ctx->containers, &stack_elem, sizeof stack_elem);
      }
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
		  ctx->id=hashIndexUpdate(stack_elem->container, GET_LONG((const char*)PARSER_GET_STRING(st, 1)));
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
#ifndef DISABLE_HEX
  case 'L':
	GET_RESULT(2);
	assert(*PARSER_GET_STRING(st, 1)=='O' || *PARSER_GET_STRING(st, 1)=='A');
	setResultFromLong(ctx->id, GET_LONG((const char*)PARSER_GET_STRING(st, 0)), *PARSER_GET_STRING(st, 1)!='O');
	break;
#else
  case 'L':
	GET_RESULT(1);
	setResultFromLong(ctx->id, GET_LONG((const char*)PARSER_GET_STRING(st, 0)));
	break;
#endif
  case 'D':
	GET_RESULT(1);
	setResultFromDouble(ctx->id, EXT_GLOBAL(strtod)((const char*)PARSER_GET_STRING(st, 0), NULL));
	break;
  case 'N':
	GET_RESULT(0);
	ZVAL_NULL(ctx->id);
	break;
  case 'O':
	GET_RESULT(2);
	assert((((*tag[1].strings[0].string)[tag[1].strings[0].off])=='v')&&
		   (((*tag[1].strings[1].string)[tag[1].strings[1].off])=='p')&&
		   (((*tag[1].strings[2].string)[tag[1].strings[2].off])=='i'));

	setResultFromObject(ctx->id, (*cb->env)->async_ctx.nextValue=GET_LONG((const char*)PARSER_GET_STRING(st, 0)), *PARSER_GET_STRING(st, 1));
	break;
  case 'F': break;
  case 'E':
	{
	  unsigned char *stringRepresentation=PARSER_GET_STRING(st, 1);
	  size_t len=st[1].length;
	  long obj = GET_LONG((const char*)PARSER_GET_STRING(st, 0));
	  GET_RESULT(2);
	  setException(ctx->id, (*cb->env)->async_ctx.nextValue=obj, stringRepresentation, len);
	  break;
	}
	default:
	  {
		short i;
		char *hosts;
		char *servlet;
		TSRMLS_FETCH();
		hosts = JG(hosts); 
		servlet = JG(servlet); 
		assert(((*cb->env)->pos) < RECV_SIZE);
#ifndef __MINGW32__
		php_write((*cb->env)->recv_buf, (*cb->env)->pos TSRMLS_CC);
#endif
		for(i=0; i<(*cb->env)->pos; i++) {
		  char c = (*cb->env)->recv_buf[i];
		  if(c<32) (*cb->env)->recv_buf[i] = '?';
		}
		php_error(E_ERROR, "php_mod_"/**/EXT_NAME()/**/"(%d): Protocol violation at pos %d while trying to connect to %s(%s). Please check that the back-end (JavaBride.war) is deployed or please switch off the java.servlet option. Received bytes: %*s\n", 88, (*cb->env)->c, hosts?hosts:"null", servlet?servlet:"null", (*cb->env)->pos, (*cb->env)->recv_buf);
	  }
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
static void remove_pipe(proxyenv*env) {
  struct pipe *pipe = &((*env)->pipe);
  if((*env)->is_shared) return;

  close(pipe->lockfile); unlink(pipe->channel);
  unlink(pipe->in); unlink(pipe->out);
}
/**
 * Server agreed that we can re-use the connection for a different web
 * context.  See ContextRunner.recycle() and ABOUT.HTM#global-servlet
 * for details.
 */
static void share_connection(proxyenv*env, char*redirect_port TSRMLS_DC) {
  assert(redirect_port);
  (*env)->servlet_ctx=JG(servlet_ctx);
  (*env)->peer = JG(peer);
  if(*redirect_port=='/') {		/* pipe */
	remove_pipe(env);
	(*env)->peerr = JG(peerr);
	EXT_GLOBAL(redirect_pipe)(env);
  }
  (*env)->is_shared=1;
  (*env)->must_reopen=0;		/* do not send a header */
}


#ifndef __MINGW32__
static void redirect(proxyenv*env, char*redirect_port, char*channel_in, char*channel_out TSRMLS_DC) {
  assert(redirect_port);
  if(*redirect_port!='/') { /* socket */
	char *server = EXT_GLOBAL(test_server)(&(*env)->peer, 0, 0 TSRMLS_CC);
	assert(server); if(!server) exit(9);
	free(server);

	/* the default peer */
	if(JG(peer)==-1) JG(peer)=(*env)->peer;
  } else {						/* pipe */
	(*env)->peerr = open(channel_in, O_RDONLY);
	(*env)->peer = open(channel_out, O_WRONLY);
	if((-1==(*env)->peerr) || (-1==(*env)->peer)) {
	  php_error(E_ERROR, "php_mod_"/**/EXT_NAME()/**/"(%d): Fatal: could not open comm. pipe.",92);
	  exit(9);
	}
	EXT_GLOBAL(redirect_pipe)(env);

	/* we can unlink the channel_in and channel_out right here because
	   the above open() calls block until both the server and the
	   client are connected. Unix will automatically remove the inodes
	   as soon as the channel isn't used anymore. */
	EXT_GLOBAL(unlink_channel)(env);

	/* the default peer */
	if(JG(peer)==-1) JG(peer)=(*env)->peer;
	if(JG(peerr)==-1) JG(peerr)=(*env)->peerr;
  }
}
#else
static void redirect(proxyenv*env, char*redirect_port, char*channel_in, char*channel_out TSRMLS_DC) {
  char *server = EXT_GLOBAL(test_server)(&(*env)->peer, 0, 0 TSRMLS_CC);
  assert(server); if(!server) exit(9);
  free(server);

  /* the default peer */
  if(JG(peer)==-1) JG(peer)=(*env)->peer;
}
#endif

static const char key_hosts[]="java.hosts";
static const char key_servlet[] = "java.servlet";
static void begin_header(parser_tag_t tag[3], parser_cb_t *cb){
  proxyenv *ctx=(proxyenv*)cb->env;
  char *str=(char*)PARSER_GET_STRING(tag[0].strings, 0);
  TSRMLS_FETCH();
  switch (*str) {
  case 'S'://Set-Cookie:
	{
	  char *cookie, *cookie_name, *path;
	  static const char setcookie[]="Set-Cookie";
	  if(strcasecmp(str, setcookie)) return;
	  cookie_name = (char*)PARSER_GET_STRING(tag[1].strings, 0);
	  cookie = (char*)PARSER_GET_STRING(tag[2].strings, 0);
	  if((path=strchr(cookie, ';'))) { /* strip off path */
		char*end;
		*path++=0;
		if((path=strchr(path, '='))) path++;
		if((end=strchr(path, ';'))) *end=0;
	  }
	  EXT_GLOBAL(setResultWith_context)(cookie_name, cookie, path);
	  break;
	}

  case 'C'://Content-Length or Connection
	{
	  static const char con_connection[]="Connection", con_close[]="close";
	  if(!strcasecmp(str, con_connection)&&!strcasecmp((char*)PARSER_GET_STRING(tag[1].strings, 0), con_close)) {
		if(!(*ctx)->must_reopen) (*ctx)->must_reopen = 1;
	  }
	  break;
	}
  case 'X':// Redirect
	{
	  char *key;
	  static const char context[] = "X_JAVABRIDGE_CONTEXT";
	  static const char redirect[]= "X_JAVABRIDGE_REDIRECT";
	  static const char kontext[]= "X_JAVABRIDGE_CONTEXT_DEFAULT";
	  if(!(*ctx)->peer_redirected && !strcasecmp(str, redirect)) {
		char *key = (char*)PARSER_GET_STRING(tag[1].strings, 0);
		size_t key_len = tag[1].strings[0].length;
		char *name = (*ctx)->server_name;
		char *idx = strchr(name, ':');
		size_t len = idx ? idx-name : strlen(name);
		char *server_name = malloc(len+1+key_len+1);
		char *pos = server_name;
		assert(server_name); if(!server_name) exit(9);

		memcpy(server_name, name, len); pos+=len;
		*pos=':';
		JG(redirect_port)=pos+1;
		memcpy(pos+1, key, key_len); pos+=key_len+1;
		*pos=0;

		if(JG(hosts)) free(JG(hosts));
		JG(hosts)=server_name;

		(*ctx)->must_reopen = 2;
	  } else if((!(*ctx)->servlet_ctx)&&(!strcasecmp(str, context))) {
		key = (char*)PARSER_GET_STRING(tag[1].strings, 0);
		(*ctx)->servlet_ctx = strdup(key);
		if(!JG(servlet_ctx)) JG(servlet_ctx)=(*ctx)->servlet_ctx;

	  } else if(!strcasecmp(str, kontext)) {
		assert(JG(servlet_ctx));

		(*ctx)->must_share = 1;
	  }
	  break;
	}
  }
}

/* asyncronuous cb */
static short handle_cached(proxyenv *env) {
  struct async_ctx *ctx = &(*env)->async_ctx;
  setResultFromObject(ctx->result, ++ctx->nextValue, 'O');
  return 1;
}  

/* synchronuous cb */
static short handle_request(proxyenv *env) {
  short rc;
  short tail_call;
  struct parse_ctx ctx = {0};
  parser_cb_t cb = {begin, end, &ctx, env};
  struct stack_elem *stack_elem;

  TSRMLS_FETCH();
 handle_request:
  if(!(*env)->is_local && IS_OVERRIDE_REDIRECT(env)) {
	parser_cb_t cb_header = {begin_header, 0, 0, env};
	rc = EXT_GLOBAL (parse_header) (env, &cb_header);
	if(!rc) return 0;
  }
  zend_stack_init(&ctx.containers);
  rc = EXT_GLOBAL (parse) (env, &cb);
  if(!rc) { zend_stack_destroy(&ctx.containers); return 0; }
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

  /* revert override redirect */
  if((*env)->peer0!=-1) {
	close((*env)->peer);
	(*env)->peer = (*env)->peer0;
	(*env)->f_recv = (*env)->f_recv0;
	(*env)->f_send = (*env)->f_send0;
	(*env)->peer0 = -1;
  } else {	 /* Override redirect opens a secondary channel to the
			  backend. Skip the following if an override redirect
			  happened in the middle of redirect/reopen handling. */
	switch(((*env)->must_reopen)) {
	  /* re-open a closed HTTP connection */
	  char*server;
	case 2: // redirect
	  (*env)->peer_redirected = 1;
	  JG(ini_user)&=~(U_SERVLET|U_SOCKNAME);
	  
	  assert((*env)->peer!=-1); 
	  if((*env)->peer!=-1) { close((*env)->peer); (*env)->peer=-1; }
	  if((*env)->must_share) 
		share_connection(env, JG(redirect_port) TSRMLS_CC);
	  else
		redirect(env, JG(redirect_port), (*env)->pipe.in, (*env)->pipe.out TSRMLS_CC);
	  break;
	case 1: // reopen 
	  assert((*env)->peer!=-1); 
	  if((*env)->peer!=-1) close((*env)->peer);
	  server = EXT_GLOBAL(test_server)(&(*env)->peer, 0, 0 TSRMLS_CC);
	  assert(server); if(!server) exit(9);
	  free(server);
	  break;
	}
  }

  if(tail_call) {
	memset(&ctx, 0, sizeof ctx);
	goto handle_request;
  }
  return 1;
}



unsigned char EXT_GLOBAL (get_mode) () {
#ifndef ZEND_ENGINE_2
  // we want arrays as values
  static const unsigned char compat = 3;
#else
#ifdef DISABLE_HEX
  static const unsigned char compat = 0;
#else
  static const unsigned char compat = 1;
#endif
#endif
  unsigned short is_level = ((EXT_GLOBAL (ini_user)&U_LOGLEVEL)!=0);
  unsigned short level = 0;
  if (is_level)
	level = EXT_GLOBAL(cfg)->logLevel_val>7?7:EXT_GLOBAL(cfg)->logLevel_val;
  
  return (is_level<<7)|64|(level<<2)|compat|EXT_GLOBAL(cfg)->extJavaCompatibility;
}

/**
 * Adjust the standard environment for the current request before we
 * connect to the back-end. Used by Fast CGI and Apache so that we can
 * connect back to the current VM.
 * 
 * Checks [HTTP_]XJAVABRIDGE_OVERRIDE_HOSTS_REDIRECT and adjusts JG(hosts).
 * Must be called after clone(cfg).
 */
void EXT_GLOBAL(override_ini_for_redirect)(TSRMLS_D) {
  static const char name[] = "override_ini_for_redirect";
  static const char override[] = "(array_key_exists('HTTP_X_JAVABRIDGE_OVERRIDE_HOSTS_REDIRECT', $_SERVER)?$_SERVER['HTTP_X_JAVABRIDGE_OVERRIDE_HOSTS_REDIRECT']:(array_key_exists('X_JAVABRIDGE_OVERRIDE_HOSTS_REDIRECT', $_SERVER)?$_SERVER['X_JAVABRIDGE_OVERRIDE_HOSTS_REDIRECT']:null));";

  zval val;
  if((SUCCESS==zend_eval_string((char*)override, &val, (char*)name TSRMLS_CC)) && (Z_TYPE(val)==IS_STRING)) {
	/* request servlet -> fast cgi server: connect back to servlet */

	char *kontext, *hosts;
	hosts = malloc(Z_STRLEN(val)+1+100);
	strncpy(hosts, Z_STRVAL(val), Z_STRLEN(val));
	hosts[Z_STRLEN(val)]=0;
	if(JG(hosts)) free(JG(hosts));
	JG(hosts)=hosts;
	kontext = strchr(hosts, '/');
	if(kontext) {
	  *kontext++=0;
	  assert(JG(servlet));
	  free(JG(servlet));
	  JG(servlet) = strdup(kontext);
	  JG(ini_user)|=U_SERVLET;
	}
	JG(ini_user)|=U_HOSTS;
  } else {
	/* request HTTP -> fast cgi or apache module: connect to java_hosts  */

	/* Coerce a http://xyz.com/kontext/foo.php request to the backend:
	   http://xyz.com:{java_hosts[0]}/kontext/foo.php.  For example if
	   we receive a request: http://localhost/sessionSharing.php and
	   java.servlet is On and java.hosts is "127.0.0.1:8080" the code
	   would connect to the backend:
	   http://127.0.0.1:8080/sessionSharing.phpjavabridge. This
	   creates a cookie with PATH value "/".  For a request:
	   http://localhost/myContext/sessionSharing.php the code would
	   connect to
	   http://127.0.0.1/myContext/sessionSharing.phpjavabridge and a
	   cookie with a PATH value "/myContext" would be created.
	*/
	char *kontext = EXT_GLOBAL (get_servlet_context) (TSRMLS_C);
	if(kontext && *kontext!='/') { /* only if context not hardcoded via "On" or "/kontext/foo.php" */
	  static const char bridge_ext[] = "javabridge";
	  static const char default_ext[] = "";
	  static const char default_servlet[] = DEFAULT_SERVLET;
	  static const char name[] = "get_self";
	  static const char override[] = "(array_key_exists('PHP_SELF', $_SERVER) && \n\
array_key_exists('HTTP_HOST', $_SERVER)) ?$_SERVER['PHP_SELF']:null;";
	  const char *bridge_extension = bridge_ext;
	  size_t bridge_extension_size = sizeof(bridge_ext);
	  char *tmp, *strval;
	  size_t len = 0;
	  if((SUCCESS==zend_eval_string((char*)override, &val, (char*)name TSRMLS_CC)) && (Z_TYPE(val)==IS_STRING) && Z_STRLEN(val)) {
		strval = Z_STRVAL(val);
		len = Z_STRLEN(val);
		if(len && *strval!='/') len=0;
	  }
	  if(!len) {
		strval = (char*)default_servlet;
		bridge_extension = default_ext;
		bridge_extension_size = sizeof(default_ext);
		len = sizeof(default_servlet)-1;
	  }
	  assert(JG(servlet));
	  free(JG(servlet));
	  JG(servlet) = tmp = malloc(len+bridge_extension_size);
	  assert(tmp); if(!tmp) exit(9);
	  strcpy(tmp, strval);
	  strcat(tmp, bridge_extension);
	  JG(ini_user)|=U_SERVLET;
	}
  }
}
/**
 * Adjust the standard environment for the current request (used for a
 * servlet backend only).  Sets the servlet_ctx value, which
 * corresponds to the Session/ContextFactory on the server side.
 * 
 * @param proxyenv The java context.  
 *
 * @return The adjusted java
 * context.  
 * 
 * @see php.java.servlet.PhpJavaServlet#getContextFactory(HttpServletRequest,
 * HttpServletResponse)
 */
static proxyenv*adjust_servlet_environment(proxyenv *env, char*servlet_context_string TSRMLS_DC) {
  static const char name[] = "adjust_environment";
  static const char context[] = "(array_key_exists('HTTP_X_JAVABRIDGE_CONTEXT', $_SERVER)?$_SERVER['HTTP_X_JAVABRIDGE_CONTEXT']:(array_key_exists('X_JAVABRIDGE_CONTEXT', $_SERVER)?$_SERVER['X_JAVABRIDGE_CONTEXT']:null));";

  zval val;

  if((SUCCESS==zend_eval_string((char*)context, &val, (char*)name TSRMLS_CC)) && (Z_TYPE(val)==IS_STRING)) {
	(*env)->current_servlet_ctx = strdup(Z_STRVAL(val));
    if(!(*env)->servlet_ctx) (*env)->servlet_ctx = (*env)->current_servlet_ctx;
	if(!JG(servlet_ctx)) JG(servlet_ctx) = (*env)->servlet_ctx;
								/* back-end must have created a session
								   proxy, otherwise we wouldn't see a
								   context. */
	(*env)->backend_has_session_proxy = 1;	
  }
  return env;
}

static char empty[] = "";
static size_t get_context_len(char *context) {
  register char *s = context;
  register size_t len=0;
  if(*s == '/') { s++; len++; }
  for(;*s && *s!='/'; s++) len++;
  return len+1;					/* include terminating \0 or / so that
								   len is always >0 */
}
static proxyenv*recycle_connection(char *context TSRMLS_DC) {
  proxyenv **penv;
  size_t len;
  if(!EXT_GLOBAL(cfg)->persistent_connections) return 0;

  if(!context) context = empty;
  len = get_context_len(context);
  
  if(SUCCESS==zend_hash_find(&JG(connections), context, len, (void**)&penv)){
	proxyenv*env = *penv;
	EXT_GLOBAL(activate_connection)(env TSRMLS_CC);
	(*env)->backend_has_session_proxy = 0;
	if(!(*env)->is_local && context) {
	  env = adjust_servlet_environment(env, context TSRMLS_CC);
	}

	return env;
  }
  return 0;
}
/**
 * Send the header, if this is a new connection.
 */
static void add_header(proxyenv *env) {
  unsigned char mode = EXT_GLOBAL (get_mode) ();
  (*env)->send_len=1; 
  *(*env)->send=mode;
}
static void init_channel(proxyenv *env);
/**
 * Create a new connection to the server or re-use a previous
 * connection.
 */
static proxyenv*create_connection(char *context_string TSRMLS_DC) {
  char *server, *context;
  int sock;
  proxyenv *jenv;
  struct sockaddr saddr;
  short is_local;
  size_t len;
  if(!(context = context_string)) context = empty;
  len = get_context_len(context);

  if(!(server=EXT_GLOBAL(test_server)(&sock, &is_local, &saddr TSRMLS_CC))) {
	return 0;
  }
  jenv = EXT_GLOBAL(createSecureEnvironment)
	(sock, handle_request, handle_cached, server, is_local, &saddr);
  if(jenv) {
	if(! (is_local || !context_string)) init_channel(jenv);
	if(EXT_GLOBAL(cfg)->persistent_connections)
	  zend_hash_update(&JG(connections), context, len, &jenv, sizeof(proxyenv *), 0);
	if(is_local || !context_string) {
	  /* "standard" local backend, send the protocol header */
	  add_header(jenv);
	} else {
	  /* create a jenv for a servlet backend, aquire a context then
		 redirect */
	  if(!((*jenv)->servlet_context_string=strdup(context))) exit(9);
	  jenv = adjust_servlet_environment(jenv, context TSRMLS_CC);
	}
  }
  return jenv;
}
static proxyenv *try_connect_to_server(short bail TSRMLS_DC) {
  char *servlet_context_string = 0;
  proxyenv *jenv =JG(jenv);
  if(jenv) return jenv;

  EXT_GLOBAL(clone_cfg)(TSRMLS_C);

  if(!EXT_GLOBAL(cfg)->is_cgi_servlet || EXT_GLOBAL(cfg)->is_fcgi_servlet) {
	EXT_GLOBAL(override_ini_for_redirect)(TSRMLS_C);
  }
  servlet_context_string = EXT_GLOBAL (get_servlet_context) (TSRMLS_C);
  jenv = recycle_connection(servlet_context_string TSRMLS_CC);
  if(jenv) return JG(jenv) = jenv;
  
  if(JG(is_closed)) {
	php_error(E_ERROR, "php_mod_"/**/EXT_NAME()/**/"(%d): Could not connect to server: Session is closed. -- This usually means that you have tried to access the server in your class' __destruct() method.",51);
	EXT_GLOBAL(destroy_cloned_cfg)(TSRMLS_C);
	return 0;
  }
  
  jenv = create_connection(servlet_context_string TSRMLS_CC);
  if(!jenv) {
	if (bail) 
	  EXT_GLOBAL(sys_error)("Could not connect to server %s(%s). Have you started the "/**/EXT_NAME()/**/" back-end (either a servlet engine, an application server, JavaBridge.jar or MonoBridge.exe) and set the "/**/EXT_NAME()/**/".socketname or "/**/EXT_NAME()/**/".hosts option?", 52);
	EXT_GLOBAL(destroy_cloned_cfg)(TSRMLS_C);
	return 0;
  }
  return JG(jenv) = jenv;
}
proxyenv *EXT_GLOBAL(connect_to_server)(TSRMLS_D) {
  return try_connect_to_server(1 TSRMLS_CC);
}
proxyenv *EXT_GLOBAL(try_connect_to_server)(TSRMLS_D) {
  return try_connect_to_server(0 TSRMLS_CC);
}

#ifdef __MINGW32__
/* named pipes are not available on windows */
static void init_channel(proxyenv *env) {
  (*env)->pipe.in = (*env)->pipe.out = (*env)->pipe.channel = 0;
}
void EXT_GLOBAL(unlink_channel)(proxyenv *env) {
}

#else

static const char in[] = ".i";
static const char out[] = ".o";
static short create_pipe(char*sockname) {
  if((mkfifo(sockname, 0) == -1) || chmod(sockname, 0666) == -1) return 0;
  return 1;
}
static short create_pipes(proxyenv*env, char*basename, size_t basename_len) {
  char *e = basename+basename_len;
  short success;
  if(((*env)->pipe.lockfile = mkstemp(basename)) == -1) return 0;
  if(!create_pipe(strcat(basename, in))) {
	*e=0;
	unlink(basename);
	return 0;
  }
  *e=0;
  success = create_pipe(strcat(basename, out));
  assert(success); if(!success) exit(6);
  (*env)->pipe.out = strdup(basename);
  *e=0;
  (*env)->pipe.in = strdup(strcat(basename, in));
  *e=0;
  (*env)->pipe.channel = basename;

  return 1;
}
/* same as init_channel, but doesn't use a tmpdir */
static void init_channel_raw(proxyenv*env) {
  static const char sockname[] = SOCKNAME;
  static const char sockname_shm[] = SOCKNAME_SHM;
  static const size_t length = sizeof(sockname)>sizeof(sockname_shm)?sizeof(sockname):sizeof(sockname_shm);
  char *pipe;

  /* pipe communication channel only available in servlets */
  (*env)->pipe.channel=0;
  if(!EXT_GLOBAL(option_set_by_user) (U_SERVLET, EXT_GLOBAL(ini_user))) return;

  pipe=malloc(length+2); /* "name.i" */
  assert(pipe); if(!pipe) exit(6);
  
  create_pipes(env, strcpy(pipe,sockname_shm), sizeof(sockname_shm)-1) ||
	create_pipes(env, strcpy(pipe,sockname), sizeof(sockname)-1);

  if(!(*env)->pipe.channel) free(pipe);
}
/* create named pipe channel in tmpdir */
static void init_channel(proxyenv*env) {
  static const char sockname[] = "/php_java_bridgeXXXXXX";
  char *pipe;
  size_t length;
  if(!EXT_GLOBAL(cfg)->tmpdir) { init_channel_raw(env); return; }

  /* pipe communication channel only available in servlets */
  (*env)->pipe.channel=0;
  if(!EXT_GLOBAL(option_set_by_user) (U_SERVLET, EXT_GLOBAL(ini_user))) return;
  length=strlen(EXT_GLOBAL(cfg)->tmpdir)+sizeof(sockname);
  pipe=malloc(length+2); /* "name.i" */
  assert(pipe); if(!pipe) exit(6);
  
  strcpy(pipe, EXT_GLOBAL(cfg)->tmpdir); strcat(pipe, sockname);
  create_pipes(env, pipe, length-1);
  if(!(*env)->pipe.channel) free(pipe);
}
/**
 * Remove the link to the pipe. Note that this does not destroy
 * the channel, it only removes its name
 */
void EXT_GLOBAL(unlink_channel)(proxyenv*env) {
  char *channel = (*env)->pipe.channel;
  if(!channel) return;

  remove_pipe(env);

  free(channel); free((*env)->pipe.in); free((*env)->pipe.out);

  (*env)->pipe.in = (*env)->pipe.out = (*env)->pipe.channel = 0;
}
#endif
const char *EXT_GLOBAL(get_channel) (proxyenv*env) {
  static const char empty[] = "";
  char *channel = (*env)->pipe.channel;
  if(channel) return channel;
  return empty;
}
void EXT_GLOBAL(clone_cfg)(TSRMLS_D) {
  JG(ini_user)=EXT_GLOBAL(ini_user);
  if(JG(hosts)) free(JG(hosts));
  if(!(JG(hosts)=strdup(EXT_GLOBAL(cfg)->hosts))) exit(9);
  if(JG(servlet)) free(JG(servlet));
  if(!(JG(servlet)=strdup(EXT_GLOBAL(cfg)->servlet))) exit(9);
}
void EXT_GLOBAL(passivate_connection)(proxyenv *env TSRMLS_DC) {
  (*env)->cfg.ini_user=JG(ini_user);
  if((*env)->cfg.hosts) free(((*env)->cfg.hosts));
  if(!((*env)->cfg.hosts=strdup(JG(hosts)))) exit(9);
  if((*env)->cfg.servlet) free(((*env)->cfg.servlet));
  if(!((*env)->cfg.servlet=strdup(JG(servlet)))) exit(9);
}
/* see override_ini_for_redirect */
void EXT_GLOBAL(activate_connection)(proxyenv *env TSRMLS_DC) {
  JG(ini_user)=(*env)->cfg.ini_user;

  //assert(!JG(hosts));
  if(JG(hosts)) free(JG(hosts)); 
  if(!(JG(hosts)=strdup((*env)->cfg.hosts))) exit(9);

  //assert(!JG(servlet));
  if(JG(servlet)) free(JG(servlet)); 
  if(!(JG(servlet)=strdup((*env)->cfg.servlet))) exit(9);
}
void EXT_GLOBAL(destroy_cloned_cfg)(TSRMLS_D) {
  if(JG(hosts)) free(JG(hosts));
  if(JG(servlet)) free(JG(servlet));
  JG(ini_user)=0;
  JG(hosts)=0;
  JG(servlet)=0;
}
char *EXT_GLOBAL(getDefaultSessionFactory)(TSRMLS_D) {
  static const char invalid[] = "0";
  register char *context = JG(servlet_ctx);
  return context?context:(char*)invalid;
}

#ifndef PHP_WRAPPER_H
#error must include php_wrapper.h
#endif
