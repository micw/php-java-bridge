#!/usr/bin/php

<?php
$v = new java("java.lang.System");
$arr=$v->getProperties();
foreach ($arr as $key => $value) {
		print $key . " -> " .  $value . "\n";
}
?>
