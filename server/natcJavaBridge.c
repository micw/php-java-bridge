/*-*- mode: C; tab-width:4 -*-*/

/* longjump */
#include <setjmp.h>

#/* socket */
#include <sys/types.h>
#include <sys/socket.h>
#include <sys/un.h>
#include <sys/stat.h>

/* select */
#include <sys/select.h>

/* strings */
#include <string.h>
/* setenv */
#include <stdlib.h>
/*signal*/
#include <signal.h>

/* posix threads implementation */
#include <pthread.h>

/* miscellaneous */
#include <stdio.h>
#include <assert.h>
#include <errno.h>
#include <unistd.h>


#include "protocol.h"

#define ID(peer, name) \
if(logLevel>=LOG_DEBUG) logDebug(env, "send: "/**/#name); \
id(peer, name);

#define ASSERTM(expr) \
if(!expr) { \
  logMemoryError(env, __FILE__, __LINE__); \
  exit(9); \
}

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

static jclass longClass=NULL;
static jmethodID longCtor=NULL;
static jmethodID longValue=NULL;

static jint logLevel=4;
static jclass bridge=NULL;

static char*sockname=NULL;

static pthread_attr_t attr;
static pthread_mutex_t mutex;
static pthread_cond_t cond;
static int count=0;
static sigset_t block;

struct peer {
  jmp_buf env;					/* exit from the loop */
  jmp_buf savepoint;			/* jump back to java */
  JNIEnv *jenv;
  short tran;					/* if we must return to java first */
  FILE*stream;
  jobject objectHash;
  jobject globalRef;
};
static void doLog (JNIEnv *jenv, char *msg, jmethodID logMessageID) {
  jstring str;
  assert(logMessageID);
  if(!logMessageID) return;
  str = (*jenv)->NewStringUTF(jenv, msg);
  assert(str);
  if(!str) return; 
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
static void logSysError(JNIEnv *jenv, char *msg) {
  char s[512];
  sprintf(s, "system error: %s: %s", msg, strerror(errno));
  logError(jenv, s);
}
static void logMemoryError(JNIEnv *jenv, char *file, int pos) {
  static char s[512];
  sprintf(s, "system error: out of memory error in: %s, line: %d", file, pos);
  logError(jenv, s);
  exit(9);
}

static void swrite(const  void  *ptr,  size_t  size,  size_t  nmemb,  struct peer*peer) {
  FILE*stream=peer->stream;
  int n;
  fflush(stream);
  n = fwrite(ptr, size, nmemb, stream);
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
  FILE*stream=peer->stream;
  int n;
  fflush(stream);
  n = fread(ptr, size, nmemb, stream);
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
	unlink(sockname); 
	free(sockname);
	sockname=NULL;
	pthread_attr_destroy(&attr);
	pthread_mutex_destroy(&mutex);
	pthread_cond_destroy(&cond);
  }
}

static void block_sig() {
  sigset_t d;
  pthread_sigmask(SIG_BLOCK, &block, &d);
}

static void enter() {
  pthread_mutex_lock(&mutex);
  assert(count>=0);
  count++;
  pthread_mutex_unlock(&mutex);
}

static void leave() {
  pthread_mutex_lock(&mutex);
  assert(count>0);
  if(!--count) pthread_cond_signal(&cond);
  pthread_mutex_unlock(&mutex);
}

static void *guard_requests(void *p) {
  int sig;
  block_sig();
  sigwait(&block, &sig);
  pthread_mutex_lock(&mutex);
  if(count) pthread_cond_wait(&cond, &mutex);
  count=-1;
  pthread_mutex_unlock(&mutex);
}

static void initGlobals(JNIEnv *env) {
  pthread_t thread;
  sigset_t d;
  jobject hash;

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

  handleRequests = (*env)->GetStaticMethodID(env, bridge, "HandleRequests", "(J)V");

  longClass = (*env)->FindClass (env, "java/lang/Long");
  longCtor = (*env)->GetMethodID(env, longClass, "<init>", "(J)V");
  longValue = (*env)->GetMethodID(env, longClass, "longValue", "()J");

  atexit(atexit_bridge);

  pthread_attr_init(&attr);
  pthread_cond_init(&cond, NULL);
  pthread_mutex_init(&mutex, NULL);
  pthread_attr_setdetachstate(&attr, PTHREAD_CREATE_DETACHED);

  sigemptyset(&block);
  sigaddset(&block, SIGTERM);
  pthread_create(&thread, &attr, guard_requests, 0);
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
	jobjectArray array;
	jlong result;
	jthrowable abort;

	sread(&php_reflect, sizeof php_reflect,1, peer);
	sread(&invoke, sizeof invoke,1, peer);
	sread(&method, sizeof method,1, peer);
	sread(&array, sizeof array,1, peer);
	sread(&result, sizeof result,1, peer);
	peer->tran=1;
	(*env)->CallVoidMethod(env, php_reflect, invoke, method, array, result, (jlong)(long)peer);
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
  case DELETELOCALREF: {
	jobject ref;
	sread(&ref, sizeof ref, 1, peer);
	/* disabled, problems with gcj */
	//(*env)->DeleteLocalRef(env, ref);
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
	jarray array;
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
	if(!ob1 || !ob2) return;
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
	if(!ob1||!ob2) return;
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
	size_t len;
	jvalue *args;
	sread(&len, sizeof len, 1, peer);
	sread(&clazz, sizeof clazz, 1, peer);
	sread(&methodID, sizeof methodID, 1, peer);
	args=calloc(len, sizeof *args);
	ASSERTM(args);
	sread(args, sizeof *args, len, peer);
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
	jarray array;
	jbyte *elems;
	jint mode;
	sread(&array, sizeof array, 1, peer);
	sread(&elems, sizeof elems, 1, peer);
	sread(&mode, sizeof mode, 1, peer);
	ob=objFromPtr(env, elems);
	if(!ob) return;
	val = (*env)->CallObjectMethod(env, peer->objectHash, hashRemove, ob);
	assert(val);
	if(!val) return;
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
	if(!ob) return;
	val = (*env)->CallObjectMethod(env, peer->objectHash, hashRemove, ob);
	assert(val);
	if(!val) return;
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
  default: {
	logError(env, "protocol error: recv unknown token");
  }
  }
  return 1;
}
static int handle_request_impl(FILE*file, JNIEnv *env, jobject globalRef) {
  struct peer peer;
  int val;
  peer.objectHash=initHash(env);
  if(!peer.objectHash) {logError(env, "could not create hash table"); return -40;}
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

JNIEXPORT void JNICALL Java_JavaBridge_handleRequests(JNIEnv*env, jobject instance, jlong socket)
{
  jobject globalRef;
  FILE *peer = (FILE*)(long)socket;
  block_sig();
  enter();
  logChannel(env, "create new communication channel", socket);

  globalRef = connection_startup(env);
  if(!globalRef){logError(env, "could not allocate global hash");if(peer) fclose(peer);return;}

  if(peer && (fwrite(&instance, sizeof instance, 1, peer)!=1)) {
	logSysError(env, "could not send instance, child not listening"); fclose(peer);return;
  }
  while(peer && !feof(peer)) {
	int term = handle_request_impl(peer, env, globalRef);
	if(term>=0) logChannel(env, "end packet", term);
	else {
	  logIntValue(env, "communication broken", term);
	  break;
	}
  }
  connection_cleanup(env, globalRef);

  logChannel(env, "terminate communication channel", socket);
  if(peer) fclose(peer);

  leave();
}



JNIEXPORT void JNICALL Java_JavaBridge_startNative
  (JNIEnv *env, jclass self, jint _logLevel, jstring _sockname)
{
  struct sockaddr_un saddr;
  int sock, n;
  FILE *peer;

  signal(SIGBUS, exit);
  signal(SIGILL, exit);
  /* do not catch these, the VM uses them internally */
  /*   signal(SIGSEGV, exit); */
  /*   signal(SIGPWR, exit); */

  logLevel = _logLevel;
  bridge = self;
  initGlobals(env);

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
  saddr.sun_family = AF_UNIX;
  memset(saddr.sun_path, 0, sizeof saddr.sun_path);
  strcpy(saddr.sun_path, sockname);
#ifndef CFG_JAVA_SOCKET_ANON
  unlink(sockname);
#else
  *saddr.sun_path=0;
#endif
  sock = socket (PF_UNIX, SOCK_STREAM, 0);
  if(!sock) {logSysError(env, "could not create socket"); return;}
  n = bind(sock,(struct sockaddr*)&saddr, sizeof saddr);
  if(n==-1) {logSysError(env, "could not bind socket"); return;}
#ifndef CFG_JAVA_SOCKET_ANON
  chmod(sockname, 0666); // the childs usually run as "nobody"
#endif
  n = listen(sock, 10);
  if(n==-1) {logSysError(env, "could not listen to socket"); return;}

  while(1) {
	int socket;

  res:errno=0; 
	pthread_mutex_lock(&mutex);
	if(count==-1) {pthread_mutex_unlock(&mutex); return;}
	pthread_mutex_unlock(&mutex);
	socket = accept(sock, NULL, 0); 
	if(socket==-1) {logSysError(env, "socket accept failed"); return;}
	//if(errno) goto res;
	peer = fdopen(socket, "r+");
	if(!peer) {logSysError(env, "could not fdopen socket");goto res;}

    (*env)->CallStaticVoidMethod(env, bridge, handleRequests,(jlong)(long)peer);
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

JNIEXPORT void JNICALL Java_JavaBridge_setResultFromArray
  (JNIEnv *env, jclass self, jlong result, jlong _peer)
{
  struct peer*peer=(struct peer*)(long)_peer;
  if(setjmp(peer->savepoint)) return;
  ID(peer, SETRESULTFROMARRAY);
  swrite(&result, sizeof result, 1, peer);
  while(handle_request(peer, env));
}

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

JNIEXPORT void JNICALL Java_JavaBridge_setException
  (JNIEnv *env, jclass self, jlong result, jlong _peer, jbyteArray value)
{
  struct peer*peer=(struct peer*)(long)_peer;
  if(setjmp(peer->savepoint)) return;
  ID(peer, SETEXCEPTION);
  swrite(&result, sizeof result, 1, peer);
  swrite(&value, sizeof value, 1, peer);
  while(handle_request(peer, env));
}
JNINativeMethod javabridge[]={
  {"setResultFromString", "(JJ[B)V", Java_JavaBridge_setResultFromString},
  {"setResultFromLong", "(JJJ)V", Java_JavaBridge_setResultFromLong},
  {"setResultFromDouble", "(JJD)V", Java_JavaBridge_setResultFromDouble},
  {"setResultFromBoolean", "(JJZ)V", Java_JavaBridge_setResultFromBoolean},
  {"setResultFromObject", "(JJLjava/lang/Object;)V", Java_JavaBridge_setResultFromObject},
  {"setResultFromArray", "(JJ)V", Java_JavaBridge_setResultFromArray},
  {"nextElement", "(JJ)J", Java_JavaBridge_nextElement},
  {"hashUpdate", "(JJ[B)J", Java_JavaBridge_hashUpdate},
  {"hashIndexUpdate", "(JJJ)J", Java_JavaBridge_hashIndexUpdate},
  {"setException", "(JJ[B)V", Java_JavaBridge_setException},
  {"startNative", "(ILjava/lang/String;)V", Java_JavaBridge_startNative},
  {"handleRequests", "(J)V", Java_JavaBridge_handleRequests},
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
  meths[0].meth[10].signature="(ILjava.lang.String;)V";
	
  if(!argv) exit(6);
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
