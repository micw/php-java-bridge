AC_DEFUN([JAVA_CHECK_STRUCT_UCRED],[
  AC_MSG_CHECKING([for struct ucred])
  AC_CACHE_VAL(have_struct_ucred,[
  AC_TRY_RUN([
#include <stdio.h>
#include <sys/types.h>
#include <sys/wait.h>
#include <sys/socket.h>
#include <sys/un.h>
#include <string.h>
#include <unistd.h>

short prep_cred(int sock) {
  static const int is_true = 1;
  return (short)setsockopt(sock, SOL_SOCKET, SO_PASSCRED, (void*)&is_true, sizeof is_true);
}

short recv_cred(int sock, struct ucred *peercred) {
  socklen_t so_len = sizeof(*peercred);
  int n = getsockopt(sock, SOL_SOCKET, SO_PEERCRED, peercred, &so_len);

  if(n==-1 || so_len!=sizeof*peercred) return -1;
  return 0;
}

int main() {
  struct sockaddr_un saddr;
  FILE *peer;
  struct ucred probe1, probe1R;
  int n, pid, ret, sock;
  char *name = tmpnam(0);

  saddr.sun_family = AF_UNIX;
  memset(saddr.sun_path, 0, sizeof saddr.sun_path);
  if(!name) name = "test.socket";
  strcpy(saddr.sun_path, name);
  unlink(saddr.sun_path);
  if(!(pid=fork())) {
    sock = socket (PF_UNIX, SOCK_STREAM, 0); if(!sock) exit(10);

    n = prep_cred(sock); if(n==-1) exit(12);

    n = bind(sock,(struct sockaddr*)&saddr, sizeof saddr); if(n==-1) exit(20);

    n = listen(sock, 10); if(n==-1) exit(30);

    sock = accept(sock, NULL, 0); if(sock==-1) exit(40);

    peer = fdopen(sock, "w"); if(!peer) exit(50);

    n=recv_cred(sock, &probe1); if(n==-1) exit(52);

    if(fwrite(&probe1, sizeof probe1, 1, peer)!=1) exit(60);

    n=fclose(peer); if(n==EOF) exit(100);

    exit(0);
  } 
  sleep(1);

  sock = socket (PF_UNIX, SOCK_STREAM, 0); if(!sock) exit(15);

  n = connect(sock,(struct sockaddr*)&saddr, sizeof saddr); if(n==-1) exit(25);

  peer = fdopen(sock, "r"); if(!peer) exit(55);

  if(fread(&probe1R, sizeof(probe1R), 1, peer)!=1) return 95; 

  n=fclose(peer); if(n==EOF) exit(105);
  
  //printf("%d, %d, %d, - %d, %d, %d\n", probe1R.pid, probe1R.uid, probe1R.gid, getpid(), getuid(), getgid());
  if(probe1R.uid!=getuid()) exit(115);
  if(probe1R.pid!=getpid()) exit(125);
  if(probe1R.gid!=getgid()) exit(135);

  wait(&ret);
  return ret;
}
],
[have_struct_ucred=yes],
[have_struct_ucred=no])
])

  if test "$have_struct_ucred" = "yes"; then
	AC_MSG_RESULT(yes)
	AC_DEFINE(HAVE_STRUCT_UCRED,1, [Define if your system supports struct ucred.])
  else
	AC_MSG_RESULT(no)
  fi
])
