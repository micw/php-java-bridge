/*-*- mode: C; tab-width:4 -*-*/

#ifndef JAVA_MULTICAST_H
#define JAVA_MULTICAST_H

#define GROUP_ADDR "239.255.6.10"
#define GROUP_PORT 9167
#define MAX_LOAD 20  // ignore servers with a load higher than this value

extern int php_java_recv_multicast(int sock, unsigned char spec);
extern void php_java_send_multicast(int sock, unsigned char spec);
extern int php_java_init_multicast();

#endif

