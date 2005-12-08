#!/bin/sh

# wrapper for the SUN VM, which identifies intel as i386

chmod +x ./php-cgi-i386-linux
exec ./php-cgi-i386-linux
