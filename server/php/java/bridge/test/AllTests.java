package php.java.bridge.test;

import junit.framework.Test;
import junit.framework.TestSuite;

public class AllTests {

    public static Test suite() {
	TestSuite suite = new TestSuite("Test for php.java.bridge.test");
	//$JUnit-BEGIN$
	suite.addTestSuite(TestInvocablePhpScriptEngine.class);
	suite.addTestSuite(TestPhpScriptEngine.class);
	//$JUnit-END$
	return suite;
    }

}
