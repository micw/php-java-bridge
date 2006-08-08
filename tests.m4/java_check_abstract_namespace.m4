AC_DEFUN([JAVA_CHECK_ABSTRACT_NAMESPACE],[
  AC_MSG_CHECKING([for abstract namespace])
  AC_CACHE_VAL(have_abstract_namespace,[
  AC_TRY_RUN([
#include <stdio.h>
#include <sys/types.h>
#include <sys/wait.h>
#include <sys/socket.h>
#include <sys/un.h>
#include <string.h>
#include <unistd.h>

int main() {
  struct sockaddr_un saddr;
  FILE *peer;
  char probe1=76, probe1R;
  char probe2=34, probe2R;
  char *name = tmpnam(0);
  int n, pid, ret, sock;

  saddr.sun_family = AF_UNIX;
  memset(saddr.sun_path, 0, sizeof saddr.sun_path);
  if(!name) name = "test.socket";
  *strcpy(saddr.sun_path, name)=0;
  unlink(saddr.sun_path);
  if(!(pid=fork())) {
    sock = socket (PF_UNIX, SOCK_STREAM, 0);
    if(!sock) exit(10);
    n = bind(sock,(struct sockaddr*)&saddr, sizeof saddr);
    if(n==-1) exit(20);
    n = listen(sock, 10);
    if(n==-1) exit(30);
    sock = accept(sock, NULL, 0); 
    if(sock==-1) exit(40);
    peer = fdopen(sock, "r+");
    if(!peer) exit(50);

    if(fwrite(&probe1, 1, 1, peer)!=1) return 60;

    n=fclose(peer);
    if(n==EOF) exit(100);
    exit(0);
  } 
  sleep(1);
  sock = socket (PF_UNIX, SOCK_STREAM, 0);
  if(!sock) exit(15);
  n = connect(sock,(struct sockaddr*)&saddr, sizeof saddr);
  if(n==-1) exit(25);
  peer = fdopen(sock, "r+");
  if(!peer) exit(55);

  sleep(1);
  if(fread(&probe1R, 1, 1, peer)!=1) return 95;
  
  n=fclose(peer);
  if(n==EOF) exit(105);
  
  if(probe1!=probe1R) exit(115);
  wait(&ret);
  return ret;
}
],
[have_abstract_namespace=yes],
[have_abstract_namespace=no])
])

  if test "$have_abstract_namespace" = "yes"; then
	AC_MSG_RESULT(yes)
	AC_DEFINE(HAVE_ABSTRACT_NAMESPACE,1, [Define if your system supports the linux abstract namespace])
  else
	AC_MSG_RESULT(no)
  fi
])
