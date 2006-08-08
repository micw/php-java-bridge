/*-*- mode: C; tab-width:4 -*-*/

#include "php_java.h"

#include <stdarg.h>
#include <errno.h>
#include <string.h>
#include <stdlib.h>
#include <unistd.h>

#include "protocol.h"
#include "java_bridge.h"
#include "php_java_snprintf.h"
#include "sio.h"

#define SLEN 256 // initial length of the parser string
#define SEND_SIZE 8192 // initial size of the send buffer

#define ILEN 40 // integer, double representation.
#define PRECISION "14" /* 15 .. 17 digits - 1 */
#define FLEN 160 // the max len of the following format strings

#define GROW(size) { \
  flen = size; \
  if((*env)->send_len+size>=(*env)->send_size) { \
    size_t nsize = (*env)->send_len+size+SEND_SIZE; \
	(*env)->send=realloc((*env)->send, (*env)->send_size=nsize); \
	assert((*env)->send); if(!(*env)->send) exit(9); \
  } \
}
#ifdef DISABLE_HEX
#define HEX_ARG "%ld"
#else
#define HEX_ARG "%lx"
#endif

static char *getSessionFactory(proxyenv *env) {
  static const char invalid[] = "0";
  register char *context = (*env)->servlet_ctx;
  return context?context:(char*)invalid;
}
static char*get_context(proxyenv *env, char context[CONTEXT_LEN_MAX], short*context_length) {
	size_t l = strlen((*env)->servlet_ctx);
	
	assert(l<CONTEXT_LEN_MAX);
	*context_length = 
	  EXT_GLOBAL(snprintf) (context, 
							CONTEXT_LEN_MAX, 
							"%c%c%s", 
							077, l&0xFF,
							(*env)->servlet_ctx);
	return context;
}

/**
 * Send the packet to the server
 */
static short send_data(proxyenv *env, char *data, size_t size) {
  size_t s=0; ssize_t n=0;
 res: 
  errno=0;
  while((size>s)&&((n=(*env)->f_send(env, data+s, size-s)) > 0)) 
	s+=n;
  if(size>s && !n && errno==EINTR) goto res; // Solaris, see INN FAQ
  return n!=-1;
}

static short add_header(proxyenv *env, size_t *size, char*header, size_t header_length) {
  if(!header_length) return 1;

  if(*size+header_length<(*env)->send_size) {
	memmove((*env)->send+header_length, (*env)->send, *size);
	memcpy(((*env)->send), header, header_length);
	*size+=header_length;
	return 1;
  } else {
	return send_data(env, header, header_length);
  }
}

static short end(proxyenv *env) {
  short success;
  size_t size = (*env)->send_len;
  char *servlet_context;
  char *context;
  short context_length = 0;
  char kontext[CONTEXT_LEN_MAX];
  /* get the context for the re-redirected connection */
  if((*env)->must_reopen==2) context = get_context(env, kontext, &context_length);
  (*env)->must_reopen=0;

  TSRMLS_FETCH();

  servlet_context = EXT_GLOBAL (get_servlet_context) (TSRMLS_C);

  if(!(*env)->is_local && servlet_context) {
	char header[SEND_SIZE];
	int header_length;
	unsigned char mode = EXT_GLOBAL (get_mode) ();

	assert(!(*env)->peer_redirected || ((*env)->peer_redirected && (((*env)->peer0)==-1)));
	header_length=EXT_GLOBAL(snprintf) (header, sizeof(header), "PUT %s HTTP/1.1\r\nHost: localhost\r\nConnection: Close\r\nContent-Type: text/html\r\nContent-Length: %lu\r\nX_JAVABRIDGE_CHANNEL: %s\r\nX_JAVABRIDGE_CONTEXT: %s\r\nX_JAVABRIDGE_CONTEXT_DEFAULT: %s\r\n\r\n%c", servlet_context, (unsigned long)(size+1), EXT_GLOBAL(get_channel)(env), getSessionFactory(env), EXT_GLOBAL(getDefaultSessionFactory)(TSRMLS_C), mode);

	success = add_header(env, &size, header, header_length);
  } else {						/* re-directed */
	success = add_header(env, &size, context, context_length);
  }
  if(success) success = send_data(env, (char*)(*env)->send, size);
  return success;
}

static char *get_cookies(zval *val, proxyenv *env) {
  static const char zero[] = "", cookies[] = "\
get_cookies();\
function get_cookies() {\
  $str=\"\";\
  $first=true;\
  foreach($_COOKIE as $k => $v) {\
    $str = $str . ($first ? \"Cookie: $k=$v\":\"; $k=$v\");\
    $first=false;\
  }\
  if(!$first) $str = $str . '\r\n';\
  return $str;\
}\
";
  TSRMLS_FETCH();
  if((SUCCESS==zend_eval_string((char*)cookies, val, "cookies" TSRMLS_CC)) && (Z_TYPE_P(val)==IS_STRING)) {
	return Z_STRVAL_P(val);
  }
  assert(0);
  return (char*)zero;
}  

/**
 * Used by getSession() to aquire the session context.
 * Sends override_redirect and cookies
 * @see check_context
 */
static short end_session(proxyenv *env) {
  short success;
  size_t size = (*env)->send_len;
  zval val;
  int peer0 = (*env)->peer0;
  char header[SEND_SIZE];
  int header_length;
  short override_redirect = (peer0!=-1)?1:2;
  unsigned char mode = EXT_GLOBAL (get_mode) ();

  TSRMLS_FETCH();

  (*env)->finish=end;
  
  header_length=EXT_GLOBAL(snprintf) (header, sizeof(header), "PUT %s HTTP/1.1\r\nHost: localhost\r\nConnection: Close\r\nContent-Type: text/html\r\nContent-Length: %lu\r\nX_JAVABRIDGE_REDIRECT: %d\r\n%sX_JAVABRIDGE_CHANNEL: %s\r\nX_JAVABRIDGE_CONTEXT: %s\r\nX_JAVABRIDGE_CONTEXT_DEFAULT: %s\r\n\r\n%c", (*env)->servlet_context_string, (unsigned long)(size+1), override_redirect, get_cookies(&val, env), EXT_GLOBAL(get_channel)(env), getSessionFactory(env), EXT_GLOBAL(getDefaultSessionFactory)(TSRMLS_C), mode);

  success = add_header(env, &size, header, header_length);
  if(success) success = send_data(env, (char*)(*env)->send, size);
  return success;
}

/**
 * Send out the data to the back-end.
 * @param env The proxyenv
 * @return 1 on success, 0 if the connection the back-end is lost
 */
static short finish(proxyenv *env) {
  short success = (*env)->finish(env);
  if(!success) return 0;
  (*env)->send_len=0;
  return 1;
}

/**
 * Send out the data to the back-end and read the response.
 * @param env The proxyenv
 * @return 1 on success, 0 if the connection the back-end is lost
 */
static short flush(proxyenv *env) {
  short success;
  if((*env)->connection_is_closed) return 0;
  success = finish(env);
  if(success) success=(*env)->handle(env);

  if(!success) (*env)->connection_is_closed = 1;

  return success;
}

static short end_connection (proxyenv *env) {
  short success;
  char *servlet_context;
  short context_length = 0;
  char *context;
  size_t size = (*env)->send_len;
  char kontext[CONTEXT_LEN_MAX];
  unsigned char mode = EXT_GLOBAL (get_mode) ();

  if((*env)->must_reopen==2) context = get_context(env, kontext, &context_length);
  (*env)->must_reopen=0;
  (*env)->finish=end;

  TSRMLS_FETCH();

  servlet_context = EXT_GLOBAL (get_servlet_context) (TSRMLS_C);

  if(!(*env)->is_local && servlet_context) {
	char header[SEND_SIZE];
	int header_length;

	assert(!(*env)->peer_redirected);

	header_length=EXT_GLOBAL(snprintf) (header, sizeof(header), "PUT %s HTTP/1.1\r\nHost: localhost\r\nConnection: Close\r\nContent-Type: text/html\r\nContent-Length: %lu\r\nX_JAVABRIDGE_CHANNEL: %s\r\nX_JAVABRIDGE_CONTEXT: %s\r\nX_JAVABRIDGE_CONTEXT_DEFAULT: %s\r\n\r\n%c", servlet_context, (unsigned long)(size+1), EXT_GLOBAL(get_channel)(env),  getSessionFactory(env), EXT_GLOBAL(getDefaultSessionFactory)(TSRMLS_C), mode);

	success = add_header(env, &size, header, header_length);
	if(success) success = send_data(env, (char*)(*env)->send, size);
  } else {						/* re-directed */
	success = add_header(env, &size, context, context_length);
	if(success) success = send_data(env, (char*)(*env)->send, size);
  }
  return success;
}
static ssize_t send_async(proxyenv*env, const void*buf, size_t length) {
  return EXT_GLOBAL(sfwrite)(buf, length, (*env)->async_ctx.peer);
}
static ssize_t send_pipe(proxyenv*env, const void*buf, size_t length) {
  return write((*env)->peer, buf, length);
}
static ssize_t send_socket(proxyenv*env, const void*buf, size_t length) {
  return send((*env)->peer, buf, length, 0);
}

static ssize_t recv_pipe(proxyenv*env, void*buf, size_t length) {
  return read((*env)->peerr, buf, length);
}

static ssize_t recv_socket(proxyenv*env, void*buf, size_t length) {
  return recv((*env)->peer, buf, length, 0);
}

short EXT_GLOBAL(begin_async) (proxyenv*env) {
  assert(((*env)->peer0==-1));	/* secondary "override redirect"
								   channel cannot be used during
								   stream mode */
  if(((*env)->peer) != -1) (*env)->async_ctx.peer = EXT_GLOBAL(sfdopen)(env);

  if((*env)->async_ctx.peer) {
								/* save default f_send, use send_async */
	(*env)->handle = (*env)->async_ctx.handle_request;
	(*env)->async_ctx.f_send = (*env)->f_send;
	(*env)->f_send = (*env)->f_send0 = send_async;
	return 1;
  } else {
	return 0;
  }
}
void EXT_GLOBAL(end_async) (proxyenv*env) {
  assert(((*env)->peer0==-1));	/* secondary "override redirect"
								   channel cannot be used during
								   stream mode */
  (*env)->handle=(*env)->handle_request;
  if((*env)->async_ctx.peer) {
	int err = EXT_GLOBAL(sfclose)((*env)->async_ctx.peer);
								/* restore default f_send */
	(*env)->f_send = (*env)->f_send0 = (*env)->async_ctx.f_send;
	(*env)->async_ctx.peer = 0;
	if(err) EXT_GLOBAL(sys_error)("could not close async buffer",93);
  }
}
void EXT_GLOBAL(redirect_pipe)(proxyenv*env) {
	(*env)->f_recv0 = (*env)->f_recv = recv_pipe;
	(*env)->f_send0 = (*env)->f_send = send_pipe;
}
void EXT_GLOBAL(check_session) (proxyenv *env TSRMLS_DC) {
  if(!(*env)->is_local && IS_SERVLET_BACKEND(env) && !(*env)->backend_has_session_proxy) {
	if((*env)->peer_redirected) { /* override redirect */
	  int sock = socket (PF_INET, SOCK_STREAM, 0);
	  struct sockaddr *saddr = &(*env)->orig_peer_saddr;
	  if (-1!=sock) {
		static const int is_true = 1;
		setsockopt(sock, 0x6, TCP_NODELAY, (void*)&is_true, sizeof is_true);
		if (-1!=connect(sock, saddr, sizeof (struct sockaddr))) {
		  (*env)->peer0 = (*env)->peer;
		  (*env)->peer = sock;
		  (*env)->f_recv = recv_socket;
		  (*env)->f_send = send_socket;
		} else {				/* could not connect */
		  close(sock);
		  EXT_GLOBAL(sys_error)("Could not connect to server",78);
		}
	  } else
		EXT_GLOBAL(sys_error)("Could not create socket",79);
	}
	(*env)->finish=end_session;
  }
}

void EXT_GLOBAL(setResultWith_context) (char*key, char*val, char*path) {
  static const char empty[] = "/";
  static const char name[] = "setResultWith_cookie";
  static const char cmd[] = "\n\
$path=trim('%s');\n\
if($path[0]!='/') $path='/'.$path;\n\
setcookie('%s', '%s', 0, $path);\n\
";
  char buf[1024];
  int ret;

  TSRMLS_FETCH();

  /* if path is empty or if java.servlet=On, discard path value. Use
	 java.servlet=User to retain the path */
  if(!path || 
	 (EXT_GLOBAL(cfg)->servlet_is_default
	  && !(EXT_GLOBAL(cfg)->is_cgi_servlet))) {
	path = (char*)empty;
  }

  EXT_GLOBAL(snprintf)(buf, sizeof(buf), (char*)cmd, path, key, val);
  ret = zend_eval_string((char*)buf, 0, (char*)name TSRMLS_CC);
  assert(SUCCESS==ret);
}

  
#define GROW_QUOTE() \
  if(pos+8>=newlen) { \
    newlen=newlen+newlen/10; \
    new=realloc(new, newlen+8); \
    assert(new); if(!new) exit(9); \
  } 
static char* replaceQuote(char *name, size_t len, size_t *ret_len) {
  static const char quote[]="&quot;";
  static const char amp[]="&amp;";
  register size_t newlen=len+8+len/10, pos=0;
  char c, *new = malloc(newlen);
  register short j;
  assert(new); if(!new) exit(9);
  
  while(len--) {
	switch (c=*name++) {
	case '\"':
	  {
		for(j=0; j<(sizeof(quote)-1); j++) {
		  new[pos++]=quote[j]; 
		  GROW_QUOTE();
		}
	  } 
	  break;
	case '&':
	  {
		for(j=0; j<(sizeof(amp)-1); j++) {
		  new[pos++]=amp[j]; 
		  GROW_QUOTE();
		}
	  } 
	  break;
	default: 
	  {
		new[pos++]=c;
		GROW_QUOTE();
	  }
	}
  }

  new[pos]=0;
  *ret_len=pos;
  return new;
}
 static void CreateObjectBegin(proxyenv *env, char*name, size_t len, char createInstance, void *result) {
   size_t flen;
   assert(createInstance=='C' || createInstance=='I');
   if(!len) len=strlen(name);
   GROW(FLEN+ILEN+len);
   (*env)->send_len+=EXT_GLOBAL(snprintf) ((char*)((*env)->send+(*env)->send_len), flen, "<C v=\"%s\" p=\"%c\" i=\""/**/HEX_ARG/**/"\">", name, createInstance, (unsigned long)((*env)->async_ctx.result=result));
   assert((*env)->send_len<=(*env)->send_size);
 }
 static short CreateObjectEnd(proxyenv *env) {
   size_t flen;
   GROW(FLEN);
   (*env)->send_len+=EXT_GLOBAL(snprintf)((char*)((*env)->send+(*env)->send_len), flen, "</C>");
   assert((*env)->send_len<=(*env)->send_size);
   return flush(env);
 }
 static void InvokeBegin(proxyenv *env, unsigned long object, char*method, size_t len, char property, void* result) {
   size_t flen;
   assert(property=='I' || property=='P');
   if(!len) len=strlen(method);
   GROW(FLEN+ILEN+len+ILEN);
   (*env)->send_len+=EXT_GLOBAL(snprintf)((char*)((*env)->send+(*env)->send_len), flen, "<I v=\""/**/HEX_ARG/**/"\" m=\"%s\" p=\"%c\" i=\""/**/HEX_ARG/**/"\">", object, method, property, (unsigned long)((*env)->async_ctx.result=result));
   assert((*env)->send_len<=(*env)->send_size);
 }
 static short InvokeEnd(proxyenv *env) {
   size_t flen;
   GROW(FLEN);
   (*env)->send_len+=EXT_GLOBAL(snprintf)((char*)((*env)->send+(*env)->send_len), flen, "</I>");
   assert((*env)->send_len<=(*env)->send_size);
   return flush(env);
 }

 static void ResultBegin(proxyenv *env, void*result) {
   size_t flen;
   GROW(FLEN+ILEN);
   (*env)->send_len+=EXT_GLOBAL(snprintf)((char*)((*env)->send+(*env)->send_len), flen, "<R i=\""/**/HEX_ARG/**/"\">", (unsigned long)((*env)->async_ctx.result=result));
   assert((*env)->send_len<=(*env)->send_size);
 }
 static void ResultEnd(proxyenv *env) {
   size_t flen;
   GROW(FLEN);
   (*env)->send_len+=EXT_GLOBAL(snprintf)((char*)((*env)->send+(*env)->send_len), flen, "</R>");
   assert((*env)->send_len<=(*env)->send_size);
   finish(env);
 }

static short EndConnection(proxyenv *env, char property) {
  size_t flen;
  assert(property=='A' || property=='E');
  GROW(FLEN);
  (*env)->send_len+=EXT_GLOBAL(snprintf)((char*)((*env)->send+(*env)->send_len), flen, "<F p=\"%c\"/>", property);
  assert((*env)->send_len<=(*env)->send_size);

  (*env)->finish=end_connection;
  return flush(env);
}

 static void String(proxyenv *env, char*name, size_t _len) {
   size_t flen;
   static const char Sb[]="<S v=\"";
   static const char Se[]="\"/>";
   size_t send_len;
   size_t len, slen;
   assert(_len || !strlen(name));
   name = replaceQuote(name, _len, &len);
   send_len = (sizeof (Sb)-1) + (sizeof (Se)-1) + len;
   GROW(send_len);
   slen=(*env)->send_len;
   memcpy((*env)->send+slen, Sb, sizeof(Sb)-1); slen+=sizeof(Sb)-1;
   memcpy((*env)->send+slen, name, len); slen+=len;
   memcpy((*env)->send+slen, Se, sizeof(Se)-1);

   (*env)->send_len+=send_len;
   assert((*env)->send_len<=(*env)->send_size);
   free(name);
 }
 static void Boolean(proxyenv *env, short boolean) {
   size_t flen;
   GROW(FLEN);
   (*env)->send_len+=EXT_GLOBAL(snprintf)((char*)((*env)->send+(*env)->send_len), flen, "<B v=\"%c\"/>", boolean?'T':'F');
   assert((*env)->send_len<=(*env)->send_size);
 }
#ifdef DISABLE_HEX
 static void Long(proxyenv *env, long l) {
   size_t flen;
   GROW(FLEN+ILEN);
   (*env)->send_len+=EXT_GLOBAL(snprintf)((char*)((*env)->send+(*env)->send_len), flen, "<L v=\"%ld\"/>", l);
   assert((*env)->send_len<=(*env)->send_size);
 }
#else
 static void Long(proxyenv *env, long l) {
   size_t flen;
   GROW(FLEN+ILEN);
   if(l<0)
	 (*env)->send_len+=EXT_GLOBAL(snprintf)((char*)((*env)->send+(*env)->send_len), flen, "<L v=\""/**/HEX_ARG/**/"\" p=\"A\"/>", (unsigned long)(-l));
   else
	 (*env)->send_len+=EXT_GLOBAL(snprintf)((char*)((*env)->send+(*env)->send_len), flen, "<L v=\""/**/HEX_ARG/**/"\" p=\"O\"/>", (unsigned long)l);
   assert((*env)->send_len<=(*env)->send_size);
 }
#endif
 static void Double(proxyenv *env, double d) {
   size_t flen;
   GROW(FLEN+ILEN);
   (*env)->send_len+=EXT_GLOBAL(snprintf)((char*)((*env)->send+(*env)->send_len), flen, "<D v=\"%."/**/PRECISION/**/"e\"/>", d);
   assert((*env)->send_len<=(*env)->send_size);
 }
 static void Object(proxyenv *env, unsigned long object) {
   size_t flen;
   GROW(FLEN+ILEN);
   if(!object) 
	 (*env)->send_len+=EXT_GLOBAL(snprintf)((char*)((*env)->send+(*env)->send_len), flen, "<O v=\"\"/>");
   else
	 (*env)->send_len+=EXT_GLOBAL(snprintf)((char*)((*env)->send+(*env)->send_len),flen, "<O v=\""/**/HEX_ARG/**/"\"/>", object);
   assert((*env)->send_len<=(*env)->send_size);
 }
 static void Exception(proxyenv *env, unsigned long object, char *str, size_t len) {
   size_t flen;
   if(!len) len=strlen(str);
   GROW(FLEN+ILEN+len);
   if(!object) 
	 (*env)->send_len+=EXT_GLOBAL(snprintf)((char*)((*env)->send+(*env)->send_len), flen, "<E v=\"\" m=\"%s\"/>", str);
   else
	 (*env)->send_len+=EXT_GLOBAL(snprintf)((char*)((*env)->send+(*env)->send_len),flen, "<E v=\""/**/HEX_ARG/**/"\" m=\"%s\"/>", object, str);
   assert((*env)->send_len<=(*env)->send_size);
 }
 static void CompositeBegin_a(proxyenv *env) {
   size_t flen;
   GROW(FLEN);
   (*env)->send_len+=EXT_GLOBAL(snprintf)((char*)((*env)->send+(*env)->send_len), flen, "<X t=\"A\">");
   assert((*env)->send_len<=(*env)->send_size);
 }
 static void CompositeBegin_h(proxyenv *env) {
   size_t flen;
   GROW(FLEN);
   (*env)->send_len+=EXT_GLOBAL(snprintf)((char*)((*env)->send+(*env)->send_len), flen, "<X t=\"H\">");
   assert((*env)->send_len<=(*env)->send_size);
 }
 static void CompositeEnd(proxyenv *env) {
   size_t flen;
   GROW(FLEN);
   (*env)->send_len+=EXT_GLOBAL(snprintf)((char*)((*env)->send+(*env)->send_len), flen, "</X>");
   assert((*env)->send_len<=(*env)->send_size);
 }
 static void PairBegin_s(proxyenv *env, char*key, size_t len) {
   size_t flen;
   assert(strlen(key));
   if(!len) len=strlen(key);
   GROW(FLEN+len);
   (*env)->send_len+=EXT_GLOBAL(snprintf)((char*)((*env)->send+(*env)->send_len), flen, "<P t=\"S\" v=\"%s\">", key);
   assert((*env)->send_len<=(*env)->send_size);
 }
 static void PairBegin_n(proxyenv *env, unsigned long key) {
   size_t flen;
   GROW(FLEN+ILEN);
   (*env)->send_len+=EXT_GLOBAL(snprintf)((char*)((*env)->send+(*env)->send_len), flen, "<P t=\"N\" v=\""/**/HEX_ARG/**/"\">", key);
   assert((*env)->send_len<=(*env)->send_size);
 }
 static void PairBegin(proxyenv *env) {
   size_t flen;
   GROW(FLEN);
   (*env)->send_len+=EXT_GLOBAL(snprintf)((char*)((*env)->send+(*env)->send_len), flen, "<P>");
   assert((*env)->send_len<=(*env)->send_size);
 }
 static void PairEnd(proxyenv *env) {
   size_t flen;
   GROW(FLEN);
   (*env)->send_len+=EXT_GLOBAL(snprintf)((char*)((*env)->send+(*env)->send_len), flen, "</P>");
   assert((*env)->send_len<=(*env)->send_size);
 }
 static void Unref(proxyenv *env, unsigned long object) {
   size_t flen;
   GROW(FLEN+ILEN);
   (*env)->send_len+=EXT_GLOBAL(snprintf)((char*)((*env)->send+(*env)->send_len), flen, "<U v=\""/**/HEX_ARG/**/"\"/>", object);
   assert((*env)->send_len<=(*env)->send_size);
 }

 

static void close_connection(proxyenv *env TSRMLS_DC) {
  if(env && *env) {
	if((*env)->peer!=-1) { /* end servlet session */

	  /* end async */
	  if((*env)->async_ctx.peer) {
		EXT_GLOBAL(sfclose)((*env)->async_ctx.peer); (*env)->async_ctx.peer=0;
		(*env)->handle = (*env)->handle_request;
		(*env)->f_send = (*env)->f_send0 = (*env)->async_ctx.f_send;
	  }

	  if(!(*env)->connection_is_closed) (*env)->writeEndConnection(env, 'E');
	  if(!(*env)->is_shared) close((*env)->peer);
	}
	if(((*env)->peerr!=-1) && (!(*env)->is_shared)) close((*env)->peerr);
	if(((*env)->peer0!=-1) && (!(*env)->is_shared)) close((*env)->peer0);
	if((*env)->s) free((*env)->s);
	if((*env)->send) free((*env)->send);
	if((*env)->server_name) free((*env)->server_name);
	if((*env)->current_servlet_ctx && (*env)->servlet_ctx != (*env)->current_servlet_ctx) free((*env)->current_servlet_ctx);
	if((*env)->servlet_ctx && (!(*env)->is_shared)) free((*env)->servlet_ctx);
	if((*env)->servlet_context_string) free((*env)->servlet_context_string);
	if((*env)->cfg.hosts) free((*env)->cfg.hosts);
	if((*env)->cfg.servlet) free((*env)->cfg.servlet);
	EXT_GLOBAL(unlink_channel)(env);
	free(*env);
	free(env);
	EXT_GLOBAL(destroy_cloned_cfg)(TSRMLS_C);
  }
}
static short recycle_connection(proxyenv *env TSRMLS_DC) {
  if(env && *env) {
	if((*env)->connection_is_closed) return 0;
	if((*env)->peer!=-1) {

	  if(!(*env)->writeEndConnection(env, 'A')) { 
		(*env)->connection_is_closed=1;
		return 0;
	  }
	}
	EXT_GLOBAL(passivate_connection)(env TSRMLS_CC);
	EXT_GLOBAL(destroy_cloned_cfg)(TSRMLS_C);
	if((*env)->current_servlet_ctx && 
	   (*env)->servlet_ctx != (*env)->current_servlet_ctx) {
	  free((*env)->current_servlet_ctx); 
	}
	(*env)->current_servlet_ctx = 0; 
  }
  return 1;
}
/**
 * Close an active connection. The connection must be active, i.e. JG(cfg).hosts,
 * JG(cfg).servlet and JG(cfg).ini_user must contain the appropriate values.
 *
 * @param env The proxy env
 * @param persistent_connection true for keep alive, false closes physical connection
 * @see activate_connection
 */
short EXT_GLOBAL(close_connection)(proxyenv*env, short persistent_connection TSRMLS_DC) {
  int success = 1;
  if(persistent_connection) success = recycle_connection(env TSRMLS_CC);
  if(!persistent_connection || !success) close_connection(env TSRMLS_CC);
  return success;
}
proxyenv *EXT_GLOBAL(createSecureEnvironment) (int peer, short (*handle_request)(proxyenv *env), short (*handle_cached)(proxyenv *env), char *server_name, short is_local, struct sockaddr *saddr) {
   proxyenv *env;  
   env=(proxyenv*)malloc(sizeof *env);     
   if(!env) return 0;
   *env=(proxyenv)calloc(1, sizeof **env); 
   if(!*env) {free(env); return 0;}

   (*env)->f_recv0 = (*env)->f_recv = recv_socket;
   (*env)->f_send0 = (*env)->f_send = send_socket;

   (*env)->peer = peer;
   (*env)->peer0 = (*env)->peerr = -1;
   (*env)->is_shared = 0;
   (*env)->peer_redirected = 0;
   memcpy(&(*env)->orig_peer_saddr, saddr, sizeof (struct sockaddr));
   
   (*env)->handle = (*env)->handle_request = handle_request;
   (*env)->async_ctx.handle_request = handle_cached;
   (*env)->async_ctx.peer = 0;
   (*env)->async_ctx.nextValue = 0;

   /* parser variables */
   (*env)->pos=(*env)->c = 0;
   (*env)->len = SLEN; 
   (*env)->s=malloc((*env)->len);
   if(!(*env)->s) {free(*env); free(env); return 0;}

   /* send buffer */
   (*env)->send_size=SEND_SIZE;
   (*env)->send=malloc(SEND_SIZE);
   if(!(*env)->send) {free((*env)->s); free(*env); free(env); return 0;}
   (*env)->send_len=0;
   
   (*env)->is_local = is_local;

   (*env)->server_name = server_name;
   (*env)->must_reopen = (*env)->must_share = 0;
   (*env)->connection_is_closed = 0;
   (*env)->current_servlet_ctx = 
	 (*env)->servlet_ctx = (*env)->servlet_context_string = 0;
   (*env)->backend_has_session_proxy = 0;
   (*env)->cfg.ini_user = 0;
   (*env)->cfg.hosts = (*env)->cfg.servlet = 0;

   (*env)->writeInvokeBegin=InvokeBegin;
   (*env)->writeInvokeEnd=InvokeEnd;
   (*env)->writeResultBegin=ResultBegin;
   (*env)->writeResultEnd=ResultEnd;
   (*env)->writeCreateObjectBegin=CreateObjectBegin;
   (*env)->writeCreateObjectEnd=CreateObjectEnd;

   (*env)->writeString=String;
   (*env)->writeBoolean=Boolean;
   (*env)->writeLong=Long;
   (*env)->writeDouble=Double;
   (*env)->writeObject=Object;
   (*env)->writeException=Exception;
   (*env)->writeCompositeBegin_a=CompositeBegin_a;
   (*env)->writeCompositeBegin_h=CompositeBegin_h;
   (*env)->writeCompositeEnd=CompositeEnd;
   (*env)->writePairBegin=PairBegin;
   (*env)->writePairBegin_s=PairBegin_s;
   (*env)->writePairBegin_n=PairBegin_n;
   (*env)->writePairEnd=PairEnd;
   (*env)->writeUnref=Unref;

   (*env)->writeEndConnection=EndConnection;
   (*env)->finish=end;

   return env;
 }

#ifndef PHP_WRAPPER_H
#error must include php_wrapper.h
#endif
