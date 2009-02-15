#-*- mode: rpm-spec; tab-width:4 -*-
%define version 5.4.3.2
%define release 1
%define PHP_MAJOR_VERSION %(((LANG=C rpm -q --queryformat "%{VERSION}" php) || echo "4.0.0") | tail -1 | sed 's/\\\..*$//')
%define PHP_MINOR_VERSION %(((LANG=C rpm -q --queryformat "%{VERSION}" php) || echo "4.0.0") | tail -1 | LANG=C cut -d. -f2)
%define have_j2 %((rpm -q --whatprovides j2sdk) >/dev/null && echo 1 || echo 0)
%define have_j3 %((rpm -q --whatprovides jdk) >/dev/null && echo 1 || echo 0)
%define have_policy_modules %(if test -f /etc/selinux/config && test -d /etc/selinux/%{__policy_tree}/modules; then echo 1; else echo 0; fi)
%define have_policy_devel %(if test -f %{_datadir}/selinux/devel/Makefile; then echo 1; else echo 0; fi)

%define tomcat_name			tomcat5
%define tomcat_webapps		%{_localstatedir}/lib/%{tomcat_name}/webapps
%define shared_java			%{_datadir}/java
%define shared_pear			%{_datadir}/pear

Name: php-java-bridge
Summary: PHP Hypertext Preprocessor to Java Bridge
Group: Development/Languages
Version: %{version}
Release: %{release}
License: LGPL
URL: http://www.sourceforge.net/projects/php-java-bridge
Source0: http://osdn.dl.sourceforge.net/sourceforge/php-java-bridge/php-java-bridge_%{version}.tar.gz


BuildRequires: php-devel >= 4.3.4
BuildRequires: gcc >= 3.2.3
BuildRequires: mono-core >= 1.1.8
BuildRequires: gcc-c++
BuildRequires: gcc-java >= 3.3.3
BuildRequires: libstdc++-devel
BuildRequires: httpd make 
BuildRequires: libtool >= 1.4.3
BuildRequires: automake >= 1.6.3
BuildRequires: autoconf >= 2.57
%if %{have_j3} == 1
BuildRequires: jdk >= 1.4.2
%else
%if %{have_j2} == 1
BuildRequires: j2sdk >= 1.4.2
%else
BuildRequires: java-devel >= 1.4.2
%endif
%endif
%if %{have_policy_modules} == 1
BuildRequires: selinux-policy
BuildRequires: policycoreutils checkpolicy coreutils
%if %{have_policy_devel} == 0
BuildRequires: selinux-policy-devel
%endif
%endif

# PHP 4 or PHP 5 or PHP 5.1
%if %{PHP_MAJOR_VERSION} == 4
Requires: php >= 4.3.2
Requires: php < 5.0.0
%else
%if %{PHP_MAJOR_VERSION} == 5
Requires: php >= 5.1.1
Requires: php < 6.0.0
%else
Requires: php >= 5.2.0
%endif
%endif
Requires: httpd 
Requires: %{tomcat_name}
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


%package mono
Group: Development/Languages
Summary: Mono module/extension for the PHP script language.
Requires: mono-core >= 1.1.8
%description mono
Java module/extension for the PHP script language.  Contains the basic
files: java extension for PHP/Apache HTTP server and a simple back-end
which automatically starts and stops when the HTTP server
starts/stops. The bridge log appears in the http server error log.

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

%setup -q

%build
set -x
PATH=/bin:%{_bindir}
LD_LIBRARY_PATH=/lib:%{_libdir}

# calculate java dir
%if %{have_j3} == 1
pkgid=`rpm -q --whatprovides jdk --queryformat "%{PKGID} %{VERSION}\n" | sed 's/\./0/g;s/_/./' |sort -r -k 2,2 -n | head -1 | awk '{print $1}'`
%else
%if %{have_j2} == 1
pkgid=`rpm -q --whatprovides j2sdk --queryformat "%{PKGID} %{VERSION}\n" | sed 's/\./0/g;s/_/./' |sort -r -k 2,2 -n | head -1 | awk '{print $1}'`
%else
pkgid=`rpm -q --whatprovides java-devel --queryformat "%{PKGID} %{VERSION}\n" | sed 's/\./0/g;s/_/./' |sort -r -k 2,2 -n | head -1 | awk '{print $1}'`
%endif
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
./configure --libdir=%{_libdir} --prefix=%{_exec_prefix} --with-java=$java_dir --with-mono
make
%if %{have_policy_modules} == 1
(cd security/module; make -f %{_datadir}/selinux/devel/Makefile; rm -rf tmp;)
%endif

%install
rm -rf $RPM_BUILD_ROOT

%makeinstall | tee install.log
echo >filelist
echo >filelist-mono
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
#echo %{shared_java}/JavaBridge.jar >>filelist-devel

files="Client.inc GlobalRef.inc Java.inc JavaBridge.inc JavaProxy.inc NativeParser.inc Options.inc Parser.inc Protocol.inc SimpleParser.inc JavaProxy.php"
mkdir -p $RPM_BUILD_ROOT/%{shared_pear}/java
for i in $files; 
  do cp server/META-INF/java/$i $RPM_BUILD_ROOT/%{shared_pear}/java/$i; 
  echo %{shared_pear}/java/$i >>filelist
done

files='java libnatcJavaBridge.so java.so'
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
i=JavaBridge.jar
cp $mod_dir/$i $RPM_BUILD_ROOT/$mod_dir/$i
rm -f $mod_dir/$i

files="Mono.inc"
mkdir -p $RPM_BUILD_ROOT/%{shared_pear}/mono
for i in $files; 
  do cp server/META-INF/java/$i $RPM_BUILD_ROOT/%{shared_pear}/mono/$i; 
  echo %{shared_pear}/mono/$i >>filelist-mono
done

files='ICSharpCode.SharpZipLib.dll IKVM.AWT.WinForms.dll IKVM.GNU.Classpath.dll IKVM.Runtime.dll'
for i in $files; do
 if test -f $mod_dir/$i; then
  cp $mod_dir/$i $RPM_BUILD_ROOT/$mod_dir/$i
  rm -f $mod_dir/$i
  echo $mod_dir/$i >>filelist-mono
 fi
done
i=RunMonoBridge
cp $mod_dir/$i $RPM_BUILD_ROOT/$mod_dir/$i
rm -f $mod_dir/$i
i=MonoBridge.exe
cp $mod_dir/$i $RPM_BUILD_ROOT/$mod_dir/$i
rm -f $mod_dir/$i

files=JavaBridge.war
mkdir -p $RPM_BUILD_ROOT/%{tomcat_webapps}
for i in $files; 
  do cp $mod_dir/$i $RPM_BUILD_ROOT/%{tomcat_webapps}
  rm -f $mod_dir/$i; 
  echo %{tomcat_webapps}/$i >>filelist
done

mkdir -p $RPM_BUILD_ROOT/etc/php.d
cat java.ini  >$RPM_BUILD_ROOT/etc/php.d/java.ini
echo /etc/php.d/java.ini >>filelist

mkdir $RPM_BUILD_ROOT/$mod_dir/lib
echo $mod_dir/lib >>filelist

# server also contains the server documentation
mv server server.backup
mkdir server
cp -r server.backup/documentation server
cp server.backup/RunJavaBridge.c server
cp server.backup/RunMonoBridge.c server
cp server.backup/natcJavaBridge.c server
(cd server.backup; find php -name "*.java" -print | cpio -dp ../server)
cp server.backup/src.zip server

%clean
rm -rf $RPM_BUILD_ROOT

%post
if test -f /etc/selinux/config; then
  if test -d /etc/selinux/%{__policy_tree}/modules; then 
	/sbin/service httpd stop > /dev/null 2>&1
	/sbin/service %{tomcat_name} stop > /dev/null 2>&1
	%{_sbindir}/semodule -i %{_docdir}/%{name}-%{version}/security/module/php-java-bridge.pp
	%{_sbindir}/semodule -i %{_docdir}/%{name}-%{version}/security/module/php-java-bridge-tomcat.pp
	chcon -t javabridge_exec_t %{_libdir}/php/modules/RunJavaBridge
	chcon -t bin_t %{_libdir}/php/modules/java
	/sbin/service httpd start > /dev/null 2>&1
	/sbin/service %{tomcat_name} start > /dev/null 2>&1
  else
	te=/etc/selinux/%{__policy_tree}/src/policy/domains/program/php-java-bridge.te
	fc=/etc/selinux/%{__policy_tree}/src/policy/file_contexts/program/php-java-bridge.fc
	echo "SECURITY ENHANCED LINUX"
	echo "-----------------------"
	echo "You are running a SELinx system. Please install the policy sources:"
	echo "rpm -i selinux-policy-%{__policy_tree}-sources-*.rpm"
	echo "sh %{_docdir}/%{name}-%{version}/security/update_policy.sh \\"
	echo "					/etc/selinux/%{__policy_tree}/src/policy"
	echo "Please see README document for more information."
	echo
  fi
fi
if test -d /var/www/html &&  ! test -e /var/www/html/JavaBridge; then
  ln -fs %{tomcat_webapps}/JavaBridge /var/www/html/;
fi
echo "PHP/Java Bridge installed. Start with:"
echo "service %{tomcat_name} restart"
echo "service httpd restart"
echo
exit 0

%post devel
exit 0

%preun
if [ $1 = 0 ]; then
	/sbin/service httpd stop > /dev/null 2>&1
	if test -d /etc/selinux/%{__policy_tree}/modules; then 
		%{_sbindir}/semodule -r javabridge
		%{_sbindir}/semodule -r javabridge_tomcat
	fi
	if test -e /var/www/html/JavaBridge && test -e %{tomcat_webapps}/JavaBridge && test %{tomcat_webapps}/JavaBridge -ef /var/www/html/JavaBridge; then
		rm -f /var/www/html/JavaBridge;
	fi
	rm -rf %{tomcat_webapps}/JavaBridge %{tomcat_webapps}/work/*
	/sbin/service httpd start > /dev/null 2>&1
fi

%preun devel
if [ $1 = 0 ]; then
	/bin/true
fi


%files -f filelist
%defattr(-,root,root)
%attr(6111,apache,apache) %{_libdir}/php/modules/RunJavaBridge
%attr(755,root,root) %{_libdir}/php/modules/JavaBridge.jar
%attr(-,tomcat,tomcat) %{tomcat_webapps}/JavaBridge.war
%doc README README.GCJ FAQ.html COPYING CREDITS NEWS test.php INSTALL.J2EE INSTALL.LINUX security 

%files mono -f filelist-mono
%defattr(-,root,root)
%attr(111,root,root) %{_libdir}/php/modules/RunMonoBridge
%attr(755,root,root) %{_libdir}/php/modules/MonoBridge.exe
%doc README.MONO+NET COPYING CREDITS NEWS tests.mono+net

%files devel -f filelist-devel
%defattr(-,root,root)
%attr(755,root,root) %{shared_java}/JavaBridge.jar
%doc FAQ.html CREDITS README.MONO+NET ChangeLog README PROTOCOL.TXT COPYING server documentation examples php_java_lib NEWS INSTALL.LINUX
