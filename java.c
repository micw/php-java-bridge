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
	assert(!JG(jenv));
	java_connect_to_server(&JG(cfg) TSRMLS_CC);
	return SUCCESS;
}
PHP_RSHUTDOWN_FUNCTION(java)
{
  if (JG(php_reflect)) (*JG(jenv))->DeleteGlobalRef(JG(jenv), JG(php_reflect));
  if(JG(jenv)&&*JG(jenv)&&(*JG(jenv))->peer) fclose((*JG(jenv))->peer);
  if(JG(jenv)&&*JG(jenv)) free(*JG(jenv));
  if(JG(jenv)) free(JG(jenv));

  JG(php_reflect) = NULL;
  JG(jenv) = NULL;
  return SUCCESS;
}

PHP_FUNCTION(java_last_exception_get)
{
  jlong result = 0;
  jmethodID lastEx;

  if (ZEND_NUM_ARGS()!=0) WRONG_PARAM_COUNT;

  result = (jlong)(long)return_value;
  
  if(!JG(jenv)) {
	php_error(E_ERROR, "java not initialized");
	return;
  }
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
  
  if(!JG(jenv)) {
	php_error(E_ERROR, "java not initialized");
	return;
  }
  clearEx = (*JG(jenv))->GetMethodID(JG(jenv), JG(reflect_class), 
          "clearException", "()V");

  (*JG(jenv))->CallVoidMethod(1, JG(jenv), JG(php_reflect), 
							  clearEx);
}

PHP_FUNCTION(java_set_jar_library_path)
{
  zval **path;
  jlong result = 0;
  jmethodID setJarPath;
  jstring p;

  if (ZEND_NUM_ARGS()!=1 || zend_get_parameters_ex(1, &path) == FAILURE) 
	WRONG_PARAM_COUNT;

  convert_to_string_ex(path);

  result = (jlong)(long)return_value;
  
  if(!JG(jenv)) {
	php_error(E_ERROR, "java not initialized");
	return;
  }
  setJarPath = (*JG(jenv))->GetMethodID(JG(jenv), JG(reflect_class), 
										"setJarLibraryPath", 
										"(Ljava/lang/String;)V");

  p = (*JG(jenv))->NewStringUTF(JG(jenv), Z_STRVAL_PP(path));
  (*JG(jenv))->CallVoidMethod(2, JG(jenv), JG(php_reflect), 
							  setJarPath, p);

  (*JG(jenv))->DeleteLocalRef(JG(jenv), p);
}

function_entry java_functions[] = {
	PHP_FE(java_last_exception_get, NULL)
	PHP_FE(java_last_exception_clear, NULL)
	PHP_FE(java_set_jar_library_path, NULL)
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
int java_ini_updated, java_ini_last_updated;
zend_class_entry php_java_class_entry;

static PHP_INI_MH(OnIniSockname)
{
	if (new_value) {
	  JG(cfg).sockname=new_value;
	  java_ini_updated|=U_SOCKNAME;
	}
	return SUCCESS;
}
static PHP_INI_MH(OnIniClassPath)
{
	if (new_value) {
	  JG(cfg).classpath =new_value;
	  java_ini_updated|=U_CLASSPATH;
	}
	return SUCCESS;
}
static PHP_INI_MH(OnIniLibPath)
{
	if (new_value) {
	  JG(cfg).ld_library_path = new_value;
	  java_ini_updated|=U_LIBRARY_PATH;
	}
	return SUCCESS;
}
static PHP_INI_MH(OnIniJava)
{
  if (new_value) {
	JG(cfg).java = new_value;
	java_ini_updated|=U_JAVA;
  }
  return SUCCESS;
}
static PHP_INI_MH(OnIniJavaHome)
{
	if (new_value) {
	  JG(cfg).java_home = new_value;
	  java_ini_updated|=U_JAVA_HOME;
	}
	return SUCCESS;
}
static PHP_INI_MH(OnIniLogLevel)
{
	if (new_value) {
	  JG(cfg).logLevel = new_value;
	  java_ini_updated|=U_LOGLEVEL;
	}
	return SUCCESS;
}
static PHP_INI_MH(OnIniLogFile)
{
	if (new_value) {
	  JG(cfg).logFile = new_value;
	  java_ini_updated|=U_LOGFILE;
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
  java_globals->php_reflect=0;
  java_globals->jenv=0;
  java_globals->reflect_class=0;
}

static void init_server()
{
  extern int java_test_server(struct cfg*cfg TSRMLS_DC);
  if(java_test_server(&JG(cfg) TSRMLS_CC)==FAILURE) 
	java_start_server(&JG(cfg));
  java_test_server(&JG(cfg) TSRMLS_CC);
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
	le_jobject = zend_register_list_destructors_ex(php_java_destructor, NULL, "java", module_number);

	ZEND_INIT_MODULE_GLOBALS(java, php_java_alloc_globals_ctor, NULL);

	if(REGISTER_INI_ENTRIES()==SUCCESS) {
	  /* set the default values for all undefined */
	  extern void java_init_cfg(struct cfg *cfg);
	  java_init_cfg(&JG(cfg));
	  JG(cfg).saddr.sun_family = AF_UNIX;
	  memset(JG(cfg).saddr.sun_path, 0, sizeof JG(cfg).saddr.sun_path);
	  strcpy(JG(cfg).saddr.sun_path, JG(cfg).sockname);
	}
	init_server();

	assert(!java_ini_last_updated);
	java_ini_last_updated=java_ini_updated;
	java_ini_updated=0;

	return SUCCESS;
}
static char*get_server_args(struct cfg*cfg) {
  int i;
  char*s;
  char*env[N_SENV];
  char*args[N_SARGS];
  unsigned int length = 0;

  java_get_server_args(cfg, env, args);

  for(i=0; i< (sizeof env)/(sizeof*env); i++) {
	if(!env[i]) break;
	length+=strlen(env[i])+1;
  }
  for(i=0; i< (sizeof args)/(sizeof*args); i++) {
	size_t l;
	if(!args[i]) break;
	l=strlen(args[i]);
	length+=(l?l:2)+1;
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
	if(!strlen(args[i])) strcat(s,"'");
	strcat(s, args[i]);
	if(!strlen(args[i])) strcat(s,"'");
	strcat(s, " ");
	free(args[i]);
  }
  s[length]=0;
  return s;
}
PHP_MINFO_FUNCTION(java)
{
	char*s=get_server_args(&JG(cfg));
	int status = java_test_server(&JG(cfg) TSRMLS_CC);

	php_info_print_table_start();
	php_info_print_table_row(2, "java support", "Enabled");
	php_info_print_table_row(2, "java bridge", java_bridge_version);
#ifndef CFG_JAVA_SOCKET_ANON
	php_info_print_table_row(2, "java command", s);
#endif
	php_info_print_table_row(2, "java.libpath", JG(cfg).ld_library_path);
	php_info_print_table_row(2, "java.classpath", JG(cfg).classpath);
	php_info_print_table_row(2, "java.java_home", JG(cfg).java_home);
	php_info_print_table_row(2, "java.java", JG(cfg).java);
	if(strlen(JG(cfg).logFile)==0) 
	  php_info_print_table_row(2, "java.log_file", "<stdout>");
	else
	  php_info_print_table_row(2, "java.log_file", JG(cfg).logFile);
	php_info_print_table_row(2, "java.log_level", JG(cfg).logLevel);
	php_info_print_table_row(2, "java status", (status==SUCCESS)?"running":"not running");
	php_info_print_table_end();

	free(s);
}

PHP_MSHUTDOWN_FUNCTION(java) 
{
  extern void php_java_shutdown_library(TSRMLS_D);
  extern void java_destroy_cfg(int, struct cfg*);

  java_destroy_cfg(java_ini_last_updated, &JG(cfg));
  java_ini_last_updated=0;

  UNREGISTER_INI_ENTRIES();
  php_java_shutdown_library(TSRMLS_C);
  return SUCCESS;
}
