<?php
require_once('rt/java_io_File.php');
require_once('rt/java_lang_System.php');
require_once('lucene/All.php');

/* Create an index */
$cwd=getcwd();
/* create the index files in the tmp dir */
$tmp_dir = create_index_dir();
$analyzer = new org_apache_lucene_analysis_standard_StandardAnalyzer();
$writer = new org_apache_lucene_index_IndexWriter("$tmp_dir", $analyzer, true);
$file = new java_io_File($cwd);
$files = $file->listFiles();
if(is_null($files)) {
  $user = java_lang_System()->getProperty("user.name");
  echo("$cwd does not exist or is not readable.\n");
  echo("The directory must be readable by the user $user and it must not\n");
  echo("be protected by a SEL rule.\n");
  exit(1);
 }
foreach($files as $f) {
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
$searcher = new org_apache_lucene_search_IndexSearcher("$tmp_dir");
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

/** helper functions */
$tmp_file=null;
$tmp_dir=null;
/** create a temporary directory for the lucene index files */
function create_index_dir() {
  global $tmp_file, $tmp_dir;
  $tmp_file=tempnam(java_lang_System()->getProperty("java.io.tmpdir"), "idx");
  $tmp_dir="${tmp_file}.d";
  mkdir($tmp_dir);
  chmod($tmp_dir, 01777);
  return $tmp_dir;
}

/** delete the lucene index files */
function delete_index_dir() {
  $dir=opendir($tmp_dir);
  while($file=readdir($dir)) {
    unlink("${tmp_dir}/${file}");
  }
  rmdir($tmp_dir);
  unlink($tmp_file);
  $tmp_file=$tmp_dir=null;
}

?>
