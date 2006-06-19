/*-*- mode: C; tab-width:4 -*-*/

/**\file 
 * Definitions for the protocol implementation.
 *
 * It defines the proxyenv structure and the communication channels.
 * @see PROTOCOL.TXT
 */

#ifndef JAVA_PROTOCOL_H
#define JAVA_PROTOCOL_H

/* peer */
#include <stdio.h>
#ifdef __MINGW32__
# include <winsock2.h>
# define close closesocket
#else
# include <sys/types.h>
# include <sys/socket.h>
# include <netinet/tcp.h>
#endif

/* 
 * we create a unix domain socket with the name .php_java_bridge in
 * the tmpdir
 */
#ifndef P_tmpdir
/* xopen, normally defined in stdio.h */
#define P_tmpdir "/tmp"
#endif 
#define SOCKNAME P_tmpdir/**/"/.php_java_bridge"/**/"XXXXXX"
/* Linux: pipes created in the shared memory */
#define SOCKNAME_SHM "/dev/shm/.php_java_bridge"/**/"XXXXXX"


/*
 * default log file is System.out
 */
#define LOGFILE ""

#define LOG_OFF 0
#define LOG_FATAL 1
#define LOG_ERROR 2 /* default level */
#define LOG_INFO 3 
#define LOG_DEBUG 4
#define DEFAULT_LEVEL "2"

#define N_JAVA_SARGS 10
#define N_JAVA_SENV 3 
#define N_MONO_SARGS 6
#define N_MONO_SENV 1
#define DEFAULT_MONO_PORT "9167" /* default port for tcp/ip */
#define DEFAULT_JAVA_PORT "9267" /* default port for tcp/ip */
#define DEFAULT_JAVA_WRAPPER EXTENSION_DIR/**/"/RunJavaBridge"
#define DEFAULT_MONO_WRAPPER EXTENSION_DIR/**/"/RunMonoBridge"
#define DEFAULT_HOST "127.0.0.1"
#define DEFAULT_SERVLET "/JavaBridge/JavaBridge.phpjavabridge"

#define RECV_SIZE 8192 // initial size of the receive buffer
#define MAX_ARGS 100   // max # of method arguments

/* checks if we use a servlet backend (re-directed or not) */
#define IS_SERVLET_BACKEND(env) (((*env)->servlet_ctx || EXT_GLOBAL (get_servlet_context) (TSRMLS_C)))

/* checks if the servlet backend uses HTTP, either because we do not
   re-direct or because we override re-direct */
#define IS_OVERRIDE_REDIRECT(env) ((((*env)->peer0!=-1) || EXT_GLOBAL (get_servlet_context) (TSRMLS_C)))

typedef struct proxyenv_ *proxyenv;
struct proxyenv_ {

  /* peer */
  int peer, peerr, peer0;		/* peer0 contains peer during override
								   redirect */
  short peer_redirected;		/* remains true during override
								   redirect */
  struct sockaddr orig_peer_saddr; /* only valid if peer is a servlet, it
								   points to the original peer */


  /* used by the parser implementation */
  unsigned char*s; size_t len; 
  ssize_t pos, c; 
  unsigned char recv_buf[RECV_SIZE];

  /* the send buffer */
  unsigned char*send;
  size_t send_len, send_size;

  char *server_name;

  /* local server (not a servlet engine) */
  short is_local;

  /* for servlets: re-open connection */
  short must_reopen; 
  short connection_is_closed;

  struct async_ctx {
	void (*handle_request)(proxyenv *env);
	unsigned long nextValue;
	void *result;
	FILE *peer;
	ssize_t (*f_send)(proxyenv*env, const void *buf, size_t len);
  } async_ctx;

  /* for servlet engines only */
  char *servlet_ctx;			/* the # of the server context runner */
  char *current_servlet_ctx;	/* the ctx # for this request only, used when persistent connections are enabled */
  char *servlet_context_string;	/* original rinit value from
								   get_servlet_context() */
  short backend_has_session_proxy;
  struct saved_cfg {
	int ini_user;
	char *hosts, *servlet;
  } cfg;
  
  void (*handle)(proxyenv *env);
  void (*handle_request)(proxyenv *env);

  void (*writeCreateObjectBegin)(proxyenv *env, char*name, size_t strlen, char createInstance, void *result);
  short (*writeCreateObjectEnd)(proxyenv *env);
  void (*writeInvokeBegin)(proxyenv *env, unsigned long object, char*method, size_t strlen, char property, void* result);
  short (*writeInvokeEnd)(proxyenv *env);
  void (*writeResultBegin)(proxyenv *env, void* result);
  void (*writeResultEnd)(proxyenv *env);
  void (*writeString)(proxyenv *env, char*name, size_t strlen);
  void (*writeBoolean)(proxyenv *env, short boolean);
  void (*writeLong)(proxyenv *env, long l);
  void (*writeDouble)(proxyenv *env, double d);
  void (*writeObject)(proxyenv *env, unsigned long object);
  void (*writeException)(proxyenv *env, unsigned long object, char*str, size_t len);
  void (*writeCompositeBegin_a)(proxyenv *env);
  void (*writeCompositeBegin_h)(proxyenv *env);
  void (*writeCompositeEnd)(proxyenv *env);
  void (*writePairBegin_s)(proxyenv *env, char*key, size_t strlen);
  void (*writePairBegin_n)(proxyenv *env, unsigned long key);
  void (*writePairBegin)(proxyenv *env);
  void (*writePairEnd)(proxyenv *env);
  void (*writeUnref)(proxyenv *env, unsigned long object);
  short (*writeEndConnection)(proxyenv *env, char property);
  short (*finish)(proxyenv *env);

  ssize_t (*f_recv)(proxyenv*env, void *buf, size_t len);
  ssize_t (*f_recv0)(proxyenv*env, void *buf, size_t len);
  ssize_t (*f_send)(proxyenv*env, const void *buf, size_t len);
  ssize_t (*f_send0)(proxyenv*env, const void *buf, size_t len);
};

#endif
