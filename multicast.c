/*-*- mode: C; tab-width:4 -*-*/

#include <sys/types.h>
#include <sys/socket.h>
#include <netinet/in.h>
#include <arpa/inet.h>
#include <sys/select.h>
#include <sys/time.h>
#include <unistd.h>
#include <errno.h>
#include <stdio.h>

#include "multicast.h"


int php_java_init_multicast() {
  int n;
  long s_true=1;
  struct sockaddr_in saddr;
  struct ip_mreqn ip_mreqn;

  ip_mreqn.imr_multiaddr.s_addr=inet_addr(GROUP_ADDR);
  ip_mreqn.imr_address.s_addr=htonl(INADDR_ANY);
  ip_mreqn.imr_ifindex=0;

  saddr.sin_family = AF_INET;
  saddr.sin_port = htons(GROUP_PORT);
  saddr.sin_addr.s_addr = htonl(INADDR_ANY);

  int sock = socket(PF_INET, SOCK_DGRAM, IPPROTO_UDP);
  if(sock!=-1) {
    setsockopt(sock, SOL_SOCKET, SO_REUSEADDR, &s_true, sizeof s_true);
    setsockopt(sock, SOL_IP, IP_MULTICAST_LOOP, &s_true, sizeof s_true);
    setsockopt(sock, SOL_IP, IP_ADD_MEMBERSHIP, &ip_mreqn, sizeof ip_mreqn);
    bind(sock, (struct sockaddr*)&saddr, sizeof saddr);
  }
  return sock;
}
  
void php_java_send_multicast(int sock, unsigned char spec) {
  unsigned char c[14] = {'R', spec, MAX_LOAD};
  struct sockaddr_in saddr;
  if(-1==sock) return;

  saddr.sin_family = AF_INET;
  saddr.sin_port = htons(GROUP_PORT);
  saddr.sin_addr.s_addr=inet_addr(GROUP_ADDR);  
  sendto(sock, c, sizeof c, 0, (struct sockaddr*)&saddr, sizeof saddr);
}

static int readInt(unsigned char*buf) {
  return (buf[0]&0xFF)<<24|(buf[1]&0xFF)<<16|(buf[2]&0xFF)<<8|(buf[3]&0xFF);
}
int php_java_recv_multicast(int sock, unsigned char spec) {
  unsigned char c[14];
  int n;
  struct timeval time = {0, 10};
  fd_set set;
  if(-1==sock) return -1;

  FD_ZERO(&set);
  FD_SET(sock, &set);

  do {
    n = select(sock+1, &set, 0, 0, &time);
    if(n<0) return -1;
    if(!n) return -1;
    read(sock, c, sizeof c);
  } while(c[0]=='R' || (spec!=0 && spec!=c[1]));

  return readInt(c+3);
}

