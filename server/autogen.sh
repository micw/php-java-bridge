#!/bin/sh
rm -rf autom4te.cache
aclocal
autoheader
autoconf
libtoolize -f
ln -s `which libtool` .
automake -a --foreign 
exit 0
