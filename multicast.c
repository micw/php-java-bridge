/*-*- mode: C; tab-width:4 -*-*/

#ifndef __MINGW32__
#include <sys/types.h>
#include <sys/socket.h>
#include <netinet/in.h>
#include <arpa/inet.h>
#include <sys/select.h>
#include <sys/time.h>
#include <unistd.h>
#include <errno.h>
#include <stdio.h>
#endif

#include "multicast.h"

static unsigned long readInt(unsigned char*buf) {
  return (buf[0]&0xFF)<<24|(buf[1]&0xFF)<<16|(buf[2]&0xFF)<<8|(buf[3]&0xFF);
}
static void writeInt(unsigned char*buf, unsigned long i) {
  buf[0]=(i&(0xFF<<24))>>24;
  buf[1]=(i&(0xFF<<16))>>16;
  buf[2]=(i&(0xFF<<8))>>8;
  buf[3]=i&0xFF;
}

int php_java_init_multicast() {
  int sock = -1;
#ifndef __MINGW32__
  long s_true=1;
  struct sockaddr_in saddr;
  struct ip_mreq ip_mreq;

  ip_mreq.imr_multiaddr.s_addr=inet_addr(GROUP_ADDR);
  ip_mreq.imr_interface.s_addr=htonl("127.0.0.1");//htonl(INADDR_ANY);

  saddr.sin_family = AF_INET;
  saddr.sin_port = htons(GROUP_PORT);
  saddr.sin_addr.s_addr = htonl("127.0.0.1"); //htonl(INADDR_ANY);

  sock = socket(PF_INET, SOCK_DGRAM, IPPROTO_UDP);
  if(sock!=-1) {
    setsockopt(sock, SOL_SOCKET, SO_REUSEADDR, &s_true, sizeof s_true);
    setsockopt(sock, IPPROTO_IP, IP_MULTICAST_LOOP, &s_true, sizeof s_true);
    setsockopt(sock, IPPROTO_IP, IP_ADD_MEMBERSHIP, &ip_mreq, sizeof ip_mreq);
    bind(sock, (struct sockaddr*)&saddr, sizeof saddr);
  }
#endif
  return sock;
}
  
void php_java_send_multicast(int sock, unsigned char spec, time_t time) {
#ifndef __MINGW32__
  unsigned char c[18] = {'R', spec, MAX_LOAD};
  struct sockaddr_in saddr;
  if(-1==sock) return;

  writeInt(c+3, (unsigned long)time);
  saddr.sin_family = AF_INET;
  saddr.sin_port = htons(GROUP_PORT);
  saddr.sin_addr.s_addr=inet_addr(GROUP_ADDR);  
  sendto(sock, c, sizeof c, 0, (struct sockaddr*)&saddr, sizeof saddr);
#endif
}

int php_java_recv_multicast(int sock, unsigned char spec, time_t time) {
#ifndef __MINGW32__
  unsigned char c[18];
  int n;
  struct timeval timeout = {0, 10};
  fd_set set;
  if(-1==sock) return -1;

  FD_ZERO(&set);
  FD_SET(sock, &set);

  do {
    time_t t;
    n = select(sock+1, &set, 0, 0, &timeout);
    if(n<0) return -1;			/* error */
    if(!n) return -1;			/* timeout */
    n=read(sock, c, sizeof c);
    if(n!=sizeof c) continue;	/* broken packet */
    t=(time_t)readInt(c+3);
    if(t!=time) continue;		/* old packet */
  } while(c[0]!='r' || (spec!=0 && spec!=c[1]));

  return (int)(0xffff & readInt(c+7));
#else
  return -1;
#endif
}

