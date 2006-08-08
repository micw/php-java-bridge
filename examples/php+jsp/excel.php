<?php

header("Content-type: application/vnd.ms-excel");
header("Content-Disposition: attachment; filename=downloaded.xls");

// create a 50x40 excel sheet and return it to the client
$workbook = new java("org.apache.poi.hssf.usermodel.HSSFWorkbook");
$sheet = $workbook->createSheet("new sheet");

java_begin_document(); // send the following as a streamed XML document
for($y=0; $y<40; $y++) {
  $row = $sheet->createRow($y);
  for($x=0; $x<50; $x++) {
    $cell = $row->createCell($x);
    $cell->setCellValue("cell $x/$y");
  }
}
java_end_document(); // back to "normal" protocol mode

// create and return the excel sheet to the client
$memoryStream = new java ("java.io.ByteArrayOutputStream");
$workbook->write($memoryStream);
$memoryStream->close();
echo (string)$memoryStream->toByteArray();
?>

