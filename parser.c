#include <stdio.h>
#include <stdlib.h>
#include <assert.h>
#include <unistd.h>

#include "parser.h"

#define BUF_SIZE 5//FIXME: use 8K
#define MAX_ARGS 10

#define APPEND(c) { \
  if(i>=(*env)->len-1) { \
    unsigned char* s1=realloc(s, (*env)->len*=2); \
    if(!s1) return 1; else s=(*env)->s=s1; \
  } \
  s[i++]=c; \
}
#define PUSH(t) { \
  parser_string_t *str = tag[t].strings; \
  short n = tag[t].n; \
  s[i]=0; \
  str[n].string=&((*env)->s); \
  str[n].off=i0; \
  str[n].length=i-i0; \
  ++tag[t].n; \
  APPEND(0); \
  i0=i; \
}
#define RESET() { \
  type=VOID;\
  mask=~(unsigned char)0; \
  level=0; \
  eor=0; \
  blen=0; \
  in_dquote=0; \
  i=0; \
  i0=0; \
}
#define CALL_BEGIN() { \
  (*cb->begin)(tag, cb); \
}
#define CALL_END() { \
  if(cb->end) (*cb->end)(tag[0].strings, cb); \
}

short parse(proxyenv *env, parser_cb_t *cb) {
  parser_string_t v1[1], v2[MAX_ARGS], v3[MAX_ARGS];
  parser_tag_t tag[] = {{0, v1}, {0, v2}, {0, v3}};
  unsigned char buf[BUF_SIZE];
  unsigned char ch, mask=~(unsigned char)0;
  enum {BEGIN, KEY, VAL, ENTITY, BLOB, VOID} type = VOID;
  short level=0, in_dquote=0, eor=0, blen=0;
  size_t pos=0, c=0, i=0, i0=0, e;
  unsigned char *s=(*env)->s;
  assert(s); if(!s) return 1;

  while(!eor) {
    if(c==pos) { 
      pos=read((*env)->peer, buf, BUF_SIZE);
      if(!pos) break;
      c=0; 
    }
    switch((ch=buf[c])&mask) 
{/* --- This block must be compilable with an ansi C compiler  --- */
		case '<':
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
		    if(type==BEGIN) level--;
		    level--;
		    break;
		case '>': if(in_dquote) {APPEND(ch); break;}
		    if(type==BEGIN){
			PUSH(type);
			CALL_END();
		    } else {
			if(type==VAL) PUSH(type);
			CALL_BEGIN();
		    }
		    tag[0].n=tag[1].n=tag[2].n=0; i0=i=0;      		/* RESET */
		    type=VOID;
		    if(level==0) eor=1; 
		    break;
		case '\0':
		    if(mask==0) {type=BLOB; mask=0; break;}
		    if(type==VOID) mask=~(unsigned char)0;
				
		    if(0!=blen) {
			APPEND(ch);
			if(0==--blen) type=VOID;
		    }
		    else {
			blen=ch;
		    }
		    break;
		case ';':
		    if(type==ENTITY) {
			switch (s[e+1]) {
			case 'l': s[e]='<'; i=e+1; break; /* lt */
			case 'g': s[e]='>'; i=e+1; break; /* gt */
			case 'a': s[e]= (s[e+2]=='m'?'&':'\''); i=e+1; break; /* amp, apos */
			case 'q': s[e]='"'; i=e+1; break; /* quot */
			}
			type=VAL; //& escapes may only appear in values
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
