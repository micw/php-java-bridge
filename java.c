/*-*- mode: C; tab-width:4 -*-*/

/* wait */
#include <sys/types.h>
#include <sys/wait.h>
/* miscellaneous */
#include <stdio.h>
#include <stdlib.h>
#include <assert.h>
#include <errno.h>

#include "php.h"
#include "php_globals.h"
#include "ext/standard/info.h"

#include "php_java.h"
#include "java_bridge.h"
#include "protocol.h"

ZEND_DECLARE_MODULE_GLOBALS(java)

PHP_RINIT_FUNCTION(java) 
{
    extern int java_connect_to_server(struct cfg*cfg TSRMLS_DC);
	if(JG(jenv)) return SUCCESS;
	return java_connect_to_server(&JG(cfg) TSRMLS_CC) || SUCCESS;
}
PHP_RSHUTDOWN_FUNCTION(java)
{
	return SUCCESS;
}

PHP_FUNCTION(java_last_exception_get)
{
  jlong result = 0;
  jmethodID lastEx;

  if (ZEND_NUM_ARGS()!=0) WRONG_PARAM_COUNT;

  result = (jlong)(long)return_value;
  
  lastEx = (*JG(jenv))->GetMethodID(JG(jenv), JG(reflect_class), 
          "lastException", "(JJ)V");

  (*JG(jenv))->LastException(JG(jenv), JG(php_reflect), lastEx, 
							  result);
}
PHP_FUNCTION(java_last_exception_clear)
{
  jlong result = 0;
  jmethodID clearEx;

  if (ZEND_NUM_ARGS()!=0) WRONG_PARAM_COUNT;

  result = (jlong)(long)return_value;
  
  clearEx = (*JG(jenv))->GetMethodID(JG(jenv), JG(reflect_class), 
          "clearException", "()V");

  (*JG(jenv))->CallVoidMethod(1, JG(jenv), JG(php_reflect), 
							  clearEx);
}

function_entry java_functions[] = {
	PHP_FE(java_last_exception_get, NULL)
	PHP_FE(java_last_exception_clear, NULL)
	{NULL, NULL, NULL}
};

zend_module_entry java_module_entry = {
	STANDARD_MODULE_HEADER,
	"java",
	java_functions,
	PHP_MINIT(java),
	PHP_MSHUTDOWN(java),
	PHP_RINIT(java),
	PHP_RSHUTDOWN(java),
	PHP_MINFO(java),
	NO_VERSION_YET,
	STANDARD_MODULE_PROPERTIES
};

#ifdef COMPILE_DL_JAVA
ZEND_GET_MODULE(java)
#endif

int  le_jobject;
int java_ini_updated = 0;
zend_class_entry php_java_class_entry;

static PHP_INI_MH(OnIniSockname)
{
	if (new_value) {
	  if(JG(cfg).sockname) free(JG(cfg).sockname);
	  JG(cfg).sockname=strdup(new_value);
	}
	return SUCCESS;
}
static PHP_INI_MH(OnIniClassPath)
{
	if (new_value) {
	  if(JG(cfg).classpath) free(JG(cfg).classpath);
	  JG(cfg).classpath =strdup(new_value);
	}
	return SUCCESS;
}
static PHP_INI_MH(OnIniLibPath)
{
	if (new_value) {
	  if(JG(cfg).ld_library_path) free(JG(cfg).ld_library_path);
	  JG(cfg).ld_library_path = strdup(new_value);
	}
	return SUCCESS;
}
static PHP_INI_MH(OnIniJava)
{
	if (new_value) {
	  if(JG(cfg).java) free(JG(cfg).java);
		JG(cfg).java = strdup(new_value);
	}
	return SUCCESS;
}
static PHP_INI_MH(OnIniJavaHome)
{
	if (new_value) {
	  if(JG(cfg).java_home) free(JG(cfg).java_home);
	  JG(cfg).java_home = strdup (new_value);
	}
	return SUCCESS;
}
static PHP_INI_MH(OnIniLogLevel)
{
	if (new_value) {
	  if(JG(cfg).logLevel) free(JG(cfg).logLevel);
	  JG(cfg).logLevel = strdup(new_value);
	}
	return SUCCESS;
}
static PHP_INI_MH(OnIniLogFile)
{
	if (new_value) {
	  if(JG(cfg).logFile) free(JG(cfg).logFile);
	  JG(cfg).logFile = strdup(new_value);
	}
	return SUCCESS;
}
PHP_INI_BEGIN()
	 PHP_INI_ENTRY("java.socketname", NULL, PHP_INI_SYSTEM, OnIniSockname)
	 PHP_INI_ENTRY("java.classpath", NULL, PHP_INI_SYSTEM, OnIniClassPath)
	 PHP_INI_ENTRY("java.libpath",   NULL, PHP_INI_SYSTEM, OnIniLibPath)
	 PHP_INI_ENTRY("java.java",   NULL, PHP_INI_SYSTEM, OnIniJava)
	 PHP_INI_ENTRY("java.java_home",   NULL, PHP_INI_SYSTEM, OnIniJavaHome)

	 PHP_INI_ENTRY("java.log_level",   NULL, PHP_INI_SYSTEM, OnIniLogLevel)
	 PHP_INI_ENTRY("java.log_file",   NULL, PHP_INI_SYSTEM, OnIniLogFile)
PHP_INI_END()


static void php_java_alloc_globals_ctor(zend_java_globals *java_globals TSRMLS_DC)
{
	memset(java_globals, 0, sizeof(zend_java_globals));
}

PHP_MINIT_FUNCTION(java)
{
	/* function definitions found in bridge.c */
	INIT_OVERLOADED_CLASS_ENTRY(php_java_class_entry, "java", NULL,
								php_java_call_function_handler,
								php_java_get_property_handler,
								php_java_set_property_handler);

	zend_register_internal_class(&php_java_class_entry TSRMLS_CC);

	/* Register the resource, with destructor (arg 1) and text
	   description (arg 3), the other arguments are just standard
	   placeholders */
	le_jobject = zend_register_list_destructors_ex(NULL, php_java_destructor, "java", module_number);

	ZEND_INIT_MODULE_GLOBALS(java, php_java_alloc_globals_ctor, NULL);
	REGISTER_INI_ENTRIES();

	extern void java_init_cfg(struct cfg*cfg);
	java_init_cfg(&JG(cfg));
	extern int java_test_server(struct cfg*cfg TSRMLS_DC);
	if(java_test_server(&JG(cfg) TSRMLS_CC)==FAILURE) 
	  java_start_server(&JG(cfg));

	return java_test_server(&JG(cfg) TSRMLS_CC) || SUCCESS;
}
static char*get_server_args(struct cfg*cfg) {
  int i;
  char*s;
  char*env[2];
  char*args[9];
  unsigned int length = 0;
  extern void java_get_server_args(struct cfg*cfg, char*env[2], char*args[9]);

  java_get_server_args(cfg, env, args);

  for(i=0; i< (sizeof env)/(sizeof*env); i++) {
	if(!env[i]) break;
	length+=strlen(env[i])+1;
  }
  for(i=0; i< (sizeof args)/(sizeof*args); i++) {
	if(!args[i]) break;
	length+=strlen(args[i])+1;
  }
  s=malloc(length+1);
  assert(s);
  *s=0;
  for(i=0; i< (sizeof env)/(sizeof*env); i++) {
	if(!env[i]) break;
	strcat(s, env[i]); strcat(s, " ");
	free(env[i]);
  }
  for(i=0; i< (sizeof args)/(sizeof*args); i++) {
	if(!args[i]) break;
	strcat(s, args[i]); strcat(s, " ");
	free(args[i]);
  }
  s[length]=0;
  return s;
}
PHP_MINFO_FUNCTION(java)
{
	int status = java_test_server(&JG(cfg) TSRMLS_CC);
	php_info_print_table_start();
	php_info_print_table_row(2, "java support", "Enabled");
	php_info_print_table_row(2, "java.libpath", JG(cfg).ld_library_path);
	php_info_print_table_row(2, "java.classpath", JG(cfg).classpath);
	php_info_print_table_row(2, "java.java_home", JG(cfg).java_home);
	php_info_print_table_row(2, "java.java", JG(cfg).java);
	php_info_print_table_row(2, "java.socketname", JG(cfg).sockname);
	if(strlen(JG(cfg).logFile)==0) 
	  php_info_print_table_row(2, "java.log_file", "<stdout>");
	else
	  php_info_print_table_row(2, "java.log_file", JG(cfg).logFile);
	php_info_print_table_row(2, "java.log_level", JG(cfg).logLevel);
	if(status==SUCCESS) {
	  php_info_print_table_row(2, "java status", "running");
	} else {
	  char*s=get_server_args(&JG(cfg));
	  php_info_print_table_row(2, "java not running, start with:", s);
	  free(s);
	}
	php_info_print_table_end();
}

PHP_MSHUTDOWN_FUNCTION(java) 
{
  extern void php_java_shutdown_library(TSRMLS_D);

  UNREGISTER_INI_ENTRIES();
  php_java_shutdown_library(TSRMLS_C);
  return SUCCESS;
}
