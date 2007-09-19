#-*- mode: rpm-spec; tab-width:4 -*-
%define version 2.1.0
%define release 1
Name: lucene4php
Summary: The lucene library for the PHP Hypertext Preprocessor
Group: Development/Languages
Version: %{version}
Release: %{release}
License: LGPL
URL: http://www.sourceforge.net/projects/php-java-bridge
Source0: http://osdn.dl.sourceforge.net/sourceforge/php-java-bridge/lucene4php_%{version}.tar.gz


BuildRequires: php-java-bridge

Requires: php >= 4.3.2
Requires: libgcj
Requires: php-java-bridge

BuildRoot: %{_tmppath}/%{name}-root

%description 
lucene module/extension for the PHP script language. 

%prep

%setup

%build
PATH=/bin:%{_bindir}
LD_LIBRARY_PATH=/lib:%{_libdir}

%install
rm -rf $RPM_BUILD_ROOT

%makeinstall
echo %{_datadir}/pear/lucene >filelist

%clean
rm -rf $RPM_BUILD_ROOT

%post
exit 0

%preun

%files -f filelist
%defattr(-,root,root)
%doc README COPYING.LIB examples
