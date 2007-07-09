/*-*- mode: C; tab-width:4 -*-*/

/* secure_protocol.c -- implementation of the PHP/Java Bridge XML
   protocol, uses SSL to communicate with the J2EE server.

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

#include "zend.h"
#include "init_cfg.h"
#if !defined(ZEND_ENGINE_2) || EXTENSION == MONO

#include "php_java.h"

#include <stdarg.h>
#include <errno.h>
#include <string.h>
#include <stdlib.h>

#include "protocol.h"
#include "java_bridge.h"

EXT_EXTERN_MODULE_GLOBALS(EXT)

/* Instead of using a separate protocol stack, this implementation
   changes f_send, f_recv and f_close in the constructor and when
   checkSession ("override redirect") is called. It reverts to the
   original values when redirect arrives. */
struct secure_proxyenv_ {
  struct proxyenv_ env;

  zval *secure_peer;			/* the SSL socket */
  char *host; int port;

  short (*f_close)(proxyenv *env);
  ssize_t (*f_send)(proxyenv*env, const void *buf, size_t len);
  ssize_t (*f_recv)(proxyenv*env, void *buf, size_t len);

  void (*destruct)(proxyenv*env);
};
typedef struct secure_proxyenv_ *secure_proxyenv;

static ssize_t send_socket(proxyenv*_env, const void*buf, size_t length) {
  static char name[] = "fwrite";
  ssize_t count;
  secure_proxyenv *env = (secure_proxyenv*)_env;
  zval array, *string, *retval, *resource = (*env)->secure_peer;
  array_init(&array);
  INIT_PZVAL(&array);

  MAKE_STD_ZVAL(string);
  ZVAL_STRINGL(string, (char*)buf, length, 1);
  zval_add_ref(&resource);
  zend_hash_next_index_insert(Z_ARRVAL(array),&resource,sizeof(zval *), NULL);
  zend_hash_next_index_insert(Z_ARRVAL(array),&string,sizeof(zval *), NULL);
  EXT_GLOBAL(call_php_function) (name, sizeof(name)-1, &array, &retval);
  count = Z_LVAL_P(retval);

  zval_dtor(&array);
  zval_ptr_dtor(&retval);

  return count;
}

static ssize_t recv_socket(proxyenv*_env, void*buf, size_t length) {
  static char name[] = "fread";
  ssize_t count;
  secure_proxyenv *env = (secure_proxyenv*)_env;
  zval array, *number, *retval, *resource = (*env)->secure_peer;
  array_init(&array);
  INIT_PZVAL(&array);

  MAKE_STD_ZVAL(number);
  ZVAL_LONG(number, length);

  zval_add_ref(&resource);
  zend_hash_next_index_insert(Z_ARRVAL(array),&resource,sizeof(zval *), NULL);
  zend_hash_next_index_insert(Z_ARRVAL(array),&number,sizeof(zval *), NULL);
  EXT_GLOBAL(call_php_function) (name, sizeof(name)-1, &array, &retval);
  memcpy(buf, Z_STRVAL_P(retval), count = Z_STRLEN_P(retval));

  zval_dtor(&array);
  zval_ptr_dtor(&number);
  zval_ptr_dtor(&retval);

  return count;
}
static short open_secure_socket(struct secure_proxyenv_*env) {
  static char name[] = "fsockopen";
  zval array, *string, *number;
  zval **sock = &env->secure_peer;
  array_init(&array);
  INIT_PZVAL(&array);

  MAKE_STD_ZVAL(string);
  ZVAL_STRING(string, env->host, 1);

  MAKE_STD_ZVAL(number);
  ZVAL_LONG(number, env->port);

  zend_hash_next_index_insert(Z_ARRVAL(array),&string,sizeof(zval *), NULL);
  zend_hash_next_index_insert(Z_ARRVAL(array),&number,sizeof(zval *), NULL);
  EXT_GLOBAL(call_php_function) (name, sizeof(name)-1, &array, sock);
  if(Z_TYPE_PP(sock)!=IS_RESOURCE) return 0;

  zval_dtor(&array);
  zval_ptr_dtor(&string);
  zval_ptr_dtor(&number);
  return 1;
}

static char *init(struct secure_proxyenv_ *env) {
  struct proxyenv_ *_env = &env->env;
  //static char prefix[] = "";
  static char prefix[] = "ssl://";
  char *name, *hosts, *host, *port, *ssl;
  size_t len;

  TSRMLS_FETCH();

  hosts = strdup(JG(hosts)); if(!hosts) exit(9);
  host = strtok(hosts, "; "); /* TODO: check other hosts from the list */

  name = strdup(host); if(!name) exit(9);

  port = strchr(host, ':');
  assert(port);
  if(port) *port++=0; else port = "8443";

  len = strlen(host);
  ssl = emalloc(len+sizeof(prefix)); if(!ssl) exit(9);

  strcpy(ssl, prefix);
  strcat(ssl, host);
  env->host = ssl;
  env->port = atoi(port);
  free(hosts);
  return name;
}

static short close_socket(proxyenv *_env) {
  static char name[] = "fclose";
  secure_proxyenv *env = (secure_proxyenv*)_env;
  zval array, *retval, *resource = (*env)->secure_peer;
  array_init(&array);
  INIT_PZVAL(&array);

  zval_add_ref(&resource);
  zend_hash_next_index_insert(Z_ARRVAL(array),&resource,sizeof(zval *), NULL);
  EXT_GLOBAL(call_php_function) (name, sizeof(name)-1, &array, &retval);

  zval_dtor(&array);
  zval_ptr_dtor(&retval);
  zval_ptr_dtor(&resource);

  return 1;
}

static void check_session (proxyenv *_env) {
  secure_proxyenv *env = (secure_proxyenv*)_env;

  TSRMLS_FETCH();

  if(!(*_env)->is_local && IS_SERVLET_BACKEND(_env) && !(*_env)->backend_has_session_proxy) {
	if((*_env)->peer_redirected) { /* override redirect */
	  if (open_secure_socket(*env)) {
		(*_env)->peer0 = (*_env)->peer;
		(*_env)->peer = 0;

		(*_env)->f_recv = recv_socket;
		(*_env)->f_send = send_socket;
		(*_env)->f_close = close_socket;
	  } else {
		EXT_GLOBAL(sys_error)("Could not connect to server",48);
	  }
	}
	(*_env)->finish=(*_env)->endSession;
  }
}
static void redirect(proxyenv*_env) {
  secure_proxyenv*env = (secure_proxyenv*)_env;
  (*_env)->f_close = (*env)->f_close;
  (*_env)->f_recv = (*env)->f_recv;
  (*_env)->f_send =(*env)->f_send;
}

static void destruct(proxyenv*_env) {
  secure_proxyenv*env = (secure_proxyenv*)_env;
  if((*env)->host) { free((*env)->host); (*env)->host = 0; }

  (*_env)->destruct(_env);
}

 

proxyenv *EXT_GLOBAL(createSecureEnvironment) (short (*handle_request)(proxyenv *env), short (*handle_cached)(proxyenv *env), short *is_local) {
  char *server;
  zval *peer = 0;
  secure_proxyenv *env;  
  struct proxyenv_ *_env;
  
  env=(secure_proxyenv*)malloc(sizeof *env);     
  if(!env) return 0;
  *env=(secure_proxyenv)calloc(1, sizeof **env);
  if(!*env) {free(env); return 0;}

  server = init(*env);
  if(!open_secure_socket(*env)) {free(server); free(*env); free(env); return 0;}

  *is_local = 0;

  if(!EXT_GLOBAL(init_environment)(&((*env)->env), handle_request, handle_cached, *is_local)) {
	free(*env);
	free(env);
	return 0;
  }

  _env = &((*env)->env);

  (*env)->f_recv = _env->f_recv;
  (*env)->f_send = _env->f_send;
  (*env)->f_close = _env->f_close;
  (*env)->destruct = _env->destruct;

  _env->server_name = server;
  _env->peer=0; // != -1 so that end_connection is called
  _env->f_recv0 = _env->f_recv = recv_socket;
  _env->f_send0 = _env->f_send = send_socket;
  _env->f_close = close_socket;
  _env->checkSession = check_session;
  _env->redirect = redirect;
  return (proxyenv*)env;
}

#ifndef PHP_WRAPPER_H
#error must include php_wrapper.h
#endif

#endif
