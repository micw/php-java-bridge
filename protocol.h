/*-*- mode: C; tab-width:4 -*-*/

#ifndef JAVA_PROTOCOL_H
#define JAVA_PROTOCOL_H

/* peer */
#include <stdio.h>
#include "sio.h"

/* 
 * we create a unix domain socket with the name .php_java_bridge in
 * the tmpdir
 */
#ifndef P_tmpdir
/* xopen, normally defined in stdio.h */
#define P_tmpdir "/tmp"
#endif 
#define SOCKNAME P_tmpdir/**/"/.php_java_bridge"/**/"XXXXXX"

/*
 * default log file is System.out
 */
#define LOGFILE ""

#define LOG_OFF 0
#define LOG_FATAL 1
#define LOG_ERROR 2
#define LOG_INFO 3 /* default level */
#define LOG_DEBUG 4


#define N_SARGS 9		/* # of server args for exec */
#define N_SENV 3		/* # of server env entries */

typedef struct proxyenv_ *proxyenv;
struct proxyenv_ {
  SFILE *peer;
  int (*handle_request)(proxyenv *env);

  void (*writeCreateObjectBegin)(proxyenv *env, char*name, size_t strlen, short createInstance, void *result);
  void (*writeCreateObjectEnd)(proxyenv *env);
  void (*writeInvokeBegin)(proxyenv *env, long object, char*method, size_t strlen, short property, void* result);
  void (*writeInvokeEnd)(proxyenv *env);
  void (*writeGetMethodBegin)(proxyenv *env, long object, char*method, size_t strlen, void* result);
  void (*writeGetMethodEnd)(proxyenv *env);
  void (*writeCallMethodBegin)(proxyenv *env, long object, long method, void* result);
  void (*writeCallMethodEnd)(proxyenv *env);
  void (*writeString)(proxyenv *env, char*name, size_t strlen);
  void (*writeBoolean)(proxyenv *env, short boolean);
  void (*writeLong)(proxyenv *env, long l);
  void (*writeDouble)(proxyenv *env, double d);
  void (*writeObject)(proxyenv *env, long object);
  void (*writeCompositeBegin_a)(proxyenv *env);
  void (*writeCompositeBegin_h)(proxyenv *env);
  void (*writeCompositeEnd)(proxyenv *env);
  void (*writePairBegin_s)(proxyenv *env, char*key, size_t strlen);
  void (*writePairBegin_n)(proxyenv *env, unsigned long key);
  void (*writePairEnd)(proxyenv *env);
};

extern proxyenv *java_createSecureEnvironment(SFILE *peer, int (*handle_request)(proxyenv *env));

#endif
