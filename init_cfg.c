/*-*- mode: C; tab-width:4 -*-*/

/* mkstemp */
#include <stdlib.h>

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

void java_init_cfg(struct cfg *cfg) {
  if(!(java_ini_updated&U_SOCKNAME)) {
	int n;
	char*s=SOCKNAME;
	char *sockname=malloc(strlen(s+1));
	assert(sockname);
	strcpy(sockname, s);
	n = mkstemp(sockname);
	assert(n);
	cfg->sockname=sockname;
  }
  if(!(java_ini_updated&U_CLASSPATH)) cfg->classpath=strdup(CFG_CLASSPATH);
  if(!(java_ini_updated&U_LIBRARY_PATH)) cfg->ld_library_path=strdup(CFG_LD_LIBRARY_PATH);
  if(!(java_ini_updated&U_JAVA)) cfg->java=strdup(CFG_JAVA);
  if(!(java_ini_updated&U_JAVA_HOME)) cfg->java_home=strdup(CFG_JAVA_HOME);
  if(!(java_ini_updated&U_LOGLEVEL)) cfg->logLevel=strdup("0");
  if(!(java_ini_updated&U_LOGFILE)) cfg->logFile=strdup("");
}
