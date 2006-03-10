/*-*- mode: C; tab-width:4 -*-*/

#include <stdarg.h>
#include <errno.h>
#include <string.h>
#include <stdlib.h>

#include "protocol.h"
#include "php_java.h"
#include "java_bridge.h"
#include "php_java_snprintf.h"

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

static char *getSessionFactory(proxyenv *env) {
  static char invalid[] = "0";
  register char *context = (*env)->servlet_ctx;
  return context?context:invalid;
}

static void send_context(proxyenv *env) {
	size_t l = strlen((*env)->servlet_ctx);
	char context[256] = { 077, l&0xFF};
	int context_length = 2;
	ssize_t n;
	
	assert(l<256);
	context_length += 
	  EXT_GLOBAL(snprintf) (context+context_length, 
							sizeof(context)-context_length, 
							"%s", 
							(*env)->servlet_ctx);
	n=(*env)->f_send(env, (*env)->peer, context, context_length);
	assert(n==context_length);
}

static void end(proxyenv *env) {
  size_t s=0, size = (*env)->send_len;
  ssize_t n=0;
  char *servlet_context;
  /* send the context for the re-redirected connection */
  if((*env)->must_reopen==2) send_context(env);
  (*env)->must_reopen=0;

  TSRMLS_FETCH();

  servlet_context = EXT_GLOBAL (get_servlet_context) (TSRMLS_C);

  if(!(*env)->is_local && servlet_context) {
	char header[SEND_SIZE];
	int header_length;
	unsigned char mode = EXT_GLOBAL (get_mode) ();
	ssize_t n;

	assert(!(*env)->peer_redirected || ((*env)->peer_redirected && ((*env)->peer0)!=-1));
	header_length=EXT_GLOBAL(snprintf) (header, sizeof(header), "PUT %s HTTP/1.1\r\nHost: localhost\r\nConnection: Keep-Alive\r\nContent-Type: text/html\r\nContent-Length: %lu\r\nX_JAVABRIDGE_CHANNEL: %s\r\nX_JAVABRIDGE_CONTEXT: %s\r\n\r\n%c", servlet_context, (unsigned long)(size+1), EXT_GLOBAL(get_channel)(TSRMLS_C), getSessionFactory(env), mode);

	n=(*env)->f_send(env, (*env)->peer, header, header_length);
	assert(n==header_length);
  }
  n=0;

 res: 
  errno=0;
  while((size>s)&&((n=(*env)->f_send(env, (*env)->peer, (*env)->send+s, size-s)) > 0)) 
	s+=n;
  if(size>s && !n && errno==EINTR) goto res; // Solaris, see INN FAQ

  (*env)->send_len=0;
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
static void end_session(proxyenv *env) {
  size_t s=0, size = (*env)->send_len;
  ssize_t n;
  zval val;
  int peer0 = (*env)->peer0;
  int peer = (*env)->peer;
  char header[SEND_SIZE];
  int header_length;
  short override_redirect = (peer0!=-1)?1:2;
  unsigned char mode = EXT_GLOBAL (get_mode) ();

  TSRMLS_FETCH();

  (*env)->finish=end;
  
  assert(!(*env)->peer_redirected || ((*env)->peer_redirected && ((*env)->peer0)!=-1));
  header_length=EXT_GLOBAL(snprintf) (header, sizeof(header), "PUT %s HTTP/1.1\r\nHost: localhost\r\nConnection: Keep-Alive\r\nContent-Type: text/html\r\nContent-Length: %lu\r\nX_JAVABRIDGE_REDIRECT: %d\r\n%sX_JAVABRIDGE_CHANNEL: %s\r\nX_JAVABRIDGE_CONTEXT: %s\r\n\r\n%c", (*env)->servlet_context_string, (unsigned long)(size+1), override_redirect, get_cookies(&val, env), EXT_GLOBAL(get_channel)(TSRMLS_C), getSessionFactory(env), mode);
  n=(*env)->f_send(env, peer, header, header_length);
  assert(n==header_length);
  n=0;

 res: 
  errno=0;
  while((size>s)&&((n=(*env)->f_send(env, (*env)->peer, (*env)->send+s, size-s)) > 0)) 
	s+=n;
  if(size>s && !n && errno==EINTR) goto res; // Solaris, see INN FAQ

  (*env)->send_len=0;

}

static void flush(proxyenv *env) {
  (*env)->finish(env);
  (*env)->handle(env);
}

void EXT_GLOBAL (protocol_end) (proxyenv *env) {
  char *servlet_context;
  
  TSRMLS_FETCH();
  servlet_context = EXT_GLOBAL (get_servlet_context) (TSRMLS_C);

  if(!(*env)->is_local && servlet_context) {
	ssize_t n;
	char header[SEND_SIZE];
	int header_length;

	assert(!(*env)->peer_redirected);

	header_length=EXT_GLOBAL(snprintf) (header, sizeof(header), "PUT %s HTTP/1.1\r\nHost: localhost\r\nConnection: Close\r\nContent-Type: text/html\r\nContent-Length: 0\r\nX_JAVABRIDGE_CONTEXT: %s\r\n\r\n", servlet_context, getSessionFactory(env));

	n=(*env)->f_send(env, (*env)->peer, header, header_length);
	assert(n==header_length);
  } else {
	if((*env)->must_reopen==2) send_context(env);
	(*env)->must_reopen=0;
  }
}
static ssize_t send_pipe(proxyenv*env, int peer, const void*buf, size_t length) {
  return write(peer, buf, length);
}
static ssize_t send_socket(proxyenv*env, int peer, const void*buf, size_t length) {
  return send(peer, buf, length, 0);
}

static ssize_t recv_pipe(proxyenv*env, void*buf, size_t length) {
  return read((*env)->peerr, buf, length);
}

static ssize_t recv_socket(proxyenv*env, void*buf, size_t length) {
  return recv((*env)->peer, buf, length, 0);
}

#ifndef __MINGW32__
void EXT_GLOBAL(redirect)(proxyenv*env, char*redirect_port, char*channel_in, char*channel_out TSRMLS_DC) {
  assert(redirect_port);
  if(*redirect_port!='/') { /* socket */
	char *server = EXT_GLOBAL(test_server)(&(*env)->peer, 0, 0 TSRMLS_CC);
	assert(server); if(!server) exit(9);
	free(server);
  } else {						/* pipe */
	(*env)->peerr = open(channel_in, O_RDONLY);
	(*env)->peer = open(channel_out, O_WRONLY);
	if((-1==(*env)->peerr) || (-1==(*env)->peer)) {
	  php_error(E_ERROR, "php_mod_"/**/EXT_NAME()/**/"(%d): Fatal: could not open comm. pipe.",92);
	  exit(9);
	}
	(*env)->f_recv0 = (*env)->f_recv = recv_pipe;
	(*env)->f_send0 = (*env)->f_send = send_pipe;
  }
}
#else
void EXT_GLOBAL(redirect)(proxyenv*env, char*redirect_port, char*channel_in, char*channel_out TSRMLS_DC) {
  char *server = EXT_GLOBAL(test_server)(&(*env)->peer, 0, 0 TSRMLS_CC);
  assert(server); if(!server) exit(9);
  free(server);
}
#endif
void EXT_GLOBAL(check_session) (proxyenv *env TSRMLS_DC) {
  if(!(*env)->is_local && IS_SERVLET_BACKEND(env) && !(*env)->backend_has_session_proxy) {
	if((*env)->peer_redirected) { /* override redirect */
	  int sock = socket (PF_INET, SOCK_STREAM, 0);
	  struct sockaddr *saddr = &(*env)->orig_peer_saddr;
	  if (-1!=sock) {
		if (-1!=connect(sock, saddr, sizeof (struct sockaddr))) {
		  (*env)->peer0 = (*env)->peer;
		  (*env)->peer = sock;
		  (*env)->f_recv = recv_socket;
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
	 java.servlet=MultiUser to retain the path */
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
   (*env)->send_len+=EXT_GLOBAL(snprintf) ((char*)((*env)->send+(*env)->send_len), flen, "<C v=\"%s\" p=\"%c\" i=\"%ld\">", name, createInstance, (long)((*env)->async_ctx.result=result));
   assert((*env)->send_len<=(*env)->send_size);
 }
 static void CreateObjectEnd(proxyenv *env) {
   size_t flen;
   GROW(FLEN);
   (*env)->send_len+=EXT_GLOBAL(snprintf)((char*)((*env)->send+(*env)->send_len), flen, "</C>");
   assert((*env)->send_len<=(*env)->send_size);
   flush(env);
 }
 static void InvokeBegin(proxyenv *env, long object, char*method, size_t len, char property, void* result) {
   size_t flen;
   assert(property=='I' || property=='P');
   if(!len) len=strlen(method);
   GROW(FLEN+ILEN+len+ILEN);
   (*env)->send_len+=EXT_GLOBAL(snprintf)((char*)((*env)->send+(*env)->send_len), flen, "<I v=\"%ld\" m=\"%s\" p=\"%c\" i=\"%ld\">", object, method, property, (long)((*env)->async_ctx.result=result));
   assert((*env)->send_len<=(*env)->send_size);
 }
 static void InvokeEnd(proxyenv *env) {
   size_t flen;
   GROW(FLEN);
   (*env)->send_len+=EXT_GLOBAL(snprintf)((char*)((*env)->send+(*env)->send_len), flen, "</I>");
   assert((*env)->send_len<=(*env)->send_size);
   flush(env);
 }

 static void ResultBegin(proxyenv *env, void*result) {
   size_t flen;
   GROW(FLEN+ILEN);
   (*env)->send_len+=EXT_GLOBAL(snprintf)((char*)((*env)->send+(*env)->send_len), flen, "<R i=\"%ld\">", (long)((*env)->async_ctx.result=result));
   assert((*env)->send_len<=(*env)->send_size);
 }
 static void ResultEnd(proxyenv *env) {
   size_t flen;
   GROW(FLEN);
   (*env)->send_len+=EXT_GLOBAL(snprintf)((char*)((*env)->send+(*env)->send_len), flen, "</R>");
   assert((*env)->send_len<=(*env)->send_size);
   end(env);
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
 static void Long(proxyenv *env, long l) {
   size_t flen;
   GROW(FLEN+ILEN);
   (*env)->send_len+=EXT_GLOBAL(snprintf)((char*)((*env)->send+(*env)->send_len), flen, "<L v=\"%ld\"/>", l);
   assert((*env)->send_len<=(*env)->send_size);
 }
 static void Double(proxyenv *env, double d) {
   size_t flen;
   GROW(FLEN+ILEN);
   (*env)->send_len+=EXT_GLOBAL(snprintf)((char*)((*env)->send+(*env)->send_len), flen, "<D v=\"%."/**/PRECISION/**/"e\"/>", d);
   assert((*env)->send_len<=(*env)->send_size);
 }
 static void Object(proxyenv *env, long object) {
   size_t flen;
   GROW(FLEN+ILEN);
   if(!object) 
	 (*env)->send_len+=EXT_GLOBAL(snprintf)((char*)((*env)->send+(*env)->send_len), flen, "<O v=\"\"/>");
   else
	 (*env)->send_len+=EXT_GLOBAL(snprintf)((char*)((*env)->send+(*env)->send_len),flen, "<O v=\"%ld\"/>", object);
   assert((*env)->send_len<=(*env)->send_size);
 }
 static void Exception(proxyenv *env, long object, char *str, size_t len) {
   size_t flen;
   if(!len) len=strlen(str);
   GROW(FLEN+ILEN+len);
   if(!object) 
	 (*env)->send_len+=EXT_GLOBAL(snprintf)((char*)((*env)->send+(*env)->send_len), flen, "<E v=\"\" m=\"%s\"/>", str);
   else
	 (*env)->send_len+=EXT_GLOBAL(snprintf)((char*)((*env)->send+(*env)->send_len),flen, "<E v=\"%ld\" m=\"%s\"/>", object, str);
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
   (*env)->send_len+=EXT_GLOBAL(snprintf)((char*)((*env)->send+(*env)->send_len), flen, "<P t=\"N\" v=\"%ld\">", key);
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
 static void Unref(proxyenv *env, long object) {
   size_t flen;
   GROW(FLEN+ILEN);
   (*env)->send_len+=EXT_GLOBAL(snprintf)((char*)((*env)->send+(*env)->send_len), flen, "<U v=\"%ld\"/>", object);
   assert((*env)->send_len<=(*env)->send_size);
 }

 

 proxyenv *EXT_GLOBAL(createSecureEnvironment) (int peer, void (*handle_request)(proxyenv *env), void (*handle_cached)(proxyenv *env), char *server_name, short is_local, struct sockaddr *saddr) {
   proxyenv *env;  
   env=(proxyenv*)malloc(sizeof *env);     
   if(!env) return 0;
   *env=(proxyenv)calloc(1, sizeof **env); 
   if(!*env) {free(env); return 0;}

   (*env)->f_recv0 = (*env)->f_recv = recv_socket;
   (*env)->f_send0 = (*env)->f_send = send_socket;

   (*env)->peer = peer;
   (*env)->peer0 = (*env)->peerr = -1;
   (*env)->peer_redirected = 0;
   memcpy(&(*env)->orig_peer_saddr, saddr, sizeof (struct sockaddr));
   
   (*env)->handle = (*env)->handle_request = handle_request;
   (*env)->async_ctx.handle_request = handle_cached;
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
   (*env)->must_reopen = 0;
   (*env)->servlet_ctx = (*env)->servlet_context_string = 0;
   (*env)->backend_has_session_proxy = 0;

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
   (*env)->finish=end;

   return env;
 }

#ifndef PHP_WRAPPER_H
#error must include php_wrapper.h
#endif
