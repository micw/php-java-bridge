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
#include "php_java_strtod.h"

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
  pval *handle;
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
 * Check for exception and communicate the exception back to the
 * server.  Return true if an exception was handled.
 */
static short handle_exception(zval*presult TSRMLS_DC) {
  short has_exception=0;
  if(JG(exception)&&Z_TYPE_P(JG(exception))!=IS_NULL) {
	proxyenv *jenv = EXT_GLOBAL(connect_to_server)(TSRMLS_C);
	long result;
	//php_error(E_WARNING, "php_mod_"/**/EXT_NAME()/**/"(%d): Unhandled exception during callback in user function: %s.", 25, fname);
	EXT_GLOBAL(get_jobject_from_object)(JG(exception), &result TSRMLS_CC);
	has_exception=1;
	(*jenv)->writeResultBegin(jenv, presult);
	(*jenv)->writeException(jenv, result, "php exception", 0);
	(*jenv)->writeResultEnd(jenv);
	zval_ptr_dtor(&JG(exception));
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

      {
      struct stack_elem stack_elem = 
		{ tmp_retval, 'A', 
		  (unsigned char*)strdup((char*)PARSER_GET_STRING(st, 2)), /* m */
		  (unsigned char*)strdup((char*)PARSER_GET_STRING(st, 1)), /* p */
		  st[2].length,			/* m_length */
		  st[1].length,			/* p_length */
		  (*cb->env)->async_ctx.nextValue =
		  strtol((const char*)PARSER_GET_STRING(st, 0), 0, 10), /* v */
		  strtol((const char*)PARSER_GET_STRING(st, 3), 0, 10), /* n */
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

	setResultFromObject(ctx->id, (*cb->env)->async_ctx.nextValue=strtol((const char*)PARSER_GET_STRING(st, 0), 0, 10), *PARSER_GET_STRING(st, 1));
	break;
  case 'E':
	{
	  unsigned char *stringRepresentation=PARSER_GET_STRING(st, 1);
	  size_t len=st[1].length;
	  long obj = strtol((const char*)PARSER_GET_STRING(st, 0), 0, 10);
	  GET_RESULT(2);
	  setException(ctx->id, (*cb->env)->async_ctx.nextValue=obj, stringRepresentation, len);
	  break;
	}
	default:
	  {
		TSRMLS_FETCH();
		assert(((*cb->env)->pos) < RECV_SIZE);
#ifndef __MINGW32__
		php_write((*cb->env)->recv_buf, (*cb->env)->pos TSRMLS_CC);
#endif
		php_error(E_ERROR, "php_mod_"/**/EXT_NAME()/**/"(%d): Protocol violation at pos %d, please check that the backend (JavaBride.war) is deployed or please switch off the java.servlet option.\n", 88, (*cb->env)->c);
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
	  if(strcmp(str, setcookie)) return;
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
	  if(!strcmp(str, con_connection)&&!strcmp((char*)PARSER_GET_STRING(tag[1].strings, 0), con_close)) {
		if(!(*ctx)->must_reopen) (*ctx)->must_reopen = 1;
	  }
	  break;
	}
  case 'X':// Redirect
	{
	  char *key;
	  static const char context[] = "X_JAVABRIDGE_CONTEXT";
	  static const char redirect[]= "X_JAVABRIDGE_REDIRECT";
	  if(!(*ctx)->peer_redirected && !strcmp(str, redirect)) {
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
	  } else if((!(*ctx)->servlet_ctx)&&(!strcmp(str, context))) {
		key = (char*)PARSER_GET_STRING(tag[1].strings, 0);
		(*ctx)->servlet_ctx = strdup(key);
	  }
	  break;
	}
  }
}

/* asyncronuous cb */
static void handle_cached(proxyenv *env) {
  struct async_ctx *ctx = &(*env)->async_ctx;
  setResultFromObject(ctx->result, ++ctx->nextValue, 'O');
}  

/* synchronuous cb */
static void handle_request(proxyenv *env) {
  short tail_call;
  struct parse_ctx ctx = {0};
  parser_cb_t cb = {begin, end, &ctx, env};
  struct stack_elem *stack_elem;

  TSRMLS_FETCH();
 handle_request:
  if(!(*env)->is_local && IS_OVERRIDE_REDIRECT(env)) {
	parser_cb_t cb_header = {begin_header, 0, 0, env};
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

  /* revert override redirect */
  if((*env)->peer0!=-1) {
	close((*env)->peer);
	(*env)->peer = (*env)->peer0;
	(*env)->f_recv = (*env)->f_recv0;
	(*env)->f_send = (*env)->f_send0;
	(*env)->peer0 = -1;
  } else	 /* Override redirect opens a secondary channel to the
			  backend. Skip the following if an override redirect
			  happened in the middle of redirect/reopen handling, i.e.
			  between "begin redirect" above (must_reopen=2) or "begin
			  reopen" (must_reopen=1) and "redirect finish" or "end
			  reopen" (see must_reopen=0 in function end() and
			  protocol_end() in protocol.c).  In other words: handle
			  override redirect _or_ redirect for one packet, but not
			  both. */

  /* re-open a closed HTTP connection */
  if((*env)->must_reopen) {
	char*server;
	if((*env)->must_reopen==2) { // redirect
	  (*env)->peer_redirected = 1;
	  JG(ini_user)&=~(U_SERVLET|U_SOCKNAME);

	  assert((*env)->peer!=-1); if((*env)->peer!=-1) close((*env)->peer);
	  EXT_GLOBAL(redirect)(env, JG(redirect_port), JG(channel_in), JG(channel_out) TSRMLS_CC);
	} else {
	  assert((*env)->peer!=-1); if((*env)->peer!=-1) close((*env)->peer);
	  server = EXT_GLOBAL(test_server)(&(*env)->peer, 0, 0 TSRMLS_CC);
	  assert(server); if(!server) exit(9);
	  free(server);
	}
  }
  if(tail_call) {
	memset(&ctx, 0, sizeof ctx);
	goto handle_request;
  }
}

unsigned char EXT_GLOBAL (get_mode) () {
#ifndef ZEND_ENGINE_2
  // we want arrays as values
  static const unsigned char compat = 3;
#else
  static const unsigned char compat = 0;
#endif
  unsigned short is_level = ((EXT_GLOBAL (ini_user)&U_LOGLEVEL)!=0);
  unsigned short level = 0;
  if (is_level)
	level = EXT_GLOBAL(cfg)->logLevel_val>7?7:EXT_GLOBAL(cfg)->logLevel_val;
  
  return (is_level<<7)|64|(level<<2)|compat|EXT_GLOBAL(cfg)->extJavaCompatibility;
}

/**
 * Adjust the standard environment for the current request before we
 * connect to the backend. Used by Fast CGI.
 */
static void override_ini_for_redirect(TSRMLS_D) {
  static const char name[] = "override_ini_for_redirect";
  static const char override[] = "(array_key_exists('HTTP_X_JAVABRIDGE_OVERRIDE_HOSTS_REDIRECT', $_SERVER)?$_SERVER['HTTP_X_JAVABRIDGE_OVERRIDE_HOSTS_REDIRECT']:(array_key_exists('X_JAVABRIDGE_OVERRIDE_HOSTS_REDIRECT', $_SERVER)?$_SERVER['X_JAVABRIDGE_OVERRIDE_HOSTS_REDIRECT']:null));";

  zval val;
  if((SUCCESS==zend_eval_string((char*)override, &val, (char*)name TSRMLS_CC)) && (Z_TYPE(val)==IS_STRING)) {
	/* request servlet -> fast cgi server: connect back to servlet */

	char *kontext, *hosts;
	hosts = malloc(Z_STRLEN(val)+1);
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
	  static const char default_servlet[] = DEFAULT_SERVLET;
	  static const char name[] = "get_self";
	  static const char override[] = "(array_key_exists('PHP_SELF', $_SERVER) && \n\
array_key_exists('HTTP_HOST', $_SERVER)) ?$_SERVER['PHP_SELF']:null;";
	  char *tmp, *strval;
	  size_t len = 0;
	  if((SUCCESS==zend_eval_string((char*)override, &val, (char*)name TSRMLS_CC)) && (Z_TYPE(val)==IS_STRING) && Z_STRLEN(val)) {
		strval = Z_STRVAL(val);
		len = Z_STRLEN(val);
		if(len && *strval!='/') len=0;
	  }
	  if(!len) {
		strval = (char*)default_servlet;
		len = sizeof(default_servlet)-1;
	  }
	  assert(JG(servlet));
	  free(JG(servlet));
	  JG(servlet) = tmp = malloc(len+sizeof(bridge_ext));
	  assert(tmp); if(!tmp) exit(6);
	  strcpy(tmp, strval);
	  strcat(tmp, bridge_ext);
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
static proxyenv*adjust_servlet_environment(proxyenv *env TSRMLS_DC) {
  static const char name[] = "adjust_environment";
  static const char context[] = "(array_key_exists('HTTP_X_JAVABRIDGE_CONTEXT', $_SERVER)?$_SERVER['HTTP_X_JAVABRIDGE_CONTEXT']:(array_key_exists('X_JAVABRIDGE_CONTEXT', $_SERVER)?$_SERVER['X_JAVABRIDGE_CONTEXT']:null));";

  zval val;
  char *servlet_context_string = EXT_GLOBAL (get_servlet_context) (TSRMLS_C);

  if(servlet_context_string)
	(*env)->servlet_context_string = strdup(servlet_context_string);
  if((SUCCESS==zend_eval_string((char*)context, &val, (char*)name TSRMLS_CC)) && (Z_TYPE(val)==IS_STRING)) {
	(*env)->servlet_ctx = strdup(Z_STRVAL(val));
								/* backend must have created a session
								   proxy, otherwise we wouldn't see a
								   context. */
	(*env)->backend_has_session_proxy = 1;	
  }
  return env;
}
static proxyenv *try_connect_to_server(short bail TSRMLS_DC) {
  char *server;
  int sock;
  short is_local;
  struct sockaddr saddr;
  proxyenv *jenv =JG(jenv);
  if(jenv) return jenv;

  if(!EXT_GLOBAL(cfg)->is_cgi_servlet || EXT_GLOBAL(cfg)->is_fcgi_servlet) 
	override_ini_for_redirect(TSRMLS_C);

  if(JG(is_closed)) {
	php_error(E_ERROR, "php_mod_"/**/EXT_NAME()/**/"(%d): Could not connect to server: Session is closed. -- This usually means that you have tried to access the server in your class' __destruct() method.",51);
	return 0;
  }
  if(!(server=EXT_GLOBAL(test_server)(&sock, &is_local, &saddr TSRMLS_CC))) {
	if (bail) 
	  EXT_GLOBAL(sys_error)("Could not connect to server. Have you started the "/**/EXT_NAME()/**/" backend (either a servlet engine, an application server, JavaBridge.jar or MonoBridge.exe) and set the "/**/EXT_NAME()/**/".socketname or "/**/EXT_NAME()/**/".hosts option?",52);
	return 0;
  }

  jenv = EXT_GLOBAL(createSecureEnvironment)
	(sock, handle_request, handle_cached, server, is_local, &saddr);
  
  if(is_local || !EXT_GLOBAL (get_servlet_context) (TSRMLS_C)) {
	/* "standard" local backend, send the protocol header */
	unsigned char mode = EXT_GLOBAL (get_mode) ();
	send(sock, &mode, sizeof mode, 0); 
  } else {
	/* create a jenv for a servlet backend, aquire a context then
	   redirect */
	jenv = adjust_servlet_environment(jenv TSRMLS_CC);
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
void EXT_GLOBAL(init_channel)(TSRMLS_D) {
  JG(channel_in) = JG(channel_out) = JG(channel) = 0;
}
void EXT_GLOBAL(destroy_channel)(TSRMLS_D) {
}

#else

static const char in[] = ".i";
static const char out[] = ".o";
static short create_pipe(char*sockname TSRMLS_DC) {
  if((mknod(sockname, S_IFIFO, 0) == -1) || chmod(sockname, 0666) == -1) return 0;
  return 1;
}
static short create_pipes(char*basename, size_t basename_len TSRMLS_DC) {
  char *e = basename+basename_len;
  short success;
  if((JG(lockfile) = mkstemp(basename)) == -1) return 0;
  if(!create_pipe(strcat(basename, in) TSRMLS_CC)) {
	*e=0;
	unlink(basename);
	return 0;
  }
  *e=0;
  success = create_pipe(strcat(basename, out) TSRMLS_CC);
  assert(success); if(!success) exit(6);
  JG(channel_out) = strdup(basename);
  *e=0;
  JG(channel_in) = strdup(strcat(basename, in));
  *e=0;
  JG(channel) = basename;
}
void EXT_GLOBAL(init_channel)(TSRMLS_D) {
  static const char sockname[] = SOCKNAME;
  static const char sockname_shm[] = SOCKNAME_SHM;
  static const char length = sizeof(sockname)>sizeof(sockname_shm)?sizeof(sockname):sizeof(sockname_shm);
  char *pipe;

  /* pipe communication channel only available in servlets */
  JG(channel)=0;
  if(!EXT_GLOBAL(option_set_by_user) (U_SERVLET, EXT_GLOBAL(ini_user))) return;

  pipe=malloc(length+2); /* "name.i" */
  assert(pipe); if(!pipe) exit(6);
  
  create_pipes(strcpy(pipe,sockname_shm), sizeof(sockname_shm)-1 TSRMLS_CC)||
	create_pipes(strcpy(pipe,sockname), sizeof(sockname)-1 TSRMLS_CC);

  if(!JG(channel)) free(pipe);
  //assert(JG(channel));
}

void EXT_GLOBAL(destroy_channel)(TSRMLS_D) {
  char *channel = (JG(channel));
  if(!channel) return;

  close(JG(lockfile)); unlink(channel);
  unlink(JG(channel_in));
  unlink(JG(channel_out));
  
  free(channel);
  free(JG(channel_in));
  free(JG(channel_out));
  JG(channel_in) = JG(channel_out) = (JG(channel)) = 0;
}
#endif
const char *EXT_GLOBAL(get_channel) (TSRMLS_D) {
  static const char empty[] = "";
  char *channel = JG(channel);
  if(channel) return channel;
  return empty;
}

#ifndef PHP_WRAPPER_H
#error must include php_wrapper.h
#endif
