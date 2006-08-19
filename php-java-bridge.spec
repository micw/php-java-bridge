#-*- mode: rpm-spec; tab-width:4 -*-
%define version 3.1.8devel
%define release 1
%define PHP_MAJOR_VERSION %(((LANG=C rpm -q --queryformat "%{VERSION}" php) || echo "4.0.0") | tail -1 | sed 's/\\\..*$//')
%define PHP_MINOR_VERSION %(((LANG=C rpm -q --queryformat "%{VERSION}" php) || echo "4.0.0") | tail -1 | LANG=C cut -d. -f2)
%define have_j2 %((rpm -q --whatprovides j2re || rpm -q --whatprovides j2sdk) >/dev/null && echo 1 || echo 0)
%define have_policy_modules %(if test -f /etc/selinux/config && test -d /etc/selinux/%{__policy_tree}/modules; then echo 1; else echo 0; fi)

%define tomcat_name        tomcat5
%define tomcat_webapps		%{_localstatedir}/lib/%{tomcat_name}/webapps
%define shared_java        %{_datadir}/java

Name: php-java-bridge
Summary: PHP Hypertext Preprocessor to Java Bridge
Group: Development/Languages
Version: %{version}
Release: %{release}
License: The PHP license (see "LICENSE" file included in distribution)
URL: http://www.sourceforge.net/projects/php-java-bridge
Source0: http://osdn.dl.sourceforge.net/sourceforge/php-java-bridge/php-java-bridge_%{version}.tar.bz2


BuildRequires: php-devel >= 4.3.4
BuildRequires: gcc >= 3.2.3
BuildRequires: gcc-c++
BuildRequires: gcc-java
BuildRequires: libstdc++-devel
BuildRequires: httpd make 
BuildRequires: libtool >= 1.4.3
BuildRequires: automake >= 1.6.3
BuildRequires: autoconf >= 2.57
%if %{have_j2} == 1
BuildRequires: j2sdk >= 1.4.2
%else
BuildRequires: java-devel >= 1.4.2
%endif
%if %{have_policy_modules} == 1
BuildRequires: policycoreutils checkpolicy coreutils
%endif

# PHP 4 or PHP 5 or PHP 5.1
%if %{PHP_MAJOR_VERSION} == 4
Requires: php >= 4.3.2
Requires: php < 5.0.0
%else
%if %{PHP_MAJOR_VERSION} == 6
Requires: php >= 6.0.0
%else
%if %{PHP_MINOR_VERSION} == 1
Requires: php >= 5.1.1
%else
Requires: php >= 5.0.4
Requires: php < 5.1.0
%endif
%endif
%endif
Requires: httpd 
Requires: libgcj
%if %{have_policy_modules} == 1
Requires: policycoreutils coreutils
%endif


BuildRoot: %{_tmppath}/%{name}-root

%description 
Java module/extension for the PHP script language.  Contains the basic
files: java extension for PHP/Apache HTTP server and a simple back-end
which automatically starts and stops when the HTTP server
starts/stops. The bridge log appears in the http server error log.

%package tomcat
Group: System Environment/Daemons
Summary: Tomcat/J2EE back-end for the PHP/Java Bridge
Requires: php-java-bridge = %{version}
Requires: tomcat5
%description tomcat
Deploys the j2ee back-end into the tomcat servlet engine.  The tomcat
back-end is more than 2 times faster than the standalone back-end but
less secure; it uses named pipes instead of abstract local "unix
domain" sockets.

%package devel
Group: Development/Libraries
Summary: PHP/Java Bridge development files and documentation
Requires: php-java-bridge = %{version}
%description devel
Contains the development documentation
and the development files needed to create java applications with
embedded PHP scripts.


%prep
echo Building for PHP %{PHP_MAJOR_VERSION}.

%setup

%build
set -x
PATH=/bin:%{_bindir}
LD_LIBRARY_PATH=/lib:%{_libdir}

# calculate java dir
%if %{have_j2} == 1
pkgid=`rpm -q --whatprovides j2sdk --queryformat "%{PKGID} %{VERSION}\n" | sed 's/\./0/g;s/_/./' |sort -r -k 2,2 -n | head -1 | awk '{print $1}'`
%else
pkgid=`rpm -q --whatprovides java-devel --queryformat "%{PKGID} %{VERSION}\n" | sed 's/\./0/g;s/_/./' |sort -r -k 2,2 -n | head -1 | awk '{print $1}'`
%endif
jdk=`rpm  -q --pkgid $pkgid`
java=`rpm -ql $jdk | grep 'bin/java$' | head -1`
java_dir=`dirname $java`
java_dir=`dirname $java_dir`
if test X`basename $java_dir` = Xjre; then
  java_dir=`dirname $java_dir`;
fi
echo "using java_dir: $java_dir"
if test X$java_dir = X; then echo "ERROR: java not installed" >2; exit 1; fi

phpize
./configure --prefix=%{_exec_prefix} --with-java=$java_dir
make
%if %{have_policy_modules} == 1
(cd security/module; make; rm -rf tmp;)
%endif

%install
rm -rf $RPM_BUILD_ROOT

%makeinstall | tee install.log
echo >filelist
echo >filelist-tomcat
echo >filelist-devel

mod_dir=`cat install.log | sed -n '/Installing shared extensions:/s///p' | awk '{print $1}'`

files="php-script.jar script-api.jar"
mkdir -p $RPM_BUILD_ROOT/%{shared_java}
for i in $files; 
  do cp $mod_dir/$i $RPM_BUILD_ROOT/%{shared_java}/$i; 
  rm -f $mod_dir/$i; 
  echo %{shared_java}/$i >>filelist-devel
done
cp $mod_dir/JavaBridge.jar $RPM_BUILD_ROOT/%{shared_java}/JavaBridge.jar; 
echo %{shared_java}/JavaBridge.jar >>filelist-devel

files='java libnatcJavaBridge.so JavaBridge.jar java.so'
mkdir -p $RPM_BUILD_ROOT/$mod_dir
for i in $files; do
 if test -f $mod_dir/$i; then
  cp $mod_dir/$i $RPM_BUILD_ROOT/$mod_dir/$i
  rm -f $mod_dir/$i
  echo $mod_dir/$i >>filelist
 fi
done
i=RunJavaBridge
cp $mod_dir/$i $RPM_BUILD_ROOT/$mod_dir/$i
rm -f $mod_dir/$i

files=JavaBridge.war
mkdir -p $RPM_BUILD_ROOT/%{tomcat_webapps}
for i in $files; 
  do cp $mod_dir/$i $RPM_BUILD_ROOT/%{tomcat_webapps}
  rm -f $mod_dir/$i; 
  echo %{tomcat_webapps}/$i >>filelist-tomcat
done

mkdir -p $RPM_BUILD_ROOT/etc/php.d
cat java-servlet.ini  >$RPM_BUILD_ROOT/etc/php.d/java-servlet.ini
cat java.ini  >$RPM_BUILD_ROOT/etc/php.d/java.ini
echo /etc/php.d/java.ini >>filelist

mkdir $RPM_BUILD_ROOT/$mod_dir/lib
echo $mod_dir/lib >>filelist

# server also contains the server documentation
mv server server.backup
mkdir server
mv server.backup/documentation server
mv server.backup/javabridge.policy server 
mv server.backup/WEB-INF server
rm -rf server/WEB-INF/lib server/WEB-INF/classes server/WEB-INF/cgi
mv server.backup/test server
(cd documentation; ln -s ../server/documentation/API .)

%clean
rm -rf $RPM_BUILD_ROOT

%post
if test -f /etc/selinux/config; then
  if test -d /etc/selinux/%{__policy_tree}/modules; then 
	/sbin/service httpd stop > /dev/null 2>&1
    %{_sbindir}/semodule -i %{_docdir}/%{name}-%{version}/security/module/php-java-bridge.pp
    chcon -t javabridge_exec_t %{_libdir}/php/modules/RunJavaBridge
    chcon -t bin_t %{_libdir}/php/modules/java
	/sbin/service httpd start > /dev/null 2>&1
  else
	te=/etc/selinux/%{__policy_tree}/src/policy/domains/program/php-java-bridge.te
	fc=/etc/selinux/%{__policy_tree}/src/policy/file_contexts/program/php-java-bridge.fc
    echo "SECURITY ENHANCED LINUX"
    echo "-----------------------"
	echo "You are running a SELinx system. Please install the policy sources:"
	echo "rpm -i selinux-policy-%{__policy_tree}-sources-*.rpm"
	echo "sh %{_docdir}/%{name}-%{version}/security/update_policy.sh \\"
    echo "                          /etc/selinux/%{__policy_tree}/src/policy"
	echo "Please see INSTALL and README documents for more information."
    echo
  fi
fi
%{_libdir}/php/modules/java --convert %{_datadir}/pear ""
echo "PHP/Java Bridge installed."
echo "Now install the tomcat or J2EE back-end or the native (lucene/itext) libs."
echo
exit 0

%post tomcat
if test -f /etc/selinux/config; then
  if test -d /etc/selinux/%{__policy_tree}/modules; then 
	/sbin/service httpd stop > /dev/null 2>&1
	/sbin/service tomcat5 stop > /dev/null 2>&1
    %{_sbindir}/semodule -i %{_docdir}/%{name}-tomcat-%{version}/security/module/php-java-bridge-tomcat.pp
	/sbin/service httpd start > /dev/null 2>&1
	/sbin/service tomcat5 start > /dev/null 2>&1
  else
	te=/etc/selinux/%{__policy_tree}/src/policy/domains/program/php-java-bridge.te
	fc=/etc/selinux/%{__policy_tree}/src/policy/file_contexts/program/php-java-bridge.fc
    echo "SECURITY ENHANCED LINUX"
    echo "-----------------------"
	echo "You are running a SELinx system. Please install the policy sources:"
	echo "rpm -i selinux-policy-%{__policy_tree}-sources-*.rpm"
	echo "sh %{_docdir}/%{name}-tomcat-%{version}/security/update_policy.sh \\"
    echo "                          /etc/selinux/%{__policy_tree}/src/policy"
	echo "Please see INSTALL and README documents for more information."
    echo
  fi
fi
if test -d /var/www/html &&  ! test -e /var/www/html/JavaBridge; then
  ln -fs %{tomcat_webapps}/JavaBridge /var/www/html/;
fi
echo "PHP/Java Bridge tomcat back-end installed. Start with:"
echo "service tomcat5 restart"
echo "service httpd restart"
echo
exit 0

%post devel
mkdir -p %{_exec_prefix}/java/packages/lib/ext/ 2>/dev/null
ln -fs %{shared_java}/JavaBridge.jar %{_exec_prefix}/java/packages/lib/ext/
ln -fs %{shared_java}/php-script.jar %{_exec_prefix}/java/packages/lib/ext/
exit 0

%preun
if [ $1 = 0 ]; then
	rm -rf %{_datadir}/pear/java/Bridge.php %{_datadir}/pear/rt
	/sbin/service httpd stop > /dev/null 2>&1
    if test -d /etc/selinux/%{__policy_tree}/modules; then 
		%{_sbindir}/semodule -r javabridge
	fi
	/sbin/service httpd start > /dev/null 2>&1
fi

%preun tomcat
if [ $1 = 0 ]; then
	if test -e /var/www/html/JavaBridge && test -e %{tomcat_webapps}/JavaBridge && test %{tomcat_webapps}/JavaBridge -ef /var/www/html/JavaBridge; then
      rm -f /var/www/html/JavaBridge;
    fi
	rm -rf %{tomcat_webapps}/JavaBridge
    if test -d /etc/selinux/%{__policy_tree}/modules; then 
		%{_sbindir}/semodule -r javabridge_tomcat
	fi
fi

%preun devel
if [ $1 = 0 ]; then
	rm -f %{_exec_prefix}/java/packages/lib/ext/JavaBridge.jar %{_exec_prefix}/java/packages/lib/ext/php-java.jar
fi


%files -f filelist
%defattr(-,root,root)
%attr(6111,apache,apache) %{_libdir}/php/modules/RunJavaBridge
%doc README LICENSE CREDITS NEWS test.php INSTALL.LINUX security 

%files tomcat -f filelist-tomcat
%defattr(-,tomcat,tomcat)
%attr(-,root,root) /etc/php.d/java-servlet.ini
%doc %attr(-,root,root) README INSTALL.J2EE INSTALL.ORACLE INSTALL.WEBSPHERE LICENSE security  

%files devel -f filelist-devel
%defattr(-,root,root)
%doc CREDITS ABOUT.HTM README.GNU_JAVA README.MONO+NET ChangeLog README PROTOCOL.TXT LICENSE server documentation examples tests.php5 tests.php4 php_java_lib NEWS INSTALL.LINUX INSTALL
