#!/bin/sh
# php fcgi wrapper
#set -x

"$@" 1>&2 &
trap "kill $! && exit 0;" 1 2 15
read 1>&2
kill $!
