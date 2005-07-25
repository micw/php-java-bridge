#!/usr/bin/php

<?php
if (!extension_loaded('mono')) {
  if (!(PHP_SHLIB_SUFFIX=="so" && dl('mono.so'))&&!(PHP_SHLIB_SUFFIX=="dll" && dl('php_mono.dll'))) {
    echo "mono extension not installed.";
    exit(2);
  }
}

class GtkDemo {
  var $Application;

  function GtkDemo() {
    mono_require("gtk-sharp");	// link the gtk-sharp library
  }

  function delete($sender, $e) {
    echo "delete called\n";
    $this->Application->Quit();
  }
  function clicked($sender, $e) {
    echo "clicked\n";
    $win = new Mono("Gtk.Window", "phpinfo()");
    $win->set_DefaultWidth(640);
    $win->set_DefaultHeight(400);
    $pane = new Mono("Gtk.ScrolledWindow");

    $view = new Mono("Gtk.TextView");
    $buffer = new Mono("Gtk.TextBuffer", new Mono("Gtk.TextTagTable"));
    ob_start();
    phpinfo();
    $buffer->set_Text(ob_get_contents());
    ob_end_clean();
    $view->set_Buffer($buffer);
    $pane->add($view);
    $win->add($pane);
    $win->ShowAll();
  }
  function init() {
    $this->Application = $Application = new Mono("Gtk.Application");
    $Application->Init();

    $win = new Mono("Gtk.Window", "Hello");
    $win->add_DeleteEvent (
			   new Mono(
				    "Gtk.DeleteEventHandler", 
				    mono_closure($this, "delete")));

    $btn = new Mono("Gtk.Button", "Show output from phpinfo()");

    $btn->add_Clicked(
		      new Mono(
			       "System.EventHandler",
			       mono_closure($this, "clicked")));
    $win->Add($btn);
    $win->ShowAll();
  }

  function run() {
    $this->init();
    $this->Application->Run();
  }
}

$demo = new GtkDemo();
$demo->run();

?>
