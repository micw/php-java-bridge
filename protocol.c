/*-*- mode: C; tab-width:4 -*-*/

#include <stdarg.h>
#include <assert.h>
#include <errno.h>
#include <string.h>
#include <stdlib.h>

#include "protocol.h"
#include "php_wrapper.h"
#include "sio.c"

// FIXME: Don't use fprintf
static void CreateObjectBegin(proxyenv *env, char*name, size_t strlen, short createInstance, void *result) {
  fprintf((*env)->peer, "<C v=\"%s\" p=\"%c\" i=\"%lx\">", name, createInstance?'C':'I', result);
}
static void CreateObjectEnd(proxyenv *env) {
  fprintf((*env)->peer, "</C>");
  (*env)->handle_request(env);
}
static void InvokeBegin(proxyenv *env, long object, char*method, size_t strlen, short property, void* result) {
  fprintf((*env)->peer, "<I v=\"%lx\" m=\"%s\" p=\"%c\" i=\"%lx\">", object, method, property?'P':'I', result);
}
static void InvokeEnd(proxyenv *env) {
  fprintf((*env)->peer, "</I>");
  (*env)->handle_request(env);
}
static void GetMethodBegin(proxyenv *env, long object, char*method, size_t strlen, void* result) {
  fprintf((*env)->peer, "<M v=\"%lx\" m=\"%s\" i=\"%lx\">", object, method, result);
}
static void GetMethodEnd(proxyenv *env) {
  fprintf((*env)->peer, "</M>");
  (*env)->handle_request(env);
}
static void CallMethodBegin(proxyenv *env, long object, long method, void* result) {
  fprintf((*env)->peer, "<F v=\"%lx\" m=\"%lx\" i=\"%lx\">", object, method, result);
}
static void CallMethodEnd(proxyenv *env) {
  fprintf((*env)->peer, "</F>");
  (*env)->handle_request(env);
}

static void String(proxyenv *env, char*name, size_t strlen) {
  fprintf((*env)->peer, "<S v=\"%s\"/>", name);
}
static void Boolean(proxyenv *env, short boolean) {
  fprintf((*env)->peer, "<B v=\"%c\"/>", boolean?'T':'F');
}
static void Long(proxyenv *env, long l) {
  fprintf((*env)->peer, "<L v=\"%lx\"/>", l);
}
static void Double(proxyenv *env, double d) {
  fprintf((*env)->peer, "<L v=\"%d\"/>", d);
}
static void Object(proxyenv *env, long object) {
  fprintf((*env)->peer, "<O v=\"%lx\"/>", object);
}
static void CompositeBegin_a(proxyenv *env) {
  fprintf((*env)->peer, "<X t=\"A\"");
}
static void CompositeBegin_h(proxyenv *env) {
  fprintf((*env)->peer, "<X t=\"H\"");
}
static void CompositeEnd(proxyenv *env) {
  fprintf((*env)->peer, "</X>");
}
static void PairBegin_s(proxyenv *env, char*key, size_t strlen) {
  fprintf((*env)->peer, "<P t=\"S\" v=\"%s\">", key);
}
static void PairBegin_n(proxyenv *env, unsigned long key) {
  fprintf((*env)->peer, "<P t=\"N\" v=\"%lx\">", key);
}
static void PairEnd(proxyenv *env) {
  fprintf((*env)->peer, "</P>");
}


proxyenv *java_createSecureEnvironment(SFILE *peer, int (*handle_request)(proxyenv *env)) {
  proxyenv *env;  
  env=(proxyenv*)malloc(sizeof *env);     
  if(!env) return 0;
  *env=(proxyenv)calloc(1, sizeof **env); 
  if(!*env) {free(env); return 0;}

  (*env)->peer = peer;
  (*env)->handle_request = handle_request;

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
