/*-*- mode: Java; tab-width:8 -*-*/

package php.java.bridge;

/*
 * Copyright (C) 2006 Jost Boekemeier
 *
 * Permission is hereby granted, free of charge, to any person obtaining a
 * copy of this software and associated documentation files (the "Software"),
 * to deal in the Software without restriction, including without limitation
 * the rights to use, copy, modify, merge, publish, distribute, sublicense,
 * and/or sell copies of the Software, and to permit persons to whom the
 * Software is furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT.  IN NO EVENT SHALL
 * THE COPYRIGHT HOLDER(S) OR AUTHOR(S) BE LIABLE FOR ANY CLAIM, DAMAGES OR
 * OTHER LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE,
 * ARISING FROM, OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR
 * OTHER DEALINGS IN THE SOFTWARE.
 */

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.PrintWriter;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.net.JarURLConnection;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.Enumeration;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.StringTokenizer;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.regex.Pattern;
import java.util.zip.ZipException;

class Snarf {

  private String baseDir = null;
  private String base = null;
  private String pkg = null;
  
  private File jarName;
  private boolean isRtJar = false;
  private PrintWriter all;

  private URLClassLoader loader;      

  public static void main(String[] args) throws Exception {
      (new Snarf(args)).run();
  }

  public Snarf(String [] args) {
      if(args.length>0) this.baseDir = args[0];
      if(args.length>1) this.pkg = args[1];
  }
    private void copyFile(File source, File target) throws IOException {
        byte[] buf = new byte[8192];
        FileInputStream in = new FileInputStream(source);
        FileOutputStream out = new FileOutputStream(target);
        int c;
	while((c = in.read(buf))!=-1) {
            out.write(buf, 0, c);
        }
        in.close();
        out.close();
    }
    private File getFile(String file) {       
      if(base!=null) return new File(base, file);
      return new File(file);
    }
    private void addURLs(List urls, List files, String bootPath) throws MalformedURLException {
      StringTokenizer tokenizer = new StringTokenizer(bootPath, File.pathSeparator);
      while(tokenizer.hasMoreTokens()) {
	  String token = tokenizer.nextToken();
	  URL url;
	  try {
	      url = new URL(token);
	  }catch (MalformedURLException e) {
	      url = new URL("file", null, token);
	  }
	  File file = new File(url.getFile());
	  if(!file.exists()  || !file.isFile() || !file.canRead()) continue;
	  if(!token.endsWith(".jar")) continue;
	  url = new URL("jar:"+url.toExternalForm()+"!/");
	  urls.add(url);
	  files.add(file);
      }
     }      
    private void run() throws IOException, ClassNotFoundException {
      String bootPath = System.getProperty("sun.boot.class.path");
      if(pkg!=null) {
	bootPath += File.pathSeparator+pkg;
      }
      base=baseDir;
      File baseFile = new File(base);
      File javaFile = new File(baseFile, "java");
      if(!javaFile.exists()) javaFile.mkdirs();
      writePhpJava();
      List fileList = new LinkedList();
      List urlList = new LinkedList();
      addURLs(urlList, fileList, bootPath);
      URL[] urls = (URL[]) urlList.toArray(new URL[urlList.size()]);
      File[] files = (File[]) fileList.toArray(new File[urls.length]);
      loader = new URLClassLoader(urls);      
      for(int i=0; i<urls.length; i++) {
	  URL url = urls[i];
	  File file = files[i];
	  String jarname = file.getName();

	  // the apple VM is incompatible with the SUN and GNU implementations
	  if(jarname.equals("classes.jar")) jarname = "rt.jar";

	  String name = jarname.substring(0, jarname.length()-4); // strip off .jar
	  name = name.toLowerCase();
	  baseFile = (baseDir!=null) ? new File(baseDir, name) : new File(name);
	  if(!baseFile.exists()) baseFile.mkdirs();
	  base=baseFile.getAbsolutePath();

	  if(!(isRtJar = (jarname.equals("rt.jar"))) 
	     copyFile(file, jarName=new File(base, jarname));

	  all=new PrintWriter(new FileOutputStream(new File(base, "All.php")));
	  all.println("<?php");
	  for(Iterator ii = getClasses(url).iterator(); ii.hasNext(); ) {
	      String className = (String) ii.next();
	      int mod;
	      try {
		  Class clazz = Class.forName(className, false, loader);
		  mod = clazz.getModifiers();
	      } catch (Exception e) {
		  e.printStackTrace();
		  mod=~0;
	      }
	      if(Modifier.isPublic(mod))
		  writeClass(url.toExternalForm(), className);
	  }
	  all.println("?>");
	  all.close();
      }
  }

  private void writeMethod(PrintWriter writer, String method) {
//      writer.print("  function ");
//      writer.print(method);
//      writer.print("() {");
//      writer.print("$this->__call('");
//      writer.print(method);
//      writer.print("', ");
//      writer.print("func_get_args()");
//      writer.println(");}");
  }
  private void writeMethods(PrintWriter writer, String className) throws ClassNotFoundException {
      Class clazz = Class.forName(className, false, loader);
      Method[] methods = clazz.getMethods();
      HashSet methodNames = new HashSet(methods.length);
      for(int i=0; i<methods.length; i++) {
	  Method method = methods[i];
	  String name = method.getName();
	  int mod = method.getModifiers();
	  if(Modifier.isPublic(mod) && !Modifier.isStatic(mod))
	      methodNames.add(name);
      }
      for(Iterator ii = methodNames.iterator(); ii.hasNext(); ) {
	  writeMethod(writer, (String) ii.next());
      }
  }
  private void writeProcedures(PrintWriter writer, String className) throws ClassNotFoundException {
      Class clazz = Class.forName(className, false, loader);
      Method[] methods = clazz.getMethods();
      HashSet methodNames = new HashSet(methods.length);
      for(int i=0; i<methods.length; i++) {
	  Method method = methods[i];
	  String name = method.getName();
	  int mod = method.getModifiers();
	  if(Modifier.isPublic(mod) && Modifier.isStatic(mod))
	      methodNames.add(name);
      }
      for(Iterator ii = methodNames.iterator(); ii.hasNext(); ) {
	  writeMethod(writer, (String) ii.next());
      }
  }
  private static final Pattern subClass = Pattern.compile("\\$");
  private void writeClass(String file, String className) throws FileNotFoundException, ClassNotFoundException {
      String name = className.replace('.', '_');
      name=subClass.matcher(name).replaceAll("__");
      PrintWriter writer = createClass(name);
      writeClass(writer, file, className, name);
      writer.println("?>");
      writer.close();
  }
  private void addIndex(String name) {
      all.print("require_once('");
      all.print(name);
      all.println("');");
  }
  private PrintWriter createClass(String name) throws FileNotFoundException {
      name = name+".php";
      addIndex(name);
      FileOutputStream out = new FileOutputStream(getFile(name));
      return new PrintWriter(out);
  }

  private void writePhpJava() throws FileNotFoundException {
      
    FileOutputStream stream = new FileOutputStream(getFile("java/Bridge.php"));
    PrintWriter out = new PrintWriter(stream);
    out.println("<?php");
    out.println("/* auto-generated file, do not edit */");
    out.println("");
    out.println("if (!extension_loaded('java')) {");
    out.println("  if (!(include_once('java/Java.php'))&&!(PHP_SHLIB_SUFFIX=='so' && dl('java.so'))&&!(PHP_SHLIB_SUFFIX=='dll' && dl('php_java.dll'))) {");
    out.println("    echo 'java extension not installed.';");
    out.println("    exit(2);");
    out.println("  }");
    out.println("}");
    out.println("");
    out.println("function java_coerce_value($value) {");
    out.println("  if($value instanceof java_Bridge) return $value->java;");
    out.println("  return $value;");
    out.println("}");
    out.println("function java_require_once($jar) {");
    out.println("  static $map = array();");
    out.println("  if(!isset($map[$jar])) {");
    out.println("    $map[$jar]=true;");
    out.println("    @java_require($jar);");
    out.println("  }");
    out.println("}");
    out.println("");
    out.println("class java_Bridge {");
    out.println("  var $java;");
    out.println("  function __java_coerceArgs($array) {return array_map('java_coerce_value', $array);}");
    out.println("  function __java_coerceArg($arg) {return java_coerce_value($arg);}");
    out.println("  function __java_init($path) { } ");
    out.println("  function __get($arg) { if(!is_null($this->java)) return $this->java->__get($this->__java_coerceArg($arg)); }");
    out.println("  function __set($key, $val) { if(!is_null($this->java)) return $this->java->__set($this->__java_coerceArg($key), $this->__java_coerceArg($val)); }");
    out.println("  function __call($m, $a) { if(!is_null($this->java)) return $this->java->__call($m, $this->__java_coerceArgs($a)); }");
    out.println("  function __toString() { if(!is_null($this->java)) return $this->java->__toString(); }");
    out.println("}");
    out.println("?>");
    out.close();
  }
  private void writeClass(PrintWriter out, String file, String clazz, String phpClazz) throws ClassNotFoundException {
      out.println("<?php");
      out.println("/* auto-generated file, do not edit */");
      out.println("/* Use the command:");
      out.println(" * java -jar JavaBridge.jar --convert . "+file);
      out.println(" * to recreate it.");
      out.println(" */");
      out.println("");
      out.println("require_once(\'java/Bridge.php\');");
      if(!isRtJar) {
	  out.print("java_require_once('");
	  out.print(jarName.getAbsolutePath());
	  out.println("');");
      }
      out.println("");

      out.print("function ");
      out.print(phpClazz);
      out.println("() {");
      out.println("  static $clazz = null;");
      out.println("  if(is_null($clazz)) {");
      out.print("    $clazz = new ");
      out.print(phpClazz);
      out.print("___Class(");
      out.print("new JavaClass('");
      out.print(clazz);
      out.println("'));");
      out.println("  }");
      out.println("  return $clazz;");
      out.println("}");
      out.println("");
      out.print("class ");
      out.print(phpClazz);
      out.println("___Class extends java_Bridge {");
      out.println("  function __construct($java) {");
      out.println("    $this->java=$java;");
      out.println("  }");
      writeProcedures(out, clazz);
      out.println("}");
      out.println("");

      out.print("class ");
      out.print(phpClazz);
      out.print(" extends ");
      out.print(phpClazz);
      out.println("___Class {");
      out.println("  function __construct() {");
      out.println("    $args = $this->__java_coerceArgs(func_get_args());");
      out.print("    array_unshift($args, '");
      out.print(clazz);
      out.println("');");
      out.println("    $this->java = new Java($args);");
      out.println("  }");
      writeMethods(out, clazz);
      out.println("}");
      
  }
  
  private static List getClasses(URL url) throws IOException {
      LinkedList classes = new LinkedList();
      JarURLConnection conn = (JarURLConnection) url.openConnection();
      JarFile file;
      try {
	  file = conn.getJarFile();
      } catch (ZipException e) { return classes; }
      for(Enumeration ee = file.entries(); ee.hasMoreElements();) {
	  JarEntry entry = (JarEntry) ee.nextElement();
	  String name = entry.getName();
	  if(!name.endsWith(".class")) continue;
	  name=name.substring(0, name.length()-6);
	  classes.add(name.replace('/', '.')); 
    }
      return classes;
  }
}
