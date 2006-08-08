/*-*- mode: C; tab-width:4 -*-*/

#include <stdlib.h>
#include <stdio.h>

/* wrapper for the GNU java library */
void usage(char*name){
  fprintf(stderr, "Usage: %s <socketname> <logLevel> <logFile>\n", name);
  fprintf(stderr, "Usage: %s --convert <dirname> <libs>\n", name);
  fprintf(stderr, "Example 1: %s /var/run/.php-java-bridge_socket 1 /var/log/php-java-bridge.log\n", name);
  fprintf(stderr, "Example 2: %s INET:9267 3 \"\" \n", name);
  exit(1);
}
int main(int argc,char**argv){
  extern void java_bridge_main_gcj(int argc, char**argv);

  if(argc<4) usage(argv[0]);

  java_bridge_main_gcj(argc,argv);
  return 0;
}
