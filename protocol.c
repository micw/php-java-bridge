/*-*- mode: C; tab-width:4 -*-*/

#include <stdarg.h>
#include <assert.h>
#include <errno.h>
#include <string.h>
#include <stdlib.h>

#include "protocol.h"
#include "php_wrapper.h"

// FIXME: Don't use sprintf
static void flush(proxyenv *env) {
  send((*env)->peer, (*env)->send, (*env)->send_len, 0);
  (*env)->send_len=0;
  (*env)->handle_request(env);
}
static void CreateObjectBegin(proxyenv *env, char*name, size_t strlen, short createInstance, void *result) {
  (*env)->send_len+=sprintf((*env)->send+(*env)->send_len, "<C v=\"%s\" p=\"%c\" i=\"%ld\">", name, createInstance?'C':'I', result);
}
static void CreateObjectEnd(proxyenv *env) {
  (*env)->send_len+=sprintf((*env)->send+(*env)->send_len, "</C>");

  flush(env);
}
static void InvokeBegin(proxyenv *env, long object, char*method, size_t strlen, short property, void* result) {
  (*env)->send_len+=sprintf((*env)->send+(*env)->send_len, "<I v=\"%ld\" m=\"%s\" p=\"%c\" i=\"%ld\">", object, method, property?'P':'I', result);
}
static void InvokeEnd(proxyenv *env) {
  (*env)->send_len+=sprintf((*env)->send+(*env)->send_len, "</I>");

  flush(env);
}
static void GetMethodBegin(proxyenv *env, long object, char*method, size_t strlen, void* result) {
  (*env)->send_len+=sprintf((*env)->send+(*env)->send_len, "<M v=\"%ld\" m=\"%s\" i=\"%ld\">", object, method, result);
}
static void GetMethodEnd(proxyenv *env) {
  (*env)->send_len+=sprintf((*env)->send+(*env)->send_len, "</M>");

  flush(env);
}
static void CallMethodBegin(proxyenv *env, long object, long method, void* result) {
  (*env)->send_len+=sprintf((*env)->send+(*env)->send_len, "<F v=\"%ld\" m=\"%ld\" i=\"%ld\">", object, method, result);
}
static void CallMethodEnd(proxyenv *env) {
  (*env)->send_len+=sprintf((*env)->send+(*env)->send_len, "</F>");

  flush(env);
}

static void String(proxyenv *env, char*name, size_t strlen) {
  (*env)->send_len+=sprintf((*env)->send+(*env)->send_len, "<S v=\"%s\"/>", name);
}
static void Boolean(proxyenv *env, short boolean) {
  (*env)->send_len+=sprintf((*env)->send+(*env)->send_len, "<B v=\"%c\"/>", boolean?'T':'F');
}
static void Long(proxyenv *env, long l) {
  (*env)->send_len+=sprintf((*env)->send+(*env)->send_len, "<L v=\"%ld\"/>", l);
}
static void Double(proxyenv *env, double d) {
  (*env)->send_len+=sprintf((*env)->send+(*env)->send_len, "<L v=\"%e\"/>", d);
}
static void Object(proxyenv *env, long object) {
  (*env)->send_len+=sprintf((*env)->send+(*env)->send_len, "<O v=\"%ld\"/>", object);
}
static void CompositeBegin_a(proxyenv *env) {
  (*env)->send_len+=sprintf((*env)->send+(*env)->send_len, "<X t=\"A\"");
}
static void CompositeBegin_h(proxyenv *env) {
  (*env)->send_len+=sprintf((*env)->send+(*env)->send_len, "<X t=\"H\"");
}
static void CompositeEnd(proxyenv *env) {
  (*env)->send_len+=sprintf((*env)->send+(*env)->send_len, "</X>");
}
static void PairBegin_s(proxyenv *env, char*key, size_t strlen) {
  (*env)->send_len+=sprintf((*env)->send+(*env)->send_len, "<P t=\"S\" v=\"%s\">", key);
}
static void PairBegin_n(proxyenv *env, unsigned long key) {
  (*env)->send_len+=sprintf((*env)->send+(*env)->send_len, "<P t=\"N\" v=\"%ld\">", key);
}
static void PairEnd(proxyenv *env) {
  (*env)->send_len+=sprintf((*env)->send+(*env)->send_len, "</P>");
}



proxyenv *java_createSecureEnvironment(int peer, int (*handle_request)(proxyenv *env)) {
  proxyenv *env;  
  env=(proxyenv*)malloc(sizeof *env);     
  if(!env) return 0;
  *env=(proxyenv)calloc(1, sizeof **env); 
  if(!*env) {free(env); return 0;}

  (*env)->peer = peer;
  (*env)->handle_request = handle_request;
  (*env)->len = 2; //FIXME: use 255
  (*env)->s=malloc((*env)->len);
  if(!(*env)->s) {free(*env); free(env); return 0;}
  (*env)->send=malloc(8192);
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
  (*env)->writePairBegin_s=PairBegin_s;
  (*env)->writePairBegin_n=PairBegin_n;
  (*env)->writePairEnd=PairEnd;

  return env;
} 

#ifndef PHP_WRAPPER_H
#error must include php_wrapper.h
#endif
