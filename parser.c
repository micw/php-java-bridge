#include <stdio.h>
#include <stdlib.h>

#include "parser.h"

#define SFILE FILE
#define sread fread
#define BUF_SIZE 5//FIXME: use 8K
#define MAX_ARGS 10
#define APPEND(c) { \
  if(i>=len-1) { \
    unsigned char* s1=realloc(s, len*=2); \
    if(!s1) {free(s); return 1;} else s=s1; \
  } \
  s[i++]=c; \
}
#define PUSH(t) { \
  parser_string_t *str = tag[t].strings; \
  short *n = &tag[t].n; \
  s[i]=0; \
  str[*n].string=s+i0; \
  str[*n].length=i-i0; \
  ++*n; \
  APPEND(0); \
  i0=i; \
}
static short parse(SFILE*peer, parser_cb_t *cb) {
  parser_string_t v1[1], v2[MAX_ARGS], v3[MAX_ARGS];
  parser_tag_t tag[] = {{0, v1}, {0, v2}, {0, v3}};
  unsigned char buf[BUF_SIZE];
  size_t len=2;//FIXME: use 255
  unsigned char *s=malloc(len), ch, mask=0;
  enum {BEGIN, KEY, VAL, ENTITY, BLOB, VOID} type = VOID;
  short level=0, in_dquote=0, eof=0, blen=0;
  size_t pos=0, c=0, i=0, i0=0, e;
  if(!s) return 1;

  while(!eof) {
    if(c==pos) { 
      if(feof(peer)) break;

      pos=sread(buf, 1, BUF_SIZE, peer); 
      c=0; 

      if(ferror(peer)) 
	return 2;
    }
    switch((ch=buf[c])&mask) {
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
	(*cb->end)(tag[0].strings, cb);
      } else {
	if(type==VAL) PUSH(type);
	(*cb->begin)(tag, cb);
      }
      tag[0].n=tag[1].n=tag[2].n=i0=i=0;      		/* RESET */
      type=VOID;
      if(level==0) eof=1;
      break;
    case '\0':
      if(mask) {type=BLOB; mask=0; break;}
      if(type==VOID) mask=~(unsigned char)0;

      if(blen) {
	APPEND(ch);
	if(!--blen) type=VOID;
      }
      else {
	blen=ch;
      }
      break;
    case ';':
      if(type==ENTITY) {
	switch (s[e]) {
	case 'l': s[e]='<'; break; /* lt */
	case 'g': s[e]='>'; break; /* gt */
	case 'a': s[e]=s[e+1]=='m'?'&':'\''; break; /* amp, apos */
	case 'q': s[e]='"'; break; /* quot */
	}
	i=e+1;
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
  free(s);
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
