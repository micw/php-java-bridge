#!/bin/sh

# php wrapper

chmod +x ./php-cgi-i386-linux
exec ./php-cgi-i386-linux -c php-cgi-i386-linux.ini "$@"
