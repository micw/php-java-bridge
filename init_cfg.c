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
  int n;
  if(!cfg->sockname) {
	char*s=SOCKNAME;
	char *sockname=malloc(strlen(s+1));
	assert(sockname);
	strcpy(sockname, s);
	n = mkstemp(sockname);
	assert(n);
	cfg->sockname=sockname;
  }
  if(!cfg->classpath) cfg->classpath=strdup(CFG_CLASSPATH);
  if(!cfg->ld_library_path) cfg->ld_library_path=strdup(CFG_LD_LIBRARY_PATH);
  if(!cfg->java) cfg->java=strdup(CFG_JAVA);
  if(!cfg->java_home) cfg->java_home=strdup(CFG_JAVA_HOME);
  if(!cfg->logLevel) cfg->logLevel=strdup("5");
  if(!cfg->logFile) cfg->logFile=strdup("");

  cfg->saddr.sun_family = AF_UNIX;
  strcpy(cfg->saddr.sun_path, cfg->sockname);
}
