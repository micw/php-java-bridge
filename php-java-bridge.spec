#-*- mode: rpm-spec; tab-width:4 -*-
%define version 2.0.8pre4
%define release 1
%define PHP_MAJOR_VERSION %(LANG=C rpm -q --queryformat "%{VERSION}" php | sed 's/\\\..*$//')
%define have_sysconfig_java %(test -s /etc/sysconfig/java && echo 1 || echo 0)

Name: php-java-bridge
Summary: PHP Hypertext Preprocessor to Java Bridge
Group: Development/Languages
Version: %{version}
Release: %{release}
License: The PHP license (see "LICENSE" file included in distribution)
URL: http://www.sourceforge.net/projects/php-java-bridge
Source0: http://osdn.dl.sourceforge.net/sourceforge/php-java-bridge/php-java-bridge_%{version}.tar.bz2


BuildRequires: php-devel >= 4.3.2
BuildRequires: gcc >= 3.2.3
BuildRequires: httpd make 
BuildRequires: libtool >= 1.4.3
BuildRequires: automake >= 1.6.3
BuildRequires: autoconf >= 2.57
%if %{have_sysconfig_java} == 1
BuildRequires: j2sdk >= 1.4.1
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
Requires: jre >= 1.4.0
Provides: php-java-bridge



BuildRoot: /var/tmp/php-java-bridge-%{version}

%description 
Java module/extension for the PHP script language.

%prep
echo Building for PHP %{PHP_MAJOR_VERSION}. have_sysconfig_java: %{have_sysconfig_java}.

%setup

%build
set -x
PATH=/bin:/usr/bin
LD_LIBRARY_PATH=/lib:/usr/lib

# calculate java dir
if test -s /etc/sysconfig/java; then
java_dir=`head -1 /etc/sysconfig/java`
else
pkgid=`rpm -q --whatprovides java-devel --queryformat "%{PKGID} %{VERSION}\n" | sed 's/\./0/g;s/_/./' |sort -r -k 2,2 -n | head -1 | awk '{print $1}'`
jdk=`rpm  -q --pkgid $pkgid`
java=`rpm -ql $jdk | grep 'bin/java$' | head -1`
java_dir=`dirname $java`
java_dir=`dirname $java_dir`
if test X`basename $java_dir` = Xjre; then
  java_dir=`dirname $java_dir`;
fi
fi
echo "using java_dir: $java_dir"
if test X$java_dir = X; then echo "ERROR: java not installed" >2; exit 1; fi

phpize
./configure --prefix=/usr --with-java=$java_dir --disable-servlet
make

%install
rm -rf $RPM_BUILD_ROOT

%makeinstall | tee install.log
echo >filelist

mod_dir=`cat install.log | sed -n '/Installing shared extensions:/s///p' | awk '{print $1}'`
files='JavaBridge.jar java.so libnatcJavaBridge.so RunJavaBridge'
mkdir -p $RPM_BUILD_ROOT/$mod_dir
for i in $files; 
  do cp $mod_dir/$i $RPM_BUILD_ROOT/$mod_dir/$i; 
  rm -f $mod_dir/$i; 
  echo $mod_dir/$i >>filelist
done

mkdir -p $RPM_BUILD_ROOT/etc/php.d
cat java.ini | sed 's|^;java\.java_home[\t =].*$|java.java_home = @JAVA_HOME@|; s|^;java\.java[\t =].*$|java.java = @JAVA_JAVA@|' >$RPM_BUILD_ROOT/etc/php.d/java.ini
echo /etc/php.d/java.ini >>filelist

mkdir -p $RPM_BUILD_ROOT/usr/sbin
cp php-java-bridge $RPM_BUILD_ROOT/usr/sbin
chmod +x $RPM_BUILD_ROOT/usr/sbin/php-java-bridge
echo /usr/sbin/php-java-bridge >>filelist

mkdir -p $RPM_BUILD_ROOT/etc/init.d
cp php-java-bridge.service $RPM_BUILD_ROOT/etc/init.d/php-java-bridge
chmod +x $RPM_BUILD_ROOT/etc/init.d/php-java-bridge
echo /etc/init.d/php-java-bridge >>filelist

mkdir $RPM_BUILD_ROOT/$mod_dir/lib
echo $mod_dir/lib >>filelist

%clean
rm -rf $RPM_BUILD_ROOT
%post
# calculate java_dir again
pkgid=`rpm -q --whatprovides jre --queryformat "%{PKGID} %{VERSION}\n" | sed 's/\./0/g;s/_/./' |sort -r -k 2,2 -n | head -1 | awk '{print $1}'`
jre=`rpm  -q --pkgid $pkgid`
java=`rpm -ql $jre | grep 'bin/java$' | head -1`
java_dir=`dirname $java`
java_dir=`dirname $java_dir`
if test X`basename $java_dir` = Xjre; then
  java_dir=`dirname $java_dir`;
fi
export java_dir java
ed -s /etc/php.d/java.ini <<EOF2
/@JAVA_HOME@/s||${java_dir-UNKNOWN}|
/@JAVA_JAVA@/s||${java-UNKNOWN}|
w
q
EOF2

chkconfig php-java-bridge on
echo "PHP/Java Bridge installed. Start with:"
echo "service php-java-bridge restart"
echo
if test -f /etc/selinux/config; then
	te=/etc/selinux/%{__policy_tree}/src/policy/domains/program/php-java-bridge.te
	fc=/etc/selinux/%{__policy_tree}/src/policy/file_contexts/program/php-java-bridge.fc
    echo "SECURITY ENHANCED LINUX"
    echo "-----------------------"
	echo "You are running a SELinx system. Please install the policy sources:"
	echo "rpm -i selinux-policy-%{__policy_tree}-sources-*.rpm"
	echo "sh %{_docdir}/php-java-bridge-%{version}/update_policy.sh \\"
    echo "                          /etc/selinux/%{__policy_tree}/src/policy"
	echo "Please see INSTALL and README documents for more information."
fi

%preun
chkconfig php-java-bridge off

%files -f filelist
%defattr(-,root,root)
%doc README README.GNU_JAVA INSTALL INSTALL.WINDOWS LICENSE ChangeLog test.php php-java-bridge.te php-java-bridge.fc update_policy.sh
