package php.java.script.servlet;

import java.io.File;
import java.io.FileReader;
import java.io.IOException;

public class ScriptFileReader extends FileReader {

    private File tempfile;

    public ScriptFileReader(File file) throws IOException {
	super(file);
	tempfile = file;
    }
    public File getFile() {
	return tempfile;
    }
}
