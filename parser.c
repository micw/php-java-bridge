#include <stdio.h>
#include <stdlib.h>
#include <unistd.h>
#include <errno.h>

#include "php_java.h"
#include "parser.h"

#define APPEND(c) { \
  if(i>=(*env)->len-1) { \
    unsigned char* s1=realloc(s, (*env)->len*=2); \
    if(!s1) exit(9); else s=(*env)->s=s1; \
  } \
  s[i++]=c; \
}
#define PUSH(t) { \
  parser_string_t *str = tag[t].strings; \
  short n = tag[t].n; \
  assert((t!=0) || (t==0 && !tag[0].n)); \
  s[i]=0; \
  str[n].string=&((*env)->s); \
  str[n].off=i0; \
  str[n].length=i-i0; \
  ++tag[t].n; \
  APPEND(0); \
  i0=i; \
}
#define RESET() { \
  type=VOJD;\
  level=0; \
  eor=0; \
  blen=0; \
  in_dquote=0; \
  i=0; \
  i0=0; \
  (*env)->c=0; (*env)->pos=0; \
}
#define CALL_BEGIN() { \
  if(cb->begin) (*cb->begin)(tag, cb); \
}
#define CALL_END() { \
  if(cb->end) (*cb->end)(tag[0].strings, cb); \
}

short EXT_GLOBAL (parse) (proxyenv *env, parser_cb_t *cb) {
  parser_string_t v1[1], v2[MAX_ARGS], v3[MAX_ARGS];
  parser_tag_t tag[] = {{0, v1}, {0, v2}, {0, v3}};
  unsigned char ch;
  // VOJD is VOID for f... windows (VOID is in winsock2.h)
  enum {BEGIN, KEY, VAL, ENTITY, BLOB, VOJD, END} type = VOJD;
  short level=0, in_dquote=0, eor=0, blen=0;
  register ssize_t pos=(*env)->pos, c=(*env)->c; size_t i=0, i0=0, e;
  register unsigned char *s=(*env)->s;
  assert(s); if(!s) return 1;

  while(!eor) {
    if(c==pos) { 
    res: 
      errno=0;
      pos=recv((*env)->peer, (*env)->recv_buf, sizeof (*env)->recv_buf, 0);
      if(!pos && errno==EINTR) goto res; // Solaris, see INN FAQ
      if(pos<=0) break;
      c=0; 
    }
    switch(ch=(*env)->recv_buf[c]) 
      {/* --- This block must be compilable with an ansi C compiler or javac  --- */
      case '<': if(in_dquote) {APPEND(ch); break;}
	level++;
	type=BEGIN;
	break;
      case '\t': case '\f': case '\n': case '\r': case ' ': if(in_dquote) {APPEND(ch); break;}
	if(type==BEGIN) {
	  PUSH(type); 
	  type = KEY; 
	}
	break;
      case '=': if(in_dquote) {APPEND(ch); break;}
	PUSH(type);
	type=VAL;
	break;
      case '/': if(in_dquote) {APPEND(ch); break;}
	if(type==BEGIN) { type=END; level--; }
	level--;
	break;
      case '>': if(in_dquote) {APPEND(ch); break;}
	if(type==END){
	  PUSH(BEGIN);
	  CALL_END();
	} else {
	  if(type==VAL||type==BEGIN) PUSH(type);
	  CALL_BEGIN();
	}
	tag[0].n=tag[1].n=tag[2].n=0; i0=i=0;      		/* RESET */
	type=VOJD;
	if(level==0) eor=1; 
	break;
      case ';':
	if(type==ENTITY) {
	  switch (s[e+1]) {
	  case 'l': s[e]='<'; i=e+1; break; /* lt */
	  case 'g': s[e]='>'; i=e+1; break; /* gt */
	  case 'a': s[e]= (s[e+2]=='m'?'&':'\''); i=e+1; break; /* amp, apos */
	  case 'q': s[e]='"'; i=e+1; break; /* quot */
	  default: APPEND(ch);
	  }
	  type=VAL; //& escapes may only appear in values
	} else {
	  APPEND(ch);
	}
	break;
      case '&': 
	type = ENTITY;
	e=i;
	APPEND(ch);
	break;
      case '"':
	in_dquote = !in_dquote;
	if(!in_dquote && type==VAL) {
	  PUSH(type);
	  type = KEY;
	}
	break;
      default:
	APPEND(ch);
      }
    c++;
  }
  RESET();
  return 0;
}

short EXT_GLOBAL (parse_header) (proxyenv *env, parser_cb_t *cb) {
  parser_string_t v1[1], v2[1], v3[1];
  parser_tag_t tag[] = {{0, v1}, {0, v2}, {0, v3}};
  unsigned char ch;
  // VOJD is VOID for f... windows (VOID is in winsock2.h)
  enum {BEGIN, KEY, VAL, VOJD, END} type = VOJD;
  short level=0, in_dquote=0, eor=0, blen=0;
  register ssize_t pos=0, c=0; size_t i=0, i0=0;
  register unsigned char *s=(*env)->s;
  assert(s); if(!s) return 1;

  while(!eor) {
    if(c==pos) { 
    res: 
      errno=0;
      pos=recv((*env)->peer, (*env)->recv_buf, sizeof (*env)->recv_buf, 0);
      if(!pos && errno==EINTR) goto res; // Solaris, see INN FAQ


      if(pos<=0) break;
      c=0; 
    }
    switch(ch=(*env)->recv_buf[c]) 
      {
      case '\r': case '\f': case ' ': case '\t': break; /* skip */
      case '\n':
	if(type==BEGIN) eor=1;
	else if(type==KEY || type==VAL) {
	  PUSH(type); 
	  CALL_BEGIN();
	}
	tag[0].n=tag[1].n=tag[2].n=0; i0=i=0;      		/* RESET */
	in_dquote=0;
	type = BEGIN; 
	break;
      case ':': if(in_dquote) {APPEND(ch); break;}
	in_dquote=1;
	PUSH(type);
	type=KEY;
	break;
      case '=':
	if(type==KEY) { 
	  PUSH(type);
	  type=VAL;
	} else {
	  APPEND(ch);
	}
	break;
      default:
	APPEND(ch);
      }
    c++;
  }
  RESET();
  (*env)->c=c; (*env)->pos=pos;
  return 0;
}

#if 0
void begin(parser_tag_t tag[3], struct parser_cb *cb){
  int i;
  for(i=0; i<tag[2].n; i++)
    printf("<begin: %s, %s (%d, %d)\n", tag[1].strings[i].string, tag[2].strings[i].string, tag[1].strings[i].length, tag[2].strings[i].length);
}
void end(parser_string_t str[1], struct parser_cb *cb){
  printf("<end> %s (%d)\n", str[0].string, str[0].length);
}

main() {
  parser_cb_t cb={begin, end, 0};
  FILE *f=fopen("xmltest.xml", "r");
  parse(f, &cb);
  fclose(f);
}

#endif
