/*-*- mode: C; tab-width:4 -*-*/

#ifndef PHP_JAVA_H
#define PHP_JAVA_H

#ifdef HAVE_CONFIG_H
#include "config.h"
#endif

#include "php_wrapper.h"
#include "php_config.h"
#include "zend_compile.h"
#include "php_ini.h"
#include "php_globals.h"
#ifdef ZTS
#include "TSRM.h"
#endif

/* socket */
#include <sys/types.h>
#include <sys/socket.h>
#include <netinet/in.h>
#include <arpa/inet.h>
#include <netdb.h>
#ifndef CFG_JAVA_SOCKET_INET
# include <sys/un.h>
# ifdef HAVE_CONFIG_H
# if !HAVE_DECL_AF_LOCAL
#  define AF_LOCAL AF_UNIX
# endif
# if !HAVE_DECL_PF_LOCAL
#  define PF_LOCAL PF_UNIX
# endif
# endif
#endif

#include "protocol.h"

extern int le_jobject;
extern zend_module_entry java_module_entry;
extern zend_class_entry *php_java_class_entry;
extern zend_class_entry *php_java_class_class_entry;
extern zend_class_entry *php_java_jsr_class_class_entry; 
extern zend_class_entry *php_java_exception_class_entry;

#ifdef ZEND_ENGINE_2
extern zend_object_handlers php_java_handlers;
#endif
extern const char * const java_bridge_version;

extern int java_ini_updated;
#define U_LOGFILE (1<<1)
#define U_LOGLEVEL (1<<2)
#define U_JAVA_HOME (1<<3)
#define U_JAVA (1<<4)
#define U_LIBRARY_PATH (1<<5)
#define U_CLASSPATH (1<<6)
#define U_SOCKNAME (1<<7)
#define U_HOSTS (1<<8)


#define phpext_java_ptr &java_module_entry

#ifdef PHP_WIN32
#define PHP_JAVA_API __declspec(dllexport)
#else
#define PHP_JAVA_API
#endif

PHP_MINIT_FUNCTION(java);
PHP_MSHUTDOWN_FUNCTION(java);
PHP_MINFO_FUNCTION(java);

struct cfg {
#ifdef CFG_JAVA_SOCKET_INET
  struct sockaddr_in saddr;
#else
  struct sockaddr_un saddr;
#endif
  int cid; // server's process id
  int err; // file descriptor: server's return code
  char*sockname;
  char*hosts;
  char*classpath;	
  char*ld_library_path;
  char*java;
  char*java_home;
  char*logLevel;
  char*logFile;
  short can_fork;				/* 0 if user has hard-coded the socketname */
};
extern struct cfg *cfg;

ZEND_BEGIN_MODULE_GLOBALS(java)
  proxyenv *jenv;
ZEND_END_MODULE_GLOBALS(java)




#ifdef ZTS
# define JG(v) TSRMG(java_globals_id, zend_java_globals *, v)
#else
# define JG(v) (java_globals.v)
#endif


extern char* java_get_server_string();

extern proxyenv *java_connect_to_server(TSRMLS_D);
extern void java_start_server();

extern char* java_test_server(int *socket);

#endif
