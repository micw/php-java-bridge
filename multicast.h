/*-*- mode: C; tab-width:4 -*-*/

#ifndef JAVA_MULTICAST_H
#define JAVA_MULTICAST_H

#include <time.h>

#define GROUP_ADDR "239.255.6.10"
#define GROUP_PORT 9168
#define MAX_LOAD 12  // ignore servers with a load higher than this value
#define LOAD_PENALTY 8 // in ms, see Listener.java
#define MAX_PENALTY (MAX_LOAD*LOAD_PENALTY)
#define MAX_TRIES MAX_LOAD // # of times we try to reach backends until we
					  // # throw an error

extern int php_java_recv_multicast(int sock, unsigned char spec, time_t time);
extern void php_java_send_multicast(int sock, unsigned char spec, time_t time);
extern int php_java_init_multicast();
extern void php_java_sleep_ms(int ms);
extern short php_java_multicast_backends_available();

#endif

