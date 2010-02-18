#!/bin/sh

cat WEB-INF/cgi/rename_to_php-cgi-i386-linux.ini | sed 's/i386-linux/x86-sunos/;s/^extension=.*$/;&/' >WEB-INF/cgi/php-cgi-x86-sunos.ini
cat WEB-INF/cgi/rename_to_php-cgi-x86-linux.sh | sed 's/i386-linux/x86-sunos/g' >WEB-INF/cgi/php-cgi-x86-sunos.sh

cat WEB-INF/cgi/rename_to_php-cgi-i386-linux.ini | sed 's/i386-linux/i386-freebsd/;s/^extension=.*$/;&/' >WEB-INF/cgi/php-cgi-i386-freebsd.ini
cat WEB-INF/cgi/rename_to_php-cgi-x86-linux.sh | sed 's/i386-linux/i386-freebsd/g' >WEB-INF/cgi/php-cgi-i386-freebsd.sh

cat WEB-INF/cgi/rename_to_php-cgi-i386-linux.ini | sed 's/^extension=.*$/;&/' >WEB-INF/cgi/php-cgi-i386-linux.ini
cat WEB-INF/cgi/rename_to_php-cgi-i386-linux.ini | sed 's/^extension=.*$/;&/' >WEB-INF/cgi/php-cgi-x86-linux.ini
cat WEB-INF/cgi/rename_to_php-cgi-x86-linux.sh  >WEB-INF/cgi/php-cgi-x86-linux.sh
cat WEB-INF/cgi/rename_to_php-cgi-i386-linux.sh  >WEB-INF/cgi/php-cgi-i386-linux.sh

cat WEB-INF/cgi/rename_to_php-cgi-i386-linux.ini | sed 's/i386-linux.so/x86-windows.dll/;s/^extension=.*$/;&/' >WEB-INF/cgi/php.ini

