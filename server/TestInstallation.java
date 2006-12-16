/*-*- mode: Java; tab-width:8 -*-*/

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.PrintWriter;
import java.util.StringTokenizer;


public class TestInstallation {

    public static void main(String[] args) throws IOException {
	String os = null;
	String separator = "/-+.,;: ";
	try {
	    String val = System.getProperty("os.name").toLowerCase();
	    StringTokenizer t = new StringTokenizer(val, separator);
	    os = t.nextToken();
	} catch (Throwable t) {/*ignore*/}
	if(os==null) os="unknown";
        File ext = null;
        try {
            ext = (args.length==0) ? new File(new File(System.getProperty("java.class.path")).getParentFile().getAbsoluteFile(), "ext") : new File(args[0], "ext");
        } catch (Throwable t) {
            ext = (args.length==0) ? new File("ext") : new File(args[0], "ext");         
        }
	if(!ext.isDirectory()) ext.mkdirs();
	File base = ext.getParentFile();
	
        ClassLoader loader = TestInstallation.class.getClassLoader();
        
        if("windows".equals(os)) {
	    PrintWriter out = new PrintWriter(new FileOutputStream(new File(base, "testphp.bat").getAbsoluteFile()));
	    out.println(".\\php-cgi -c php.ini test.php >RESULT.html");
	    out.println("@echo off");
	    out.println("");
	    out.println("echo Now check the RESULT.html.");
	    out.println("echo Read the INSTALL.J2EE document.");
	    out.println("pause");
	    out.close();
	    out = new PrintWriter(new FileOutputStream(new File(base, "php.ini").getAbsoluteFile()));
	    out.println("extension_dir=ext");
	    out.println("extension=php_java.dll");
	    out.println("[java]");
	    out.println("java.java_home=" + System.getProperty("java.home"));
	    out.println("java.java=" +(new File(System.getProperty("java.home"), "bin"+File.separator+"javaw.exe")));
	    out.println("java.log_level=2");
	    out.println(";java.log_file=ext/JavaBridge.log");
	    out.close();
  	    InputStream in = loader.getResourceAsStream("WEB-INF/cgi/php-cgi-x86-windows.exe");
  	    extractFile(in, new File(base, "php-cgi.exe").getAbsoluteFile());
  	    in.close();
  	    in = loader.getResourceAsStream("WEB-INF/cgi/java-x86-windows.dll");
  	    extractFile(in, new File(ext, "php_java.dll").getAbsoluteFile());
  	    in.close();
  	    in = loader.getResourceAsStream("WEB-INF/cgi/php5ts.dll");
  	    extractFile(in, new File(base, "php5ts.dll").getAbsoluteFile());
  	    in.close();
        } else if("linux".equals(os)) {
	    PrintWriter out = new PrintWriter(new FileOutputStream(new File(base, "testphp.sh").getAbsoluteFile()));
	    out.println("#!/bin/sh");
	    out.println("chmod +x php-cgi");
	    out.println("./php-cgi -c php.ini test.php >RESULT.html || echo 'test failed!'");
	    out.println("");
	    out.println("echo 'Now check the RESULT.html.'");
	    out.println("echo 'Read the INSTALL.LINUX or INSTALL.J2EE document.'");
	    out.close();
	    out = new PrintWriter(new FileOutputStream(new File(base, "php.ini").getAbsoluteFile()));
	    out.println("include_path=.");
	    out.println("extension_dir=ext");
	    out.println("extension=java.so");
	    out.println("[java]");
	    out.println("java.java_home=" + System.getProperty("java.home"));
	    out.println("java.java=" +(new File(System.getProperty("java.home"), "bin"+File.separator+"java")));
	    out.println("java.log_level=2");
	    out.println(";java.log_file=ext/JavaBridge.log");
	    out.close();
  	    InputStream in = loader.getResourceAsStream("WEB-INF/cgi/php-cgi-i386-linux");
  	    extractFile(in, new File(base, "php-cgi").getAbsoluteFile());
  	    in.close();
  	    in = loader.getResourceAsStream("WEB-INF/cgi/java-i386-linux.so");
  	    extractFile(in, new File(ext, "java.so").getAbsoluteFile());
  	    in.close();
        } else if("sunos".equals(os)) {
	    PrintWriter out = new PrintWriter(new FileOutputStream(new File(base, "testphp.sh").getAbsoluteFile()));
	    out.println("#!/bin/sh");
	    out.println("chmod +x php-cgi");
	    out.println("./php-cgi -c php.ini test.php >RESULT.html || echo 'test failed!'");
	    out.println("");	    
	    out.println("echo 'Now check the RESULT.html.'");
	    out.println("echo 'Read the INSTALL.J2EE document.'");
	    out.close();
	    out = new PrintWriter(new FileOutputStream(new File(base, "php.ini").getAbsoluteFile()));
	    out.println("include_path=.");
	    out.println("extension_dir=ext");
	    out.println("extension=java.so");
	    out.println("[java]");
	    out.println("java.java_home=" + System.getProperty("java.home"));
	    out.println("java.java=" +(new File(System.getProperty("java.home"), "bin"+File.separator+"java")));
	    out.println("java.log_level=2");
	    out.println(";java.log_file=ext/JavaBridge.log");
	    out.close();
  	    InputStream in = loader.getResourceAsStream("WEB-INF/cgi/php-cgi-x86-sunos");
  	    extractFile(in, new File(base, "php-cgi").getAbsoluteFile());
  	    in.close();
  	    in = loader.getResourceAsStream("WEB-INF/cgi/java-x86-sunos.so");
  	    extractFile(in, new File(ext, "java.so").getAbsoluteFile());
  	    in.close();
        } else { 
	    System.err.println("Unknown OS: " + os);
	    System.err.println("Will use the pure PHP implementation instead.");
	    PrintWriter out = new PrintWriter(new FileOutputStream(new File(base, "testphp.sh").getAbsoluteFile()));
	    out.println("#!/bin/sh");
	    out.println((new File(System.getProperty("java.home"), "bin"+File.separator+"java")) +" -jar ext/JavaBridge.jar SERVLET:8080 &");
	    out.println("echo Java servlet engine started, waiting 5 seconds");
	    out.println("sleep 5");
	    out.println("php -c php.ini test.php >RESULT.html || echo 'test failed!'");
	    out.println("kill $!");
	    out.println("");	    
	    out.println("echo 'Now check the RESULT.html.'");
	    out.println("echo 'Read the INSTALL.J2EE document.'");
	    out.close();
	    out = new PrintWriter(new FileOutputStream(new File(base, "php.ini").getAbsoluteFile()));
	    out.println("include_path=.");
	    out.close();
	}
        extractPurePhpJavaBridge(base, loader);
	InputStream in = loader.getResourceAsStream("WEB-INF/lib/JavaBridge.jar");
	extractFile(in, new File(ext, "JavaBridge.jar").getAbsoluteFile());
	in.close();
	in = loader.getResourceAsStream("test.php");
	extractFile(in, new File(base, "test.php").getAbsoluteFile());
	in.close();
    }
    private static void extractPurePhpJavaBridge(File base, ClassLoader loader) throws IOException {
	String files[] = {"Client.php", "GlobalRef.php", "Java.php", "JavaProxy.php", "NativeParser.php", "Options.php", "Parser.php", "Protocol.php", "SimpleParser.php", "README" };
	File javaDir = new File(base, "java");
	if(!javaDir.exists()) javaDir.mkdir();
	for(int i=0; i<files.length; i++) {
	    InputStream in = loader.getResourceAsStream("java/"+files[i]);
	    extractFile(in, new File(javaDir, files[i]).getAbsoluteFile());
	    in.close();
	}
    }
    private static void extractFile(InputStream in, File target) throws IOException {
	byte[] buf = new byte[8192];
	FileOutputStream out = new FileOutputStream(target);
	int c;
  	while((c = in.read(buf))!=-1) {
	    out.write(buf, 0, c);
	}
	out.close();
    }
}
