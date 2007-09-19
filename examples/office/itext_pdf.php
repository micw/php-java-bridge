<?php
if(!extension_loaded("java"))
  require_once("http://localhost:8080/JavaBridge/java/Java.inc");
java_autoload("itext.jar");

try {
  $document = new com_lowagie_text_Document();
  $out = new java_io_ByteArrayOutputStream();
  $pdfWriter = com_lowagie_text_pdf_PdfWriter::type()->getInstance($document, $out);

  $document->open();
  $font = com_lowagie_text_FontFactory::type()->getFont(
	      com_lowagie_text_FontFactory::type()->HELVETICA, 
	      24, 
	      com_lowagie_text_Font::type()->BOLDITALIC, 
	      new java_awt_Color(0, 0, 255));
  
  $paragraph = new com_lowagie_text_Paragraph("Hello World", $font);
  $document->add($paragraph);

  $document->close();
  $pdfWriter->close();

  // print the generated document
  header("Content-type: application/pdf");
  header("Content-Disposition: attachment; filename=HelloWorld.pdf");
  echo java_values($out->toByteArray());
} catch (JavaException $e) {
  echo "Exception occured: "; echo $e; echo "<br>\n";
}
?>
