#!/usr/bin/php -nq

<?php
if (!extension_loaded('mono')) {
  if (!(PHP_SHLIB_SUFFIX=="so" && dl('mono.so'))&&!(PHP_SHLIB_SUFFIX=="dll" && dl('php_mono.dll'))) {
    echo "mono extension not installed.";
    exit(2);
  }
}
ini_set("max_execution_time", 0);

class GtkFileSelectorDemo {

  var $filew;

  function GtkFileSelectorDemo () {
    mono_require("gtk-sharp");
  }

  function ok($obj, $args) {
    echo "ok called\n";
    echo $this->filew->get_Filename() . "\n";
  }

  function quit($obj, $args) {
    echo "quit called\n";
    $this->Application->Quit();
  }

  function init() {
    $Application = $this->Application = new MonoClass("Gtk.Application");
    $Application->Init();

    $filew = $this->filew = new Mono("Gtk.FileSelection", "Open a file ...");
    $filew->add_DeleteEvent (new Mono("Gtk.DeleteEventHandler", mono_closure($this, "quit")));
    $b=$filew->get_OkButton();
    $b->add_Clicked (new Mono("System.EventHandler", mono_closure($this, "ok")));
    $b=$filew->get_CancelButton();
    $b->add_Clicked (new Mono("System.EventHandler", mono_closure($this, "quit")));
    $filew->set_Filename ("penguin.png");
    $filew->Show();
  }

  function run() {
    $this->init();
    $this->Application->Run();
  }
}
$demo=new GtkFileSelectorDemo();
$demo->run();

?>
