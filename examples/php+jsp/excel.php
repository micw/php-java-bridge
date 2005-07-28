<?php
header("Content-type: application/x-excel");
header("Content-Disposition: attachment; filename=downloaded.xls");
java_require("http://php-java-bridge.sf.net/poi.jar");

// create a 50x40 excel sheet and return it to the client
$workbook = new java("org.apache.poi.hssf.usermodel.HSSFWorkbook");
$sheet = $workbook->createSheet("new sheet");
$style = $workbook->createCellStyle();
// access the inner class AQUA within HSSFColor, note the $ syntax.
$Aqua = new java_class('org.apache.poi.hssf.util.HSSFColor$AQUA');
$style->setFillBackgroundColor($Aqua->index);
$style->setFillPattern($style->BIG_SPOTS);

for($y=0; $y<40; $y++) {
  $row = $sheet->createRow($y);
  for($x=0; $x<50; $x++) {
    $cell = $row->createCell($x);
    $cell->setCellValue("cell $x/$y");
    $cell->setCellStyle($style);
  }
}

// create and return the excel sheet to the client
$memoryStream = new java ("java.io.ByteArrayOutputStream");
$workbook->write($memoryStream);
$memoryStream->close();
echo $memoryStream->toByteArray();
?>

