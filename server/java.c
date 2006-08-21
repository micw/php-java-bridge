/*-*- mode: C; tab-width:4 -*-*/

/*
  Copyright (C) 2003, 2006 Jost Boekemeier

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
  exception statement from your version. 
*/

#include <stdlib.h>
#include <stdio.h>

/* wrapper for the GNU java library */
void usage(char*name){
  fprintf(stderr, "Usage: %s <socketname> <logLevel> <logFile>\n", name);
  fprintf(stderr, "Usage: %s --convert <dirname> <libs>\n", name);
  fprintf(stderr, "Example 1: %s /var/run/.php-java-bridge_socket 1 /var/log/php-java-bridge.log\n", name);
  fprintf(stderr, "Example 2: %s INET:9267 3 \"\" \n", name);
  exit(1);
}
int main(int argc,char**argv){
  extern void java_bridge_main_gcj(int argc, char**argv);

  if(argc<4) usage(argv[0]);

  java_bridge_main_gcj(argc,argv);
  return 0;
}
