AC_DEFUN([JAVA_CHECK_BROKEN_STDIO_BUFFERING],[
  AC_MSG_CHECKING([for broken stdio buffering])
  AC_CACHE_VAL(jb_cv_have_broken_stdio_buffering,[
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
  int n, pid, ret, sock;
  char *name = tmpnam(0);

  saddr.sun_family = AF_UNIX;
  memset(saddr.sun_path, 0, sizeof saddr.sun_path);
  if(!name) name = "test.socket";
  strcpy(saddr.sun_path, name);
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
    if(fread(&probe2R, 1, 1, peer)!=1) return 70;
    if(fwrite(&probe2, 1, 1, peer)!=1) return 80;
    if(fread(&probe1R, 1, 1, peer)!=1) return 90;

    n=fclose(peer);
    if(n==EOF) exit(100);
    
    if(probe1!=probe1R) exit(110);
    if(probe2!=probe2R) exit(120);
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
  if(fwrite(&probe2, 1, 1, peer)!=1) return 85;
  if(fread(&probe2R, 1, 1, peer)!=1) return 75;
  sleep(1);
  if(fwrite(&probe1, 1, 1, peer)!=1) return 65;
  
  n=fclose(peer);
  if(n==EOF) exit(105);
  
  if(probe1!=probe1R) exit(115);
  if(probe2!=probe2R) exit(125);
  wait(&ret);
  return ret;
}
],
[jb_cv_have_broken_stdio_buffering=no],
[jb_cv_have_broken_stdio_buffering=yes])
])

  if test "$jb_cv_have_broken_stdio_buffering" = "yes"; then
	AC_MSG_RESULT(yes)
	AC_DEFINE(HAVE_BROKEN_STDIO,1, [Define if your system cannot fdopen(socket, "r+")])
  else
	AC_MSG_RESULT(no)
  fi
])
