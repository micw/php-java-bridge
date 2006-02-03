#-*- mode: rpm-spec; tab-width:4 -*-
%define version 3.0.7rc
%define release 1
%define PHP_MAJOR_VERSION %(((LANG=C rpm -q --queryformat "%{VERSION}" php) || echo "4.0.0") | tail -1 | sed 's/\\\..*$//')
%define have_j2 %((rpm -q --whatprovides j2re || rpm -q --whatprovides j2sdk) >/dev/null && echo 1 || echo 0)
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


# PHP 4 or PHP 5
%if %{PHP_MAJOR_VERSION} == 4
Requires: php >= 4.3.2
Requires: php < 5.0.0
%else
Requires: php >= 5.0.4
%endif
Requires: httpd 

%if %{have_j2} == 1
Requires: j2re >= 1.4.2
%else
Requires: jre >= 1.4.0
%endif

BuildRoot: %{_tmppath}/%{name}-root

%description 
Java module/extension for the PHP script language.

%package standalone
Group: System Environment/Daemons
Summary: Standalone backend for the PHP/Java Bridge
Requires: php-java-bridge = %{version}
%description standalone
Standalone backend for the PHP/Java Bridge

%package tomcat
Group: System Environment/Daemons
Summary: Tomcat/J2EE backend for the PHP/Java Bridge
Requires: php-java-bridge = %{version}
Requires: tomcat5
Conflicts: php-java-bridge-standalone
%description tomcat
Tomcat/J2EE backend for the PHP/Java Bridge

%package devel
Group: Development/Libraries
Summary: PHP/Java Bridge development files and documentation
Requires: php-java-bridge = %{version}
%description devel
PHP/Java Bridge development files and documentation


%prep
echo Building for PHP %{PHP_MAJOR_VERSION}.

%setup

%build
set -x
PATH=/bin:/usr/bin
LD_LIBRARY_PATH=/lib:/usr/lib

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
./configure --prefix=/usr --with-java=$java_dir
make

%install
rm -rf $RPM_BUILD_ROOT

%makeinstall | tee install.log
echo >filelist
echo >filelist-standalone
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

files='JavaBridge.jar java.so libnatcJavaBridge.so RunJavaBridge'
mkdir -p $RPM_BUILD_ROOT/$mod_dir
for i in $files; 
  do cp $mod_dir/$i $RPM_BUILD_ROOT/$mod_dir/$i; 
  rm -f $mod_dir/$i; 
  echo $mod_dir/$i >>filelist
done

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

cat java-standalone.ini | sed 's|^;java\.java_home[\t =].*$|java.java_home = @JAVA_HOME@|; s|^;java\.java[\t =].*$|java.java = @JAVA_JAVA@|' >$RPM_BUILD_ROOT/etc/php.d/java-standalone.ini
echo /etc/php.d/java-standalone.ini >>filelist-standalone

mkdir -p $RPM_BUILD_ROOT/usr/sbin
cp php-java-bridge $RPM_BUILD_ROOT/usr/sbin
chmod +x $RPM_BUILD_ROOT/usr/sbin/php-java-bridge
echo /usr/sbin/php-java-bridge >>filelist-standalone

mkdir -p $RPM_BUILD_ROOT/etc/init.d
cp php-java-bridge.service $RPM_BUILD_ROOT/etc/init.d/php-java-bridge
chmod +x $RPM_BUILD_ROOT/etc/init.d/php-java-bridge
echo /etc/init.d/php-java-bridge >>filelist-standalone

mkdir $RPM_BUILD_ROOT/$mod_dir/lib
echo $mod_dir/lib >>filelist

%clean
rm -rf $RPM_BUILD_ROOT

%post
if test -f /etc/selinux/config; then
	echo "You are running SELinx. Please install the standalone or servlet backend."
    echo
else
    echo "PHP/Java Bridge installed. Start with:"
    echo "service httpd restart"
    echo
fi

%post tomcat
echo "PHP/Java Bridge tomcat backend installed. Start with:"
echo "service tomcat5 restart"
echo "service httpd restart"
echo
if test -f /etc/selinux/config; then
	te=/etc/selinux/%{__policy_tree}/src/policy/domains/program/php-java-bridge.te
	fc=/etc/selinux/%{__policy_tree}/src/policy/file_contexts/program/php-java-bridge.fc
    echo "SECURITY ENHANCED LINUX"
    echo "-----------------------"
	echo "You are running a SELinx system. Please install the policy sources:"
	echo "rpm -i selinux-policy-%{__policy_tree}-sources-*.rpm"
	echo "sh %{_docdir}/%{name}-tomcat-%{version}/update_policy.sh \\"
    echo "                          /etc/selinux/%{__policy_tree}/src/policy"
	echo "Please see INSTALL and README documents for more information."
    echo
fi

%post standalone
# calculate java_dir again
for i in jre j2re jdk j2sdk java; do 
package=`rpm -q --whatprovides $i --queryformat "%{PKGID} %{VERSION}\n"` && break
done
pkgid=`echo $package| sed 's/\./0/g;s/_/./' | sort -r -k 2,2 -n | head -1 | awk '{print $1}'`

jre=`rpm  -q --pkgid $pkgid`
java=`rpm -ql $jre | grep 'bin/java$' | head -1`
java_dir=`dirname $java`
java_dir=`dirname $java_dir`
if test X`basename $java_dir` = Xjre; then
  java_dir=`dirname $java_dir`;
fi
export java_dir java
ed -s /etc/php.d/java-standalone.ini <<EOF2
/@JAVA_HOME@/s||${java_dir-UNKNOWN}|
/@JAVA_JAVA@/s||${java-UNKNOWN}|
w
q
EOF2

chkconfig php-java-bridge on
echo "PHP/Java Bridge standalone backend installed. Start with:"
echo "service php-java-bridge restart"
echo "service httpd restart"
echo
if test -f /etc/selinux/config; then
	te=/etc/selinux/%{__policy_tree}/src/policy/domains/program/php-java-bridge.te
	fc=/etc/selinux/%{__policy_tree}/src/policy/file_contexts/program/php-java-bridge.fc
    echo "SECURITY ENHANCED LINUX"
    echo "-----------------------"
	echo "You are running a SELinx system. Please install the policy sources:"
	echo "rpm -i selinux-policy-%{__policy_tree}-sources-*.rpm"
	echo "sh %{_docdir}/%{name}-standalone-%{version}/update_policy.sh \\"
    echo "                          /etc/selinux/%{__policy_tree}/src/policy"
	echo "Please see INSTALL and README documents for more information."
    echo
fi

%preun standalone
if [ $1 = 0 ]; then
	/sbin/service php-java-bridge stop > /dev/null 2>&1
	/sbin/chkconfig --del php-java-bridge
fi

%preun tomcat
rm -rf %{tomcat_webapps}/JavaBridge

%files -f filelist
%defattr(-,root,root)
%doc README README.GNU_JAVA README.MONO+NET LICENSE CREDITS NEWS ChangeLog test.php test.php4 examples tests.php5 php_java_lib INSTALL.LINUX 

%files standalone -f filelist-standalone
%defattr(-,root,root)
%doc INSTALL LICENSE php-java-bridge.te php-java-bridge.fc update_policy.sh 

%files tomcat -f filelist-tomcat
%defattr(-,tomcat,tomcat)
%attr(-,root,root) /etc/php.d/java-servlet.ini
%doc %attr(-,root,root) INSTALL.J2EE LICENSE php-java-bridge.te php-java-bridge-tomcat.te php-java-bridge.fc update_policy.sh 

%files devel -f filelist-devel
%defattr(-,root,root)
%doc PROTOCOL.TXT LICENSE server/documentation documentation server/test

