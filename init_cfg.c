/*-*- mode: C; tab-width:4 -*-*/

/* mkstemp */
#include <stdlib.h>

/* time */
#include <time.h>

/* assert */
#include <assert.h>

#include "protocol.h"
#include "php_java.h"

#ifndef CFG_CLASSPATH
#define CFG_CLASSPATH "/opt/j2sdkee1.2.1/lib/classes"
#endif
#ifndef CFG_LD_LIBRARY_PATH
#define CFG_LD_LIBRARY_PATH "/usr/local/lib"
#endif
#ifndef CFG_JAVA
#define CFG_JAVA "/opt/jdk1.4/bin/java"
#endif
#ifndef CFG_JAVA_HOME
#define CFG_JAVA_HOME "/opt/jdk1.4"
#endif
#ifndef BRIDGE_VERSION
#define BRIDGE_VERSION "<unknown>"
#endif

const char * const java_bridge_version = BRIDGE_VERSION;

#ifdef CFG_JAVA_SOCKET_ANON
static void init_socket(struct cfg*cfg) {
  static const char * const bridge="@java-bridge-";
  size_t len = strlen(bridge)+ sizeof(uid_t)*2+ sizeof(time_t)*2; //assuming byte=8bit

  char *sockname=malloc(len+1);
  assert(sockname); if(!sockname) exit(6);

  snprintf(sockname, len+1, "%s%lx%lx", bridge, 
		   (unsigned long)getuid(),(unsigned long)time(0));
  cfg->sockname=sockname;
}
#else
static void init_socket(struct cfg*cfg) {
	int n;
	char*s=SOCKNAME;
	char *sockname=malloc(strlen(s+1));
	assert(sockname); if(!sockname) exit(6);
	strcpy(sockname, s);
	n = mkstemp(sockname);
	assert(n); if(!n) exit(6);
	cfg->sockname=sockname;
}
#endif
void java_init_cfg(struct cfg *cfg) {
  if(!(java_ini_updated&U_SOCKNAME)) {
	init_socket(cfg);
  }
  if(!(java_ini_updated&U_CLASSPATH)) cfg->classpath=strdup(CFG_CLASSPATH);
  if(!(java_ini_updated&U_LIBRARY_PATH)) cfg->ld_library_path=strdup(CFG_LD_LIBRARY_PATH);
  if(!(java_ini_updated&U_JAVA)) cfg->java=strdup(CFG_JAVA);
  if(!(java_ini_updated&U_JAVA_HOME)) cfg->java_home=strdup(CFG_JAVA_HOME);
  if(!(java_ini_updated&U_LOGLEVEL)) cfg->logLevel=strdup("0");
  if(!(java_ini_updated&U_LOGFILE)) cfg->logFile=strdup("");
}
