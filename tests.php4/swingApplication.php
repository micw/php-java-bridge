#!/usr/bin/php

<?php
if(!extension_loaded('java')) {
  dl('java.' . PHP_SHLIB_SUFFIX);
}

class SwingApplication {
  var $labelPrefix = "Number of button clicks: ";
  var $numClicks = 0;

  function actionPerformed($e) { 
    $numClicks++;
    $label->setText($labelPrefix . numClicks);
  }     

  function createComponents() { 
    $button = new java("javax.swing.JButton", "I'm a Swing button!");
    $button->addActionListener(java_closure($this, new JavaClass("java.awt.event.ActionListener")));
    $label->setLabelFor($button);
    $pane = new java("javax.swing.Jpanel", new java("javax.swing.GridLayout", 0, 1));
    $pane->add(button);
    $pane->add(label);
    $BorderFactory = new JavaClass("javax.swing.BorderFactory");
    $pane->setBorder($BorderFactory->createEmptyBorder(30,30,10,30));
    return $pane;
  }
  
  function createAndShowGUI() {
    $frame = new java("javax.swing.JFrame", "SwingApplication");
    $frame->setDefaultcloseOperation($frame->EXIT_ON_CLOSE);
    $contents = $this->createComponents();
    $contentPane = $frame->getContentPane();
    $BorderLayout = new JavaClass("javax.swing.BorderLayout");
    $contentPane->add($contents, $BorderLayout->CENTER);
    $frame->pack();
    $frame->setVisible(true);
  }
}

class Runnable {
  function run() { 
    $app = new SwingApplication();
    $app->createAndShowGUI();
  }
}
$SwingUtilities = new JavaClass("javax.swing.SwingUtilities");
$SwingUtilities->invokeLater(java_closure(new Runnable(), new JavaClass("java.lang.Runnable")));

?>
