/*-*- mode: C; tab-width:4 -*-*/

#include <stdarg.h>
#include <assert.h>
#include <errno.h>
#include <string.h>
#include <stdlib.h>

#include "protocol.h"
#include "sio.c"

#define swrite java_swrite
void java_swrite(const  void  *ptr,  size_t  size,  size_t  nmemb,  SFILE *stream) {
  int n = SFWRITE(ptr, size, nmemb, stream);
  //printf("write char:::%d\n", (unsigned int) ((char*)ptr)[0]);
  assert(n==nmemb);
  if(n!=nmemb) exit(6);
}

#define sread java_sread
void java_sread(void *ptr, size_t size, size_t nmemb, SFILE *stream) {
  int n = SFREAD(ptr, size, nmemb, stream);
  //printf("read char:::%d\n", (unsigned int) ((char*)ptr)[0]);
  assert(n==nmemb);
  if(n!=nmemb) exit(7);
}

#define id java_id
void java_id(proxyenv *env, char id) {
  swrite(&id, sizeof id, 1, (*env)->peer);
}

/*
 * The following Invoke, CreateObject, GetSetProp and LastException  methods start everything
 */
static void do_invoke(proxyenv *env, jobject php_reflect, jmethodID invoke, jobject obj, jstring method, jobjectArray array, jlong result) {
  swrite(&php_reflect, sizeof php_reflect, 1, (*env)->peer);
  swrite(&invoke, sizeof invoke, 1, (*env)->peer);
  swrite(&obj, sizeof obj, 1, (*env)->peer);
  swrite(&method, sizeof method, 1, (*env)->peer);
  swrite(&array, sizeof array, 1, (*env)->peer);
  swrite(&result, sizeof result, 1, (*env)->peer);
  (*env)->handle_request(env);
}
static void Invoke(proxyenv *env, jobject php_reflect, jmethodID invoke, jobject obj, jstring method, jobjectArray array, jlong result) {
  id(env, INVOKE);
  do_invoke(env, php_reflect, invoke, obj, method, array, result);
}

static void LastException(proxyenv *env, jobject php_reflect, jmethodID lastEx, jlong result) {
  id(env, LASTEXCEPTION);
  swrite(&php_reflect, sizeof php_reflect, 1, (*env)->peer);
  swrite(&lastEx, sizeof lastEx, 1, (*env)->peer);
  swrite(&result, sizeof result, 1, (*env)->peer);
  (*env)->handle_request(env);
}
static void CreateObject(proxyenv *env, jobject php_reflect, jmethodID invoke, jstring method, jobjectArray array, jlong result) {
  id(env, CREATEOBJECT);
  swrite(&php_reflect, sizeof php_reflect, 1, (*env)->peer);
  swrite(&invoke, sizeof invoke, 1, (*env)->peer);
  swrite(&method, sizeof method, 1, (*env)->peer);
  swrite(&array, sizeof array, 1, (*env)->peer);
  swrite(&result, sizeof result, 1, (*env)->peer);
  (*env)->handle_request(env);
}

static void GetSetProp(proxyenv *env, jobject php_reflect, jmethodID gsp, jobject obj, jstring propName, jobjectArray value, jlong result) {
  id(env, GETSETPROP);
  do_invoke(env, php_reflect, gsp, obj, propName, value, result);
}



static jobject AllocObject (proxyenv *env, jclass clazz) {
  jobject result;
  id(env, ALLOCOBJECT);
  swrite(&clazz, sizeof clazz, 1, (*env)->peer);
  sread(&result, sizeof result, 1, (*env)->peer);
  return result;
}
static jobject CallObjectMethod (short count, proxyenv *env, jobject obj, jmethodID methodID, ...) {
  va_list args;
  jvalue arg;
  jobject result;
  id(env, CALLOBJECTMETHOD);
  swrite(&count, sizeof count, 1, (*env)->peer);
  swrite(&obj, sizeof obj, 1, (*env)->peer);
  swrite(&methodID, sizeof methodID, 1, (*env)->peer);
  va_start(args, methodID);
  while(count--) {
	arg=va_arg(args,jvalue );
	swrite(&arg, sizeof arg, 1, (*env)->peer);
  }
  va_end(args);
  sread(&result, sizeof result, 1, (*env)->peer);
  return result;
}
static void CallVoidMethod (short count, proxyenv *env, jobject obj, jmethodID methodID, ...) {
  va_list args;
  jvalue arg;
  jobject result;
  id(env, CALLVOIDMETHOD);
  swrite(&count, sizeof count, 1, (*env)->peer);
  swrite(&obj, sizeof obj, 1, (*env)->peer);
  swrite(&methodID, sizeof methodID, 1, (*env)->peer);
  va_start(args, methodID);
  while(count--) {
	arg=va_arg(args, jvalue);
	swrite(&arg, sizeof arg, 1, (*env)->peer);
  }
  va_end(args);
}
static void DeleteGlobalRef (proxyenv *env, jobject gref) {
  id(env, DELETEGLOBALREF);
  swrite(&gref, sizeof gref, 1, (*env)->peer);
}
static void DeleteLocalRef (proxyenv *env, jobject obj) {
  id(env, DELETELOCALREF);
  swrite(&obj, sizeof obj, 1, (*env)->peer);
}
static void ExceptionClear (proxyenv *env) {
  id(env, EXCEPTIONCLEAR);
}
static jthrowable ExceptionOccurred (proxyenv *env) {
  jthrowable result;
  id(env, EXCEPTIONOCCURRED);
  sread(&result, sizeof result, 1, (*env)->peer);
  return result;
}
static jclass FindClass (proxyenv *env, const char *name) {
  size_t len=strlen(name);
  jclass clazz;
  id(env, FINDCLASS);
  swrite(&len, sizeof len, 1, (*env)->peer);
  swrite(name, sizeof*name, len, (*env)->peer);
  sread(&clazz, sizeof clazz, 1, (*env)->peer);
  return clazz;
}
static jsize GetArrayLength (proxyenv *env, jarray array) {
  jsize result;
  id(env, GETARRAYLENGTH);
  swrite(&array, sizeof array, 1, (*env)->peer);
  sread(&result, sizeof result, 1, (*env)->peer);
  return result;
}
static jbyte *GetByteArrayElements (proxyenv *env, jbyteArray array, jboolean *isCopy) {
  size_t count;
  jboolean dummy;
  jbyte *result;
  id(env, GETBYTEARRAYELEMENTS);
  swrite(&array, sizeof array, 1, (*env)->peer);
  sread(&dummy, sizeof dummy, 1, (*env)->peer);
  sread(&count, sizeof count, 1, (*env)->peer);
  result=(jbyte*)calloc(count, sizeof*result);
  assert(result);
  sread(result, sizeof*result, count, (*env)->peer);
  assert(isCopy);
  if(isCopy) *isCopy=dummy;
  swrite(&result, sizeof result, 1, (*env)->peer);
  return result;
}
static jmethodID GetMethodID (proxyenv *env, jclass clazz, const char *name, const char *sig) {
  jmethodID mid;
  size_t len;
  id(env, GETMETHODID);
  swrite(&clazz, sizeof clazz, 1, (*env)->peer);
  len=strlen(name);
  swrite(&len, sizeof len, 1, (*env)->peer);
  swrite(name, sizeof*name, len, (*env)->peer);
  len=strlen(sig);
  swrite(&len, sizeof len, 1, (*env)->peer);
  swrite(sig, sizeof*sig, len, (*env)->peer);
  sread(&mid, sizeof mid, 1, (*env)->peer);
  return mid;
}
static jclass GetObjectClass (proxyenv *env, jobject obj) {
  jclass result;
  id(env, GETOBJECTCLASS);
  swrite(&obj, sizeof obj, 1, (*env)->peer);
  sread(&result, sizeof result, 1, (*env)->peer);
  return result;
}
static const char* GetStringUTFChars (proxyenv *env, jstring str, jboolean *isCopy) {
  size_t count;
  char *result;
  jboolean dummy;
  id(env, GETSTRINGUTFCHARS);
  swrite(&str, sizeof str, 1, (*env)->peer);
  sread(&dummy, sizeof dummy, 1, (*env)->peer);
  sread(&count, sizeof count, 1, (*env)->peer);
  result=(char*)malloc(count+1);
  assert(result);
  sread(result, sizeof*result, count, (*env)->peer);
  result[count]=0;
  assert(isCopy);
  if(isCopy) *isCopy=dummy;
  swrite(&result, sizeof result, 1, (*env)->peer);
  return result;
}
static jbyteArray NewByteArray (proxyenv *env, jsize len) {
  jbyteArray result;
  id(env, NEWBYTEARRAY);
  swrite(&len, sizeof len, 1, (*env)->peer);
  sread(&result, sizeof result, 1, (*env)->peer);
  return result;
}
static jobject NewGlobalRef (proxyenv *env, jobject lobj) {
  jobject result;
  id(env, NEWGLOBALREF);
  swrite(&lobj, sizeof lobj, 1, (*env)->peer);
  sread(&result, sizeof result, 1, (*env)->peer);
  return result;
}
static jobject NewObject (short count, proxyenv *env, jclass clazz, jmethodID methodID, ...) {
  va_list args;
  jvalue arg;
  jobject result;
  id(env, NEWOBJECT);
  swrite(&count, sizeof count, 1, (*env)->peer);
  swrite(&clazz, sizeof clazz, 1, (*env)->peer);
  swrite(&methodID, sizeof methodID, 1, (*env)->peer);
  va_start(args, methodID);
  while(count--) {
	arg=va_arg(args, jvalue);
	swrite(&arg, sizeof arg, 1, (*env)->peer);
  }
  va_end(args);
  sread(&result, sizeof result, 1, (*env)->peer);
  return result;
}
static jobjectArray NewObjectArray (proxyenv *env, jsize len, jclass clazz, jobject init) {
  jobjectArray result;
  id(env, NEWOBJECTARRAY);
  swrite(&len, sizeof len, 1, (*env)->peer);
  swrite(&clazz, sizeof clazz, 1, (*env)->peer);
  swrite(&init, sizeof init, 1, (*env)->peer);
  sread(&result, sizeof result, 1, (*env)->peer);
  return result;
}
static jstring NewStringUTF (proxyenv *env, const char *utf) {
  jstring result;
  size_t length=strlen(utf);
  id(env, NEWSTRINGUTF);
  swrite(&length, sizeof length, 1, (*env)->peer);
  swrite(utf, sizeof*utf, length, (*env)->peer);
  sread(&result, sizeof result, 1, (*env)->peer);
  return result;
}
static void ReleaseByteArrayElements (proxyenv *env, jbyteArray array, jbyte *elems, jint mode) {
  id(env, RELEASEBYTEARRAYELEMENTS);
  swrite(&array, sizeof array, 1, (*env)->peer);
  swrite(&elems, sizeof elems, 1, (*env)->peer);
  swrite(&mode, sizeof mode, 1, (*env)->peer);
  assert(!mode);
  free(elems);
}
static void ReleaseStringUTFChars (proxyenv *env, jstring array, const char*elems) {
  id(env, RELEASESTRINGUTFCHARS);
  swrite(&array, sizeof array, 1, (*env)->peer);
  swrite(&elems, sizeof elems, 1, (*env)->peer);
  free((char*)elems);
}
static void SetByteArrayRegion (proxyenv *env, jbyteArray array, jsize start, jsize len, jbyte *buf) {
  id(env, SETBYTEARRAYREGION);
  swrite(&array, sizeof array, 1, (*env)->peer);
  swrite(&start, sizeof start, 1, (*env)->peer);
  swrite(&len, sizeof len, 1, (*env)->peer);
  swrite(buf, sizeof*buf, len, (*env)->peer);
}
static void SetObjectArrayElement (proxyenv *env, jobjectArray array, jsize index, jobject val) {
  id(env, SETOBJECTARRAYELEMENT);
  swrite(&array, sizeof array, 1, (*env)->peer);
  swrite(&index, sizeof index, 1, (*env)->peer);
  swrite(&val, sizeof val, 1, (*env)->peer);
}


proxyenv *java_createSecureEnvironment(SFILE *peer, int (*handle_request)(proxyenv *env)) {
  proxyenv *env;  
  env=(proxyenv*)malloc(sizeof *env);     
  if(!env) exit(9);
  *env=(proxyenv)calloc(1, sizeof **env); 
  if(!*env) exit(9);

  (*env)->peer = peer;
  (*env)->handle_request = handle_request;

  (*env)->LastException=LastException;
  (*env)->GetSetProp=GetSetProp;
  (*env)->Invoke=Invoke;
  (*env)->CreateObject=CreateObject;
  (*env)->AllocObject=AllocObject;
  (*env)->CallObjectMethod=CallObjectMethod;
  (*env)->CallVoidMethod=CallVoidMethod;
  (*env)->DeleteGlobalRef=DeleteGlobalRef;
  (*env)->DeleteLocalRef=DeleteLocalRef;
  (*env)->ExceptionClear=ExceptionClear;
  (*env)->ExceptionOccurred=ExceptionOccurred;
  (*env)->FindClass=FindClass;
  (*env)->GetArrayLength=GetArrayLength;
  (*env)->GetByteArrayElements=GetByteArrayElements;
  (*env)->GetMethodID=GetMethodID;
  (*env)->GetObjectClass=GetObjectClass;
  (*env)->GetStringUTFChars=GetStringUTFChars;
  (*env)->NewByteArray=NewByteArray;
  (*env)->NewGlobalRef=NewGlobalRef;
  (*env)->NewObject=NewObject;
  (*env)->NewObjectArray=NewObjectArray;
  (*env)->NewStringUTF=NewStringUTF;
  (*env)->ReleaseByteArrayElements=ReleaseByteArrayElements;
  (*env)->ReleaseStringUTFChars=ReleaseStringUTFChars;
  (*env)->SetByteArrayRegion=SetByteArrayRegion;
  (*env)->SetObjectArrayElement=SetObjectArrayElement;

  return env;
} 
