#-*- mode: rpm-spec; tab-width:4 -*-
%define version 2.0.7pre
%define release 1
Name: php-java-bridge
Summary: PHP Hypertext Preprocessor to Java Bridge
Group: Development/Languages
Version: %{version}
Release: %{release}
Copyright: The PHP license (see "LICENSE" file included in distribution)
URL: http://www.sourceforge.net/projects/php-java-bridge
Source0: http://osdn.dl.sourceforge.net/sourceforge/php-java-bridge/php-java-bridge_%{version}.tar.bz2
BuildRequires: php-devel >= 4.3.6
BuildRequires: gcc >= 3.3.3
BuildRequires: httpd j2sdk
Requires: php >= 4.3.2
Requires: httpd 
Requires: j2re >= 1.4.0
Provides: php-java-bridge



BuildRoot: /var/tmp/php-java-bridge-%{version}

%description 
Java module/extension for the PHP script language.

%prep

%setup

%build
set -x
PATH=/bin:/usr/bin
LD_LIBRARY_PATH=/lib:/usr/lib

# calculate java dir
java_dir=`head -1 /etc/sysconfig/java`
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
cat <<EOF >$RPM_BUILD_ROOT/etc/php.d/java.ini
extension = java.so
[java]
java.log_level=1
java.log_file=/var/log/php-java-bridge.log

# Comment out the following line if you want to start java
# automatically as a sub-process of the Apache 2.0 service or if you
# have already started multicast backends for failover/load balancing.
java.socketname=/var/run/.php-java-bridge_socket

EOF
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
pkgid=`rpm -q --whatprovides j2re --queryformat "%{PKGID} %{VERSION}\n" | sed 's/\./0/g;s/_/./' |sort -r -k 2,2 -n | head -1 | awk '{print $1}'`
jre=`rpm  -q --pkgid $pkgid`
java=`rpm -ql $jre | grep 'bin/java$' | head -1`

# Do not rely on sysconfig anymore but use the most recent rpm, see
# pkgid above. The reason is that we prefer 1.4.2 or jdk1.5 over
# old 1.4.1 installations. When IBM/RedHat ships a 1.4.2_02 or 1.5
# RPM, we'll change it back.

# if test -s /etc/sysconfig/java; then
# # IBM and RedHat 
# 	java_dir=`head -1 /etc/sysconfig/java`
# else
# Sun JDK RPM nonsense
	java_dir=`dirname $java`
	java_dir=`dirname $java_dir`
	if test X`basename $java_dir` = Xjre; then
		java_dir=`dirname $java_dir`;
	fi
# fi
cat <<EOF2 >>/etc/php.d/java.ini
java.java_home=$java_dir
java.java=$java
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
