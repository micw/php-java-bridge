#-*- mode: rpm-spec; tab-width:4 -*-
%define version `cat VERSION`
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
Requires: httpd j2re
Provides: php-java-bridge



BuildRoot: /var/tmp/php-java-bridge-%{version}

%description 
The PHP/Java bridge allows one to access java based applications running in a java application server running on the local host.  The PHP/Java bridge communicates with the application server through local sockets using an efficient communication protocol.  This means that only one JVM runs to serve all clients within a multi-process HTTP-Server.  Each client process communicates with a corresponding thread spawned by the running application server.  

%prep

%setup

%build
set -x
PATH=/bin:/usr/bin
LD_LIBRARY_PATH=/lib:/usr/lib

# calculate java dir
java_dir=`head -1 /etc/sysconfig/java`
phpize
./configure --prefix=/usr --with-java=$java_dir
make CFLAGS="-DNDEBUG"

%install
rm -rf $RPM_BUILD_ROOT

%makeinstall | tee install.log
echo >filelist

mod_dir=`cat install.log | sed -n '/Installing shared extensions:/s///p' | awk '{print $1}'`
files="JavaBridge.class java.so libnatcJavaBridge.so"
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

# comment out the following line if you want to start java
# automatically as a sub-process of the Apache 2.0 
# service -- not recommended.
java.socketname=/tmp/.php-java-bridge


EOF
echo /etc/php.d/java.ini >>filelist

mkdir -p $RPM_BUILD_ROOT/usr/bin
cp php-java-bridge $RPM_BUILD_ROOT/usr/bin
chmod +x $RPM_BUILD_ROOT/usr/bin/php-java-bridge
echo /usr/bin/php-java-bridge >>filelist

mkdir -p $RPM_BUILD_ROOT/etc/init.d
cp php-java-bridge.service $RPM_BUILD_ROOT/etc/init.d/php-java-bridge
chmod +x $RPM_BUILD_ROOT/etc/init.d/php-java-bridge
echo /etc/init.d/php-java-bridge >>filelist


%clean
rm -rf $RPM_BUILD_ROOT
%post
# calculate java_dir again
jre=`rpm -q --whatprovides j2re`
java=`rpm -ql $jre | grep 'bin/java$'`
java_dir=`head -1 /etc/sysconfig/java`
cat <<EOF2 >>/etc/php.d/java.ini
java.java_home=$java_dir
java.java=$java
EOF2

chkconfig php-java-bridge on
service php-java-bridge start

%preun
service php-java-bridge stop
chkconfig php-java-bridge off

%files -f filelist
%defattr(-,root,root)
%doc README INSTALL LICENSE ChangeLog

