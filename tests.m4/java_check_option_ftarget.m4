AC_DEFUN([JAVA_CHECK_OPTION_FTARGET],[
AC_MSG_CHECKING([for xftarget option]) 
AC_CACHE_VAL(jb_cv_have_ftarget,[
echo 'public class ftarget {}' >ftarget.java 
jb_cv_have_ftarget=no
if test "$have_gcj" = "yes"; then
 ${GCJ} -C -ftarget=1.4 ftarget.java 2>test.log && jb_cv_have_ftarget=yes 
 else
 jb_cv_have_ftarget=yes
fi
rm -f ftarget.java ftarget.class test.log
]) 

if test "$jb_cv_have_ftarget" = "yes"; then 
 JAVA_FTARGET_FLAGS="-ftarget=1.4"
 AC_MSG_RESULT(yes) 
else 
 JAVA_FTARGET_FLAGS=""
 AC_MSG_RESULT(no)
fi 
])
