/*-*- mode: C; tab-width:4 -*-*/

#ifndef PHP_JAVA_H
#define PHP_JAVA_H

#ifdef HAVE_CONFIG_H
#include "config.h"
#endif

#include "php.h"
#include "zend_compile.h"
#include "php_ini.h"
#include "php_globals.h"

/* socket */
#include <sys/types.h>
#include <sys/socket.h>
#ifdef CFG_JAVA_SOCKET_INET
# include <netinet/in.h>
#else
# include <sys/un.h>
#endif

#include <jni.h>

#include "protocol.h"

extern int le_jobject;
extern zend_module_entry java_module_entry;
extern zend_class_entry *php_java_class_entry, *php_java_exception_class_entry;
extern const char * const java_bridge_version;

extern int java_ini_updated;
#define U_LOGFILE (1<<1)
#define U_LOGLEVEL (1<<2)
#define U_JAVA_HOME (1<<3)
#define U_JAVA (1<<4)
#define U_LIBRARY_PATH (1<<5)
#define U_CLASSPATH (1<<6)
#define U_SOCKNAME (1<<7)


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
  struct sockaddr_un saddr;
  int cid; // server's process id
  int err; // file descriptor: server's return code
  char*sockname;
  char*classpath;	
  char*ld_library_path;
  char*java;
  char*java_home;
  char*logLevel;
  char*logFile;
};

ZEND_BEGIN_MODULE_GLOBALS(java)
  proxyenv *jenv;
  jobject php_reflect;
  jclass  reflect_class, iterator_class;
  jmethodID clearEx, lastEx, setJarPath, invoke, gsp, co;
  jmethodID getPhpMap, hasMore, getType, moveForward;
  struct cfg cfg;
ZEND_END_MODULE_GLOBALS(java)




#ifdef ZTS
# define JG(v) TSRMG(java_globals_id, zend_java_globals *, v)
#else
# define JG(v) (java_globals.v)
#endif


extern void java_get_server_args(struct cfg*cfg, 
								 char*env[N_SENV], 
								 char*args[N_SARGS]);

#endif
