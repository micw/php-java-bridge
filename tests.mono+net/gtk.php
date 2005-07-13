<?php

class Method {
  function Invoke ($sender, $e) {
    echo "invoked";
  }
}

$Application = new Mono("Gtk.Application");
$Application->Init ();
$win = new Mono("Gtk.Window", "Hello");
$win->add_DeleteEvent(mono_closure(new Method(), new Mono("GtkSharp.DeleteEventHandler")));
$btn = new Mono("Gtk.Button", "Click Me");
$btn->add_Clicked(new Mono ("System.EventHandler", mono_closure(new Method(), new Mono("System.EventHander.Method"))));
$win->Add($btn);
$win->ShowAll();
$Application->Run();

?>

