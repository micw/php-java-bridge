AC_DEFUN([JAVA_CHECK_JNI],[
AC_MSG_CHECKING([for jni interface]) 
AC_CACHE_VAL(have_jni,[
echo 'public class jni {static native int startNative(int logLevel, int backlog, String sockname); public static void main(String s[[]]){try{System.loadLibrary("jni"); System.exit(1==startNative(0, 0, null)?0:1);}catch(Throwable t){System.err.print(t); System.exit(2);}}}' >jni.java 
cat <<\end >jni.c
#include "jni.h" 
JNIEXPORT jint JNICALL Java_jni_startNative
(JNIEnv *env, jclass self, jint _logLevel,jint _backlog, jstring _sockname) {
return env && self && !_logLevel && !_backlog && !_sockname; 
}
end
have_jni=no
if test "$have_ar" = "yes"; then
 if test "$PHP_JAVA" != "yes"; then
  $CC $lt_prog_compiler_pic -olibjni.so $JAVA_INCLUDES -shared jni.c 2>test.log
  $PHP_JAVA/bin/javac jni.java 2>>test.log
  LD_LIBRARY_PATH=`pwd` $PHP_JAVA/bin/java -Djava.library.path=`pwd` jni 2>>test.log && have_jni=yes 
 else 
  $CC $lt_prog_compiler_pic -olibjni.so -shared jni.c 2>test.log && have_jni=yes
 fi
fi
rm -f libjni.so jni.java jni.c test.log
]) 

if test "$have_jni" = "yes"; then 
 AC_MSG_RESULT(yes) 
 AC_DEFINE(HAVE_JNI,1, [Define if your java supports jni])
else 
 AC_MSG_RESULT(no)
fi 
])
