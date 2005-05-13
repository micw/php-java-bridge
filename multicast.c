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
#include <sys/time.h>
#include <unistd.h>
#include <errno.h>
#include <stdio.h>
#endif

#include "multicast.h"

#if defined(__GNU_LIBRARY__) && !defined(_SEM_SEMUN_UNDEFINED)
/* union semun is defined by including <sys/sem.h> */
#else
/* according to X/OPEN we have to define it ourselves */
union semun {
  int val;                  /* value for SETVAL */
  struct semid_ds *buf;     /* buffer for IPC_STAT, IPC_SET */
  unsigned short *array;    /* array for GETALL, SETALL */
  /* Linux specific part: */
  struct seminfo *__buf;    /* buffer for IPC_INFO */
};
#endif

static void enter(int id) {
  static struct sembuf ops = {0, -1, 0};
  semop(id, &ops, 1);
}
static void leave(int id) {
  static struct sembuf ops = {0, 1, 0};
  semop(id, &ops, 1);
}
static int init() {
  union semun val;
  struct semid_ds buf;
  int id = semget(0x9168, 1, IPC_CREAT | 0640);
  val.buf = &buf;
  semctl(id, 0, IPC_STAT, val);
  if(!buf.sem_otime) {
    val.val=1;
    semctl(id, 0, SETVAL, val);
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

int php_java_init_multicast() {
  int sock = -1;
#ifndef __MINGW32__
  long s_true=1;
  long s_false=0;
  struct sockaddr_in saddr;
  struct ip_mreq ip_mreq;

  ip_mreq.imr_multiaddr.s_addr=inet_addr(GROUP_ADDR);
  ip_mreq.imr_interface.s_addr=inet_addr("127.0.0.1");//htonl(INADDR_ANY);

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
  int sock = -1;
#ifndef __MINGW32__
  long s_false=0;
  struct sockaddr_in saddr;
  struct ip_mreq ip_mreq;

  ip_mreq.imr_multiaddr.s_addr=inet_addr(GROUP_ADDR);
  ip_mreq.imr_interface.s_addr=inet_addr("127.0.0.1");//htonl(INADDR_ANY);

  saddr.sin_family = AF_INET;
  saddr.sin_port = htons(GROUP_PORT);
  saddr.sin_addr.s_addr = inet_addr("127.0.0.1"); //htonl(INADDR_ANY);

  sock = socket(PF_INET, SOCK_DGRAM, IPPROTO_UDP);
  if(sock!=-1) {
	int id, err;
	enter(id=init()); 
    {
	  setsockopt(sock, SOL_SOCKET, SO_REUSEADDR, &s_false, sizeof s_false);
	  err=bind(sock, (struct sockaddr*)&saddr, sizeof saddr);
	  close(sock);
	} 
    leave(id);
    if(-1==err) return 1;
  }
#endif
  return 0;
}
  
void php_java_sleep_ms(int ms) {
  struct timeval timeout = {0, ms*1000};
  select(0, 0, 0, 0, &timeout);
}
  
void php_java_send_multicast(int sock, unsigned char spec, time_t time) {
#ifndef __MINGW32__
  unsigned char c[18] = {'R', spec, 0xff & getpid()}; //FIXME: use maxtime here
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

