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
#  ifndef HAVE_DECL_AF_LOCAL
#   define AF_LOCAL AF_UNIX
#  endif
#  ifndef HAVE_DECL_PF_LOCAL
#   define PF_LOCAL PF_UNIX
#  endif
# endif
#endif

/* strings */
#include <string.h>
/* setenv */
#include <stdlib.h>
/*signal*/
#include <signal.h>

/* posix threads implementation */
#ifndef __MINGW32__
# include <pthread.h>
# include <semaphore.h>
#endif

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
#include "sio.c"

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


static jclass exceptionClass=NULL;

static jclass enumClass=NULL;
static jclass hashClass=NULL;
static jmethodID init=NULL;

static jmethodID hashPut=NULL;
static jmethodID hashRemove=NULL;
static jmethodID hashKeys=NULL;

static jmethodID enumMore=NULL;
static jmethodID enumNext=NULL;

static jmethodID handleRequests=NULL;
static jmethodID handleRequest=NULL;
static jmethodID trampoline=NULL;

static jclass longClass=NULL;
static jmethodID longCtor=NULL;
static jmethodID longValue=NULL;

static jmethodID getClass=NULL;

static jint logLevel=4;
static jclass bridge=NULL;

static char*sockname=NULL;

#ifndef __MINGW32__
static pthread_attr_t attr;
static pthread_mutex_t mutex;
static int count=0;
static pthread_cond_t cond;
static volatile sem_t cond_sig;
static volatile short bridge_shutdown = 0;
#endif

struct peer {
  jmp_buf env;					/* exit from the loop */
  jmp_buf savepoint;			/* jump back to java */
  JNIEnv *jenv;
  short tran;					/* if we must return to java first */
  SFILE*stream;
  jobject objectHash;
  jobject globalRef;
};
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
  if(!bridge) return;
  if(!logMessageID)
	logMessageID = (*jenv)->GetStaticMethodID(jenv, bridge, "logDebug", "(Ljava/lang/String;)V");
  doLog(jenv, msg, logMessageID);
}
static void logError(JNIEnv *jenv, char *msg) {
  static jmethodID logMessageID=NULL;
  assert(bridge);
  if(!bridge) return;
  if(!logMessageID)
	logMessageID = (*jenv)->GetStaticMethodID(jenv, bridge, "logError", "(Ljava/lang/String;)V");
  doLog(jenv, msg, logMessageID);
}
static void logFatal(JNIEnv *jenv, char *msg) {
  static jmethodID logMessageID=NULL;
  assert(bridge);
  if(!bridge) return;
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

static void swrite(const  void  *ptr,  size_t  size,  size_t  nmemb,  struct peer*peer) {
  SFILE*stream=peer->stream;
  int n = SFWRITE(ptr, size, nmemb, stream);
  //printf("write char:::%d\n", (unsigned int) ((char*)ptr)[0]);
  if(n!=nmemb) {
	if(peer->tran) {		/* first clear the java stack, then longjmp */
	  (*peer->jenv)->ThrowNew(peer->jenv, exceptionClass, "child aborted connection during write");
	  longjmp(peer->savepoint, 1);
	} else
	   longjmp(peer->env, 2);
  }

}
static void sread(void *ptr, size_t size, size_t nmemb, struct peer *peer) {
  SFILE*stream=peer->stream;
  int n = SFREAD(ptr, size, nmemb, stream);
  //printf("read char:::%d\n", (unsigned int) ((char*)ptr)[0]);
  if(n!=nmemb) {
	if(peer->tran) {		/* first clear the java stack, then longjmp */
	  (*peer->jenv)->ThrowNew(peer->jenv, exceptionClass, "child aborted connection during read");
	  longjmp(peer->savepoint, 1);
	} else
	  longjmp(peer->env, 1);
  }
}

static void id(struct peer*peer, char id) {
  swrite(&id, sizeof id, 1, peer);
}

static jobject objFromPtr(JNIEnv *env, void*val) {
  jobject obj = (*env)->NewObject (env, longClass, longCtor, (jlong)(unsigned long)val);
  assert(obj);
  return obj;
}
static void* ptrFromObj(JNIEnv *env, jobject val) {
  jlong result = (*env)->CallLongMethod(env, val, longValue);
  assert(result);
  return (void*)(unsigned long)result;
}

static void logRcv(JNIEnv*env, char c) {
  char*s=malloc(20);
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
  sem_destroy((sem_t*)&cond_sig);
  pthread_attr_destroy(&attr);
  pthread_mutex_destroy(&mutex);
  pthread_cond_destroy(&cond);
#endif
  free(sockname);
  sockname=NULL;
  }
}

static void enter() {
#ifndef __MINGW32__
  pthread_mutex_lock(&mutex);
  assert(count>=0);
  count++;
  pthread_mutex_unlock(&mutex);
#endif
}

static void leave() {
#ifndef __MINGW32__
  pthread_mutex_lock(&mutex);
  assert(count>0);
  if(!--count) pthread_cond_signal(&cond);
  pthread_mutex_unlock(&mutex);
#endif
}

static void *guard_requests(void *p) {
#ifndef __MINGW32__
  int err;

 again: 
  err = sem_wait((sem_t*)&cond_sig); 
  if(err==-1 && errno==EINTR) goto again; /* handle ctrl-z */
 
  pthread_mutex_lock(&mutex);
  bridge_shutdown=1;
  if(count) pthread_cond_wait(&cond, &mutex);
  pthread_mutex_unlock(&mutex);
  exit(0);
#endif
}

static void initGlobals(JNIEnv *env) {
  jobject hash;
  jmethodID addSystemLibraries;
  jstring arg;

  exceptionClass = (*env)->FindClass(env, "java/lang/Throwable");
  enumClass = (*env)->FindClass(env, "java/util/Enumeration");
  hashClass = (*env)->FindClass(env, "java/util/Hashtable");
  init = (*env)->GetMethodID(env, hashClass, "<init>", "()V");
  hash = (*env)->NewObject(env, hashClass, init);
  hashPut = (*env)->GetMethodID(env, hashClass, "put", "(Ljava/lang/Object;Ljava/lang/Object;)Ljava/lang/Object;");
  hashRemove = (*env)->GetMethodID(env, hashClass, "remove", "(Ljava/lang/Object;)Ljava/lang/Object;");
  hashKeys = (*env)->GetMethodID(env, hashClass, "keys", "()Ljava/util/Enumeration;");
  
  enumMore = (*env)->GetMethodID(env, enumClass, "hasMoreElements", "()Z");
  enumNext = (*env)->GetMethodID(env, enumClass, "nextElement", "()Ljava/lang/Object;");

  handleRequests = (*env)->GetStaticMethodID(env, bridge, "HandleRequests", "(III)V");
  handleRequest = (*env)->GetStaticMethodID(env, bridge, "HandleRequest", "(Ljava/lang/Object;J)I");
  trampoline = (*env)->GetStaticMethodID(env, bridge, "Trampoline", "(Ljava/lang/Object;JZ)Z");

  addSystemLibraries = (*env)->GetStaticMethodID(env, bridge, "addSystemLibraries", "(Ljava/lang/String;)V");
  arg = (*env)->NewStringUTF(env, EXTENSION_DIR);
  (*env)->CallStaticVoidMethod(env, bridge, addSystemLibraries, arg);
  (*env)->DeleteLocalRef(env, arg);
  
  longClass = (*env)->FindClass (env, "java/lang/Long");
  longCtor = (*env)->GetMethodID(env, longClass, "<init>", "(J)V");
  longValue = (*env)->GetMethodID(env, longClass, "longValue", "()J");

  getClass = (*env)->GetStaticMethodID(env, bridge, "GetClass", "(Ljava/lang/Object;)Ljava/lang/Class;");
  assert(getClass);
  atexit(atexit_bridge);

#ifndef __MINGW32__
  {
	pthread_t thread;
	pthread_attr_init(&attr);
	pthread_cond_init(&cond, NULL);
	pthread_mutex_init(&mutex, NULL);
	pthread_attr_setdetachstate(&attr, PTHREAD_CREATE_DETACHED);
	
	sem_init((sem_t*)&cond_sig, 0, 0);
	pthread_create(&thread, &attr, guard_requests, 0);
  }
#endif
}
static jobject initHash(JNIEnv *env) {
  jobject hash = (*env)->NewObject(env, hashClass, init);
  jobject objectHash = (*env)->NewGlobalRef(env, hash);
  return objectHash;
}
static int handle_request(struct peer*peer, JNIEnv *env) {
  char c;

  //logDebug(env, "waiting for request from client");
  sread(&c, 1, 1, peer);
  if(logLevel>=4) logRcv(env, c);
  switch(c) {
  case PROTOCOL_END: {
	return 0;
  }
/*
 client                                            server

 createObj (wait for 0)      ->                      createObj -> create
                                   <-                setResult1 (wait for 0)
 setresult, send 0           ->                      terminate loop (back to java)
                                   <-                setResultn (wait for 0)
 setresult, send 0           ->                      always:end of co, send 0
 terminate co loop                 <-
 */

  case LASTEXCEPTION: {
	jobject php_reflect;
	jmethodID lastEx;
	jlong result;
	sread(&php_reflect, sizeof php_reflect,1, peer);
	sread(&lastEx, sizeof lastEx,1, peer);
	sread(&result, sizeof result,1, peer);
	(*env)->CallVoidMethod(env, php_reflect, lastEx, result, (jlong)(long)peer);
	ID(peer, 0); 
	break;
  }	
  case INVOKE:
  case GETSETPROP: {
	jobject php_reflect;
	jmethodID invoke;
	jobject obj;
	jstring method;
	jobjectArray array;
	jlong result;
	jthrowable abort;

	sread(&php_reflect, sizeof php_reflect,1, peer);
	sread(&invoke, sizeof invoke,1, peer);
	sread(&obj, sizeof obj,1, peer);
	sread(&method, sizeof method,1, peer);
	sread(&array, sizeof array,1, peer);
	sread(&result, sizeof result,1, peer);
	peer->tran=1;
	(*env)->CallVoidMethod(env, php_reflect, invoke, obj, method, array, result, (jlong)(long)peer);
	abort = (*env)->ExceptionOccurred(env);
	if(abort) {  /* connection aborted by client, java stack cleared */
	  (*env)->ExceptionClear(env);
	  peer->tran=0;
	  longjmp(peer->env, 1);
	}
	ID(peer, 0);
	break;
  }
  case CREATEOBJECT: {
	jobject php_reflect;
	jmethodID invoke;
	jstring method;
	jboolean createInstance;
	jobjectArray array;
	jlong result;
	jthrowable abort;

	sread(&php_reflect, sizeof php_reflect,1, peer);
	sread(&invoke, sizeof invoke,1, peer);
	sread(&method, sizeof method,1, peer);
	sread(&createInstance, sizeof createInstance,1, peer);
	sread(&array, sizeof array,1, peer);
	sread(&result, sizeof result,1, peer);
	peer->tran=1;
	(*env)->CallVoidMethod(env, php_reflect, invoke, method, createInstance, array, result, (jlong)(long)peer);
	abort = (*env)->ExceptionOccurred(env);
	if(abort) { /* connection aborted by client, java stack cleared */
	  (*env)->ExceptionClear(env);
	  peer->tran=0;
	  longjmp(peer->env, 1);
	}
	ID(peer, 0);
	break;
  }

  case ALLOCOBJECT: {
	jclass clazz;
	jobject result;
	sread(&clazz, sizeof clazz,1, peer);
	result = (*env)->AllocObject(env, clazz);
	swrite(&result, sizeof result, 1, peer);
	break;
  }
  case CALLOBJECTMETHOD: {
	jobject result;
	jobject obj;
	jmethodID methodID;
	short nargs;
	jvalue *args;
	sread(&nargs, sizeof nargs,1, peer);
	sread(&obj, sizeof obj, 1, peer);
	sread(&methodID, sizeof methodID,1, peer);
	args=calloc(nargs, sizeof*args);
	ASSERTM(args);
	sread(args, sizeof *args,nargs, peer);
	result = (*env)->CallObjectMethodA(env, obj, methodID, args);
	swrite(&result, sizeof result, 1, peer);
	free(args);
	break;
  }
  case CALLVOIDMETHOD: {
	jobject obj;
	jmethodID methodID;
	short nargs;
	jvalue *args;
	sread(&nargs, sizeof nargs, 1, peer);
	sread(&obj, sizeof obj, 1, peer);
	sread(&methodID, sizeof methodID, 1, peer);
	args=calloc(nargs, sizeof*args);
	ASSERTM(args);
	sread(args, sizeof *args, nargs, peer);
	(*env)->CallVoidMethodA(env, obj, methodID, args);
	free(args);
	break;
  }
  case DELETEGLOBALREF: {
	long c;
	jobject ref, ob, ob2;
	sread(&ref, sizeof ref, 1, peer);
	(*env)->DeleteGlobalRef(env, ref);
	ob = objFromPtr(env, ref);
	ob2 = (*env)->CallObjectMethod(env, peer->globalRef, hashRemove, ob);
	assert(ob2);
	c = (long)ptrFromObj(env,(void*)ob2);
	assert(c>0);
	if(--c>0) {
	  jobject val = objFromPtr(env, (void*)c);
	  assert(val);
	  if(val) {
		(*env)->CallObjectMethod(env, peer->globalRef, hashPut, ob, val);
		(*env)->DeleteLocalRef(env, val);
	  }
	}
	break;
  }
  case EXCEPTIONCLEAR: {
	(*env)->ExceptionClear(env);
	break;
  }
  case EXCEPTIONOCCURRED: {
	jthrowable result;
	result = (*env)->ExceptionOccurred(env);
	swrite(&result, sizeof result, 1, peer);
	break;
  }
  case FINDCLASS: {
	jclass clazz;
	size_t len;
	char *name;
	sread(&len, sizeof len, 1, peer);
	name=malloc(len+1);
	ASSERTM(name);
	sread(name, sizeof *name, len, peer);
	name[len]=0;
	clazz = (*env)->FindClass(env, name); 
	swrite(&clazz, sizeof clazz, 1, peer);
	free(name);
	break;
  }
  case GETARRAYLENGTH: {
	jsize result;
	jarray array;
	sread(&array, sizeof array, 1, peer);
	result = (*env)->GetArrayLength(env, array);
	swrite(&result, sizeof result, 1, peer);
	break;
  }
  case GETBYTEARRAYELEMENTS: {
	jobject ob1, ob2;
	void *key;
	jbyte *result;
	jboolean isCopy;
	jbyteArray array;
	size_t count;
	sread(&array, sizeof array, 1, peer);
	count = (*env)->GetArrayLength(env, array);
	result = (*env)->GetByteArrayElements(env, array, &isCopy);
	isCopy=JNI_TRUE;
	swrite(&isCopy, sizeof isCopy, 1, peer);
	swrite(&count, sizeof count, 1, peer);
	swrite(result, sizeof *result, count, peer);
	sread(&key, sizeof key, 1, peer);

	ob1=objFromPtr(env, key);
	ob2=objFromPtr(env, result);
	assert(ob1 && ob2);
	if(!ob1 || !ob2) return -41;
	(*env)->CallObjectMethod(env, peer->objectHash, hashPut, ob1, ob2);
	break;
  }
  case GETMETHODID: { 
	jmethodID id;
	jclass clazz;
	size_t len;
	char *name;
	char *sig;
	sread(&clazz, sizeof clazz, 1, peer);
	sread(&len, sizeof len, 1, peer);
	name=malloc(len+1);
	ASSERTM(name);
	sread(name, sizeof*name, len, peer);
	name[len]=0;
	sread(&len,sizeof len, 1, peer);
	sig=malloc(len+1);
	ASSERTM(sig);
	sread(sig, sizeof*sig, len, peer);
	sig[len]=0;
	id = (*env)->GetMethodID(env, clazz, name, sig);
	swrite(&id, sizeof id, 1, peer);
	free(name);
	free(sig);
	break;
  }
  case GETOBJECTCLASS: {
	jclass result;
	jobject obj;
	sread(&obj, sizeof obj, 1, peer);
	result = (*env)->GetObjectClass(env, obj);
	swrite(&result, sizeof result, 1, peer);
	break;
  }
  case GETSTRINGUTFCHARS: {
	jobject ob1, ob2;
	void *key;
	jboolean isCopy;
	char*result;
	size_t length;
	jstring str;
	sread(&str, sizeof str, 1, peer);
	result = (char*)(*env)->GetStringUTFChars(env, str, &isCopy);
	isCopy=JNI_TRUE;
	swrite(&isCopy, sizeof isCopy, 1, peer);
	length=(*env)->GetStringUTFLength(env, str);
	swrite(&length, sizeof length, 1, peer);
	swrite(result, sizeof*result, length, peer);
	sread(&key, sizeof key, 1, peer);
	ob1=objFromPtr(env, key);
	ob2=objFromPtr(env, result);
	if(!ob1||!ob2) return -41;
	(*env)->CallObjectMethod(env, peer->objectHash, hashPut, ob1, ob2);
	break;
  }
  case NEWBYTEARRAY: {
	jbyteArray result;
	jsize len;
	sread(&len, sizeof len, 1, peer);
	result = (*env)->NewByteArray(env, len);
	swrite(&result, sizeof result, 1, peer);
	break;
  }
  case NEWGLOBALREF: {
	jobject result;
	jobject obj, ob, ob2;
	sread(&obj, sizeof obj, 1, peer);
	result = (*env)->NewGlobalRef(env, obj);
	ob=objFromPtr(env, result);
	swrite(&result, sizeof result, 1, peer);
	assert(ob);
	ob2=(*env)->CallObjectMethod(env, peer->globalRef, hashRemove, ob);
	if(ob&&ob2) {
	  long c = (long)ptrFromObj(env,ob2);
	  ob2 = objFromPtr(env,(void*)(++c));
	  assert(ob2);
	  if(ob2) {
		(*env)->CallObjectMethod(env, peer->globalRef, hashPut, ob, ob2);
		(*env)->DeleteLocalRef(env, ob2);
	  }		
	} else {
	  ob2 = objFromPtr(env,(void*)1);
	  assert(ob2);
	  if(ob&&ob2) { 
		(*env)->CallObjectMethod(env, peer->globalRef, hashPut, ob, ob2);
		(*env)->DeleteLocalRef(env, ob2);
	  }
	}
	break;
  }
  case NEWOBJECT: {
	jobject result;
	jclass clazz;
	jmethodID methodID;
	short count;
	jvalue *args;
	sread(&count, sizeof count, 1, peer);
	sread(&clazz, sizeof clazz, 1, peer);
	sread(&methodID, sizeof methodID, 1, peer);
	args=calloc(count, sizeof *args);
	ASSERTM(args);
	sread(args, sizeof *args, count, peer);
	result= (*env)->NewObjectA(env, clazz, methodID, args);
	swrite(&result, sizeof result, 1, peer);
	free(args);
	break;
  }
  case NEWOBJECTARRAY: {
	jobjectArray result;
	jsize len;
	jclass clazz;
	jobject init;
	sread(&len, sizeof len, 1, peer);
	sread(&clazz, sizeof clazz, 1, peer);
	sread(&init, sizeof init, 1, peer);
	result = (*env)->NewObjectArray(env, len, clazz, init);
	swrite(&result, sizeof result, 1, peer);
	break;
  } 
  case NEWSTRINGUTF: {
	jstring result;
	size_t len;
	char *utf;
	sread(&len, sizeof len, 1, peer);
	utf=malloc(len+1);
	ASSERTM(utf);
	sread(utf, sizeof*utf, len, peer);
	utf[len]=0;
	result = (*env)->NewStringUTF(env, utf);
	swrite(&result, sizeof result, 1, peer);
	free(utf);
	break;
  }
  case RELEASEBYTEARRAYELEMENTS: {
	jobject val, ob;
	jbyteArray array;
	jbyte *elems;
	jint mode;
	sread(&array, sizeof array, 1, peer);
	sread(&elems, sizeof elems, 1, peer);
	sread(&mode, sizeof mode, 1, peer);
	ob=objFromPtr(env, elems);
	assert(ob); if(!ob) return -41;
	val = (*env)->CallObjectMethod(env, peer->objectHash, hashRemove, ob);
	assert(val); if(!val) return -41;
	assert(!mode);
	(*env)->ReleaseByteArrayElements(env, array, ptrFromObj(env, val), mode);
	(*env)->DeleteLocalRef(env, val);
	(*env)->DeleteLocalRef(env, ob);
	break;
  }
  case RELEASESTRINGUTFCHARS: {
	jobject val, ob;
	jstring array;
	char*elems;
	sread(&array, sizeof array, 1, peer);
	sread(&elems, sizeof elems, 1, peer);
	ob=objFromPtr(env, elems);
	if(!ob) return -41;
	val = (*env)->CallObjectMethod(env, peer->objectHash, hashRemove, ob);
	assert(val);
	if(!val) return -41;
	(*env)->ReleaseStringUTFChars(env, array, ptrFromObj(env, val));
	(*env)->DeleteLocalRef(env, val);
	(*env)->DeleteLocalRef(env, ob);
	break;
  }
  case SETBYTEARRAYREGION: {
	jbyteArray array;
	jsize start;
	jsize len;
	jbyte *buf;
	sread(&array, sizeof array, 1, peer);
	sread(&start, sizeof start, 1, peer);
	sread(&len, sizeof len, 1, peer);
	buf=calloc(len, sizeof*buf);
	ASSERTM(buf);
	sread(buf, sizeof*buf, len, peer);
	(*env)->SetByteArrayRegion(env, array, start, len, buf);
	free(buf);
	break;
  }
  case SETOBJECTARRAYELEMENT: {
	jobjectArray array;
	jsize index;
	jobject val;
	sread(&array, sizeof array, 1, peer);
	sread(&index, sizeof index, 1, peer);
	sread(&val, sizeof val, 1, peer);
	(*env)->SetObjectArrayElement(env, array, index, val);
	break;
  }
  case ISINSTANCEOF: {
	jobject obj, clazz;
	jclass clazz1;
	jboolean result;
	sread(&obj, sizeof obj, 1, peer);
	sread(&clazz, sizeof clazz, 1, peer);
	/* jni crashes if we pass an object object instead of a class
	   object  */
	clazz1 = (*env)->CallStaticObjectMethod(env, bridge, getClass, clazz);
	result = clazz1 ? (*env)->IsInstanceOf(env, obj, clazz1) : JNI_FALSE;
	swrite(&result, sizeof result, 1, peer);
	break;
  }
  case TRANSACTION_BEGIN: 
  case TRANSACTION_END: 
	return c;

  default: {
	logError(env, "protocol error: recv unknown token");
  }
  }
  return 1;
}

JNIEXPORT jint JNICALL Java_JavaBridge_handleRequest(JNIEnv*env, jclass self, jobject globalRef, jlong socket)
{
  SFILE *file = (SFILE*)(long)socket;
  struct peer peer;
  int val;
  peer.objectHash=initHash(env);
  if(!peer.objectHash) {logFatal(env, "could not create hash table"); return -40;}
  peer.stream=file;
  peer.jenv=env;
  peer.tran=0;
  peer.globalRef=globalRef;
  val=setjmp(peer.env);
  if(val) {
	(*env)->DeleteGlobalRef(env, peer.objectHash);
	return -val;
  }
  val=handle_request(&peer, env);
  (*env)->DeleteGlobalRef(env, peer.objectHash);
  return val;
}
static void logIntValue(JNIEnv*env, char*t, unsigned long i) {
  char*s=malloc(160);
  assert(s);
  if(!s) return;
  sprintf(s, "%s: %lx",t,i);
  logDebug(env, s);
  free(s);
}
static void logChannel(JNIEnv*env, char*t, unsigned long i) {
 if(logLevel>2) logIntValue(env, t, i);
}

static jobject connection_startup(JNIEnv *env) {
  jobject hash = (*env)->NewObject(env, hashClass, init);
  jobject globalRef = (*env)->NewGlobalRef(env, hash);

  return globalRef;
}

static void connection_cleanup (JNIEnv *env, jobject globalRef) {
  long c;
  jobject enumeration, ref, ob2;
  /* cleanup global refs that the client left */
  enumeration=(*env)->CallObjectMethod(env, globalRef, hashKeys);
  while((*env)->CallBooleanMethod(env, enumeration, enumMore)) {
	ref = (*env)->CallObjectMethod(env, enumeration, enumNext);
	ob2=(*env)->CallObjectMethod(env, globalRef, hashRemove, ref);
	assert(ob2);
	if(ob2) {
	  c = (int)ptrFromObj(env,ob2);
	  assert(c>0);
	  while(c-->0) {
		(*env)->DeleteGlobalRef(env, ptrFromObj(env,ref));
	  }
	}
  }
  (*env)->DeleteGlobalRef(env, globalRef);
}
JNIEXPORT jboolean JNICALL Java_JavaBridge_trampoline(JNIEnv*env, jclass self, jobject globalRef, jlong socket, jboolean jump)
{
  SFILE *peer = (SFILE*)(long)socket;
  while(peer && !SFEOF(peer)) {
	jint term;
	if(SFERROR(peer)) { logSysError(env, "communication error"); break; }
    term = (jump==JNI_TRUE) ? 
	  (*env)->CallStaticIntMethod(env, bridge, handleRequest, globalRef, socket) : 
	  Java_JavaBridge_handleRequest(env, bridge, globalRef, socket);
	  
	if(term>=0) {
	  logChannel(env, "end packet", term);
	  switch (term) { 
	  case TRANSACTION_BEGIN: 
		if((*env)->CallStaticBooleanMethod(env, bridge, trampoline, 
										globalRef, socket, JNI_FALSE)==JNI_FALSE) return JNI_FALSE;
		break;
	  case TRANSACTION_END:
		return JNI_TRUE;
	  }
	} else {
	  logIntValue(env, "communication broken", term);
	  return JNI_FALSE;
	}
  }
  assert(0);
  return JNI_FALSE;
}

#ifdef HAVE_STRUCT_UCRED

/* Prepare the socket to receive auth information directly from the
   kernel.  Will work on Solaris, Linux and modern BSD operating
   systems and only if the socket is of type LOCAL (UNIX). 
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

JNIEXPORT void JNICALL Java_JavaBridge_handleRequests(JNIEnv*env, jobject instance, jint socket, jint uid, jint gid)
{
  jobject globalRef;
  SFILE *peer = SFDOPEN(socket, "r+");
  if(!peer) {logSysFatal(env, "could not fdopen socket"); return;}

  logChannel(env, "create new communication channel", (unsigned long)peer);
  globalRef = connection_startup(env);
  if(!globalRef){logFatal(env, "could not allocate global hash");if(peer) SFCLOSE(peer);return;}

  if(peer && (SFWRITE(&instance, sizeof instance, 1, peer)!=1)) {
	logSysFatal(env, "could not send instance, child not listening"); connection_cleanup(env, globalRef); SFCLOSE(peer);return;
  }
  enter();
  (*env)->CallStaticBooleanMethod(env, bridge, trampoline, globalRef, (jlong)(long)peer, JNI_TRUE);
  connection_cleanup(env, globalRef);

  logChannel(env, "terminate communication channel", (unsigned long)peer);
  if(peer) SFCLOSE(peer);

  leave();
}


#ifndef __MINGW32__
static void post(int i) {
  sem_post((sem_t*)&cond_sig);
  signal(SIGTERM, SIG_DFL);
}
#endif

JNIEXPORT jboolean JNICALL Java_JavaBridge_openLog
  (JNIEnv *env, jclass self, jstring _logfile)
{
#ifndef __MINGW__
  char*logfile=NULL;

  assert(_logfile);

  if(_logfile!=NULL) {
	jboolean isCopy;
	const char*sname = (*env)->GetStringUTFChars(env, _logfile, &isCopy);
	logfile=strdup(sname);
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

JNIEXPORT void JNICALL Java_JavaBridge_startNative
  (JNIEnv *env, jclass self, jint _logLevel, jstring _sockname)
{
#ifndef CFG_JAVA_SOCKET_INET
  struct sockaddr_un saddr;
#else
  struct sockaddr_in saddr;
#endif
  int sock, n;
  SFILE *peer;

  logLevel = _logLevel;
  bridge = self;
  initGlobals(env);

#ifndef __MINGW32__
  signal(SIGTERM, post);
#endif

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
  if(!sock) {logSysFatal(env, "could not create socket"); return;}
#else
  saddr.sin_family = AF_INET;
  saddr.sin_port=htons(atoi(sockname));
  saddr.sin_addr.s_addr = inet_addr( "127.0.0.1" );
  sock = socket (PF_INET, SOCK_STREAM, 0);
  if(!sock) {logSysFatal(env, "could not create socket"); return;}
  if (-1==prep_cred(sock)) logSysFatal(env, "socket cannot receive credentials");
#endif
  n = bind(sock,(struct sockaddr*)&saddr, sizeof saddr);
  if(n==-1) {logSysFatal(env, "could not bind socket"); return;}
#if !defined(HAVE_ABSTRACT_NAMESPACE) && !defined(CFG_JAVA_SOCKET_INET)
  chmod(sockname, 0666); // the childs usually run as "nobody"
#endif
  n = listen(sock, 20);
  if(n==-1) {logSysFatal(env, "could not listen to socket"); return;}

  while(1) {
	int socket, uid=-1, gid=-1;

  res:errno=0; 
	socket = accept(sock, NULL, 0); 
#ifndef __MINGW32__
	if(bridge_shutdown) while(1) sleep(65535);
#endif
	if(socket==-1) {logSysFatal(env, "socket accept failed"); return;}
	//if(errno) goto res;
	if(-1==recv_cred(socket, &uid, &gid)) logSysFatal(env, "could not get credentials");
    (*env)->CallStaticVoidMethod(env, bridge, handleRequests,socket, uid, gid);
  }
}


JNIEXPORT void JNICALL Java_JavaBridge_setResultFromString
  (JNIEnv *env, jclass self, jlong result, jlong _peer, jbyteArray value)
{
  struct peer*peer=(struct peer*)(long)_peer;
  if(setjmp(peer->savepoint)) return;
  ID(peer, SETRESULTFROMSTRING);
  swrite(&result, sizeof result, 1, peer);
  swrite(&value, sizeof value, 1, peer);
  while(handle_request(peer, env));
}

JNIEXPORT void JNICALL Java_JavaBridge_setResultFromLong
  (JNIEnv *env, jclass self, jlong result, jlong _peer, jlong value)
{
  struct peer*peer=(struct peer*)(long)_peer;
  if(setjmp(peer->savepoint)) return;
  ID(peer, SETRESULTFROMLONG);
  swrite(&result, sizeof result, 1, peer);
  swrite(&value, sizeof value, 1, peer);
  while(handle_request(peer, env));
}

JNIEXPORT void JNICALL Java_JavaBridge_setResultFromDouble
  (JNIEnv *env, jclass self, jlong result, jlong _peer, jdouble value)
{
  struct peer*peer=(struct peer*)(long)_peer;
  if(setjmp(peer->savepoint)) return;
  ID(peer, SETRESULTFROMDOUBLE);
  swrite(&result, sizeof result, 1, peer);
  swrite(&value, sizeof value, 1, peer);
  while(handle_request(peer, env));
}

JNIEXPORT void JNICALL Java_JavaBridge_setResultFromBoolean
  (JNIEnv *env, jclass self, jlong result, jlong _peer, jboolean value)
{
  struct peer*peer=(struct peer*)(long)_peer;
  if(setjmp(peer->savepoint)) return;
  ID(peer, SETRESULTFROMBOOLEAN);
  swrite(&result, sizeof result, 1, peer);
  swrite(&value, sizeof value, 1, peer);
  while(handle_request(peer, env));
}

JNIEXPORT void JNICALL Java_JavaBridge_setResultFromObject
  (JNIEnv *env, jclass self, jlong result, jlong _peer, jobject value)
{
  struct peer*peer=(struct peer*)(long)_peer;
  if(setjmp(peer->savepoint)) return;
  ID(peer, SETRESULTFROMOBJECT);
  swrite(&result, sizeof result, 1, peer);
  swrite(&value, sizeof value, 1, peer);
  while(handle_request(peer, env));
}

JNIEXPORT jboolean JNICALL Java_JavaBridge_setResultFromArray
  (JNIEnv *env, jclass self, jlong result, jlong _peer, jobject value)
{
  jboolean send_content;
  struct peer*peer=(struct peer*)(long)_peer;
  if(setjmp(peer->savepoint)) return JNI_FALSE;
  ID(peer, SETRESULTFROMARRAY);
  swrite(&result, sizeof result, 1, peer);
  swrite(&value, sizeof value, 1, peer);
  sread(&send_content, sizeof send_content, 1, peer);
  while(handle_request(peer, env));
  return send_content;
}

/*
 * The following is from Sam Ruby's original PHP 4 code. When the
 * result was an array or Hashtable, the ext/java extension copied the
 * entire(!) array or hash to the PHP interpreter.  Since PHP 5 this
 * is dead code.
 */
#ifndef DISABLE_DEPRECATED
JNIEXPORT jlong JNICALL Java_JavaBridge_nextElement
  (JNIEnv *env, jclass self, jlong array, jlong _peer)
{
  jlong result;
  struct peer*peer=(struct peer*)(long)_peer;
  if(setjmp(peer->savepoint)) return 0;
  ID(peer, NEXTELEMENT);
  swrite(&array, sizeof array, 1, peer);
  while(handle_request(peer, env));
  sread(&result, sizeof result, 1, peer);
  while(handle_request(peer, env));
  return result;
}

JNIEXPORT jlong JNICALL Java_JavaBridge_hashIndexUpdate
  (JNIEnv *env, jclass self, jlong array, jlong _peer, jlong key)
{
  jlong result;
  struct peer*peer=(struct peer*)(long)_peer;
  if(setjmp(peer->savepoint)) return 0;
  ID(peer, HASHINDEXUPDATE);
  swrite(&array, sizeof array, 1, peer);
  swrite(&key, sizeof key, 1, peer);
  while(handle_request(peer, env));
  sread(&result, sizeof result, 1, peer);
  while(handle_request(peer, env));
  return result;
}
JNIEXPORT jlong JNICALL Java_JavaBridge_hashUpdate
  (JNIEnv *env, jclass self, jlong array, jlong _peer, jbyteArray key)
{
  jlong result;
  struct peer*peer=(struct peer*)(long)_peer;
  if(setjmp(peer->savepoint)) return 0;
  ID(peer, HASHUPDATE);
  swrite(&array, sizeof array, 1, peer);
  swrite(&key, sizeof key, 1, peer);
  while(handle_request(peer, env));
  sread(&result, sizeof result, 1, peer);
  while(handle_request(peer, env));
  return result;
}
#endif /* DISABLE_DEPRECATED */

JNIEXPORT void JNICALL Java_JavaBridge_setException
  (JNIEnv *env, jclass self, jlong result, jlong _peer, jthrowable value, jbyteArray strValue)
{
  struct peer*peer=(struct peer*)(long)_peer;
  if(setjmp(peer->savepoint)) return;
  ID(peer, SETEXCEPTION);
  swrite(&result, sizeof result, 1, peer);
  swrite(&value, sizeof value, 1, peer);
  swrite(&strValue, sizeof strValue, 1, peer);
  while(handle_request(peer, env));
}
JNINativeMethod javabridge[]={
  {"setResultFromString", "(JJ[B)V", Java_JavaBridge_setResultFromString},
  {"setResultFromLong", "(JJJ)V", Java_JavaBridge_setResultFromLong},
  {"setResultFromDouble", "(JJD)V", Java_JavaBridge_setResultFromDouble},
  {"setResultFromBoolean", "(JJZ)V", Java_JavaBridge_setResultFromBoolean},
  {"setResultFromObject", "(JJLjava/lang/Object;)V", Java_JavaBridge_setResultFromObject},
  {"setResultFromArray", "(JJLjava/lang/Object;)Z", Java_JavaBridge_setResultFromArray},
  {"nextElement", "(JJ)J", Java_JavaBridge_nextElement},
  {"hashUpdate", "(JJ[B)J", Java_JavaBridge_hashUpdate},
  {"hashIndexUpdate", "(JJJ)J", Java_JavaBridge_hashIndexUpdate},
  {"setException", "(JJLjava/lang/Throwable;[B)V", Java_JavaBridge_setException},
  {"startNative", "(ILjava/lang/String;)V", Java_JavaBridge_startNative},
  {"handleRequests", "(III)V", Java_JavaBridge_handleRequests},
  {"handleRequest", "(Ljava/lang/Object;J)I", Java_JavaBridge_handleRequest},
  {"trampoline", "(Ljava/lang/Object;JZ)Z", Java_JavaBridge_trampoline},
  {"openLog", "(Ljava/lang/String;)Z", Java_JavaBridge_openLog},
};

static struct NativeMethods {
  char*class;
  JNINativeMethod*meth;
  int n;
} meths[]={
  {"JavaBridge", javabridge, (sizeof javabridge)/(sizeof *javabridge)},
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
  reflectClass = (*jenv)->FindClass(jenv, "JavaBridge");
  assert(reflectClass); if(!reflectClass) exit(9);
  init = (*jenv)->GetStaticMethodID(jenv, reflectClass, "init", "([Ljava/lang/String;)V");
  assert(init); if(!init) exit(9);
  stringClass = (*jenv)->FindClass(jenv, "java/lang/String");
  assert(stringClass); if(!stringClass) exit(9);
  arr = (*jenv)->NewObjectArray(jenv, argc, stringClass, 0);
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
  meths[0].meth[4].signature="(JJLjava.lang.Object;)V";
  meths[0].meth[5].signature="(JJLjava.lang.Object;)Z";
  meths[0].meth[9].signature="(JJLjava.lang.Throwable;[B)V";
  meths[0].meth[10].signature="(ILjava.lang.String;)V";
  meths[0].meth[12].signature="(Ljava.lang.Object;J)I";
  meths[0].meth[13].signature="(Ljava.lang.Object;JZ)Z";
  meths[0].meth[14].signature="(Ljava.lang.String;)Z";

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
