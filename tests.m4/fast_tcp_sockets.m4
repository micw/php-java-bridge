AC_DEFUN([CHECK_FAST_TCP_SOCKETS],[
  AC_MSG_CHECKING([for fast tcp sockets])
  AC_CACHE_VAL(have_fast_tcp_sockets,[
  AC_TRY_RUN([
#include <signal.h>
#include <sys/time.h>
#include <sys/socket.h>
#include <netinet/in.h>
#include <arpa/inet.h>
#include <netdb.h>

#include <string.h>
#include <stdlib.h>
#include <stdio.h>


#define SIZE 8192
#define PORT 9789
#define COUNT 200

struct sockaddr_in saddr, saddr2;
static char RES[]="<N i=\"0\"/>;";
static char REQ[]="@<I v=\"0\" m=\"lastException\" p=\"P\" i=\"136070284\"></I>";
static char REQ1[]="@";
static char REQ2[]="<I v=\"0\" m=\"lastException\" p=\"P\" i=\"136070284\"></I>";

void sys_error(char *s) {
  perror(s);
  exit(50);
}
void runTest1() {
  int i, n, count, sock, err;
  char b[sizeof RES];
  for(i=0; i<COUNT; i++) {
    struct sockaddr_in saddr = saddr2;
    sock = socket(PF_INET, SOCK_STREAM, 0); if(sock==-1) sys_error("socket");
    err = connect(sock, (struct sockaddr*)&saddr, sizeof(saddr)); if(err == -1) sys_error("connect");
    n = send(sock, REQ, sizeof(REQ)-1, 0); if(n!=sizeof(REQ)-1) exit(2);
    for(n=0; n<sizeof(RES)-1; n+=count) if((count=recv(sock,b,sizeof(RES)-n-1,0))<0) exit(3);
    //printf("test1: %12s\r", b); 	
    err = close(sock); if (n==-1) sys_error("close");
  }
}

void runTest2() {
  int i, n, count, sock, err;
  char b[sizeof RES];
  for(i=0; i<COUNT; i++) {
    struct sockaddr_in saddr = saddr2;
    sock = socket(PF_INET, SOCK_STREAM, 0); if(sock==-1) sys_error("socket2");
    err = connect(sock, (struct sockaddr*)&saddr, sizeof(saddr)); if(err == -1) sys_error("connect2");
    n = send(sock, REQ1, sizeof(REQ1)-1, 0); if(n!=sizeof(REQ1)-1) exit(5);
    n = send(sock, REQ2, sizeof(REQ2)-1, 0); if(n!=sizeof(REQ2)-1) exit(6);
    for(n=0; n<sizeof(RES)-1; n+=count) if((count=recv(sock,b,sizeof(RES)-n-1,0))<0) exit(7);
    //printf("test1: %12s\r", b); 	
    err = close(sock); if (err==-1) sys_error("close2");
  }
}
unsigned long ctm () {
  struct timeval t;
  gettimeofday(&t, 0);
  return t.tv_sec*1000000+t.tv_usec;
}

main() {
  const count = 10, true=1;
  int ss, i, pid, status;
  
  saddr2.sin_family=saddr.sin_family=AF_INET;
  saddr2.sin_addr.s_addr=saddr.sin_addr.s_addr=inet_addr("127.0.0.1");
  saddr2.sin_port=saddr.sin_port=htons(PORT);

  ss = socket(PF_INET, SOCK_STREAM, 0);
  setsockopt(ss, SOL_SOCKET, SO_REUSEADDR, (void*)&true, sizeof true);
  if(-1==bind(ss, (struct sockaddr*)&saddr, sizeof(saddr))) sys_error("bind");
  if(-1==listen(ss, 10)) sys_error("listen");
  
  if(pid=fork()) {
    unsigned long T1, T2, T3, t1, t2, r;
    sleep(1);
    
    T1 = ctm();
    runTest1();
    T2 = ctm();
    runTest2();
    T3 = ctm();
    t1 = T2-T1; 
    t2 = T3-T2;
    r = t2/t1;
    //printf("Test1: %lu\nTest2: %lu\nTest2/Test1: %lu (%f)\n", t1, t2, r, ((float)t2)/((float)t1));
    //if(r>1) puts("Test failed");
    close(ss);
    kill(pid, SIGTERM);
    exit(1);	//    exit(r>1?1:0);
  } else {
    while(1) {
      socklen_t len = sizeof(saddr);
      int s = accept(ss, (struct sockaddr*)&saddr, &len); if(s==-1) exit(0);
      static char b[SIZE];
      int n, count;
      for(n=0; n<sizeof(REQ1)-1; n+=count) if((count=recv(s,b,sizeof(REQ1)-n-1,0))<0) abort();
      for(n=0; n<sizeof(REQ2)-1; n+=count) if((count=recv(s,b,sizeof(REQ2)-n-1,0))<0) abort();
      n = send(s, RES, sizeof(RES)-1,0); if(n!=sizeof(RES)-1) abort();
      n = close(s); if(n == -1) abort();
    }
  }
}
],
[have_fast_tcp_sockets=yes],
[have_fast_tcp_sockets=no])
])

  if test "$have_fast_tcp_sockets" = "yes"; then
	AC_MSG_RESULT(yes)
	AC_DEFINE(HAVE_FAST_TCP_SOCKETS,1, [Define if your system has a fast TCP socket implementation])
  else
	AC_MSG_RESULT(no)
  fi
])
