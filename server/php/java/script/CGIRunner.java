/*-*- mode: Java; tab-width:8 -*-*/

package php.java.script;

import java.io.BufferedWriter;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.Reader;
import java.io.Writer;
import java.util.Hashtable;

import php.java.bridge.PhpProcedureProxy;
import php.java.bridge.SessionFactory;
import php.java.bridge.Util;


public class CGIRunner extends Thread {
	
    protected boolean running = true;
    protected Hashtable env;
    protected SessionFactory ctx;
    protected OutputStream out;
    protected Reader reader;
    
    private Process proc = null;


    protected Lock phpScript = new Lock();

    protected class Lock {
	private Object val = null;
	private boolean finish = false;
		
	public synchronized Object getVal() {
	    if(!finish && val==null)
		try {
		    wait();
		} catch (InterruptedException e) {
		    e.printStackTrace();
		}
	    return val;
	}
	public synchronized void setVal(Object val) {
	    this.val = val;
	    notify();
	}
	public synchronized void finish() {
	    finish = true;
	    notify();
	}
    }
    public CGIRunner(Reader reader, Hashtable env, SessionFactory ctx, OutputStream out) {
	super("HttpProxy");
	this.reader = reader;
	this.ctx = ctx;
	this.env = env;
	this.out = out;
    }

    private static final String locations[] = new String[] {"/usr/bin/php-cgi", "/usr/bin/php", "c:/php5/php-cgi.exe"};
    public void run() {
	   	try {
     		doRun();
	   		} catch (IOException e) {
	   			    Util.printStackTrace(e);
	   			} finally {
	   			    synchronized(this) {
	   			    	if(proc!=null) proc.destroy();

	   				notify();
	   				running = false;
	   				phpScript.finish();
	   			    }
	   			}
    }
    
    protected void doRun() throws IOException {
        Process proc = null;
    	int n;
            Runtime rt = Runtime.getRuntime();
            
            String php = System.getProperty("php.java.bridge.php_exec");
            if(php==null) {
            	File location;
            	for(int i=0; i<locations.length; i++) {
            		location = new File(locations[i]);
            		if(location.exists()) {php = location.getAbsolutePath(); break;}
            	}
            }
            if(php==null) php="php-cgi";
            
            String home = System.getProperty("user.home");
            File homeDir = home==null?null:new File(home);
	    proc = rt.exec(php, Util.hashToStringArray(env), homeDir);
	    try { proc.getErrorStream().close(); } catch (IOException e) {}

	    InputStream natIn = proc.getInputStream();
	    OutputStream natOut = proc.getOutputStream();
	    char[] cbuf = new char[Util.BUF_SIZE];
	    Writer writer = new BufferedWriter(new OutputStreamWriter(natOut));
	    while((n = reader.read(cbuf))!=-1) {
		writer.write(cbuf, 0, n);
	    }
	    writer.close();
	    byte[] buf = new byte[Util.BUF_SIZE];
	    Util.parseBody(buf, natIn, this.out);
	    natIn.close();
    }

    public synchronized void call(PhpProcedureProxy script) throws InterruptedException {
	phpScript.setVal(script);
	wait();
    }
	
    public PhpProcedureProxy getPhpScript() throws InterruptedException {
	return (PhpProcedureProxy)phpScript.getVal();
    }
	
    public synchronized void stopContinuation() {
	notify();
	if(running)
	    try {
		wait();
	    } catch (InterruptedException e) {
		Util.printStackTrace(e);
	    }
    }
}
