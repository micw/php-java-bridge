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
#include <sys/un.h>

#include <jni.h>

#include "protocol.h"

extern int le_jobject;
extern zend_module_entry java_module_entry;
extern zend_class_entry php_java_class_entry;


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
  int cid; //server's process id
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
  jclass  reflect_class;
  struct cfg cfg;
ZEND_END_MODULE_GLOBALS(java)




#ifdef ZTS
# define JG(v) TSRMG(java_globals_id, zend_java_globals *, v)
#else
# define JG(v) (java_globals.v)
#endif


#endif
