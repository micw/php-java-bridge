/*-*- mode: C; tab-width:4 -*-*/

/**\file
 * Parse the XML protocol.
 *
 * Creates a parser structure and calls the registered begin and end
 * callbacks.  Note: The parser implementation is dublicated from
 * Parser.java.  If you change this code, you must change Parser.java,
 * too!
 */

#ifndef JAVA_PARSER_H
#define JAVA_PARSER_H

#include "protocol.h"

#define PARSER_GET_STRING(pst, pos) ((*pst[pos].string)+pst[pos].off)
typedef struct {
  size_t length, off;
  unsigned char** string; //address of s (stored in proxyenv)
} parser_string_t;

typedef struct {
  short n;
  parser_string_t *strings;
} parser_tag_t;

typedef struct parser_cb {
  void (*begin)(parser_tag_t[3], struct parser_cb *);
  void (*end)(parser_string_t[1], struct parser_cb *);
  void *ctx;
  proxyenv *env;
} parser_cb_t;

extern short EXT_GLOBAL (parse)(proxyenv *env, parser_cb_t *cb);
extern short EXT_GLOBAL (parse_header) (proxyenv *env, parser_cb_t *cb);

#endif
