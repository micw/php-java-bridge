/*-*- mode: C; tab-width:4 -*-*/

#ifndef JAVA_PROTOCOL_H
#define JAVA_PROTOCOL_H

/* peer */
#include <stdio.h>

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
#define DEFAULT_LEVEL "2"

#define N_SARGS 9				/* # of server args for exec */
#define N_SENV 3				/* # of server env entries */
#define DEFAULT_PORT "9167"		/* default port for tcp/ip */
#define DEFAULT_HOST "127.0.0.1"

typedef struct proxyenv_ *proxyenv;
struct proxyenv_ {
  int peer;

  /* used by the parser implementation */
  unsigned char*s;
  size_t len;

  /* the send buffer */
  unsigned char*send;
  size_t send_len, send_size;

  char *server_name;

  void (*handle_request)(proxyenv *env);

  void (*writeCreateObjectBegin)(proxyenv *env, char*name, size_t strlen, char createInstance, void *result);
  void (*writeCreateObjectEnd)(proxyenv *env);
  void (*writeInvokeBegin)(proxyenv *env, long object, char*method, size_t strlen, char property, void* result);
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
  void (*writePairBegin)(proxyenv *env);
  void (*writePairEnd)(proxyenv *env);
  void (*writeUnref)(proxyenv *env, long object);
};

extern proxyenv *java_createSecureEnvironment(int peer, void (*handle_request)(proxyenv *env), char*server);

#endif
