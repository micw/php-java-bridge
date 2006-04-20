#!/bin/env php
<?php

$HOST="192.168.6.203";
$PORT=8080;
$SERVICE="jaxws-fromwsdl/addnumbers?wsdl";

try {
  $port = new SoapClient("http://$HOST:$PORT/$SERVICE");
  $number1 = 10;
  $number2 = 20;
  echo ("Invoking one-way operation. Nothing is returned from service.\n");
  $port->oneWayInt($number1);
  echo ("Invoking addNumbers($number1, $number2)\n");
  $result = $port->addNumbers($number1, $number2);
  echo ("The result of adding $number1 and $number2 is $result\n\n");
  $number1 = -10;
  echo ("Invoking addNumbers($number1, $number2)\n");
  $result = $port->addNumbers($number1, $number2);
  echo ("The result of adding $number1 and $number2 is $result\n\n");
}
catch (Exception $ex) {
  $str = $ex->faultstring;
  echo "Exception: $str";
}
