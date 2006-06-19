package php.java.bridge;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.PrintWriter;
import java.lang.reflect.Modifier;
import java.net.JarURLConnection;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.Enumeration;
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
  private PrintWriter all;

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
      if(!baseFile.exists()) baseFile.mkdirs();
      writePhpJava();
      List fileList = new LinkedList();
      List urlList = new LinkedList();
      addURLs(urlList, fileList, bootPath);
      URL[] urls = (URL[]) urlList.toArray(new URL[urlList.size()]);
      File[] files = (File[]) fileList.toArray(new File[urls.length]);
      URLClassLoader loader = new URLClassLoader(urls);      
      for(int i=0; i<urls.length; i++) {
	  URL url = urls[i];
	  File file = files[i];
	  String jarname = file.getName();
	  String name = jarname.substring(0, jarname.length()-4); // strip off .jar
	  baseFile = (baseDir!=null) ? new File(baseDir, name) : new File(name);
	  if(!baseFile.exists()) baseFile.mkdirs();
	  base=baseFile.getAbsolutePath();

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
		  writeClass(className);
	  }
	  all.println("?>");
	  all.close();
      }
  }

  private static final Pattern subClass = Pattern.compile("\\$");
  private void writeClass(String clazz) throws FileNotFoundException {
      String name = clazz.replace('.', '_');
      name=subClass.matcher(name).replaceAll("__");
      PrintWriter writer = createClass(name);
      writeHeader(writer, clazz, name);
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
    FileOutputStream stream = new FileOutputStream(getFile("php_Java.php"));
    PrintWriter out = new PrintWriter(stream);
    out.println("<?php");
    out.println("if (!extension_loaded('java')) {");
    out.println("  if (!(PHP_SHLIB_SUFFIX=='so' && dl('java.so'))&&!(PHP_SHLIB_SUFFIX=='dll' && dl('php_java.dll'))) {");
    out.println("    echo 'java extension not installed.';");
    out.println("    exit(2);");
    out.println("  }");
    out.println("}");
    out.println("");
    out.println("function java_coerce_value($value) {");
    out.println("  if($value instanceof php_Java) return $value->java;");
    out.println("  return $value;");
    out.println("}");
    out.println("function java_require_once($jar) {");
    out.println("  static $map = array();");
    out.println("  if(!isset($map[$jar])) {");
    out.println("    $map[$jar]=true;");
    out.println("    java_require($jar);");
    out.println("  }");
    out.println("}");
    out.println("");
    out.println("class php_Java {");
    out.println("  var $java;");
    out.println("  function __java_coerceArgs($array) {return array_map('java_coerce_value', $array);}");
    out.println("  function __java_coerceArg($arg) {return java_coerce_value($arg);}");
    out.println("  function __java_init($path) { } ");
    out.println("  function __get($arg) { if(!is_null($this->java)) return $this->java->__get($this->__java_coerceArg($arg)); }");
    out.println("  function __put($key, $val) { if(!is_null($this->java)) return $this->java->__put($this->__java_coerceArg($key), $this->__java_coerceArg($val)); }");
    out.println("  function __call($m, $a) { if(!is_null($this->java)) return $this->java->__call($m, $this->__java_coerceArgs($a)); }");
    out.println("  function __toString() { if(!is_null($this->java)) return $this->java->__toString(); }");
    out.println("}");
    out.println("?>");
    out.close();
  }
  private void writeHeader(PrintWriter out, String clazz, String phpClazz) {
      out.println("<?php");
      out.println("require_once(\'php_Java.php\');");
      out.print("java_require_once('");
      out.print(jarName.getAbsolutePath());
      out.println("');");
      out.println("");
      out.print("function ");
      out.print(phpClazz);
      out.println("() {");
      out.println("  static $clazz = null;");
      out.print("  if(is_null($clazz)) $clazz = new JavaClass('");
      out.print(clazz);
      out.println("');");
      out.println("  return $clazz;");
      out.println("}");
      out.println("");
      out.print("class ");
      out.print(phpClazz);
      out.println(" extends php_Java {");
      out.println("  function __construct() {");
      out.println("    $args = $this->__java_coerceArgs(func_get_args());");
      out.print("    array_unshift($args, '");
      out.print(clazz);
      out.println("');");
      out.println("    $this->java = new Java($args);");
      out.println("  }");
      out.println("}");
      out.println("?>");
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
