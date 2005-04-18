/*-*- mode: C; tab-width:4 -*-*/

#ifndef JAVA_MULTICAST_H
#define JAVA_MULTICAST_H

#include <time.h>

#define GROUP_ADDR "239.255.6.10"
#define GROUP_PORT 9167
#define MAX_LOAD 12  // ignore servers with a load higher than this value

extern int php_java_recv_multicast(int sock, unsigned char spec, time_t time);
extern void php_java_send_multicast(int sock, unsigned char spec, time_t time);
extern int php_java_init_multicast();

#endif

