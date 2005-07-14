<?php
if (!extension_loaded('mono')) {
  echo "Please permanently activate the extension. Loading mono extension now...\n";
  if (!(PHP_SHLIB_SUFFIX=="so" && dl('mono.so'))&&!(PHP_SHLIB_SUFFIX=="dll" && dl('php_mono.dll'))) {
    echo "mono extension not installed.";
    exit(2);
  }
}

// Create a simple GTK window. The callbacks handle_system and
// handle_delete will be called when a button is clicked or when the
// application window is closed.
function createWindow($Application) {
  $win = new Mono("Gtk.Window", "Hello");

  $win->add_DeleteEvent (
    new Mono("GtkSharp.DeleteEventHandler", 
      mono_closure(
        new handle_delete($Application), // delete impl for Delete
	new MonoClass('GtkSharp.DeleteEventHandler$Method') // interface
        )));

  $btn = new Mono("Gtk.Button", "Click Me");

  $btn->add_Clicked(
    new Mono ("System.EventHandler", 
      mono_closure(
        new handle_system(), // clicked impl for EventHandler
	new MonoClass('System.EventHandler$Method') // interface
        )));
  
  $win->Add($btn);
  return $win;
}

// callbacks
class handle_system {
  function Invoke ($sender, $e) { // callback called when button is clicked
    echo "Button Clicked\n";
    echo "Sender:$sender\nEvent: $e\n\n";
  }
}

class handle_delete {
  var $Application;
  function handle_delete ($Application) {
    $this->Application = $Application;
  }

  function Invoke ($obj, $args) { // callback called when window is closed
    $this->Application->Quit();
  }
}

// create a standard GTK application, you need gtk-sharp.dll installed
$Assembly=new MonoClass("System.Reflection.Assembly");
$Assembly->Load("gtk-sharp");
$Application = new Mono("Gtk.Application");
$Application->Init();
createWindow($Application)->ShowAll();
$Application->Run();

?>

