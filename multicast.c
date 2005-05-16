/*-*- mode: C; tab-width:4 -*-*/

#ifndef __MINGW32__
#include <sys/types.h>
#include <sys/ipc.h>
#include <sys/sem.h>

#include <sys/types.h>
#include <sys/socket.h>
#include <netinet/in.h>
#include <arpa/inet.h>
#include <sys/select.h>
#include <unistd.h>
#include <errno.h>
#include <string.h>
#include <stdio.h>

#include "multicast.h"

union php_java_semun {
  int val;
  struct semid_ds *buf;
  unsigned short *array;
};

static void enter(int id) {
  static struct sembuf ops = {0, -1, 0};
  semop(id, &ops, 1);
}
static void leave(int id) {
  static struct sembuf ops = {0, 1, 0};
  semop(id, &ops, 1);
}
static int init() {
  union php_java_semun val;
  struct semid_ds buf;
  int id = semget(0x9168, 1, IPC_CREAT | 0640);
  if(id==-1) return -1;
  val.buf = &buf;
  if(-1==semctl(id, 0, IPC_STAT, val)) return -1;
  if(!buf.sem_otime) {
    val.val=1;
    if(-1==semctl(id, 0, SETVAL, val)) return -1;
  }
  return id;
}

static unsigned long readInt(unsigned char*buf) {
  return (buf[0]&0xFF)<<24|(buf[1]&0xFF)<<16|(buf[2]&0xFF)<<8|(buf[3]&0xFF);
}
static void writeInt(unsigned char*buf, unsigned long i) {
  buf[0]=(i&(0xFF<<24))>>24;
  buf[1]=(i&(0xFF<<16))>>16;
  buf[2]=(i&(0xFF<<8))>>8;
  buf[3]=i&0xFF;
}
#endif
#include <sys/time.h>

int php_java_init_multicast() {
  int sock = -1;
#ifndef __MINGW32__
  long s_true=1;
  long s_false=0;
  struct sockaddr_in saddr;
  struct ip_mreq ip_mreq;

  memset(&ip_mreq, 0, sizeof ip_mreq);
  ip_mreq.imr_multiaddr.s_addr=inet_addr(GROUP_ADDR);
  ip_mreq.imr_interface.s_addr=inet_addr("127.0.0.1");//htonl(INADDR_ANY);

  memset(&saddr, 0, sizeof saddr);
  saddr.sin_family = AF_INET;
  saddr.sin_port = htons(GROUP_PORT);
  saddr.sin_addr.s_addr = inet_addr("127.0.0.1"); //htonl(INADDR_ANY);

  sock = socket(PF_INET, SOCK_DGRAM, IPPROTO_UDP);
  if(sock!=-1) {
    setsockopt(sock, SOL_SOCKET, SO_REUSEADDR, &s_true, sizeof s_true);
    setsockopt(sock, IPPROTO_IP, IP_MULTICAST_LOOP, &s_false, sizeof s_false);
    setsockopt(sock, IPPROTO_IP, IP_ADD_MEMBERSHIP, &ip_mreq, sizeof ip_mreq);
    bind(sock, (struct sockaddr*)&saddr, sizeof saddr);
  }
#endif
  return sock;
}

/*
 * Stupid test if there are any backends registered
 */
short php_java_multicast_backends_available() {
  short has_backend = 0;
#ifndef __MINGW32__
  int sock;
  long s_false=0;
  long s_true=1;
  struct sockaddr_in saddr, saddr2;
  struct ip_mreq ip_mreq;

  memset(&saddr, 0, sizeof saddr);
  ip_mreq.imr_multiaddr.s_addr=inet_addr(GROUP_ADDR);
  ip_mreq.imr_interface.s_addr=inet_addr("127.0.0.1");//htonl(INADDR_ANY);

  memset(&saddr, 0, sizeof saddr);
  saddr.sin_family = AF_INET;
  saddr.sin_port = htons(GROUP_PORT);
  saddr.sin_addr.s_addr = inet_addr("127.0.0.1"); //htonl(INADDR_ANY);

  memset(&saddr2, 0, sizeof saddr);
  saddr2.sin_family = AF_INET;
  saddr2.sin_port = htons(GROUP_PORT);
  saddr2.sin_addr.s_addr = inet_addr(GROUP_ADDR); 

  sock = socket(PF_INET, SOCK_DGRAM, IPPROTO_UDP);
  if(sock!=-1) {
	int id, err;
	id=init(); 
	if(id != -1) {
      unsigned char c[1] = {'P'}; /* will be rejected as a broken packet */
	  enter(id);
      /* FIXME: Should protect this from signals.  If someone manages
	   to stop the client in this section, one has to remove the
	   semaphore manually (see commands ipcs -S and ipcrm -S). But
	   since the load balancing code will be rewritten in java anyway
	   (this file, large parts of bind.c and three functions in client
	   will go away) I just keep this hack until the new code is in
	   place */
	  has_backend = 1;
	  setsockopt(sock, SOL_SOCKET, SO_REUSEADDR, &s_false, sizeof s_false);
	  err=bind(sock, (struct sockaddr*)&saddr, sizeof saddr);
	  if(err!=-1) has_backend = 0;
	  if(has_backend) {
		setsockopt(sock, SOL_SOCKET, SO_REUSEADDR, &s_true, sizeof s_true);
		bind(sock, (struct sockaddr*)&saddr, sizeof saddr);
		err = sendto(sock, c, sizeof c, 0, (struct sockaddr*)&saddr2, sizeof saddr2);
		if(err==-1) {
		  has_backend = 0;
		}
	  }
	  close(sock);
	  leave(id);
	} 
  }
#endif
  return has_backend;
}
  
void php_java_sleep_ms(int ms) {
#ifndef __MINGW32__
  struct timeval timeout = {0, ms*1000};
  select(0, 0, 0, 0, &timeout);
#endif
}

void php_java_send_multicast(int sock, unsigned char spec, time_t time) {
#ifndef __MINGW32__
  unsigned char c[18] = {'R', spec, 0xff & getpid()}; //FIXME: use maxtime here
  struct sockaddr_in saddr;
  if(-1==sock) return;

  writeInt(c+3, (unsigned long)time);
  memset(&saddr, 0, sizeof saddr);
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
  struct timeval timeout = {0, 30000};// FIXME: round trips in the
									  // local network are usually
									  // below 10 ms, 30ms is too
									  // much.

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

