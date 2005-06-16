/*-*- mode: C; tab-width:4 -*-*/

#include <stdarg.h>
#include <assert.h>
#include <errno.h>
#include <string.h>
#include <stdlib.h>

#include "protocol.h"
#include "php_java.h"

#define SLEN 256 // initial length of the parser string
#define SEND_SIZE 8192 // initial size of the send buffer

#define ILEN 40 // integer, double representation.
#define PRECISION "20"
#define FLEN 160 // the max len of the following format strings

#ifndef __MINGW32__
extern int php_java_snprintf(char *buf, size_t len, const char *format,...);
#else
#define php_java_snprintf ap_php_snprintf 
#endif

#define GROW(size) { \
  flen = size; \
  if((*env)->send_len+size>=(*env)->send_size) { \
    size_t nsize = (*env)->send_len+size+SEND_SIZE; \
	(*env)->send=realloc((*env)->send, (*env)->send_size=nsize); \
	assert((*env)->send); if(!(*env)->send) exit(9); \
  } \
}

static void flush(proxyenv *env) {
  size_t s=0, size = (*env)->send_len;
  ssize_t n=0;

  if(get_servlet_context()) {
	char header[1024];
	int header_length;
	unsigned short level = cfg->logLevel_val>4?4:cfg->logLevel_val;

#ifndef ZEND_ENGINE_2
  // we want arrays as values
	unsigned char mode=128|64|(level<<2)|2;
#else
	unsigned char mode=128|64|(level<<2)|0;
#endif

	if((*env)->cookie_name) 
	  header_length=php_java_snprintf(header, sizeof(header), "PUT %s HTTP/1.1\r\nHost: localhost\r\nConnection: keep-alive\r\nCookie: %s=%s\r\nContent-Type: text/html\r\nContent-Length: %ld\r\n\r\n%c", get_servlet_context(), (*env)->cookie_name, (*env)->cookie_value, size+1, mode);
	else
	  header_length=php_java_snprintf(header, sizeof(header), "PUT %s HTTP/1.1\r\nHost: localhost\r\nConnection: keep-alive\r\nContent-Type: text/html\r\nContent-Length: %ld\r\n\r\n%c", get_servlet_context(), size+1, mode);

	send((*env)->peer, header, header_length, 0);
  }

 res: 
  errno=0;
  while((size>s)&&((n=send((*env)->peer, (*env)->send+s, size-s, 0)) > 0)) 
	s+=n;
  if(size>s && !n && errno==EINTR) goto res; // Solaris, see INN FAQ

  (*env)->send_len=0;
  (*env)->handle_request(env);
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
   (*env)->send_len+=php_java_snprintf((char*)((*env)->send+(*env)->send_len), flen, "<C v=\"%s\" p=\"%c\" i=\"%ld\">", name, createInstance, (long)result);
   assert((*env)->send_len<=(*env)->send_size);
 }
 static void CreateObjectEnd(proxyenv *env) {
   size_t flen;
   GROW(FLEN);
   (*env)->send_len+=php_java_snprintf((char*)((*env)->send+(*env)->send_len), flen, "</C>");
   assert((*env)->send_len<=(*env)->send_size);
   flush(env);
 }
 static void InvokeBegin(proxyenv *env, long object, char*method, size_t len, char property, void* result) {
   size_t flen;
   assert(property=='I' || property=='P');
   if(!len) len=strlen(method);
   GROW(FLEN+ILEN+len+ILEN);
   (*env)->send_len+=php_java_snprintf((char*)((*env)->send+(*env)->send_len), flen, "<I v=\"%ld\" m=\"%s\" p=\"%c\" i=\"%ld\">", object, method, property, (long)result);
   assert((*env)->send_len<=(*env)->send_size);
 }
 static void InvokeEnd(proxyenv *env) {
   size_t flen;
   GROW(FLEN);
   (*env)->send_len+=php_java_snprintf((char*)((*env)->send+(*env)->send_len), flen, "</I>");
   assert((*env)->send_len<=(*env)->send_size);
   flush(env);
 }
 static void GetMethodBegin(proxyenv *env, long object, char*method, size_t len, void* result) {
   size_t flen;
   if(!len) len=strlen(method);
   GROW(FLEN+ILEN+len+ILEN);
   (*env)->send_len+=php_java_snprintf((char*)((*env)->send+(*env)->send_len), flen, "<M v=\"%ld\" m=\"%s\" i=\"%ld\">", object, method, (long) result);
   assert((*env)->send_len<=(*env)->send_size);
 }
 static void GetMethodEnd(proxyenv *env) {
   size_t flen;
   GROW(FLEN);
   (*env)->send_len+=php_java_snprintf((char*)((*env)->send+(*env)->send_len), flen, "</M>");
   assert((*env)->send_len<=(*env)->send_size);
   flush(env);
 }
 static void CallMethodBegin(proxyenv *env, long object, long method, void* result) {
   size_t flen;
   GROW(FLEN+ILEN+ILEN+ILEN);
   (*env)->send_len+=php_java_snprintf((char*)((*env)->send+(*env)->send_len), flen, "<F v=\"%ld\" m=\"%ld\" i=\"%ld\">", object, method, (long)result);
   assert((*env)->send_len<=(*env)->send_size);
 }
 static void CallMethodEnd(proxyenv *env) {
   size_t flen;
   GROW(FLEN);
   (*env)->send_len+=php_java_snprintf((char*)((*env)->send+(*env)->send_len), flen, "</F>");
   assert((*env)->send_len<=(*env)->send_size);
   flush(env);
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
   (*env)->send_len+=php_java_snprintf((char*)((*env)->send+(*env)->send_len), flen, "<B v=\"%c\"/>", boolean?'T':'F');
   assert((*env)->send_len<=(*env)->send_size);
 }
 static void Long(proxyenv *env, long l) {
   size_t flen;
   GROW(FLEN+ILEN);
   (*env)->send_len+=php_java_snprintf((char*)((*env)->send+(*env)->send_len), flen, "<L v=\"%ld\"/>", l);
   assert((*env)->send_len<=(*env)->send_size);
 }
 static void Double(proxyenv *env, double d) {
   size_t flen;
   GROW(FLEN+ILEN);
   (*env)->send_len+=php_java_snprintf((char*)((*env)->send+(*env)->send_len), flen, "<D v=\"%."/**/PRECISION/**/"e\"/>", d);
   assert((*env)->send_len<=(*env)->send_size);
 }
 static void Object(proxyenv *env, long object) {
   size_t flen;
   GROW(FLEN+ILEN);
   if(!object) 
	 (*env)->send_len+=php_java_snprintf((char*)((*env)->send+(*env)->send_len), flen, "<O v=\"\"/>");
   else
	 (*env)->send_len+=php_java_snprintf((char*)((*env)->send+(*env)->send_len),flen, "<O v=\"%ld\"/>", object);
   assert((*env)->send_len<=(*env)->send_size);
 }
 static void CompositeBegin_a(proxyenv *env) {
   size_t flen;
   GROW(FLEN);
   (*env)->send_len+=php_java_snprintf((char*)((*env)->send+(*env)->send_len), flen, "<X t=\"A\">");
   assert((*env)->send_len<=(*env)->send_size);
 }
 static void CompositeBegin_h(proxyenv *env) {
   size_t flen;
   GROW(FLEN);
   (*env)->send_len+=php_java_snprintf((char*)((*env)->send+(*env)->send_len), flen, "<X t=\"H\">");
   assert((*env)->send_len<=(*env)->send_size);
 }
 static void CompositeEnd(proxyenv *env) {
   size_t flen;
   GROW(FLEN);
   (*env)->send_len+=php_java_snprintf((char*)((*env)->send+(*env)->send_len), flen, "</X>");
   assert((*env)->send_len<=(*env)->send_size);
 }
 static void PairBegin_s(proxyenv *env, char*key, size_t len) {
   size_t flen;
   if(!len) len=strlen(key);
   GROW(FLEN+len);
   (*env)->send_len+=php_java_snprintf((char*)((*env)->send+(*env)->send_len), flen, "<P t=\"S\" v=\"%s\">", key);
   assert((*env)->send_len<=(*env)->send_size);
 }
 static void PairBegin_n(proxyenv *env, unsigned long key) {
   size_t flen;
   GROW(FLEN+ILEN);
   (*env)->send_len+=php_java_snprintf((char*)((*env)->send+(*env)->send_len), flen, "<P t=\"N\" v=\"%ld\">", key);
   assert((*env)->send_len<=(*env)->send_size);
 }
 static void PairBegin(proxyenv *env) {
   size_t flen;
   GROW(FLEN);
   (*env)->send_len+=php_java_snprintf((char*)((*env)->send+(*env)->send_len), flen, "<P>");
   assert((*env)->send_len<=(*env)->send_size);
 }
 static void PairEnd(proxyenv *env) {
   size_t flen;
   GROW(FLEN);
   (*env)->send_len+=php_java_snprintf((char*)((*env)->send+(*env)->send_len), flen, "</P>");
   assert((*env)->send_len<=(*env)->send_size);
 }
 static void Unref(proxyenv *env, long object) {
   size_t flen;
   GROW(FLEN+ILEN);
   (*env)->send_len+=php_java_snprintf((char*)((*env)->send+(*env)->send_len), flen, "<U v=\"%ld\"/>", object);
   assert((*env)->send_len<=(*env)->send_size);
 }

 

 proxyenv *java_createSecureEnvironment(int peer, void (*handle_request)(proxyenv *env), char *server_name) {
   proxyenv *env;  
   env=(proxyenv*)malloc(sizeof *env);     
   if(!env) return 0;
   *env=(proxyenv)calloc(1, sizeof **env); 
   if(!*env) {free(env); return 0;}

   (*env)->peer = peer;
   (*env)->handle_request = handle_request;

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
   
   (*env)->server_name = server_name;
   (*env)->cookie_name = (*env)->cookie_value = 0;

   (*env)->writeInvokeBegin=InvokeBegin;
   (*env)->writeInvokeEnd=InvokeEnd;
   (*env)->writeCreateObjectBegin=CreateObjectBegin;
   (*env)->writeCreateObjectEnd=CreateObjectEnd;
   (*env)->writeGetMethodBegin=GetMethodBegin;
   (*env)->writeGetMethodEnd=GetMethodEnd;
   (*env)->writeCallMethodBegin=CallMethodBegin;
   (*env)->writeCallMethodEnd=CallMethodEnd;
   (*env)->writeString=String;
   (*env)->writeBoolean=Boolean;
   (*env)->writeLong=Long;
   (*env)->writeDouble=Double;
   (*env)->writeObject=Object;
   (*env)->writeCompositeBegin_a=CompositeBegin_a;
   (*env)->writeCompositeBegin_h=CompositeBegin_h;
   (*env)->writeCompositeEnd=CompositeEnd;
   (*env)->writePairBegin=PairBegin;
   (*env)->writePairBegin_s=PairBegin_s;
   (*env)->writePairBegin_n=PairBegin_n;
   (*env)->writePairEnd=PairEnd;
   (*env)->writeUnref=Unref;

   return env;
 }

#ifndef PHP_WRAPPER_H
#error must include php_wrapper.h
#endif
