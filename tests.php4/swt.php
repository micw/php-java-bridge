#!/usr/bin/php

<?php
if(!extension_loaded('java')) {
  dl('java.' . PHP_SHLIB_SUFFIX);
}
class SelectionImpl {
  var $shell;

  function SelectionImpl($shell) {
    $this->shell = $shell;
  }

  function widgetSelected($arg) { $this->message($arg); }
  function widgetDefaultSelected($arg) { echo "widget default selected \n"; }

  function message($e) {
    $mb = new java("org.eclipse.swt.widgets.MessageBox", $this->shell);
    $mb->setMessage("Thank you.");
    $mb->open();
  }
}

$SWT = new JavaClass("org.eclipse.swt.SWT");
$display = new java("org.eclipse.swt.widgets.Display");
$shell = new java("org.eclipse.swt.widgets.Shell");
$shell->setSize(320, 200);
$shell->setLayout(new java("org.eclipse.swt.layout.FillLayout"));

$button = new java("org.eclipse.swt.widgets.Button", $shell, $SWT->PUSH);
$button->setText("Click here.");
$button->addSelectionListener(java_closure(new SelectionImpl($shell)));
$shell->open();
while (!$shell->isDisposed()) {
  if (!$display->readAndDispatch()) {
    $display->sleep();
  }
}
$display->dispose();

?>
