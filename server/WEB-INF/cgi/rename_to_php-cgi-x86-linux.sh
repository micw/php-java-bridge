#!/bin/sh

# wrapper for the IBM VM, which identifies intel as x86. 

chmod +x ./php-cgi-i386-linux
exec ./php-cgi-i386-linux -c php-cgi-i386-linux.ini


