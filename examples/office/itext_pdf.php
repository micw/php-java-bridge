<?php
require_once("itext/All.php");
require_once("rt/java_io_ByteArrayOutputStream.php");
require_once("rt/java_lang_System.php");
require_once("rt/java_awt_Color.php");

try {
  $document = new com_lowagie_text_Document();
  $out = new java_io_ByteArrayOutputStream();
  $pdfWriter = com_lowagie_text_pdf_PdfWriter()->getInstance($document, $out);

  $document->open();
  $font = com_lowagie_text_FontFactory()->getFont(
	      com_lowagie_text_FontFactory()->HELVETICA, 
	      24, 
	      com_lowagie_text_Font()->BOLDITALIC, 
	      new java_awt_Color(0, 0, 255));
  
  $paragraph = new com_lowagie_text_Paragraph("Hello World", $font);
  $document->add($paragraph);

  $document->close();
  $pdfWriter->close();

  // print the generated document
  header("Content-type: application/pdf");
  header("Content-Disposition: attachment; filename=HelloWorld.pdf");
  echo java_cast($out->toByteArray(), "string");
} catch (JavaException $e) {
  echo "Exception occured: {$e->__toString()}<br>\n";
}
?>
