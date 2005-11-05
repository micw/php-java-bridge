/*-*- mode: C; tab-width:4 -*-*/

#ifdef HAVE_CONFIG_H
#include "config.h"
#endif

/* longjump */
#include <setjmp.h>

/* socket */
#include <sys/types.h>
#include <sys/types.h>
#include <sys/stat.h>
#include <fcntl.h>
#ifdef __MINGW32__
# include <winsock2.h>
#else
# include <sys/socket.h>
# ifdef CFG_JAVA_SOCKET_INET
#  include <netinet/in.h>
# else
#  include <sys/un.h>
#  ifdef HAVE_CONFIG_H
#  if !HAVE_DECL_AF_LOCAL
#   define AF_LOCAL AF_UNIX
#  endif
#  endif
#  ifdef HAVE_CONFIG_H
#  if !HAVE_DECL_PF_LOCAL
#   define PF_LOCAL PF_UNIX
#  endif
#  endif
# endif
#endif

/* strings */
#include <string.h>
/* setenv */
#include <stdlib.h>
/*signal*/
#include <signal.h>

/* miscellaneous */
#include <stdio.h>
#include <errno.h>
#include <unistd.h>

#ifdef __MINGW32__
# ifndef HAVE_BROKEN_STDIO
# define HAVE_BROKEN_STDIO
# endif
# ifndef CFG_JAVA_SOCKET_INET
# define CFG_JAVA_SOCKET_INET
# endif
#endif

#undef NDEBUG
#ifndef JAVA_COMPILE_DEBUG
#define NDEBUG
#endif
#include <assert.h>
#include "protocol.h"

#include "jni.h"

#define ID(peer, name) \
if(logLevel>=LOG_DEBUG) logDebug(env, "send: "/**/#name); \
id(peer, name);

#define ASSERTM(expr) \
if(!expr) { \
  logMemoryError(env, __FILE__, __LINE__); \
  exit(9); \
}

#ifndef EXTENSION_DIR
#error EXTENSION_DIR must point to the PHP extension directory
#endif

static jint logLevel=4;
static jclass bridge=NULL;

static char*sockname=NULL;

static void doLog (JNIEnv *jenv, char *msg, jmethodID logMessageID) {
  jstring str;
  if(!logMessageID) { fputs(msg, stderr); fputs("\n", stderr); fflush(stderr); return; }
  str = (*jenv)->NewStringUTF(jenv, msg);
  if(!str) { fputs(msg, stderr); fputs("\n", stderr); fflush(stderr); return; }
  (*jenv)->CallStaticVoidMethod(jenv, bridge, logMessageID, str);
  (*jenv)->DeleteLocalRef(jenv, str);
}
static void logDebug(JNIEnv *jenv, char *msg) {
  static jmethodID logMessageID=NULL;
  assert(bridge);
  if(logLevel<=3 || !bridge) return;
  if(!logMessageID)
	logMessageID = (*jenv)->GetStaticMethodID(jenv, bridge, "logDebug", "(Ljava/lang/String;)V");
  doLog(jenv, msg, logMessageID);
}
static void logError(JNIEnv *jenv, char *msg) {
  static jmethodID logMessageID=NULL;
  assert(bridge);
  if(logLevel<=1 || !bridge) return;
  if(!logMessageID)
	logMessageID = (*jenv)->GetStaticMethodID(jenv, bridge, "logError", "(Ljava/lang/String;)V");
  doLog(jenv, msg, logMessageID);
}
static void logFatal(JNIEnv *jenv, char *msg) {
  static jmethodID logMessageID=NULL;
  assert(bridge);
  if(logLevel<=0 || !bridge) return;
  if(!logMessageID)
	logMessageID = (*jenv)->GetStaticMethodID(jenv, bridge, "logFatal", "(Ljava/lang/String;)V");
  doLog(jenv, msg, logMessageID);
}
static void logSysError(JNIEnv *jenv, char *msg) {
  char s[512];
  sprintf(s, "system error: %s: %s", msg, strerror(errno));
  logError(jenv, s);
}
static void logSysFatal(JNIEnv *jenv, char *msg) {
  char s[512];
  sprintf(s, "system error: %s: %s", msg, strerror(errno));
  logFatal(jenv, s);
}
static void logMemoryError(JNIEnv *jenv, char *file, int pos) {
  static char s[512];
  sprintf(s, "system error: out of memory error in: %s, line: %d", file, pos);
  logFatal(jenv, s);
  exit(9);
}

static void logRcv(JNIEnv*env, char c) {
  char*s;
  if(logLevel<=3) return;

  s=malloc(20);
  assert(s);
  if(!s) return;
  sprintf(s, "recv: %i", (unsigned int)c);
  logDebug(env, s);
  free(s);
}

static void atexit_bridge() {
  if(sockname) {
#ifndef __MINGW32__
# if !defined(HAVE_ABSTRACT_NAMESPACE) && !defined(CFG_JAVA_SOCKET_INET)
  unlink(sockname);
# endif
#endif
  free(sockname);
  sockname=NULL;
  }
}

static void initGlobals(JNIEnv *env) {
  // FIXME: set uid and gid 
  /*
  jmethodID initGlobals;
  jstring arg;

  initGlobals = (*env)->GetStaticMethodID(env, bridge, "initGlobals", "(Ljava/lang/String;)V");
  arg = (*env)->NewStringUTF(env, EXTENSION_DIR);
  (*env)->CallStaticVoidMethod(env, bridge, initGlobals, arg);
  (*env)->DeleteLocalRef(env, arg);
  */
  atexit(atexit_bridge);
}

#ifdef HAVE_STRUCT_UCRED

/* Prepare the socket to receive auth information directly from the
   kernel. 
 */
static int prep_cred(int sock) {
  static const int true = 1;
  int ret = setsockopt(sock, SOL_SOCKET, SO_PASSCRED, (void*)&true, sizeof true);
  return ret;
}

/* Receive authentification information (enforced by the BSD or Linux
   kernel). It is impossible to fake the auth information.
 */
static int recv_cred(int sock, int *uid, int *gid) {
  struct ucred ucred;
  socklen_t so_len = sizeof ucred;
  int n = getsockopt(sock, SOL_SOCKET, SO_PEERCRED, &ucred, &so_len);
  int ret = (n==-1 || so_len!=sizeof ucred) ? -1 : 0;
  if(ret!=-1) {
	*uid=ucred.uid;
	*gid=ucred.gid;
  }  
  return ret;
}
#else  /* struct ucred missing */
#define prep_cred(a) 0
#define recv_cred(a, b, c) 0
#endif

JNIEXPORT jboolean JNICALL Java_php_java_bridge_JavaBridge_openLog
  (JNIEnv *env, jclass self, jstring _logfile)
{
#ifndef __MINGW__
  char*logfile=NULL;

  assert(_logfile);

  if(_logfile!=NULL) {
	jboolean isCopy;
	const char*sname = (*env)->GetStringUTFChars(env, _logfile, &isCopy);
	if(sname && strlen(sname)) logfile=strdup(sname);
	(*env)->ReleaseStringUTFChars(env, _logfile, sname);
  } else {
	char *s = LOGFILE;
	if(s && strlen(s)>0) logfile = strdup(s);
  }
  
  if(logfile) {
	int fd, null;
	fd = open(logfile, O_WRONLY | O_CREAT | O_APPEND | O_TRUNC, 0644);
	if(fd==-1) return JNI_FALSE;
	null = open("/dev/null", O_RDONLY);
	if(null!=-1) dup2 (null,0); 
	if(fd!=-1) { 
	  if(dup2(fd,1)==-1) return JNI_FALSE;
	  if(dup2(fd,2)==-1) return JNI_FALSE;
	}
	return JNI_TRUE;
  }
#endif
  return JNI_FALSE;
}

JNIEXPORT jint JNICALL Java_php_java_bridge_JavaBridge_sread
  (JNIEnv *env, jclass self, jint peer, jbyteArray _buffer, jint nmemb)
{
  int socket = (int)peer;
  jbyte *buffer = (*env)->GetByteArrayElements(env, _buffer, JNI_FALSE);
  ssize_t n;
  assert(nmemb);
 res: errno=0;
  n=recv(socket, buffer, nmemb, 0);
  if(!n && errno==EINTR) goto res; // Solaris, see INN FAQ
  (*env)->ReleaseByteArrayElements(env, _buffer, buffer, 0);
  return n;
}
JNIEXPORT jint JNICALL Java_php_java_bridge_JavaBridge_swrite
  (JNIEnv *env, jclass self, jint peer, jbyteArray _buffer, jint nmemb)
{
  int socket = (int)peer;
  jbyte *buffer = (*env)->GetByteArrayElements(env, _buffer, JNI_FALSE);
  size_t s=0, size = (size_t) nmemb;
  ssize_t n = 0;
  assert(nmemb);

 res: errno=0;
  while((size>s)&&((n=send(socket, buffer+s, size-s, 0)) > 0))
	s+=n;
  if(size>s && !n && errno==EINTR) goto res; // Solaris, see INN FAQ

  (*env)->ReleaseByteArrayElements(env, _buffer, buffer, 0);
  return n;
}
JNIEXPORT void JNICALL Java_php_java_bridge_JavaBridge_sclose
  (JNIEnv *env, jclass self, jint peer)
{
  int socket=(int)peer;
  close(socket);
}

JNIEXPORT jint JNICALL Java_php_java_bridge_JavaBridge_startNative
(JNIEnv *env, jclass self, jint _logLevel,jint _backlog, jstring _sockname) {
#ifndef CFG_JAVA_SOCKET_INET
  struct sockaddr_un saddr;
#else
  struct sockaddr_in saddr;
#endif
  int sock, n;

  logLevel = _logLevel;
  bridge = self;

  if(_sockname!=NULL) {
	jboolean isCopy;
	const char*sname = (*env)->GetStringUTFChars(env, _sockname, &isCopy);
	sockname=strdup(sname);
	(*env)->ReleaseStringUTFChars(env, _sockname, sname);
  } else {
	char *s = SOCKNAME;
	sockname = strdup(s);
  }
  /* socket */
#ifndef CFG_JAVA_SOCKET_INET
  saddr.sun_family = AF_LOCAL;
  memset(saddr.sun_path, 0, sizeof saddr.sun_path);
  strcpy(saddr.sun_path, sockname);
# ifndef HAVE_ABSTRACT_NAMESPACE
  unlink(sockname);
# else
  *saddr.sun_path=0;
# endif
  sock = socket (PF_LOCAL, SOCK_STREAM, 0);
  if(!sock) {logSysFatal(env, "could not create socket"); return 0;}
  if (-1==prep_cred(sock)) logSysFatal(env, "socket cannot receive credentials");
#else
  saddr.sin_family = AF_INET;
  saddr.sin_port=htons(atoi(sockname));
  saddr.sin_addr.s_addr = inet_addr( "127.0.0.1" );
  sock = socket (PF_INET, SOCK_STREAM, 0);
  if(!sock) {logSysFatal(env, "could not create socket"); return 0;}
#endif
  n = bind(sock,(struct sockaddr*)&saddr, sizeof saddr);
  if(n==-1) {logSysFatal(env, "could not bind socket"); return 0;}
#if !defined(HAVE_ABSTRACT_NAMESPACE) && !defined(CFG_JAVA_SOCKET_INET)
  chmod(sockname, 0666); // the childs usually run as "nobody"
#endif
  n = listen(sock, 20);
  if(n==-1) {logSysFatal(env, "could not listen to socket"); return 0;}

  initGlobals(env);

  return sock;
}

JNIEXPORT jint JNICALL Java_php_java_bridge_JavaBridge_accept
  (JNIEnv *env, jclass self, jint _sock)
{
  int sock=(int)_sock, socket;

 res:errno=0; 
  socket = accept(sock, NULL, 0); 
  if(socket==-1) {
	if(errno==EINTR) goto res; // Solaris, see INN FAQ
	
	logSysFatal(env, "socket accept failed"); 
	return 0;
  }
  //if(-1==recv_cred(socket, &uid, &gid)) logSysFatal(env, "could not get credentials");
  return socket;
}

JNINativeMethod javabridge[]={
  {"startNative", "(IILjava/lang/String;)I", Java_php_java_bridge_JavaBridge_startNative},
  {"openLog", "(Ljava/lang/String;)Z", Java_php_java_bridge_JavaBridge_openLog},

  {"swrite", "(I[BI)I", Java_php_java_bridge_JavaBridge_swrite},
  {"sread", "(I[BI)I", Java_php_java_bridge_JavaBridge_sread},
  {"sclose", "(I)V", Java_php_java_bridge_JavaBridge_sclose},
};

static struct NativeMethods {
  char*class;
  JNINativeMethod*meth;
  int n;
} meths[]={
  {"php/java/bridge/JavaBridge", javabridge, (sizeof javabridge)/(sizeof *javabridge)},
};

static void jniRegisterNatives (JNIEnv *env)
{
  int i;
  jint r;
  jclass k;

  for(i=0; i<((sizeof meths)/(sizeof*meths)); i++) {
	k = (*env)->FindClass (env, meths[i].class);
	assert(k); if(!k) exit(9);
	r = (*env)->RegisterNatives (env, k, meths[i].meth, meths[i].n);
	assert(r==JNI_OK); if(r!=JNI_OK) exit(9);
  }
}

void java_bridge_main(int argc, char**argv) 
{
  JavaVMOption options[3];
  JavaVM *jvm;
  JNIEnv *jenv;
  JavaVMInitArgs vm_args; /* JDK 1.2 VM initialization arguments */
  jclass reflectClass, stringClass;
  jobjectArray arr;
  jmethodID init;
  int i, err, off;
  vm_args.version = JNI_VERSION_1_2; /* New in 1.1.2: VM version */
  /* Get the default initialization arguments and set the class 
   * path */
  JNI_GetDefaultJavaVMInitArgs(&vm_args);
  vm_args.nOptions=0;
  vm_args.options=options;
  if(argv[1]) vm_args.options[vm_args.nOptions++].optionString=argv[1];
  if(argv[2]) vm_args.options[vm_args.nOptions++].optionString=argv[2];
  if(argv[3]) vm_args.options[vm_args.nOptions++].optionString=argv[3];
  vm_args.ignoreUnrecognized=JNI_TRUE;

  /* load and initialize a Java VM, return a JNI interface 
   * pointer in env */
  err=JNI_CreateJavaVM(&jvm, (void*)&jenv, &vm_args);
  assert(!err); if(err) exit(9);
  jniRegisterNatives(jenv);
  reflectClass = (*jenv)->FindClass(jenv, "php/java/bridge/JavaBridge");
  assert(reflectClass); if(!reflectClass) exit(9);
  init = (*jenv)->GetStaticMethodID(jenv, reflectClass, "init", "([Ljava/lang/String;)V");
  assert(init); if(!init) exit(9);
  stringClass = (*jenv)->FindClass(jenv, "java/lang/String");
  assert(stringClass); if(!stringClass) exit(9);
  arr = (*jenv)->NewObjectArray(jenv, argc-6, stringClass, 0);
  assert(arr); if(!arr) exit(9);

  off = N_SARGS-4;
  for (i=0; (i+6)<argc; i++) {
	jstring arg;
	if(!argv[i+off]) break;
    arg = (*jenv)->NewStringUTF(jenv, argv[i+off]);
	assert(arg); if(!arg) exit(9);
    (*jenv)->SetObjectArrayElement(jenv, arr, i, arg);
  }
  (*jenv)->CallStaticVoidMethod(jenv, reflectClass, init, arr);
  (*jvm)->DestroyJavaVM(jvm);

  assert(0);
  while(1)			  /* DestroyJavaVM should already block forever */
	sleep(65535);
}

void java_bridge_main_gcj(int argc, char**_argv) 
{
  char **argv;
  /* someone should really fix this bug in gcj */
  meths[0].meth[0].signature="(IILjava.lang.String;)I";
  meths[0].meth[1].signature="(Ljava.lang.String;)Z";

  if(!_argv) exit(6);
  if(argc==4) {
	argv=calloc(N_SARGS, sizeof*argv);
	argv[N_SARGS-4]=_argv[1];			/* socketname */
	argv[N_SARGS-3]=_argv[2];			/* logLevel */
	argv[N_SARGS-2]=_argv[3];			/* logFile */
	argv[N_SARGS-1]=0;					/* last arg */
  } else {
	argv=_argv;
  }
  java_bridge_main(N_SARGS, argv);
}
