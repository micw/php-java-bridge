#!/bin/sh

rm -f
wget php-java-bridge.sf.net -OABOUT.HTM
ed ABOUT.HTM <<\EOF
1,$s|http://cvs\.sourceforge\.net/viewcvs\.py/php-java-bridge/php-java-bridge/README?view=markup|README|
1,$s|http://cvs\.sourceforge\.net/viewcvs\.py/php-java-bridge/php-java-bridge/INSTALL?view=markup|INSTALL|
1,$s|http://cvs\.sourceforge\.net/viewcvs\.py/php-java-bridge/php-java-bridge/INSTALL\.WINDOWS?view=markup|INSTALL.WINDOWS|
1,$s|http://cvs\.sourceforge\.net/viewcvs\.py/php-java-bridge/php-java-bridge/PROTOCOL\.TXT?view=markup|PROTOCOL.TXT|
1,$s|http://cvs\.sourceforge\.net/viewcvs\.py/php-java-bridge/php-java-bridge/examples/clients/getProperties\.py?view=markup|examples/clients/getProperties.py|
1,$s|http://cvs\.sourceforge\.net/viewcvs\.py/php-java-bridge/php-java-bridge/examples/clients/listToArray\.scm?view=markup|examples/clients/listToArray.scm|
1,$s/^.*"SourceForge\.net Logo".*$//
w
q
EOF

exit 0;
