/*-*- mode: C; tab-width:4 -*-*/

#ifndef PROXY_PROTOCOL_H
#define PROXY_PROTOCOL_H

/* peer */
#include <stdio.h>
/* jni */
#include <jni.h>

/* 
 * we create a unix domain socket with the name .php_java_bridge in
 * the tmpdir
 */
#ifndef P_tmpdir
/* xopen, normally defined in stdio.h */
#define P_tmpdir "/tmp"
#endif 
#define SOCKNAME P_tmpdir/**/"/.php_java_bridge"/**/"XXXXXX"

#define LOG_OFF 0
#define LOG_FATAL 1
#define LOG_ERROR 2
#define LOG_INFO 3 /* default level */
#define LOG_DEBUG 4

#define PROTOCOL_END 0
#define ALLOCOBJECT 1
#define CALLOBJECTMETHOD 2
#define CALLVOIDMETHOD 3
#define DELETEGLOBALREF 4
#define DELETELOCALREF 5
#define EXCEPTIONCLEAR 6
#define EXCEPTIONOCCURRED 7
#define FINDCLASS 8
#define GETARRAYLENGTH 9
#define GETBYTEARRAYELEMENTS 10
#define GETMETHODID 11
#define GETOBJECTCLASS 12
#define GETSTRINGUTFCHARS 13
#define NEWBYTEARRAY 14
#define NEWGLOBALREF 15
#define NEWOBJECT 16
#define NEWOBJECTARRAY 17
#define NEWSTRINGUTF 18
#define RELEASEBYTEARRAYELEMENTS 19
#define RELEASESTRINGUTFCHARS 20
#define SETBYTEARRAYREGION 21
#define SETOBJECTARRAYELEMENT 22

#define SETRESULTFROMSTRING 101
#define SETRESULTFROMLONG 102
#define SETRESULTFROMDOUBLE 103
#define SETRESULTFROMBOOLEAN 104
#define SETRESULTFROMOBJECT 105
#define SETRESULTFROMARRAY 106
#define NEXTELEMENT 107
#define HASHINDEXUPDATE 108
#define HASHUPDATE 109
#define SETEXCEPTION 110

#define INVOKE 50
#define CREATEOBJECT 51
#define GETSETPROP 52
#define LASTEXCEPTION 53

#define N_SARGS 9				/* # of server args for exec */
#define N_SENV 3				/* # of server env entries */

typedef struct proxyenv_ *proxyenv;
struct proxyenv_ {
  FILE *peer;

  void (*LastException)(proxyenv *env, jobject php_reflect, jmethodID lastEx, jlong result);
  void (*CreateObject)(proxyenv *env, jobject php_reflect, jmethodID invoke, jstring classname, jobjectArray array, jlong result);
  void (*Invoke)(proxyenv *env, jobject php_reflect, jmethodID invoke, jobject obj, jstring method, jobjectArray array, jlong result);
  void (*GetSetProp)(proxyenv *env, jobject php_reflect, jmethodID gsp, jobject obj, jstring propName, jobjectArray value, jlong result);

  jobject (*AllocObject) (proxyenv *env, jclass clazz);
  jobject (*CallObjectMethod) (short count, proxyenv *env, jobject obj, jmethodID methodID, ...);
  void (*CallVoidMethod) (short count, proxyenv *env, jobject obj, jmethodID methodID, ...);
  void (*DeleteGlobalRef) (proxyenv *env, jobject gref);
  void (*DeleteLocalRef) (proxyenv *env, jobject obj);
  void (*ExceptionClear) (proxyenv *env);
  jthrowable (*ExceptionOccurred) (proxyenv *env);
  jclass (*FindClass) (proxyenv *env, const char *name);
  jsize (*GetArrayLength) (proxyenv *env, jarray array);
  jbyte *(*GetByteArrayElements) (proxyenv *env, jbyteArray array, jboolean *isCopy);
  jmethodID (*GetMethodID) (proxyenv *env, jclass clazz, const char *name, const char *sig);
  jclass (*GetObjectClass) (proxyenv *env, jobject obj);
  const char* (*GetStringUTFChars) (proxyenv *env, jstring str, jboolean *isCopy);
  jbyteArray (*NewByteArray) (proxyenv *env, jsize len);
  jobject (*NewGlobalRef) (proxyenv *env, jobject lobj);
  jobject (*NewObject) (short count, proxyenv *env, jclass clazz, jmethodID methodID, ...);
  jobjectArray (*NewObjectArray) (proxyenv *env, jsize len, jclass clazz, jobject init);
  jstring (*NewStringUTF) (proxyenv *env, const char *utf);
  void (*ReleaseByteArrayElements) (proxyenv *env, jbyteArray array, jbyte *elems, jint mode);
  void (*ReleaseStringUTFChars) (proxyenv *env, jstring array, const char*elems);
  void (*SetByteArrayRegion) (proxyenv *env, jbyteArray array, jsize start, jsize len, jbyte *buf);
  void (*SetObjectArrayElement) (proxyenv *env, jobjectArray array, jsize index, jobject val);
  int (*handle_request)(proxyenv *env);
};
extern proxyenv *java_createSecureEnvironment(FILE *peer, int (*handle_request)(proxyenv *env));
#endif
