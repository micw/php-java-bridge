#ifndef JAVA_PARSER_H
#define JAVA_PARSER_H


typedef struct {
  size_t length;
  unsigned char*string;
} parser_string_t;

typedef struct {
  short n;
  parser_string_t *strings;
} parser_tag_t;

typedef struct parser_cb {
  void (*begin)(parser_tag_t[3], struct parser_cb *);
  void (*end)(parser_string_t[1], struct parser_cb *);
  void *ctx;
} parser_cb_t;

#endif
