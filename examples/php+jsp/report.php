<?php require_once("java/Java.inc");
header("Content-type: text/html");

/**
 * This example demonstrates how to use a complex library such as
 * Eclipse BIRT.
 * 
 * The library must be initialized once when the servlet context or
 * the VM starts. And it must be destroyed before the servlet context
 * or the VM terminates.
 * 
 * The PHP/Java Bridge has a method "getManagedInstance()" which
 * accepts a Java instance implementing java.io.Closeable and
 * optionally java.util.concurrent.Callable.
 * 
 * If "getManagedInstance()" is invoked, it registers the instance
 * with the current java (web-) context so that the method "close()"
 * is called automatically when the (web-) context
 * terminates. Furthermore the optional method "call()" can be used
 * for initialization. It is automatically synchronized against all
 * other calls.
 */


// load resources, .rpt files and images from the current working dir
$here = getcwd();

// eclipse birt uses its own lib versions, load them dynamically from
// WEB-INF/eclipse.birt.lib (see also: WEB-INF/platform)
java_require("$here/WEB-INF/eclipse.birt.lib");

// Dynamically create a Java object from the PHP class "BirtEngine". 
$birtReportEngineObject = java_closure(new BirtEngine(java_context()->getServletContext()), 
                             null, 
                             java("java.util.concurrent.Callable"));

// call() it and register its close() hook.
$birtReportEngine = java_context()->init($birtReportEngineObject);


/**
 * This class can be used to manage birt report engines.
 * It implements two methods, call() and close(), which can
 * start up and shut down the library
 */
class BirtEngine {
  const CONFIG_FILE = "BirtConfig.properties";
  private $ctx;
  private $configProps;
  
  public function BirtEngine ($servletCtx) {
    $this->ctx = $servletCtx;
  }

  public function call () {
    $birtEngine = $this->ctx->getAttribute("php.java.BIRT.ENGINE");
    if(!java_is_null($birtEngine)) return $birtEngine;

    $this->loadEngineProps ();
    $config = new java("org.eclipse.birt.report.engine.api.EngineConfig");

    if(!java_is_null($this->configProps)) {
      $logLevel = $this->configProps->getProperty("logLevel");

      $level = java("java.util.logging.Level")->OFF;
      if ($logLevel->equalsIgnoreCase("SEVERE")) {
        $level = java("java.util.logging.Level")->SEVERE;
      } else if ($logLevel->equalsIgnoreCase("WARNING")) {
        $level = java("java.util.logging.Level")->WARNING;
      } else if ($logLevel->equalsIgnoreCase("INFO")) {
        $level = java("java.util.logging.Level")->INFO;
      } else if ($logLevel->equalsIgnoreCase("CONFIG")) {
        $level = java("java.util.logging.Level")->CONFIG;
      } else if ($logLevel->equalsIgnoreCase("FINE")) {
        $level = java("java.util.logging.Level")->FINE;
      } else if ($logLevel->equalsIgnoreCase("FINER")) {
        $level = java("java.util.logging.Level")->FINER;
      } else if ($logLevel->equalsIgnoreCase("FINEST")) {
        $level = java("java.util.logging.Level")->FINEST;
      }
      $config->setLogConfig($this->configProps->getProperty("logDirectory"), $level);
    }else{
      $config->setLogConfig(null, java("java.util.logging.Level")->OFF);
    }

    $config->setBIRTHome("");

    $context = new java ("org.eclipse.birt.core.framework.PlatformServletContext", $this->ctx);
    $config->setPlatformContext( $context );

    try {
      $Platform = java("org.eclipse.birt.core.framework.Platform");
      $IReportEngineFactory = java("org.eclipse.birt.report.engine.api.IReportEngineFactory");
      $Platform->startup( $config );

      if (java_is_false($Platform->runningEclipse()))
        throw new JavaException("java.lang.IllegalStateException", "startup failed");

      $factory = $Platform->createFactoryObject($IReportEngineFactory->EXTENSION_REPORT_ENGINE_FACTORY);
      
      $birtEngine = $factory->createReportEngine ($config);
      $this->ctx->setAttribute("php.java.BIRT.ENGINE", $birtEngine);
      $this->registerOnShutdown();
      
      return $birtEngine;
    } catch ( JavaException $e ) {
      echo $e;
    }
    return null;
  }
      
  private function registerOnShutdown() {
    $EngineFactory = java("php.java.script.servlet.EngineFactory");
    $closeable = $EngineFactory->->getInvocablePhpScriptEngine(java_context()->getServlet(),
							       $this->ctx,
							       java_context()->getHttpServletRequest(),
							       java_context()->getHttpServletResponse());
    $closeable->eval ('function close() {' .
		      ' $birtEngine = java_context()->getHttpServletContext()->getAttribute("php.java.BIRT.ENGINE");' .
		      ' if(!java_is_null($birtEngine)) {' .
		      '  $birtEngine->destroy ();' .
		      '  $this->ctx->removeAttribute("php.java.BIRT.ENGINE");' .
		      '  java("org.eclipse.birt.core.framework.Platform")->shutdown();}'
		      '}');
    java_context()->onShutdown($closeable->getInterface(java("java.io.Closeable")));
  }

  private function loadEngineProps () {
    try {
      $cl = java("java.lang.Thread")->currentThread()->getContextClassLoader();
      $in = $cl->getResourceAsStream (BirtEngine::CONFIG_FILE);
      if (!java_is_null ($in)) {
        $this->configProps->load($in);
        $in->close();
      }
    } catch (JavaException $e) {
      echo $e;
    }
  }
}


// ... now load the .rpt file, render it and return the output to the client ...


// Create a HTML render context
$renderContext = new java ("org.eclipse.birt.report.engine.api.HTMLRenderContext");
$renderContext->setBaseImageURL("$here/images");
$contextMap = new java("java.util.HashMap");
$contextMap->put(java("org.eclipse.birt.report.engine.api.EngineConstants")->APPCONTEXT_HTML_RENDER_CONTEXT, 
		 $renderContext );


// Load the report design
$design = $birtReportEngine->openReportDesign("$here/test.rptdesign");
$task = $birtReportEngine->createRunAndRenderTask( $design );  
$task->setAppContext( $contextMap );

// Add HTML render options
$options = new java("org.eclipse.birt.report.engine.api.HTMLRenderOption");
$options->setOutputFormat($options->OUTPUT_FORMAT_HTML);

// Create the output
$out = new java("java.io.ByteArrayOutputStream");
$options->setOutputStream($out);
$task->setRenderOption($options);
$task->run ();
$task->close();

// destroy the created engine
$birtReportEngine->destroy ();

// Return the generated output to the client
echo (string)$out;

?>
