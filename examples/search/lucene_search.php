<?php
require_once('rt/java_io_File.php');
require_once('lucene/All.php');

/* Create an index */
$here=getcwd();
$analyzer = new org_apache_lucene_analysis_standard_StandardAnalyzer();
$writer = new org_apache_lucene_index_IndexWriter("$here", $analyzer, true);
$file = new java_io_File($here);
foreach($file->listFiles() as $f) {
	$doc = new org_apache_lucene_document_Document();
	$doc->add(new org_apache_lucene_document_Field("name", 
                   $f->getName(), 
		   org_apache_lucene_document_Field__Store()->YES, 
                   org_apache_lucene_document_Field__Index()->UN_TOKENIZED));
	$writer->addDocument($doc);
}
$writer->optimize();
$writer->close();

/* Search */
$searcher = new org_apache_lucene_search_IndexSearcher("$here");
$term=new org_apache_lucene_index_Term("name", "lucene_search.php");
$phrase = new org_apache_lucene_search_PhraseQuery();
$phrase->add($term);
$hits = $searcher->search($phrase);

/* Print result */
$iter = $hits->iterator();
$n = $hits->length();
echo "Hits: $n\n";

while($iter->hasNext()) {
  $next = $iter->next();
  $name = $next->get("name");
  echo "$name\n";
}
?>
