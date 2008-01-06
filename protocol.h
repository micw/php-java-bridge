/*-*- mode: C; tab-width:4 -*-*/

/** protocol.h -- implementation of the PHP/Java Bridge XML protocol.

  Copyright (C) 2003-2007 Jost Boekemeier

  This file is part of the PHP/Java Bridge.

  The PHP/Java Bridge ("the library") is free software; you can
  redistribute it and/or modify it under the terms of the GNU General
  Public License as published by the Free Software Foundation; either
  version 2, or (at your option) any later version.

  The library is distributed in the hope that it will be useful, but
  WITHOUT ANY WARRANTY; without even the implied warranty of
  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
  General Public License for more details.

  You should have received a copy of the GNU General Public License
  along with the PHP/Java Bridge; see the file COPYING.  If not, write to the
  Free Software Foundation, Inc., 59 Temple Place, Suite 330, Boston, MA
  02111-1307 USA.

  Linking this file statically or dynamically with other modules is
  making a combined work based on this library.  Thus, the terms and
  conditions of the GNU General Public License cover the whole
  combination.

  As a special exception, the copyright holders of this library give you
  permission to link this library with independent modules to produce an
  executable, regardless of the license terms of these independent
  modules, and to copy and distribute the resulting executable under
  terms of your choice, provided that you also meet, for each linked
  independent module, the terms and conditions of the license of that
  module.  An independent module is a module which is not derived from
  or based on this library.  If you modify this library, you may extend
  this exception to your version of the library, but you are not
  obligated to do so.  If you do not wish to do so, delete this
  exception statement from your version. */  

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
 * Max. number of bytes in a context ID, should be > POSIX_PATH_MAX 
 */
#define CONTEXT_LEN_MAX 512


/*
 * default log file is System.out
 */
#define LOGFILE ""

#define LOG_OFF 0
#define LOG_FATAL 1  /* default level */
#define LOG_ERROR 2
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

typedef struct sfile SFILE;
typedef struct proxyenv_ *proxyenv;

/** A procedure type which can be used to create a procedure to create
	a proxy environment, e.g. a secure (SSL) or a standard environment */
typedef proxyenv* (environment_factory) 
  (short (*handle_request)(proxyenv *env), 
   short (*handle_cached)(proxyenv *env), 
   short *is_local);

struct proxyenv_ {

  /* peer */
  int peer, peerr, peer0;		/* peer0 contains peer during override
								   redirect */
  short is_shared;				/* 1, if peer, peerr, servlet_ctx is shared */
  short peer_redirected;		/* remains true during override
								   redirect */
  struct sockaddr orig_peer_saddr; /* only valid if peer is a servlet, it
								   points to the original peer */

  /* the name of the comm. pipe */
  struct pipe {
	char*channel, *in, *out;
	int lockfile;
  } pipe;

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
  short must_reopen, must_share;
  short connection_is_closed;

  struct async_ctx {
	short (*handle_request)(proxyenv *env);
	unsigned long nextValue;
	void *result;
	SFILE *peer;
	ssize_t (*f_send)(proxyenv*env, const void *buf, size_t len);
  } async_ctx;

  /* for servlet engines only */
  char *servlet_ctx;			/* the # of the server context runner */
  char *current_servlet_ctx;	/* the ctx # for this request only, used when persistent connections are enabled */
  char *servlet_context_string;	/* original rinit value from
								   get_servlet_context() */
  short backend_has_session_proxy;
  struct saved_cfg {			/* copy of JG(cfg) used in activate/passivate connection */
	int ini_user;
	short java_socket_inet;
	char *hosts, *servlet;
  } cfg;
  
  short (*handle)(proxyenv *env);
  short (*handle_request)(proxyenv *env);

  void  (*checkSession)(proxyenv *env);
  void  (*redirect)(proxyenv *env);

  void (*writeCreateObjectBegin)(proxyenv *env, char*name, size_t strlen, char createInstance, void *result);
  short (*writeCreateObjectEnd)(proxyenv *env);
  void (*writeInvokeBegin)(proxyenv *env, unsigned long object, char*method, size_t strlen, char property, void* result);
  short (*writeInvokeEnd)(proxyenv *env);
  void (*writeResultBegin)(proxyenv *env, void* result);
  void (*writeResultEnd)(proxyenv *env);
  void (*writeString)(proxyenv *env, char*name, size_t strlen);
  void (*writeBoolean)(proxyenv *env, short boolean);
  void (*writeLong)(proxyenv *env, long l);
  void (*writeULong)(proxyenv *env, unsigned long l);
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
  short (*endSession)(proxyenv *env);

  short   (*f_close)(proxyenv *env);
  ssize_t (*f_recv)(proxyenv*env, void *buf, size_t len);
  ssize_t (*f_recv0)(proxyenv*env, void *buf, size_t len);
  ssize_t (*f_send)(proxyenv*env, const void *buf, size_t len);
  ssize_t (*f_send0)(proxyenv*env, const void *buf, size_t len);

  void (*destruct)(proxyenv *env);
};

#endif
