/*-*- mode: C; tab-width:4 -*-*/

/** protocol.h -- implementation of the PHP/Java Bridge XML protocol.

  Copyright (C) 2003-2007 Jost Boekemeier

  This file is part of the PHP/Java Bridge.

  The PHP/Java Bridge ("the library") is free software; you can
  redistribute it and/or modify it under the terms of the GNU General
  Public License as published by the Free Software Foundation; either
  version 2, or (at your option) any later version.

  The library is distributed in the hope that it will be useful, but
  WITHOUT ANY WARRANTY; without even the implied warranty of
  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
  General Public License for more details.

  You should have received a copy of the GNU General Public License
  along with the PHP/Java Bridge; see the file COPYING.  If not, write to the
  Free Software Foundation, Inc., 59 Temple Place, Suite 330, Boston, MA
  02111-1307 USA.

  Linking this file statically or dynamically with other modules is
  making a combined work based on this library.  Thus, the terms and
  conditions of the GNU General Public License cover the whole
  combination.

  As a special exception, the copyright holders of this library give you
  permission to link this library with independent modules to produce an
  executable, regardless of the license terms of these independent
  modules, and to copy and distribute the resulting executable under
  terms of your choice, provided that you also meet, for each linked
  independent module, the terms and conditions of the license of that
  module.  An independent module is a module which is not derived from
  or based on this library.  If you modify this library, you may extend
  this exception to your version of the library, but you are not
  obligated to do so.  If you do not wish to do so, delete this
  exception statement from your version. */  

#ifndef JAVA_PROTOCOL_H
#define JAVA_PROTOCOL_H

/* peer */
#include <stdio.h>
#ifdef __MINGW32__
# include <winsock2.h>
# define close closesocket
#else
# include <sys/types.h>
# include <sys/socket.h>
# include <netinet/tcp.h>
#endif

/* 
 * we create a unix domain socket with the name .php_java_bridge in
 * the tmpdir
 */
#ifndef P_tmpdir
/* xopen, normally defined in stdio.h */
#define P_tmpdir "/tmp"
#endif 
#define SOCKNAME P_tmpdir/**/"/.php_java_bridge"/**/"XXXXXX"
/* Linux: pipes created in the shared memory */
#define SOCKNAME_SHM "/dev/shm/.php_java_bridge"/**/"XXXXXX"

/* 
 * Max. number of bytes in a context ID, should be > POSIX_PATH_MAX 
 */
#define CONTEXT_LEN_MAX 512


/*
 * default log file is System.out
 */
#define LOGFILE ""

#define LOG_OFF 0
#define LOG_FATAL 1  /* default level */
#define LOG_ERROR 2
#define LOG_INFO 3 
#define LOG_DEBUG 4
#define DEFAULT_LEVEL "2"

#define N_JAVA_SARGS 10
#define N_JAVA_SENV 3 
#define N_MONO_SARGS 6
#define N_MONO_SENV 1
#define DEFAULT_MONO_PORT "9167" /* default port for tcp/ip */
#define DEFAULT_JAVA_PORT "9267" /* default port for tcp/ip */
#define DEFAULT_HOST "127.0.0.1"
#define DEFAULT_SERVLET "/JavaBridge/JavaBridge.phpjavabridge"

#define RECV_SIZE 8192 // initial size of the receive buffer
#define MAX_ARGS 100   // max # of method arguments

#endif
