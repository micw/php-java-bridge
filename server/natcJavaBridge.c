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
  exit(6); \
}

static jclass exceptionClass=NULL;

static jclass hashClass=NULL;
static jmethodID init=NULL;

static jmethodID hashPut=NULL;
static jmethodID hashRemove=NULL;

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
  int n = fwrite(ptr, size, nmemb, stream);
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
  int n = fread(ptr, size, nmemb, stream);
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
  hashClass = (*env)->FindClass(env, "java/util/Hashtable");
  init = (*env)->GetMethodID(env, hashClass, "<init>", "()V");
  hash = (*env)->NewObject(env, hashClass, init);
  hashPut = (*env)->GetMethodID(env, hashClass, "put", "(Ljava/lang/Object;Ljava/lang/Object;)Ljava/lang/Object;");
  hashRemove = (*env)->GetMethodID(env, hashClass, "remove", "(Ljava/lang/Object;)Ljava/lang/Object;");
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
	jobject ref;
	sread(&ref, sizeof ref, 1, peer);
	(*env)->DeleteGlobalRef(env, ref);
	break;
  }
  case DELETELOCALREF: {
	jobject ref;
	sread(&ref, sizeof ref, 1, peer);
	(*env)->DeleteLocalRef(env, ref);
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
	jobject obj;
	sread(&obj, sizeof obj, 1, peer);
	result = (*env)->NewGlobalRef(env, obj);
	swrite(&result, sizeof result, 1, peer);
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
static int handle_request_impl(FILE*file, JNIEnv *env) {
  struct peer peer;
  int val;
  peer.objectHash=initHash(env);
  if(!peer.objectHash) {logError(env, "could not create hash table"); return -40;}
  peer.stream=file;
  peer.jenv=env;
  peer.tran=0;
  val=setjmp(peer.env);
  if(val) {
	(*env)->DeleteGlobalRef(env, peer.objectHash);
	return -val;
  }
  val=handle_request(&peer, env);
  (*env)->DeleteGlobalRef(env, peer.objectHash);
  return val;
}
static void logIntValue(JNIEnv*env, char*t, int i) {
  char*s=malloc(160);
  assert(s);
  if(!s) return;
  sprintf(s, "%s: %i",t,i);
  logDebug(env, s);
  free(s);
}
static void logChannel(JNIEnv*env, char*t, int i) {
 if(logLevel>2) logIntValue(env, t, i);
}

struct param {int s; JNIEnv *env; JavaVM *vm;};
static void *handle_requests(void *p) {
  JNIEnv *env;
  struct param *param = (struct param*)p;
  FILE *peer;
  block_sig();
  enter();
  int err = (*param->vm)->AttachCurrentThread(param->vm, (void**)&env, NULL);

  if(err) {logError(env, "could not attach to java vm"); free(p); return NULL;}
  logChannel(env, "create new communication channel", param->s);
  peer = fdopen(param->s, "r+");
  if(!peer) logSysError(env, "could not fdopen socket");

  while(peer && !feof(peer)) {
	int term = handle_request_impl(peer, env);
	if(term>=0) logChannel(env, "end packet", term);
	else {
	  logIntValue(env, "communication broken", term);
	  break;
	}
  }
  logChannel(env, "terminate communication channel", param->s);
  (*param->vm)->DetachCurrentThread(param->vm);
  if(peer) fclose(peer);
  close(param->s);

  free(p);
  leave();
  return NULL;
}


  
JNIEXPORT void JNICALL Java_JavaBridge_startNative
  (JNIEnv *env, jclass self, jint _logLevel, jstring _sockname)
{
  pthread_t thread;
  struct sockaddr_un saddr;
  int sock, n;

  initGlobals(env);
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
  saddr.sun_family = AF_UNIX;
  strcpy(saddr.sun_path, sockname);
  unlink(sockname); 

  sock = socket (PF_UNIX, SOCK_STREAM, 0);
  if(!sock) {logSysError(env, "could not create socket"); return;}
  n = bind(sock,(struct sockaddr*)&saddr, sizeof saddr);
  if(n==-1) {logSysError(env, "could not bind socket"); return;}
  chmod(sockname, 0666); // the childs usually run as "nobody"
  n = listen(sock, 10);
  if(n==-1) {logSysError(env, "could not listen to socket"); return;}

  while(1) {
	struct param *param = malloc(sizeof*param);
	if(!param) {logMemoryError(env, __FILE__, __LINE__); return;}

  res:errno=0; 
	pthread_mutex_lock(&mutex);
	if(count==-1) {pthread_mutex_unlock(&mutex); return;}
	pthread_mutex_unlock(&mutex);
	param->s = accept(sock, NULL, 0); 
	if(param->s==-1) {logSysError(env, "socket accept failed"); return;}
	if(errno) goto res; 		

	param->env = env;
	n=(*env)->GetJavaVM(env, &param->vm);
	if(n) {logError(env, "could not get java vm"); return;}
	pthread_create(&thread, &attr, handle_requests, param);
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
