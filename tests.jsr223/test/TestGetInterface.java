package test;

import java.io.ByteArrayOutputStream;
import java.io.Closeable;
import java.io.OutputStreamWriter;

import javax.script.Invocable;
import javax.script.ScriptEngine;
import javax.script.ScriptEngineManager;

public class TestGetInterface {

	/**
	 * @param args
	 */
	public static void main(String[] args) throws Exception {
		//System.setProperty("php.java.bridge.default_log_file", "");
		//System.setProperty("php.java.bridge.default_log_level", "3");
		new TestGetInterface().doWork();

	}

	private ScriptEngine scriptEngine;

	String classA = "class A{function toString(){return '::A';} function invokeA($b){$b->invokeB();}}\n";
	String classB = "class B{function toString(){return '::B';} function invokeB(){echo '::B';}}\n";
	String test = "<?php "+classA+classB+" $thiz=java_context()->getAttribute('thiz');\n$thiz->call(java_closure(new A()), java_closure(new B())); ?>";
	
	private void doWork() throws Exception {
		scriptEngine = new ScriptEngineManager().getEngineByName("php-invocable");
		scriptEngine.put("thiz", this);
		ByteArrayOutputStream out;
		OutputStreamWriter writer;
		scriptEngine.getContext().setWriter(writer = new OutputStreamWriter(out = new ByteArrayOutputStream()));
		scriptEngine.eval(test);
		((Closeable)scriptEngine).close();

		writer.close();
		if(!"::B".equals(out.toString())) {
			System.err.println("test failed");
			System.exit(1);
		}
		System.out.println("test okay");
		System.exit(0);
	}

	interface IA { public void invokeA(IB ccb); };
	interface IB { public void invokeB(); };
	
	public void call(Object $cca, Object $ccb) {
		IA cca = (IA) ((Invocable)scriptEngine).getInterface($cca, IA.class);
		IB ccb = (IB) ((Invocable)scriptEngine).getInterface($ccb, IB.class);
		cca.invokeA(ccb);
	}
}
