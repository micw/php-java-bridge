#!/usr/bin/php
require_once ("java/Java.inc");
java_autoload("itext.jar");

/*
 * Concat: merge pdf files
 *
 * Usage: concat.php outfile.pdf file1.pdf file2.pdf ...
 */

array_shift($argv); $argc--;

$outfile=$argv[0];
$args=$argv;
$pageOffset=0;
$f=1;
$master = new java_util_ArrayList();

while(--$argc) {
  $reader = new com_lowagie_text_pdf_PdfReader($args[$f]);
  $reader->consolidateNamedDestinations();
  $n = java_values($reader->getNumberOfPages());
  $bookmarks = com_lowagie_text_pdf_SimpleBookmark::type()->getBookmark($reader);
  if(java_values($bookmarks)!=null) {
    if($pageOffset!=0) {
      com_lowagie_text_pdf_SimpleBookmark::type()->shiftPageNumbers($bookmarks, $pageOffset, null);
      $master->addAll($bookmarks);
    }
  }
  $pageOffset += $n;
  echo ("There are " . $n . " pages in " . $args[$f]); echo "\n";
  if($f==1) {
    $document = new com_lowagie_text_Document($reader->getPageSizeWithRotation(1));
    $writer = new com_lowagie_text_pdf_PdfCopy($document, new java_io_FileOutputStream($outfile));
    $document->open();
  }
  for($i=0; $i<$n; ) {
    ++$i;
    $page = $writer->getImportedPage($reader, $i);
    $writer->addPage($page);
    echo "Processed page: " .$i; echo "\n";
  }
  $form = $reader->getAcroForm();
  if(java_values($form)!=null)
    $writer->copyAcroForm($reader);
  $f++;
}
if(java_values($master->size())>0) {
  $writer->setOutlines($master);
 }
$document->close();
      
?>

