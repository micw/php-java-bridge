<?php
require_once('rt/java_io_File.php');
require_once('rt/java_lang_System.php');
require_once('rt/java_util_LinkedList.php');
require_once('lucene/All.php');

try {
  echo "indexing ... ";
  /* Create an index */
  $cwd=getcwd();
  /* create the index files in the tmp dir */
  $tmp = create_index_dir();
  $analyzer = new org_apache_lucene_analysis_standard_StandardAnalyzer();
  $writer = new org_apache_lucene_index_IndexWriter($tmp, $analyzer, true);
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
    $doc->add(new org_apache_lucene_document_Field(
	       "name", 
	       $f->getName(), 
	       org_apache_lucene_document_Field__Store()->YES, 
	       org_apache_lucene_document_Field__Index()->UN_TOKENIZED));
    $writer->addDocument($doc);
  }
  $writer->optimize();
  $writer->close();
  echo "done\n";

  echo "searching... ";
  /* Search */
  $searcher = new org_apache_lucene_search_IndexSearcher($tmp);
  $phrase = new org_apache_lucene_search_MatchAllDocsQuery();
  $hits = $searcher->search($phrase);

  /* Print result */
  $iter = $hits->iterator();
  $n = $hits->length();
  echo "done\n";
  echo "Hits: $n\n";

  /* Instead of retrieving the values one-by-one, we store them into a
   * LinkedList on the server side and then retrieve the list in one
   * query:
   */
  $resultList = new java_util_LinkedList();

				// create an XML document from the
				// following PHP code, ...
  java_lang_System::javaBeginDocument();
  while($n--) {
    $next = $iter->next();
    $name = $next->get("name");
    $resultList->add($name);
  }
				//  ... execute the XML document on
				//  the server side, ...
  java_lang_System::javaEndDocument();
  
				// .. retrieve the result, ...
  $result = java_lang_System::javaValues($resultList); 
				// ... print the result array
  print_r($result);

  delete_index_dir();
} catch (JavaException $e) {
  echo "Exception occured: {$e->__toString()}<br>\n";
}

/** helper functions */
$tmp_file=null;
$tmp_dir=null;
/** create a temporary directory for the lucene index files. Make sure
 * to create the tmpdir from Java so that the directory has
 * javabridge_tmp_t Security Enhanced Linux permission. Note that PHP
 * does not have access to tempfiles with java_bridge_tmp_t: PHP
 * inherits the rights from the HTTPD, usually httpd_tmp_t.
 */
function create_index_dir() {
  global $tmp_file, $tmp_dir;
  $javaTmpdir = java_lang_System()->getProperty("java.io.tmpdir");
  $tmpdir = java_values($javaTmpdir);
  $tmp_file=tempnam($tmpdir, "idx");
  $tmp_dir=new java_io_File("${tmp_file}.d");
  $tmp_dir->mkdir();
  return java_values($tmp_dir->toString());
}

/** delete the lucene index files */
function delete_index_dir() {
  global $tmp_file, $tmp_dir;
  $files = $tmp_dir->listFiles();
  foreach($files as $f) {
    $f->delete();
  }
  $tmp_dir->delete();
  unlink($tmp_file);
  $tmp_file=$tmp_dir=null;
}

?>
