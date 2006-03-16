/*-*- mode: C; tab-width:4 -*-*/

/* execve,select */
#ifndef __MINGW32__
#ifdef HAVE_SYS_TIME_H
#include <sys/time.h>
#endif
#ifdef HAVE_SYS_SELECT_H
#include <sys/select.h>
#endif
#endif
#include <sys/types.h>
#include <unistd.h>

/* stat */
#include <sys/stat.h>

/* fcntl */
#include <fcntl.h>

/* strings */
#include <string.h>
/* setenv */
#include <stdlib.h>

/* signal */
#include <signal.h>

/* poll */
#include <sys/poll.h>

/* wait */
#include <sys/wait.h>

/* miscellaneous */
#include <stdio.h>
#include <errno.h>
#include <time.h>

/* path and dir separators */
#include "php_wrapper.h"
#include "zend.h"

#include "php_java.h"
#include "java_bridge.h"

#ifndef EXTENSION_DIR
#error EXTENSION_DIR must point to the PHP extension directory
#endif

const static char inet_socket_prefix[]="INET_LOCAL:";
const static char local_socket_prefix[]="LOCAL:";
const static char ext_dir[] = "extension_dir";

EXT_EXTERN_MODULE_GLOBALS(EXT)

/* Windows can handle slashes as well as backslashes so use / everywhere */
static const char separator = '/';
static const char path_separator[2] = {ZEND_PATHS_SEPARATOR, 0};
#if EXTENSION == JAVA
static void EXT_GLOBAL(get_server_args)(char*env[N_SENV], char*args[N_SARGS], short for_display TSRMLS_DC) {
  char *s, *p;
  char*program=EXT_GLOBAL(cfg)->vm;
  char*cp=EXT_GLOBAL(cfg)->classpath;
  char*lib_path=EXT_GLOBAL(cfg)->ld_library_path;
  char*sys_libpath=getenv("LD_LIBRARY_PATH");
  char*home=EXT_GLOBAL(cfg)->vm_home;
  char *ext = php_ini_string((char*)ext_dir, sizeof ext_dir, 0);
#ifdef CFG_JAVA_SOCKET_INET
  const char* s_prefix = inet_socket_prefix;
#else
  const char *s_prefix = local_socket_prefix;
#endif
  char *sockname, *cfg_sockname=EXT_GLOBAL(get_sockname)(TSRMLS_C), *cfg_logFile=EXT_GLOBAL(cfg)->logFile;

  /* if socketname is off, show the user how to start a TCP backend */
  if(for_display && !(EXT_GLOBAL(option_set_by_user) (U_SOCKNAME, EXT_GLOBAL(ini_user)))) {
	cfg_sockname="0";
	s_prefix=inet_socket_prefix;
	cfg_logFile="";
  }
  /* send a prefix so that the server does not select a different
   protocol */
  sockname = malloc(strlen(s_prefix)+strlen(cfg_sockname)+1);
  strcpy(sockname, s_prefix);
  strcat(sockname, cfg_sockname);

								/* library path usually points to the
								   extension dir */
  if(!(EXT_GLOBAL(option_set_by_user) (U_LIBRARY_PATH, EXT_GLOBAL(ini_user)))) {	/* look into extension_dir then */
	if(ext) lib_path = ext;
  }
  if(!*program) {				/* look into extension_dir then */
	static const char java[] = "/java";
	if(ext) {
	  program = malloc(strlen(ext)+sizeof(java)); assert(program); if(!program) exit(6);
	  strcpy(program, ext); strcat(program, java);
	} else {
	  program = strdup(program);
	}
  } else {
	program = strdup(program);
  }
  
  if(!sys_libpath) sys_libpath="";
  args[0]=program;
  p=strdup("-Dphp.java.bridge.default_log_level=2");
  args[1] = p;					/* default log level */
  s="-Djava.class.path=";
								/* library path usually points to the
								   extension dir */
  if(ext && !(EXT_GLOBAL(option_set_by_user) (U_CLASSPATH, EXT_GLOBAL(ini_user)))) {	/* look into extension_dir then */
	static char bridge[]="/JavaBridge.jar";
	char *slash;
	p=malloc(strlen(s)+strlen(ext)+sizeof(bridge));
	strcpy(p, s); strcat(p, ext); 
	slash=p+strlen(p)-1;
	if(*p&&*slash==separator) *slash=0;
	strcat(p, bridge);
  } else {
	p=malloc(strlen(s)+strlen(cp)+1);
	strcpy(p, s); strcat(p, cp);
  }
  args[2] = p;					/* user classes */
  args[3] = strdup("-Djava.awt.headless=true");
  args[4] = strdup("php.java.bridge.JavaBridge");

  args[5] = sockname;
  args[6] = strdup(EXT_GLOBAL(cfg)->logLevel);
  args[7] = strdup(cfg_logFile);
  args[8] = NULL;

  if(*home) {					/* set VM home */
	s="JAVA_HOME=";
	p=malloc(strlen(s)+strlen(home)+1);
	strcpy(p, s); strcat(p, home);
	env[0] = p;
  } else {						/* VM in PATH; don't set java home */
	env[0] = strdup("");
  }

  s="LD_LIBRARY_PATH=";
  p=malloc(strlen(s)+strlen(lib_path)+1+strlen(sys_libpath)+1);
  strcpy(p, s); strcat(p, lib_path); 
  strcat(p, path_separator); strcat(p, sys_libpath);
  env[1] = p;					/* library path */
  env[2] = NULL;
}
#elif EXTENSION == MONO
static void EXT_GLOBAL(get_server_args)(char*env[N_SENV], char*args[N_SARGS], short for_display TSRMLS_DC) {
  static const char executable[] = "/MonoBridge.exe";
  char *p, *slash;
  char*program=EXT_GLOBAL(cfg)->vm;
#ifdef CFG_JAVA_SOCKET_INET
  const char* s_prefix = inet_socket_prefix;
#else
  const char *s_prefix = local_socket_prefix;
#endif
  char *sockname, *cfg_sockname=EXT_GLOBAL(get_sockname)(TSRMLS_C), *cfg_logFile=EXT_GLOBAL(cfg)->logFile;
  char*home = EXT_GLOBAL(cfg)->vm_home;
  if(!(EXT_GLOBAL(option_set_by_user) (U_JAVA_HOME, EXT_GLOBAL(ini_user)))) {	/* look into extension_dir then */
	char *ext = php_ini_string((char*)ext_dir, sizeof ext_dir, 0);
	if(ext) home = ext;
  }
  args[0]=strdup(program);		/* mono */
  p=malloc(strlen(home)+sizeof executable);
  strcpy(p, home); 
  slash=p+strlen(p)-1;
  if(*p&&*slash==separator) *slash=0;
  strcat(p, executable);

  args[1] = p;
  /* if socketname is off, show the user how to start a TCP backend */
  if(for_display && !(EXT_GLOBAL(option_set_by_user) (U_SOCKNAME, EXT_GLOBAL(ini_user)))) {
	cfg_sockname="0";
	s_prefix=inet_socket_prefix;
	cfg_logFile="";
  }
  /* send a prefix so that the server does not select a different */
  /* channel */
  sockname = malloc(strlen(s_prefix)+strlen(cfg_sockname)+1);
  strcpy(sockname, s_prefix);
  strcat(sockname, cfg_sockname);
  args[2] = sockname;
  args[3] = strdup(EXT_GLOBAL(cfg)->logLevel);
  args[4] = strdup(cfg_logFile);
  args[5] = NULL;
  env[0] = NULL;
}
#endif
static short use_wrapper(char*wrapper) {
  struct stat buf;
  short use_wrapper=(EXT_GLOBAL(option_set_by_user) (U_WRAPPER, EXT_GLOBAL(ini_user)));
  if(use_wrapper) return use_wrapper;

#ifndef __MINGW32__
  if(!stat(wrapper, &buf) && (S_IFREG&buf.st_mode)) {
	if(getuid()==buf.st_uid)
	  use_wrapper=(S_IXUSR&buf.st_mode);
	else if(getgid()==buf.st_gid)
	  use_wrapper=(S_IXGRP&buf.st_mode);
	else 
	  use_wrapper=(S_IXOTH&buf.st_mode);
  }
#endif

  return use_wrapper;
}
  
/*
 * Get a string of the server arguments. Useful for display only.
 */
static char*get_server_string(short for_display TSRMLS_DC) {
  short must_use_wrapper = use_wrapper(EXT_GLOBAL(cfg)->wrapper);
  int i;
  char*s;
  char*env[N_SENV];
  char*args[N_SARGS];
  unsigned int length = 0;

  EXT_GLOBAL(get_server_args)(env, args, for_display TSRMLS_CC);
  if(must_use_wrapper)
	length+=strlen(EXT_GLOBAL(cfg)->wrapper)+1;
#ifndef __MINGW32__
  for(i=0; i< (sizeof env)/(sizeof*env); i++) {
	if(!env[i]) break;
	length+=strlen(env[i])+1;
  }
#endif
  for(i=0; i< (sizeof args)/(sizeof*args); i++) {
	size_t l;
	if(!args[i]) break;
	l=strlen(args[i]);
	length+=(l?l:2)+1;
  }
  s=malloc(length+1);
  assert(s); if(!s) exit(9);

  *s=0;
#ifndef __MINGW32__
  for(i=0; i< (sizeof env)/(sizeof*env); i++) {
	if(!env[i]) break;
	strcat(s, env[i]); strcat(s, " ");
	free(env[i]);
  }
#endif
  if(must_use_wrapper) {
	strcat(s, EXT_GLOBAL(cfg)->wrapper);
	strcat(s, " ");
  }
  for(i=0; i< (sizeof args)/(sizeof*args); i++) {
	if(!args[i]) break;
	strcat(s, args[i]);
	strcat(s, " ");
	free(args[i]);
  }
  s[length]=0;
  return s;
}
char*EXT_GLOBAL(get_server_string)(TSRMLS_D) {
  return get_server_string(1 TSRMLS_CC);
}

static void exec_vm(TSRMLS_D) {
#ifdef CFG_JAVA_INPROCESS
  extern int EXT_GLOBAL(bridge_main)(int argc, char**argv) ;
  static char*env[N_SENV];
  static char*args[N_SARGS];
  EXT_GLOBAL(get_server_args)(env, args, 0);
  if(N_SENV>2) {
	if(*env[0]) putenv(env[0]);
	if(*env[1]) putenv(env[1]);
  }
  EXT_GLOBAL(bridge_main)(N_SARGS, args);
#else
  static char*env[N_SENV];
  static char*_args[N_SARGS+1];
  char **args=_args+1, *cmd;
  EXT_GLOBAL(get_server_args)(env, args, 0 TSRMLS_CC);
  if(N_SENV>2) {
	if(*env[0]) putenv(env[0]);
	if(*env[1]) putenv(env[1]);
  }
  if(use_wrapper(EXT_GLOBAL(cfg)->wrapper)) {
	*--args = strdup(EXT_GLOBAL(cfg)->wrapper);
	execv(args[0], args);
  }
  if(*args[0]=='/') execv(args[0], args); else execvp(args[0], args);

  /* exec failed */
  cmd = get_server_string(0 TSRMLS_CC);
  php_error(E_WARNING, "php_mod_"/**/EXT_NAME()/**/"(%d) system error: Could not execute backend: %s: %s", 105, cmd, strerror(errno));
  free(cmd);
#endif
}


static int test_local_server(void) {
  int sock, n;
#ifndef CFG_JAVA_SOCKET_INET
  sock = socket (PF_LOCAL, SOCK_STREAM, 0);
#else
  sock = socket (PF_INET, SOCK_STREAM, 0);
#endif
  if(sock==-1) return -1;
  n = connect(sock,(struct sockaddr*)&EXT_GLOBAL(cfg)->saddr, sizeof EXT_GLOBAL(cfg)->saddr);
  if(n==-1) { close(sock); return -1; }
  return sock;
}

/*
  return 0 if user has hard-coded the socketname
*/
static short can_fork(void) {
  return EXT_GLOBAL(cfg)->can_fork;
}

/*
 * Test for a running server.  Return the server name and the socket
 * if _socket!=NULL. If all ckecks fail a local backend is started.
 */
char* EXT_GLOBAL(test_server)(int *_socket, short *local, struct sockaddr*_saddr TSRMLS_DC) {
  int sock;
  short called_from_init = !(local || _socket);

								/* java.servlet=On forces
								   java.socketname Off */
  short socketname_set = EXT_GLOBAL(cfg)->socketname_set &&
	EXT_GLOBAL(option_set_by_user) (U_SOCKNAME, JG(ini_user)) &&
	!(EXT_GLOBAL(option_set_by_user) (U_SERVLET, JG(ini_user)));

  if(local) *local=0;
  /* check for local server if socketname set or (socketname not set
	 and hosts not set), in which case we may have started a local
	 backend ourselfs. Do not check if socketname not set and we are
	 called from init, in which case we know that a local backend is
	 not running. */
  if (((socketname_set || can_fork()) && (socketname_set || !called_from_init))
	  && -1!=(sock=test_local_server()) ) {
	if(_socket) {
	  *_socket=sock;
	} else {
	  close(sock);
	}
	if(local) *local=1;
	return strdup(EXT_GLOBAL(get_sockname)(TSRMLS_C));
  }

  /* host list */
  if(JG(hosts) && *(JG(hosts))) {
	char *host, *hosts = strdup(JG(hosts));
	
	assert(hosts); if(!hosts) return 0;
	for(host=strtok(hosts, "; "); host; host=strtok(0, "; ")) {
	  struct sockaddr_in saddr;
	  char *_port = strrchr(host, ':'), *ret;
	  int port = 0;
	  
	  if(_port) { 
		*_port++=0;
		if(strlen(_port)) port=atoi(_port);
	  }
	  if(!port) port=atoi(DEFAULT_PORT);
	  memset(&saddr, 0, sizeof saddr);
	  saddr.sin_family = AF_INET;
	  saddr.sin_port=htons(port);
#ifndef __MINGW32__
	  if(!isdigit(*host)) {
		struct hostent *hostent = gethostbyname(host);
		if(hostent) {
		  memcpy(&saddr.sin_addr,hostent->h_addr,sizeof(struct in_addr));
		} else {
		  inet_aton(host, &saddr.sin_addr);
		}
	  } else {
		inet_aton(host, &saddr.sin_addr);
	  }
#else
	  saddr.sin_addr.s_addr = inet_addr(host);
#endif

	  sock = socket (PF_INET, SOCK_STREAM, 0);
	  if(-1==sock) continue;
	  if (-1==connect(sock,(struct sockaddr*)&saddr, sizeof (struct sockaddr))) {
		close(sock);
		continue;
	  }
	  if(_socket) *_socket=sock;
	  else close(sock);
	  if(_port) _port[-1]=':';
	  ret = strdup(host);
	  free(hosts);
	  if(_saddr) memcpy(_saddr, &saddr, sizeof (struct sockaddr));
	  if(EXT_GLOBAL(cfg)->socketname_set)
		EXT_GLOBAL(cfg)->socketname_set = 0;
	  return ret;
	}
	free(hosts);
  }

  socketname_set = EXT_GLOBAL(option_set_by_user) (U_SOCKNAME, JG(ini_user)) ;
  if (((socketname_set || can_fork()) && (socketname_set || !called_from_init))
	  && -1!=(sock=test_local_server()) ) {
	if(_socket) {
	  *_socket=sock;
	} else {
	  close(sock);
	}
	if(local) *local=1;
	if(!EXT_GLOBAL(cfg)->socketname_set)
	  EXT_GLOBAL(cfg)->socketname_set = 1;
	return strdup(EXT_GLOBAL(get_sockname)(TSRMLS_C));
  }

  return 0;
}
static const long timeout = 50000l; /* ys */
static void sleep_ms() {
  struct timeval timeval = {0l, timeout};
  select(0, 0, 0, 0, &timeval);
}
static int wait_server(void) {
  static const int wait_count = 30;
  int count=wait_count, sock;
#ifndef __MINGW32__
  struct pollfd pollfd[1] = {{EXT_GLOBAL(cfg)->err, POLLIN, 0}};
  
  /* wait for the server that has just started */
  while(EXT_GLOBAL(cfg)->cid && -1==(sock=test_local_server()) && --count) {
	if(EXT_GLOBAL(cfg)->err && poll(pollfd, 1, 0)) 
	  return FAILURE; /* server terminated with error code */
	sleep_ms();
  }
  count=10;
  while(EXT_GLOBAL(cfg)->cid && -1==sock && -1==(sock=test_local_server()) && --count) {
	if(EXT_GLOBAL(cfg)->err && poll(pollfd, 1, 0)) 
	  return FAILURE; /* server terminated with error code */
	php_error(E_NOTICE, "php_mod_"/**/EXT_NAME()/**/"(%d): waiting for server another %d seconds",57, count);
	sleep(1);
  }
#else
  while(EXT_GLOBAL(cfg)->cid && -1==(sock=test_local_server()) && --count) {
	Sleep(timeout/1000);
  }
  count=10;
  while(EXT_GLOBAL(cfg)->cid && -1==sock && -1==(sock=test_local_server()) && --count) {
	php_error(E_NOTICE, "php_mod_"/**/EXT_NAME()/**/"(%d): waiting for server another %d interval",57, count);
	Sleep(1000);
  }
#endif
  if(EXT_GLOBAL(cfg)->cid && count) {
	close(sock);
	return SUCCESS;
  } else {
	return FAILURE;
  }
}


/* handle keyboard interrupt */
#ifndef __MINGW32__
static int s_pid=0;
static void s_kill(int sig) {
  if(s_pid) kill(s_pid, SIGTERM);
}
#else
#ifndef _WIN32_WINNT
# define _WIN32_WINNT 0x500
#endif

#include <windows.h>
#include <tchar.h>
#include <stdarg.h>
#include <tlhelp32.h>

static struct s_pid {
  short use_wrapper;
  PROCESS_INFORMATION p;
} s_pid;



/**
 * Unix kill emulation for windows.
 * From http://www.rsdn.ru/?qna/?baseserv/killproc.xml
 */
static BOOL WINAPI KillProcess(IN DWORD dwProcessId)
{
  HANDLE hProcess;
  DWORD dwError;

  // first try to obtain handle to the process without the use of any
  // additional privileges
  hProcess = OpenProcess(PROCESS_TERMINATE, FALSE, dwProcessId);
  if (hProcess == NULL)
    {
      if (GetLastError() != ERROR_ACCESS_DENIED)
		return FALSE;

      OSVERSIONINFO osvi;

      // determine operating system version
      osvi.dwOSVersionInfoSize = sizeof(osvi);
      GetVersionEx(&osvi);

      // we cannot do anything else if this is not Windows NT
      if (osvi.dwPlatformId != VER_PLATFORM_WIN32_NT)
		return FALSE;

      // enable SE_DEBUG_NAME privilege and try again

      TOKEN_PRIVILEGES Priv, PrivOld;
      DWORD cbPriv = sizeof(PrivOld);
      HANDLE hToken;

      // obtain the token of the current thread 
      if (!OpenThreadToken(GetCurrentThread(), 
						   TOKEN_QUERY|TOKEN_ADJUST_PRIVILEGES,
						   FALSE, &hToken))
		{
		  if (GetLastError() != ERROR_NO_TOKEN)
			return FALSE;

		  // revert to the process token
		  if (!OpenProcessToken(GetCurrentProcess(),
								TOKEN_QUERY|TOKEN_ADJUST_PRIVILEGES,
								&hToken))
			return FALSE;
		}

      assert(ANYSIZE_ARRAY > 0);

      Priv.PrivilegeCount = 1;
      Priv.Privileges[0].Attributes = SE_PRIVILEGE_ENABLED;
      LookupPrivilegeValue(NULL, SE_DEBUG_NAME, &Priv.Privileges[0].Luid);

      // try to enable the privilege
      if (!AdjustTokenPrivileges(hToken, FALSE, &Priv, sizeof(Priv),
								 &PrivOld, &cbPriv))
		{
		  dwError = GetLastError();
		  CloseHandle(hToken);
		  return FALSE;
		}

      if (GetLastError() == ERROR_NOT_ALL_ASSIGNED)
		{
		  // the SE_DEBUG_NAME privilege is not present in the caller's
		  // token
		  CloseHandle(hToken);
		  return FALSE;
		}

      // try to open process handle again
      hProcess = OpenProcess(PROCESS_TERMINATE, FALSE, dwProcessId);
      dwError = GetLastError();
		
      // restore the original state of the privilege
      AdjustTokenPrivileges(hToken, FALSE, &PrivOld, sizeof(PrivOld),
							NULL, NULL);
      CloseHandle(hToken);

      if (hProcess == NULL)
		return FALSE;
    }

  // terminate the process
  if (!TerminateProcess(hProcess, (UINT)-1))
    {
      dwError = GetLastError();
      CloseHandle(hProcess);
      return FALSE;
    }

  CloseHandle(hProcess);

  // completed successfully
  return TRUE;
}

typedef LONG	NTSTATUS;
typedef LONG	KPRIORITY;

#define NT_SUCCESS(Status) ((NTSTATUS)(Status) >= 0)

#define STATUS_INFO_LENGTH_MISMATCH      ((NTSTATUS)0xC0000004L)

#define SystemProcessesAndThreadsInformation	5

typedef struct _CLIENT_ID {
  DWORD	    UniqueProcess;
  DWORD	    UniqueThread;
} CLIENT_ID;

typedef struct _UNICODE_STRING {
  USHORT	    Length;
  USHORT	    MaximumLength;
  PWSTR	    Buffer;
} UNICODE_STRING;

typedef struct _VM_COUNTERS {
  SIZE_T	    PeakVirtualSize;
  SIZE_T	    VirtualSize;
  ULONG	    PageFaultCount;
  SIZE_T	    PeakWorkingSetSize;
  SIZE_T	    WorkingSetSize;
  SIZE_T	    QuotaPeakPagedPoolUsage;
  SIZE_T	    QuotaPagedPoolUsage;
  SIZE_T	    QuotaPeakNonPagedPoolUsage;
  SIZE_T	    QuotaNonPagedPoolUsage;
  SIZE_T	    PagefileUsage;
  SIZE_T	    PeakPagefileUsage;
} VM_COUNTERS;

typedef struct _SYSTEM_THREADS {
  LARGE_INTEGER   KernelTime;
  LARGE_INTEGER   UserTime;
  LARGE_INTEGER   CreateTime;
  ULONG			WaitTime;
  PVOID			StartAddress;
  CLIENT_ID	    ClientId;
  KPRIORITY	    Priority;
  KPRIORITY	    BasePriority;
  ULONG			ContextSwitchCount;
  LONG			State;
  LONG			WaitReason;
} SYSTEM_THREADS, * PSYSTEM_THREADS;

// Note that the size of the SYSTEM_PROCESSES structure is different on
// NT 4 and Win2K, but we don't care about it, since we don't access neither
// IoCounters member nor Threads array

typedef struct _SYSTEM_PROCESSES {
  ULONG			NextEntryDelta;
  ULONG			ThreadCount;
  ULONG			Reserved1[6];
  LARGE_INTEGER   CreateTime;
  LARGE_INTEGER   UserTime;
  LARGE_INTEGER   KernelTime;
  UNICODE_STRING  ProcessName;
  KPRIORITY	    BasePriority;
  ULONG			ProcessId;
  ULONG			InheritedFromProcessId;
  ULONG			HandleCount;
  ULONG			Reserved2[2];
  VM_COUNTERS	    VmCounters;
#if _WIN32_WINNT >= 0x500
  IO_COUNTERS	    IoCounters;
#endif
  SYSTEM_THREADS  Threads[1];
} SYSTEM_PROCESSES, * PSYSTEM_PROCESSES;

static BOOL WINAPI KillProcessTreeNtHelper(IN PSYSTEM_PROCESSES pInfo, IN DWORD dwProcessId)
{
  assert(pInfo != NULL);

  PSYSTEM_PROCESSES p = pInfo;

  // kill all children first
  for (;;)
    {
      if (p->InheritedFromProcessId == dwProcessId)
		KillProcessTreeNtHelper(pInfo, p->ProcessId);

      if (p->NextEntryDelta == 0)
		break;

      // find the address of the next process structure
      p = (PSYSTEM_PROCESSES)(((LPBYTE)p) + p->NextEntryDelta);
    }

  // kill the process itself
  if (!KillProcess(dwProcessId))
    return GetLastError();

  return ERROR_SUCCESS;
}

static BOOL WINAPI KillProcessTreeWinHelper(IN DWORD dwProcessId)
{
  HINSTANCE hKernel;
  HANDLE (WINAPI * _CreateToolhelp32Snapshot)(DWORD, DWORD);
  BOOL (WINAPI * _Process32First)(HANDLE, PROCESSENTRY32 *);
  BOOL (WINAPI * _Process32Next)(HANDLE, PROCESSENTRY32 *);

  // get handle to KERNEL32.DLL
  hKernel = GetModuleHandle(_T("kernel32.dll"));
  assert(hKernel != NULL);

  // locate necessary functions in KERNEL32.DLL
  *(FARPROC *)&_CreateToolhelp32Snapshot =
    GetProcAddress(hKernel, "CreateToolhelp32Snapshot");
  *(FARPROC *)&_Process32First =
    GetProcAddress(hKernel, "Process32First");
  *(FARPROC *)&_Process32Next =
    GetProcAddress(hKernel, "Process32Next");

  if (_CreateToolhelp32Snapshot == NULL ||
      _Process32First == NULL ||
      _Process32Next == NULL)
    return ERROR_PROC_NOT_FOUND;

  HANDLE hSnapshot;
  PROCESSENTRY32 Entry;

  // create a snapshot
  hSnapshot = _CreateToolhelp32Snapshot(TH32CS_SNAPPROCESS, 0);
  if (hSnapshot == INVALID_HANDLE_VALUE)
    return GetLastError();

  Entry.dwSize = sizeof(Entry);
  if (!_Process32First(hSnapshot, &Entry))
    {
      DWORD dwError = GetLastError();
      CloseHandle(hSnapshot);
      return dwError;
    }

  // kill all children first
  do
    {
      if (Entry.th32ParentProcessID == dwProcessId)
		KillProcessTreeWinHelper(Entry.th32ProcessID);

      Entry.dwSize = sizeof(Entry);
    }
  while (_Process32Next(hSnapshot, &Entry));

  CloseHandle(hSnapshot);

  // kill the process itself
  if (!KillProcess(dwProcessId))
    return GetLastError();

  return ERROR_SUCCESS;
}

static BOOL WINAPI KillProcessEx(IN DWORD dwProcessId, IN BOOL bTree)
{
  if (!bTree)
    return KillProcess(dwProcessId);

  OSVERSIONINFO osvi;
  DWORD dwError;

  // determine operating system version
  osvi.dwOSVersionInfoSize = sizeof(osvi);
  GetVersionEx(&osvi);

  if (osvi.dwPlatformId == VER_PLATFORM_WIN32_NT &&
      osvi.dwMajorVersion < 5)
    {
      HINSTANCE hNtDll;
      NTSTATUS (WINAPI * _ZwQuerySystemInformation)(UINT, PVOID, ULONG, PULONG);

      // get handle to NTDLL.DLL
      hNtDll = GetModuleHandle(_T("ntdll.dll"));
      assert(hNtDll != NULL);

      // find the address of ZwQuerySystemInformation
      *(FARPROC *)&_ZwQuerySystemInformation =
		GetProcAddress(hNtDll, "ZwQuerySystemInformation");
      if (_ZwQuerySystemInformation == NULL)
		return FALSE;

      // obtain a handle to the default process heap
      HANDLE hHeap = GetProcessHeap();
    
      NTSTATUS Status;
      ULONG cbBuffer = 0x8000;
      PVOID pBuffer = NULL;

      // it is difficult to say a priory which size of the buffer 
      // will be enough to retrieve all information, so we start
      // with 32K buffer and increase its size until we get the
      // information successfully
      do
		{
		  pBuffer = HeapAlloc(hHeap, 0, cbBuffer);
		  if (pBuffer == NULL)
			return FALSE;

		  Status = _ZwQuerySystemInformation(
											 SystemProcessesAndThreadsInformation,
											 pBuffer, cbBuffer, NULL);

		  if (Status == STATUS_INFO_LENGTH_MISMATCH)
			{
			  HeapFree(hHeap, 0, pBuffer);
			  cbBuffer *= 2;
			}
		  else if (!NT_SUCCESS(Status))
			{
			  HeapFree(hHeap, 0, pBuffer);
			  return FALSE;
			}
		}
      while (Status == STATUS_INFO_LENGTH_MISMATCH);

      // call the helper function
      dwError = KillProcessTreeNtHelper((PSYSTEM_PROCESSES)pBuffer, 
										dwProcessId);
		
      HeapFree(hHeap, 0, pBuffer);
    }
  else
    {
      // call the helper function
      dwError = KillProcessTreeWinHelper(dwProcessId);
    }

  return dwError == ERROR_SUCCESS;
}

static void s_kill(int sig) {
  if(!s_pid.use_wrapper) {
								/* we can kill our child directly */
	if(s_pid.p.hProcess) TerminateProcess(s_pid.p.hProcess, 1);
  }
  else {
								/* emulate unix kill behaviour */
	if(s_pid.p.dwProcessId) KillProcessEx(s_pid.p.dwProcessId, 1);
  }
}



#endif

void EXT_GLOBAL(start_server)(TSRMLS_D) {
  int pid=0, err=0, p[2];
  char *test_server = 0;
#ifndef __MINGW32__
  if(can_fork() && !(test_server=EXT_GLOBAL(test_server)(0, 0, 0 TSRMLS_CC)) && pipe(p)!=-1) {
	if(!(pid=fork())) {		/* daemon */
	  close(p[0]);
	  if(!fork()) {			/* guard */
		setsid();
		if(!(pid=fork())) {	/* java */
		  close(p[1]);
		  exec_vm(TSRMLS_C); 
		  exit(105);
		}
		/* protect guard */
		signal(SIGHUP, SIG_IGN); 
		s_pid=pid; signal(SIGINT, s_kill); 
		signal(SIGTERM, SIG_IGN);
		
		write(p[1], &pid, sizeof pid);
		waitpid(pid, &err, 0);
		write(p[1], &err, sizeof err);
		exit(0);
	  } 
	  exit(0);
	}
	close(p[1]);
	wait(&err);
	if((read(p[0], &pid, sizeof pid))!=(sizeof pid)) pid=0;
	
	EXT_GLOBAL(cfg)->cid=pid;
	EXT_GLOBAL(cfg)->err=p[0];
	wait_server();
  } else 
#else
	if(can_fork() && !(test_server=EXT_GLOBAL(test_server)(0, 0, 0 TSRMLS_CC))) {
	  char *cmd = get_server_string(0 TSRMLS_CC);
	  DWORD properties = CREATE_NEW_CONSOLE;
	  STARTUPINFO su_info;
	  s_pid.use_wrapper = use_wrapper(EXT_GLOBAL(cfg)->wrapper);

	  ZeroMemory(&su_info, sizeof(STARTUPINFO));
	  su_info.cb = sizeof(STARTUPINFO);
	  EXT_GLOBAL(cfg)->cid=0;
	  if(CreateProcess(NULL, cmd, 
					   NULL, NULL, 1, properties, NULL, NULL, 
					   &su_info, &s_pid.p)) {
		EXT_GLOBAL(cfg)->cid=s_pid.p.dwProcessId;
		wait_server();
	  } else {
		php_error(E_WARNING, "php_mod_"/**/EXT_NAME()/**/"(%d) system error: Could not start backend: %s; Code: %ld.", 105, cmd, (long)GetLastError());
	  }
	  free(cmd);
	} else
#endif /* MINGW32 */
	  {
		EXT_GLOBAL(cfg)->cid=EXT_GLOBAL(cfg)->err=0;
	  }
  if(test_server) free(test_server);
}

static void wait_for_daemon(void) {
#ifndef __MINGW32__
  const static sig[] = {SIGTERM, SIGKILL};
  fd_set rfds;
  int err, c, i;

  assert(EXT_GLOBAL(cfg)->err); if(!(EXT_GLOBAL(cfg)->err)) return;
  assert(EXT_GLOBAL(cfg)->cid);

  /* first kill is trapped, second kill is received with default
	 handler. If the server still exists, we send it a -9 */
  kill(EXT_GLOBAL(cfg)->cid, SIGTERM);
  FD_ZERO(&rfds);
  FD_SET(EXT_GLOBAL(cfg)->err, &rfds);
  for(i=0; i<2; i++) {
	struct timeval timeval = {2l, 0};
	if(select(1+EXT_GLOBAL(cfg)->err, &rfds, 0, 0, &timeval) > 0) break;
	kill(EXT_GLOBAL(cfg)->cid, sig[i]);
  }	

  if((read(EXT_GLOBAL(cfg)->err, &err, sizeof err))!=sizeof err) err=0;
  //printf("VM terminated with code: %ld\n", err);
  close(EXT_GLOBAL(cfg)->err);
  EXT_GLOBAL(cfg)->err=0;
#else
  s_kill(0);					/* always -9 on windows */
#endif
}

void EXT_GLOBAL(shutdown_library)() 
{
  if(EXT_GLOBAL(cfg)->cid) wait_for_daemon();
}

void EXT_GLOBAL(sys_error)(const char *str, int code) {
#ifndef __MINGW32__
  php_error(E_ERROR, "php_mod_"/**/EXT_NAME()/**/"(%d) system error: %s. %s.", code, strerror(errno), str);
#else
  php_error(E_ERROR, "php_mod_"/**/EXT_NAME()/**/"(%d) system error code: %ld. %s.", code, (long)GetLastError(), str);
#endif
}

#ifndef PHP_WRAPPER_H
#error must include php_wrapper.h
#endif
