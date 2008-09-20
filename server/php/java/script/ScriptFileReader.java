package php.java.script;

import java.io.File;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.Reader;
import java.io.StringReader;

import php.java.bridge.Util;

public class ScriptFileReader extends FileReader {

    private File tempfile;

    private static File getFile(File file, Reader reader) throws IOException {
	FileOutputStream fout = new FileOutputStream(file);
	OutputStreamWriter writer = new OutputStreamWriter(fout);
	char[] cbuf = new char[Util.BUF_SIZE];
	int length;
	while((length=reader.read(cbuf, 0, cbuf.length))>0) 
	    writer.write(cbuf, 0, length);
	writer.close();
	return file;
    }
    public ScriptFileReader(File file, Reader r) throws IOException {
	super(getFile(file, r));
	tempfile = file;
    }
    public ScriptFileReader(File file, String str) throws IOException {
	this (file, new StringReader(str));
    }
    public ScriptFileReader(File file) throws IOException {
	super(file);
	tempfile = file;
    }
    public File getFile() {
	return tempfile;
    }
}
