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
#define FLEN 160 // the max len of the following format strings


#define GROW(size) { \
  if((*env)->send_len+size>=(*env)->send_size) { \
	(*env)->send=realloc((*env)->send, (*env)->send_size*=2); \
	assert((*env)->send); if(!(*env)->send) exit(9); \
  } \
}

static void flush(proxyenv *env) {
   send((*env)->peer, (*env)->send, (*env)->send_len, 0);
   (*env)->send_len=0;
   (*env)->handle_request(env);
}
#define GROW_QUOTE() \
  if(pos+1>=newlen) { \
    newlen=newlen+newlen/10; \
    new=realloc(new, newlen); \
    assert(new); if(!new) exit(9); \
  } 
static char* replaceQuote(char *name, size_t len, size_t *ret_len) {
  static const char quote[]="&quote;";
  register size_t newlen=len+len/10, pos=0;
  char c, *s, *new = malloc(newlen);
  register short j;
  assert(new); if(!new) exit(9);
  
  while(len--) {
	if((c=*name++)=='&') {
	  for(j=0; j< sizeof(quote); j++) {
		new[pos++]=quote[j]; 
		GROW_QUOTE();
	  }
	} else {
	  new[pos++]=c;
	  GROW_QUOTE();
	}
  }
  new[newlen]=0;
  *ret_len=newlen;
  return new;
}
 static void CreateObjectBegin(proxyenv *env, char*name, size_t len, char createInstance, void *result) {
   assert(createInstance=='C' || createInstance=='I');
   if(!len) len=strlen(name);
   GROW(FLEN+ILEN+len);
   (*env)->send_len+=sprintf((*env)->send+(*env)->send_len, "<C v=\"%s\" p=\"%c\" i=\"%ld\">", name, createInstance, (long)result);
   assert((*env)->send_len<=(*env)->send_size);
 }
 static void CreateObjectEnd(proxyenv *env) {
   GROW(FLEN);
   (*env)->send_len+=sprintf((*env)->send+(*env)->send_len, "</C>");
   assert((*env)->send_len<=(*env)->send_size);
   flush(env);
 }
 static void InvokeBegin(proxyenv *env, long object, char*method, size_t len, char property, void* result) {
   assert(property=='I' || property=='P');
   if(!len) len=strlen(method);
   GROW(FLEN+ILEN+len+ILEN);
   (*env)->send_len+=sprintf((*env)->send+(*env)->send_len, "<I v=\"%ld\" m=\"%s\" p=\"%c\" i=\"%ld\">", object, method, property, (long)result);
   assert((*env)->send_len<=(*env)->send_size);
 }
 static void InvokeEnd(proxyenv *env) {
   GROW(FLEN);
   (*env)->send_len+=sprintf((*env)->send+(*env)->send_len, "</I>");
   assert((*env)->send_len<=(*env)->send_size);
   flush(env);
 }
 static void GetMethodBegin(proxyenv *env, long object, char*method, size_t len, void* result) {
   if(!len) len=strlen(method);
   GROW(FLEN+ILEN+len+ILEN);
   (*env)->send_len+=sprintf((*env)->send+(*env)->send_len, "<M v=\"%ld\" m=\"%s\" i=\"%ld\">", object, method, (long) result);
   assert((*env)->send_len<=(*env)->send_size);
 }
 static void GetMethodEnd(proxyenv *env) {
   GROW(FLEN);
   (*env)->send_len+=sprintf((*env)->send+(*env)->send_len, "</M>");
   assert((*env)->send_len<=(*env)->send_size);
   flush(env);
 }
 static void CallMethodBegin(proxyenv *env, long object, long method, void* result) {
   GROW(FLEN+ILEN+ILEN+ILEN);
   (*env)->send_len+=sprintf((*env)->send+(*env)->send_len, "<F v=\"%ld\" m=\"%ld\" i=\"%ld\">", object, method, (long)result);
   assert((*env)->send_len<=(*env)->send_size);
 }
 static void CallMethodEnd(proxyenv *env) {
   GROW(FLEN);
   (*env)->send_len+=sprintf((*env)->send+(*env)->send_len, "</F>");
   assert((*env)->send_len<=(*env)->send_size);
   flush(env);
 }

 static void String(proxyenv *env, char*name, size_t _len) {
   size_t len;
   if(!_len) _len=strlen(name);
   name = replaceQuote(name, _len, &len);
   GROW(FLEN+len);
   (*env)->send_len+=sprintf((*env)->send+(*env)->send_len, "<S v=\"%s\"/>", name);
   assert((*env)->send_len<=(*env)->send_size);
   free(name);
 }
 static void Boolean(proxyenv *env, short boolean) {
   GROW(FLEN);
   (*env)->send_len+=sprintf((*env)->send+(*env)->send_len, "<B v=\"%c\"/>", boolean?'T':'F');
   assert((*env)->send_len<=(*env)->send_size);
 }
 static void Long(proxyenv *env, long l) {
   GROW(FLEN+ILEN);
   (*env)->send_len+=sprintf((*env)->send+(*env)->send_len, "<L v=\"%ld\"/>", l);
   assert((*env)->send_len<=(*env)->send_size);
 }
 static void Double(proxyenv *env, double d) {
   GROW(FLEN+ILEN);
   (*env)->send_len+=sprintf((*env)->send+(*env)->send_len, "<D v=\"%e\"/>", d);
   assert((*env)->send_len<=(*env)->send_size);
 }
 static void Object(proxyenv *env, long object) {
   GROW(FLEN+ILEN);
   if(!object) 
	 (*env)->send_len+=sprintf((*env)->send+(*env)->send_len, "<O v=\"\"/>");
   else
	 (*env)->send_len+=sprintf((*env)->send+(*env)->send_len, "<O v=\"%ld\"/>", object);
   assert((*env)->send_len<=(*env)->send_size);
 }
 static void CompositeBegin_a(proxyenv *env) {
   GROW(FLEN);
   (*env)->send_len+=sprintf((*env)->send+(*env)->send_len, "<X t=\"A\">");
   assert((*env)->send_len<=(*env)->send_size);
 }
 static void CompositeBegin_h(proxyenv *env) {
   GROW(FLEN);
   (*env)->send_len+=sprintf((*env)->send+(*env)->send_len, "<X t=\"H\">");
   assert((*env)->send_len<=(*env)->send_size);
 }
 static void CompositeEnd(proxyenv *env) {
   GROW(FLEN);
   (*env)->send_len+=sprintf((*env)->send+(*env)->send_len, "</X>");
   assert((*env)->send_len<=(*env)->send_size);
 }
 static void PairBegin_s(proxyenv *env, char*key, size_t len) {
   if(!len) len=strlen(key);
   GROW(FLEN+len);
   (*env)->send_len+=sprintf((*env)->send+(*env)->send_len, "<P t=\"S\" v=\"%s\">", key);
   assert((*env)->send_len<=(*env)->send_size);
 }
 static void PairBegin_n(proxyenv *env, unsigned long key) {
   GROW(FLEN+ILEN);
   (*env)->send_len+=sprintf((*env)->send+(*env)->send_len, "<P t=\"N\" v=\"%ld\">", key);
   assert((*env)->send_len<=(*env)->send_size);
 }
 static void PairBegin(proxyenv *env) {
   GROW(FLEN);
   (*env)->send_len+=sprintf((*env)->send+(*env)->send_len, "<P>");
   assert((*env)->send_len<=(*env)->send_size);
 }
 static void PairEnd(proxyenv *env) {
   GROW(FLEN);
   (*env)->send_len+=sprintf((*env)->send+(*env)->send_len, "</P>");
   assert((*env)->send_len<=(*env)->send_size);
 }
 static void Unref(proxyenv *env, long object) {
   GROW(FLEN+ILEN);
   (*env)->send_len+=sprintf((*env)->send+(*env)->send_len, "<U v=\"%ld\"/>", object);
   assert((*env)->send_len<=(*env)->send_size);
 }

 

 proxyenv *java_createSecureEnvironment(int peer, void (*handle_request)(proxyenv *env)) {
   proxyenv *env;  
   env=(proxyenv*)malloc(sizeof *env);     
   if(!env) return 0;
   *env=(proxyenv)calloc(1, sizeof **env); 
   if(!*env) {free(env); return 0;}

   (*env)->peer = peer;
   (*env)->handle_request = handle_request;
   (*env)->len = SLEN; 
   (*env)->s=malloc((*env)->len);
   if(!(*env)->s) {free(*env); free(env); return 0;}
   (*env)->send_size=SEND_SIZE;
   (*env)->send=malloc(SEND_SIZE);
   if(!(*env)->send) {free((*env)->s); free(*env); free(env); return 0;}
   (*env)->send_len=0;

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
