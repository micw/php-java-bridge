AC_DEFUN([JAVA_FUNCTION_CHECKS],[

 AC_CHECK_HEADERS([ \
   sys/param.h sys/types.h sys/time.h assert.h fcntl.h \
   limits.h signal.h stdarg.h stdlib.h string.h \
   syslog.h sys/ioctl.h sys/poll.h sys/select.h \
   sys/socket.h sys/un.h sys/wait.h unistd.h 
 ],[],[],[
#ifdef HAVE_SYS_PARAM_H
#include <sys/param.h>
#endif
#ifdef HAVE_SYS_TYPES_H
#include <sys/types.h>
#endif
#ifdef HAVE_SYS_TIME_H
#include <sys/time.h>
#endif
 ])

AC_CHECK_DECLS([AF_LOCAL, PF_LOCAL],,, 
[
#include <sys/types.h>
#include <sys/socket.h>
#include <sys/un.h>
])

dnl add -lsocket to the link line of the module and the server part
 AC_CHECK_LIB(socket, socket, LIBS="$LIBS -lsocket")

dnl add -lrt for solaris x86
 AC_CHECK_LIB(rt, sem_init, LIBS="$LIBS -lrt")

])
 
