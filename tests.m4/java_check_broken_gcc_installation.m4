AC_DEFUN([JAVA_CHECK_BROKEN_GCC_INSTALLATION],[
  AC_MSG_CHECKING([for broken gcc installation])
  AC_CACHE_VAL(have_broken_gcc_installation,[
    ver3="`gcc --version | head -1 | awk '{print ($3 >="3.0")}'`"
    lgcc64="`gcc -m64 -print-libgcc-file-name`"
    lgcc32="`gcc -m32 -print-libgcc-file-name`"
    lgccs64="`gcc -m64 -print-file-name=libgcc_s.so`"
    lgccs32="`gcc -m32 -print-file-name=libgcc_s.so`"
    have_broken_gcc_installation=yes
    if test $ver3 = "1"; then 
     have_broken_gcc_installation=no
    elif test $lgcc64 = $lgcc32  || test $lgccs64 != $lgccs32; then
     have_broken_gcc_installation=no
    fi
])

  if test "$have_broken_gcc_installation" = "yes"; then
	AC_MSG_RESULT(yes)
  else
	AC_MSG_RESULT(no)
  fi
])
