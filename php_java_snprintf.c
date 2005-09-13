/*-*- mode: C; tab-width:8 -*-*/

/* ====================================================================
 * Copyright (c) 1995-1998 The Apache Group.  All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions
 * are met:
 *
 * 1. Redistributions of source code must retain the above copyright
 *    notice, this list of conditions and the following disclaimer. 
 *
 * 2. Redistributions in binary form must reproduce the above copyright
 *    notice, this list of conditions and the following disclaimer in
 *    the documentation and/or other materials provided with the
 *    distribution.
 *
 * 3. All advertising materials mentioning features or use of this
 *    software must display the following acknowledgment:
 *    "This product includes software developed by the Apache Group
 *    for use in the Apache HTTP server project (http://www.apache.org/)."
 *
 * 4. The names "Apache Server" and "Apache Group" must not be used to
 *    endorse or promote products derived from this software without
 *    prior written permission.
 *
 * 5. Redistributions of any form whatsoever must retain the following
 *    acknowledgment:
 *    "This product includes software developed by the Apache Group
 *    for use in the Apache HTTP server project (http://www.apache.org/)."
 *
 * THIS SOFTWARE IS PROVIDED BY THE APACHE GROUP ``AS IS'' AND ANY
 * EXPRESSED OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE
 * IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR
 * PURPOSE ARE DISCLAIMED.  IN NO EVENT SHALL THE APACHE GROUP OR
 * ITS CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL,
 * SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT
 * NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES;
 * LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION)
 * HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT,
 * STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE)
 * ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED
 * OF THE POSSIBILITY OF SUCH DAMAGE.
 * ====================================================================
 *
 * This software consists of voluntary contributions made by many
 * individuals on behalf of the Apache Group and was originally based
 * on public domain software written at the National Center for
 * Supercomputing Applications, University of Illinois, Urbana-Champaign.
 * For more information on the Apache Group and the Apache HTTP server
 * project, please see <http://www.apache.org/>.
 *
 * This code is based on, and used with the permission of, the
 * SIO stdio-replacement strx_* functions by Panos Tsirigotis
 * <panos@alumni.cs.colorado.edu> for xinetd.
 */

#ifndef ZEND_ENGINE_2

#include "php_java.h"
#include <stddef.h>
#include <stdio.h>
#include <ctype.h>
#include <sys/types.h>
#include <stdarg.h>
#include <string.h>
#include <stdlib.h>
#include <math.h>
#ifdef HAVE_INTTYPES_H
#include <inttypes.h>
#endif

#define FALSE			0
#define TRUE			1
#define NUL			'\0'
#define INT_NULL		((int *)0)

#define S_NULL			"(null)"
#define S_NULL_LEN		6

#define FLOAT_DIGITS		6
#define EXPONENT_LENGTH		10

typedef enum {
	PHP_JAVA_LM_STD = 0,
#if SIZEOF_INTMAX_T
	PHP_JAVA_LM_INTMAX_T,
#endif
#if SIZEOF_PTRDIFF_T
	PHP_JAVA_LM_PTRDIFF_T,
#endif
#if SIZEOF_LONG_LONG
	PHP_JAVA_LM_LONG_LONG,
#endif
	PHP_JAVA_LM_SIZE_T,
	PHP_JAVA_LM_LONG,
	PHP_JAVA_LM_LONG_DOUBLE
} EXT_GLOBAL (length_modifier_e);

typedef enum {
	PHP_JAVA_NO = 0, PHP_JAVA_YES = 1
} EXT_GLOBAL (boolean_e);

#if SIZEOF_LONG_LONG_INT
# define PHP_JAVA_WIDE_INT		long long int
#elif SIZEOF_LONG_LONG
# define PHP_JAVA_WIDE_INT		long long
#elif _WIN64
# define PHP_JAVA_WIDE_INT		__int64
#else
# define PHP_JAVA_WIDE_INT		long
#endif
typedef PHP_JAVA_WIDE_INT EXT_GLOBAL (wide_int);
typedef unsigned PHP_JAVA_WIDE_INT EXT_GLOBAL (wide_int_u);

typedef int EXT_GLOBAL (bool_int);

static char * EXT_GLOBAL (cvt) (double arg, int ndigits, int *decpt, int *sign, int eflag, char *buf);
static char * EXT_GLOBAL (ecvt) (double arg, int ndigits, int *decpt, int *sign, char *buf);
static char * EXT_GLOBAL (fcvt) (double arg, int ndigits, int *decpt, int *sign, char *buf);
static char * EXT_GLOBAL (gcvt) (double number, int ndigit, char *buf, EXT_GLOBAL (boolean_e) altform);


static char * EXT_GLOBAL (conv_10) (register EXT_GLOBAL (wide_int) num, register EXT_GLOBAL (bool_int) is_unsigned,
	   register EXT_GLOBAL (bool_int) * is_negative, char *buf_end, register int *len);

static char * EXT_GLOBAL (conv_fp) (register char format, register double num,
		 EXT_GLOBAL (boolean_e) add_dp, int precision, EXT_GLOBAL (bool_int) * is_negative, char *buf, int *len);

static char * EXT_GLOBAL (conv_p2) (register EXT_GLOBAL (wide_int_u) num, register int nbits,
		 char format, char *buf_end, register int *len);

/*
 * Convert num to its decimal format.
 * Return value:
 *   - a pointer to a string containing the number (no sign)
 *   - len contains the length of the string
 *   - is_negative is set to TRUE or FALSE depending on the sign
 *     of the number (always set to FALSE if is_unsigned is TRUE)
 *
 * The caller provides a buffer for the string: that is the buf_end argument
 * which is a pointer to the END of the buffer + 1 (i.e. if the buffer
 * is declared as buf[ 100 ], buf_end should be &buf[ 100 ])
 */
static char *
EXT_GLOBAL (conv_10) (register EXT_GLOBAL (wide_int) num, register EXT_GLOBAL (bool_int) is_unsigned,
	   register EXT_GLOBAL (bool_int) * is_negative, char *buf_end, register int *len)
{
	register char *p = buf_end;
	register EXT_GLOBAL (wide_int_u) magnitude;

	if (is_unsigned) {
		magnitude = (EXT_GLOBAL (wide_int_u)) num;
		*is_negative = FALSE;
	} else {
		*is_negative = (num < 0);

		/*
		 * On a 2's complement machine, negating the most negative integer 
		 * results in a number that cannot be represented as a signed integer.
		 * Here is what we do to obtain the number's magnitude:
		 *      a. add 1 to the number
		 *      b. negate it (becomes positive)
		 *      c. convert it to unsigned
		 *      d. add 1
		 */
		if (*is_negative) {
			EXT_GLOBAL (wide_int) t = num + 1;

			magnitude = ((EXT_GLOBAL (wide_int_u)) - t) + 1;
		} else
			magnitude = (EXT_GLOBAL (wide_int_u)) num;
	}

	/*
	 * We use a do-while loop so that we write at least 1 digit 
	 */
	do {
		register EXT_GLOBAL (wide_int_u) new_magnitude = magnitude / 10;

		*--p = (char)(magnitude - new_magnitude * 10 + '0');
		magnitude = new_magnitude;
	}
	while (magnitude);

	*len = buf_end - p;
	return (p);
}

/* If you change this value then also change bug24640.phpt.
 */
#define	NDIG	80


/*
 * Convert a floating point number to a string formats 'f', 'e' or 'E'.
 * The result is placed in buf, and len denotes the length of the string
 * The sign is returned in the is_negative argument (and is not placed
 * in buf).
 */
static char *
 EXT_GLOBAL (conv_fp) (register char format, register double num,
		 EXT_GLOBAL (boolean_e) add_dp, int precision, EXT_GLOBAL (bool_int) * is_negative, char *buf, int *len)
{
	register char *s = buf;
	register char *p;
	int decimal_point;
	char buf1[NDIG];

	if (format == 'f')
		p = EXT_GLOBAL (fcvt) (num, precision, &decimal_point, is_negative, buf1);
	else						/* either e or E format */
		p = EXT_GLOBAL (ecvt) (num, precision + 1, &decimal_point, is_negative, buf1);

	/*
	 * Check for Infinity and NaN
	 */
	if (isalpha((int)*p)) {
		*len = strlen(p);
		memcpy(buf, p, *len + 1);
		*is_negative = FALSE;
		return (buf);
	}
	if (format == 'f') {
		if (decimal_point <= 0) {
			*s++ = '0';
			if (precision > 0) {
				*s++ = '.';
				while (decimal_point++ < 0)
					*s++ = '0';
			} else if (add_dp) {
				*s++ = '.';
			}
		} else {
			while (decimal_point-- > 0) {
				*s++ = *p++;
			}
			if (precision > 0 || add_dp) {
				*s++ = '.';
			}
		}
	} else {
		*s++ = *p++;
		if (precision > 0 || add_dp)
			*s++ = '.';
	}

	/*
	 * copy the rest of p, the NUL is PHP_JAVA_NOT copied
	 */
	while (*p)
		*s++ = *p++;

	if (format != 'f') {
		char temp[EXPONENT_LENGTH];		/* for exponent conversion */
		int t_len;
		EXT_GLOBAL (bool_int) exponent_is_negative;

		*s++ = format;			/* either e or E */
		decimal_point--;
		if (decimal_point != 0) {
			p = EXT_GLOBAL (conv_10) ((EXT_GLOBAL (wide_int)) decimal_point, FALSE, &exponent_is_negative,
						&temp[EXPONENT_LENGTH], &t_len);
			*s++ = exponent_is_negative ? '-' : '+';

			/*
			 * Make sure the exponent has at least 2 digits
			 */
			if (t_len == 1)
				*s++ = '0';
			while (t_len--)
				*s++ = *p++;
		} else {
			*s++ = '+';
			*s++ = '0';
			*s++ = '0';
		}
	}
	*len = s - buf;
	return (buf);
}


/*
 * Convert num to a base X number where X is a power of 2. nbits determines X.
 * For example, if nbits is 3, we do base 8 conversion
 * Return value:
 *      a pointer to a string containing the number
 *
 * The caller provides a buffer for the string: that is the buf_end argument
 * which is a pointer to the END of the buffer + 1 (i.e. if the buffer
 * is declared as buf[ 100 ], buf_end should be &buf[ 100 ])
 */
static char *
 EXT_GLOBAL (conv_p2) (register EXT_GLOBAL (wide_int_u) num, register int nbits,
		 char format, char *buf_end, register int *len)
{
	register int mask = (1 << nbits) - 1;
	register char *p = buf_end;
	static char low_digits[] = "0123456789abcdef";
	static char upper_digits[] = "0123456789ABCDEF";
	register char *digits = (format == 'X') ? upper_digits : low_digits;

	do {
		*--p = digits[num & mask];
		num >>= nbits;
	}
	while (num);

	*len = buf_end - p;
	return (p);
}

/*
 * cvt.c - IEEE floating point formatting routines for FreeBSD
 * from GNU libc-4.6.27
 */

/*
 *    EXT_GLOBAL (ecvt) converts to decimal
 *      the number of digits is specified by ndigit
 *      decpt is set to the position of the decimal point
 *      sign is set to 0 for positive, 1 for negative
 */


static char *
EXT_GLOBAL (cvt) (double arg, int ndigits, int *decpt, int *sign, int eflag, char *buf)
{
	register int r2;
	int mvl;
	double fi, fj;
	register char *p, *p1;

	if (ndigits >= NDIG - 1)
		ndigits = NDIG - 2;
	r2 = 0;
	*sign = 0;
	p = &buf[0];
	if (arg < 0) {
		*sign = 1;
		arg = -arg;
	}
	arg = modf(arg, &fi);
	p1 = &buf[NDIG];
	/*
	 * Do integer part
	 */
	if (fi != 0) {
		p1 = &buf[NDIG];
		while (fi != 0) {
			fj = modf(fi / 10, &fi);
			if (p1 <= &buf[0]) {
				mvl = NDIG - ndigits;
				memmove(&buf[mvl], &buf[0], NDIG-mvl-1);
				p1 += mvl;
			}
			*--p1 = (int) ((fj + .03) * 10) + '0';
			r2++;
		}
		while (p1 < &buf[NDIG])
			*p++ = *p1++;
	} else if (arg > 0) {
		while ((fj = arg * 10) < 1) {
			if (!eflag && (r2 * -1) < ndigits) {
				break;
			}
			arg = fj;
			r2--;
		}
	}
	p1 = &buf[ndigits];
	if (eflag == 0)
		p1 += r2;
	*decpt = r2;
	if (p1 < &buf[0]) {
		buf[0] = '\0';
		return (buf);
	}
	if (p <= p1 && p < &buf[NDIG]) {
		arg = modf(arg * 10, &fj);
		if ((int)fj==10) {
			*p++ = '1';
			fj = 0;
			*decpt = ++r2;
		}
		while (p <= p1 && p < &buf[NDIG]) {
			*p++ = (int) fj + '0';
			arg = modf(arg * 10, &fj);
		}
	}
	if (p1 >= &buf[NDIG]) {
		buf[NDIG - 1] = '\0';
		return (buf);
	}
	p = p1;
	*p1 += 5;
	while (*p1 > '9') {
		*p1 = '0';
		if (p1 > buf)
			++ * --p1;
		else {
			*p1 = '1';
			(*decpt)++;
			if (eflag == 0) {
				if (p > buf)
					*p = '0';
				p++;
			}
		}
	}
	*p = '\0';
	return (buf);
}

static char *
EXT_GLOBAL (ecvt) (double arg, int ndigits, int *decpt, int *sign, char *buf)
{
	return (EXT_GLOBAL (cvt) (arg, ndigits, decpt, sign, 1, buf));
}

static char *
EXT_GLOBAL (fcvt) (double arg, int ndigits, int *decpt, int *sign, char *buf)
{
	return (EXT_GLOBAL (cvt) (arg, ndigits, decpt, sign, 0, buf));
}

/*
 * EXT_GLOBAL (gcvt)  - Floating output conversion to
 * minimal length string
 */

static char *
EXT_GLOBAL (gcvt) (double number, int ndigit, char *buf, EXT_GLOBAL (boolean_e) altform)
{
	int sign, decpt;
	register char *p1, *p2;
	register int i;
	char buf1[NDIG];

	if (ndigit >= NDIG - 1) {
		ndigit = NDIG - 2;	
	}

	p1 = EXT_GLOBAL (ecvt) (number, ndigit, &decpt, &sign, buf1);
	p2 = buf;
	if (sign)
		*p2++ = '-';
	for (i = ndigit - 1; i > 0 && p1[i] == '0'; i--)
		ndigit--;
	if ((decpt >= 0 && decpt - ndigit > 4)
		|| (decpt < 0 && decpt < -3)) {		/* use E-style */
		decpt--;
		*p2++ = *p1++;
		*p2++ = '.';
		for (i = 1; i < ndigit; i++)
			*p2++ = *p1++;
		if (*(p2 - 1) == '.') {
			*p2++ = '0';
		}	
		*p2++ = 'e';
		if (decpt < 0) {
			decpt = -decpt;
			*p2++ = '-';
		} else
			*p2++ = '+';
		if (decpt / 100 > 0)
			*p2++ = decpt / 100 + '0';
		if (decpt / 10 > 0)
			*p2++ = (decpt % 100) / 10 + '0';
		*p2++ = decpt % 10 + '0';
	} else {
		if (decpt <= 0) {
			if (*p1 != '0') {
				*p2++ = '0';
				*p2++ = '.';
			}
			while (decpt < 0) {
				decpt++;
				*p2++ = '0';
			}
		}
		for (i = 1; i <= ndigit; i++) {
			*p2++ = *p1++;
			if (i == decpt)
				*p2++ = '.';
		}
		if (ndigit < decpt) {
			while (ndigit++ < decpt)
				*p2++ = '0';
			*p2++ = '.';
		}
	}
	if (p2[-1] == '.' && !altform)
		p2--;
	*p2 = '\0';
	return (buf);
}

/*
 * NUM_BUF_SIZE is the size of the buffer used for arithmetic conversions
 *
 * XXX: this is a magic number; do not decrease it
 */
#define NUM_BUF_SIZE		512


/*
 * Descriptor for buffer area
 */
struct buf_area {
	char *buf_end;
	char *nextb;				/* pointer to next byte to read/write   */
};

typedef struct buf_area buffy;

/*
 * The INS_CHAR macro inserts a character in the buffer and writes
 * the buffer back to disk if necessary
 * It uses the char pointers sp and bep:
 *      sp points to the next available character in the buffer
 *      bep points to the end-of-buffer+1
 * While using this macro, note that the nextb pointer is PHP_JAVA_NOT updated.
 *
 * PHP_JAVA_NOTE: Evaluation of the c argument should not have any side-effects
 */
#define INS_CHAR(c, sp, bep, cc) \
	{                            \
		if (sp < bep)            \
		{                        \
			*sp++ = c;           \
		}                        \
		cc++;                    \
	}

#define NUM( c )			( c - '0' )

#define STR_TO_DEC( str, num )		\
    num = NUM( *str++ ) ;		\
    while ( isdigit((int)*str ) )		\
    {					\
	num *= 10 ;			\
	num += NUM( *str++ ) ;		\
    }

/*
 * This macro does zero padding so that the precision
 * requirement is satisfied. The padding is done by
 * adding '0's to the left of the string that is going
 * to be printed.
 */
#define FIX_PRECISION( adjust, precision, s, s_len )	\
    if ( adjust )					\
	while ( s_len < precision )			\
	{						\
	    *--s = '0' ;				\
	    s_len++ ;					\
	}

/*
 * Macro that does padding. The padding is done by printing
 * the character ch.
 */
#define PAD( width, len, ch )	do		\
	{					\
	    INS_CHAR( ch, sp, bep, cc ) ;	\
	    width-- ;				\
	}					\
	while ( width > len )

/*
 * Prefix the character ch to the string str
 * Increase length
 * Set the has_prefix flag
 */
#define PREFIX( str, length, ch )	 *--str = ch ; length++ ; has_prefix = PHP_JAVA_YES


/*
 * Do format conversion placing the output in buffer
 */
static int format_converter(register buffy * odp, const char *fmt,
							va_list ap)
{
	register char *sp;
	register char *bep;
	register int cc = 0;
	register int i;

	register char *s = NULL;
	char *q;
	int s_len;

	register int min_width = 0;
	int precision = 0;
	enum {
		LEFT, RIGHT
	} adjust;
	char pad_char;
	char prefix_char;

	double fp_num;
	EXT_GLOBAL (wide_int) i_num = (EXT_GLOBAL (wide_int)) 0;
	EXT_GLOBAL (wide_int_u) ui_num;

	char num_buf[NUM_BUF_SIZE];
	char char_buf[2];			/* for printing %% and %<unknown> */

	/*
	 * Flag variables
	 */
	EXT_GLOBAL (length_modifier_e) modifier;
	EXT_GLOBAL (boolean_e) alternate_form;
	EXT_GLOBAL (boolean_e) print_sign;
	EXT_GLOBAL (boolean_e) print_blank;
	EXT_GLOBAL (boolean_e) adjust_precision;
	EXT_GLOBAL (boolean_e) adjust_width;
	EXT_GLOBAL (bool_int) is_negative;

	sp = odp->nextb;
	bep = odp->buf_end;

	while (*fmt) {
		if (*fmt != '%') {
			INS_CHAR(*fmt, sp, bep, cc);
		} else {
			/*
			 * Default variable settings
			 */
			adjust = RIGHT;
			alternate_form = print_sign = print_blank = PHP_JAVA_NO;
			pad_char = ' ';
			prefix_char = NUL;

			fmt++;

			/*
			 * Try to avoid checking for flags, width or precision
			 */
			if (isascii((int)*fmt) && !islower((int)*fmt)) {
				/*
				 * Recognize flags: -, #, BLANK, +
				 */
				for (;; fmt++) {
					if (*fmt == '-')
						adjust = LEFT;
					else if (*fmt == '+')
						print_sign = PHP_JAVA_YES;
					else if (*fmt == '#')
						alternate_form = PHP_JAVA_YES;
					else if (*fmt == ' ')
						print_blank = PHP_JAVA_YES;
					else if (*fmt == '0')
						pad_char = '0';
					else
						break;
				}

				/*
				 * Check if a width was specified
				 */
				if (isdigit((int)*fmt)) {
					STR_TO_DEC(fmt, min_width);
					adjust_width = PHP_JAVA_YES;
				} else if (*fmt == '*') {
					min_width = va_arg(ap, int);
					fmt++;
					adjust_width = PHP_JAVA_YES;
					if (min_width < 0) {
						adjust = LEFT;
						min_width = -min_width;
					}
				} else
					adjust_width = PHP_JAVA_NO;

				/*
				 * Check if a precision was specified
				 *
				 * XXX: an unreasonable amount of precision may be specified
				 * resulting in overflow of num_buf. Currently we
				 * ignore this possibility.
				 */
				if (*fmt == '.') {
					adjust_precision = PHP_JAVA_YES;
					fmt++;
					if (isdigit((int)*fmt)) {
						STR_TO_DEC(fmt, precision);
					} else if (*fmt == '*') {
						precision = va_arg(ap, int);
						fmt++;
						if (precision < 0)
							precision = 0;
					} else
						precision = 0;
				} else
					adjust_precision = PHP_JAVA_NO;
			} else
				adjust_precision = adjust_width = PHP_JAVA_NO;

			/*
			 * Modifier check
			 */
			switch (*fmt) {
				case 'L':
					fmt++;
					modifier = PHP_JAVA_LM_LONG_DOUBLE;
					break;
				case 'l':
					fmt++;
#if SIZEOF_LONG_LONG
					if (*fmt == 'l') {
						fmt++;
						modifier = PHP_JAVA_LM_LONG_LONG;
					} else
#endif
						modifier = PHP_JAVA_LM_LONG;
					break;
				case 'z':
					fmt++;
					modifier = PHP_JAVA_LM_SIZE_T;
					break;
				case 'j':
					fmt++;
#if SIZEOF_INTMAX_T
					modifier = PHP_JAVA_LM_INTMAX_T;
#else
					modifier = PHP_JAVA_LM_SIZE_T;
#endif
					break;
				case 't':
					fmt++;
#if SIZEOF_PTRDIFF_T
					modifier = PHP_JAVA_LM_PTRDIFF_T;
#else
					modifier = PHP_JAVA_LM_SIZE_T;
#endif
					break;
				case 'h':
					fmt++;
					if (*fmt == 'h') {
						fmt++;
					}
					/* these are promoted to int, so no break */
				default:				
					modifier = PHP_JAVA_LM_STD;
					break;
			}

			/*
			 * Argument extraction and printing.
			 * First we determine the argument type.
			 * Then, we convert the argument to a string.
			 * On exit from the switch, s points to the string that
			 * must be printed, s_len has the length of the string
			 * The precision requirements, if any, are reflected in s_len.
			 *
			 * PHP_JAVA_NOTE: pad_char may be set to '0' because of the 0 flag.
			 *   It is reset to ' ' by non-numeric formats
			 */
			switch (*fmt) {
				case 'u':
					switch(modifier) {
						default:
							i_num = (EXT_GLOBAL (wide_int)) va_arg(ap, unsigned int);
							break;
						case PHP_JAVA_LM_LONG_DOUBLE:
							goto fmt_error;
						case PHP_JAVA_LM_LONG:
							i_num = (EXT_GLOBAL (wide_int)) va_arg(ap, unsigned long int);
							break;
						case PHP_JAVA_LM_SIZE_T:
							i_num = (EXT_GLOBAL (wide_int)) va_arg(ap, size_t);
							break;
#if SIZEOF_LONG_LONG
						case PHP_JAVA_LM_LONG_LONG:
							i_num = (EXT_GLOBAL (wide_int)) va_arg(ap, EXT_GLOBAL (wide_int_u));
							break;
#endif
#if SIZEOF_INTMAX_T
						case PHP_JAVA_LM_INTMAX_T:
							i_num = (EXT_GLOBAL (wide_int)) va_arg(ap, uintmax_t);
							break;
#endif
#if SIZEOF_PTRDIFF_T
						case PHP_JAVA_LM_PTRDIFF_T:
							i_num = (EXT_GLOBAL (wide_int)) va_arg(ap, ptrdiff_t);
							break;
#endif
					}
					/*
					 * The rest also applies to other integer formats, so fall
					 * into that case.
					 */
				case 'd':
				case 'i':
					/*
					 * Get the arg if we haven't already.
					 */
					if ((*fmt) != 'u') {
						switch(modifier) {
							default:
								i_num = (EXT_GLOBAL (wide_int)) va_arg(ap, int);
								break;
							case PHP_JAVA_LM_LONG_DOUBLE:
								goto fmt_error;
							case PHP_JAVA_LM_LONG:
								i_num = (EXT_GLOBAL (wide_int)) va_arg(ap, long int);
								break;
							case PHP_JAVA_LM_SIZE_T:
#if SIZEOF_SSIZE_T
								i_num = (EXT_GLOBAL (wide_int)) va_arg(ap, ssize_t);
#else
								i_num = (EXT_GLOBAL (wide_int)) va_arg(ap, size_t);
#endif
								break;
#if SIZEOF_LONG_LONG
							case PHP_JAVA_LM_LONG_LONG:
								i_num = (EXT_GLOBAL (wide_int)) va_arg(ap, EXT_GLOBAL (wide_int));
								break;
#endif
#if SIZEOF_INTMAX_T
							case PHP_JAVA_LM_INTMAX_T:
								i_num = (EXT_GLOBAL (wide_int)) va_arg(ap, intmax_t);
								break;
#endif
#if SIZEOF_PTRDIFF_T
							case PHP_JAVA_LM_PTRDIFF_T:
								i_num = (EXT_GLOBAL (wide_int)) va_arg(ap, ptrdiff_t);
								break;
#endif
						}
					}
					s = EXT_GLOBAL (conv_10) (i_num, (*fmt) == 'u', &is_negative,
								&num_buf[NUM_BUF_SIZE], &s_len);
					FIX_PRECISION(adjust_precision, precision, s, s_len);

					if (*fmt != 'u') {
						if (is_negative)
							prefix_char = '-';
						else if (print_sign)
							prefix_char = '+';
						else if (print_blank)
							prefix_char = ' ';
					}
					break;


				case 'o':
					switch(modifier) {
						default:
							ui_num = (EXT_GLOBAL (wide_int_u)) va_arg(ap, unsigned int);
							break;
						case PHP_JAVA_LM_LONG_DOUBLE:
							goto fmt_error;
						case PHP_JAVA_LM_LONG:
							ui_num = (EXT_GLOBAL (wide_int_u)) va_arg(ap, unsigned long int);
							break;
						case PHP_JAVA_LM_SIZE_T:
							ui_num = (EXT_GLOBAL (wide_int_u)) va_arg(ap, size_t);
							break;
#if SIZEOF_LONG_LONG
						case PHP_JAVA_LM_LONG_LONG:
							ui_num = (EXT_GLOBAL (wide_int_u)) va_arg(ap, EXT_GLOBAL (wide_int_u));
							break;
#endif
#if SIZEOF_INTMAX_T
						case PHP_JAVA_LM_INTMAX_T:
							ui_num = (EXT_GLOBAL (wide_int_u)) va_arg(ap, uintmax_t);
							break;
#endif
#if SIZEOF_PTRDIFF_T
						case PHP_JAVA_LM_PTRDIFF_T:
							ui_num = (EXT_GLOBAL (wide_int_u)) va_arg(ap, ptrdiff_t);
							break;
#endif
					}
					s = EXT_GLOBAL (conv_p2) (ui_num, 3, *fmt,
								&num_buf[NUM_BUF_SIZE], &s_len);
					FIX_PRECISION(adjust_precision, precision, s, s_len);
					if (alternate_form && *s != '0') {
						*--s = '0';
						s_len++;
					}
					break;


				case 'x':
				case 'X':
					switch(modifier) {
						default:
							ui_num = (EXT_GLOBAL (wide_int_u)) va_arg(ap, unsigned int);
							break;
						case PHP_JAVA_LM_LONG_DOUBLE:
							goto fmt_error;
						case PHP_JAVA_LM_LONG:
							ui_num = (EXT_GLOBAL (wide_int_u)) va_arg(ap, unsigned long int);
							break;
						case PHP_JAVA_LM_SIZE_T:
							ui_num = (EXT_GLOBAL (wide_int_u)) va_arg(ap, size_t);
							break;
#if SIZEOF_LONG_LONG
						case PHP_JAVA_LM_LONG_LONG:
							ui_num = (EXT_GLOBAL (wide_int_u)) va_arg(ap, EXT_GLOBAL (wide_int_u));
							break;
#endif
#if SIZEOF_INTMAX_T
						case PHP_JAVA_LM_INTMAX_T:
							ui_num = (EXT_GLOBAL (wide_int_u)) va_arg(ap, uintmax_t);
							break;
#endif
#if SIZEOF_PTRDIFF_T
						case PHP_JAVA_LM_PTRDIFF_T:
							ui_num = (EXT_GLOBAL (wide_int_u)) va_arg(ap, ptrdiff_t);
							break;
#endif
					}
					s = EXT_GLOBAL (conv_p2) (ui_num, 4, *fmt,
								&num_buf[NUM_BUF_SIZE], &s_len);
					FIX_PRECISION(adjust_precision, precision, s, s_len);
					if (alternate_form && i_num != 0) {
						*--s = *fmt;	/* 'x' or 'X' */
						*--s = '0';
						s_len += 2;
					}
					break;


				case 's':
					s = va_arg(ap, char *);
					if (s != NULL) {
						s_len = strlen(s);
						if (adjust_precision && precision < s_len)
							s_len = precision;
					} else {
						s = S_NULL;
						s_len = S_NULL_LEN;
					}
					pad_char = ' ';
					break;


				case 'f':
				case 'e':
				case 'E':
					switch(modifier) {
						case PHP_JAVA_LM_LONG_DOUBLE:
							fp_num = (double) va_arg(ap, long double);
							break;
						case PHP_JAVA_LM_STD:
							fp_num = va_arg(ap, double);
							break;
						default:
							goto fmt_error;
					}

					if (zend_isnan(fp_num)) {
						s = "nan";
						s_len = 3;
					} else if (zend_isinf(fp_num)) {
						s = "inf";
						s_len = 3;
					} else {
						s = EXT_GLOBAL (conv_fp) (*fmt, fp_num, alternate_form,
						 (adjust_precision == PHP_JAVA_NO) ? FLOAT_DIGITS : precision,
									&is_negative, &num_buf[1], &s_len);
						if (is_negative)
							prefix_char = '-';
						else if (print_sign)
							prefix_char = '+';
						else if (print_blank)
							prefix_char = ' ';
					}
					break;


				case 'g':
				case 'G':
					switch(modifier) {
						case PHP_JAVA_LM_LONG_DOUBLE:
							fp_num = (double) va_arg(ap, long double);
							break;
						case PHP_JAVA_LM_STD:
							fp_num = va_arg(ap, double);
							break;
						default:
							goto fmt_error;
					}

					if (zend_isnan(fp_num)) {
 						s = "NAN";
 						s_len = 3;
 						break;
 					} else if (zend_isinf(fp_num)) {
 						if (fp_num > 0) {
 							s = "INF";
 							s_len = 3;
 						} else {
 							s = "-INF";
 							s_len = 4;
 						}
 						break;
 					}

					if (adjust_precision == PHP_JAVA_NO)
						precision = FLOAT_DIGITS;
					else if (precision == 0)
						precision = 1;
					/*
					 * * We use &num_buf[ 1 ], so that we have room for the sign
					 */
					s = EXT_GLOBAL (gcvt) (fp_num, precision, &num_buf[1],
							alternate_form);
					if (*s == '-')
						prefix_char = *s++;
					else if (print_sign)
						prefix_char = '+';
					else if (print_blank)
						prefix_char = ' ';

					s_len = strlen(s);

					if (alternate_form && (q = strchr(s, '.')) == NULL)
						s[s_len++] = '.';
					if (*fmt == 'G' && (q = strchr(s, 'e')) != NULL)
						*q = 'E';
					break;


				case 'c':
					char_buf[0] = (char) (va_arg(ap, int));
					s = &char_buf[0];
					s_len = 1;
					pad_char = ' ';
					break;


				case '%':
					char_buf[0] = '%';
					s = &char_buf[0];
					s_len = 1;
					pad_char = ' ';
					break;


				case 'n':
					*(va_arg(ap, int *)) = cc;
					break;

					/*
					 * Always extract the argument as a "char *" pointer. We 
					 * should be using "void *" but there are still machines 
					 * that don't understand it.
					 * If the pointer size is equal to the size of an unsigned
					 * integer we convert the pointer to a hex number, otherwise 
					 * we print "%p" to indicate that we don't handle "%p".
					 */
				case 'p':
					if (sizeof(char *) <= sizeof(EXT_GLOBAL (wide_int_u))) {
						ui_num = (EXT_GLOBAL (wide_int_u))((size_t) va_arg(ap, char *));
						s = EXT_GLOBAL (conv_p2) (ui_num, 4, 'x', 
								&num_buf[NUM_BUF_SIZE], &s_len);
						if (ui_num != 0) {
							*--s = 'x';
							*--s = '0';
							s_len += 2;
						}
					} else {
						s = "%p";
						s_len = 2;
					}
					pad_char = ' ';
					break;


				case NUL:
					/*
					 * The last character of the format string was %.
					 * We ignore it.
					 */
					continue;


fmt_error:
					php_error(E_ERROR, "Illegal length modifier specified '%c' in s[np]printf call", *fmt);
					/*
					 * The default case is for unrecognized %'s.
					 * We print %<char> to help the user identify what
					 * option is not understood.
					 * This is also useful in case the user wants to pass
					 * the output of format_converter to another function
					 * that understands some other %<char> (like syslog).
					 * Note that we can't point s inside fmt because the
					 * unknown <char> could be preceded by width etc.
					 */
				default:
					char_buf[0] = '%';
					char_buf[1] = *fmt;
					s = char_buf;
					s_len = 2;
					pad_char = ' ';
					break;
			}

			if (prefix_char != NUL) {
				*--s = prefix_char;
				s_len++;
			}
			if (adjust_width && adjust == RIGHT && min_width > s_len) {
				if (pad_char == '0' && prefix_char != NUL) {
					INS_CHAR(*s, sp, bep, cc)
						s++;
					s_len--;
					min_width--;
				}
				PAD(min_width, s_len, pad_char);
			}
			/*
			 * Print the string s. 
			 */
			for (i = s_len; i != 0; i--) {
				INS_CHAR(*s, sp, bep, cc);
				s++;
			}

			if (adjust_width && adjust == LEFT && min_width > s_len)
				PAD(min_width, s_len, pad_char);
		}
		fmt++;
	}
	odp->nextb = sp;
	return (cc);
}


/*
 * This is the general purpose conversion function.
 */
static void strx_printv(int *ccp, char *buf, size_t len, const char *format,
						va_list ap)
{
	buffy od;
	int cc;

	/*
	 * First initialize the descriptor
	 * Notice that if no length is given, we initialize buf_end to the
	 * highest possible address.
	 */
	if (len == 0) {
		od.buf_end = (char *) ~0;
		od.nextb   = (char *) ~0;
	} else {
		od.buf_end = &buf[len-1];
		od.nextb   = buf;
	}

	/*
	 * Do the conversion
	 */
	cc = format_converter(&od, format, ap);
	if (len != 0 && od.nextb <= od.buf_end)
		*(od.nextb) = '\0';
	if (ccp)
		*ccp = cc;
}


int EXT_GLOBAL (snprintf) (char *buf, size_t len, const char *format,...)
{
	int cc;
	va_list ap;

	va_start(ap, format);
	strx_printv(&cc, buf, len, format, ap);
	va_end(ap);
	return (cc);
}


int EXT_GLOBAL (vsnprintf) (char *buf, size_t len, const char *format, va_list ap)
{
	int cc;

	strx_printv(&cc, buf, len, format, ap);
	return (cc);
}

#ifndef PHP_WRAPPER_H
#error must include php_wrapper.h
#endif

#endif
