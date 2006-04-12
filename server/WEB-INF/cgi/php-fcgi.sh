#!/bin/sh
# php fcgi wrapper
#set -x

"$@" 1>&2 &
trap "kill $!" 1 2 15
cat 1>&2
kill $!
