<?php
$body = file_get_contents('php://input');

$error_dir = __DIR__ . "/../../protected/emc-shopkeeper-errors/";
if (!file_exists($error_dir)){
	mkdir($error_dir);
}

$num_files = 10;
$max_file_size = 1000000;
for ($i = 0; $i < $num_files; $i++){
	$file = $error_dir . "errors.$i.xml";
	if (!file_exists($file)){
		break;	
	}
	
	$size = filesize($file);
	if ($size < $max_file_size){
		break;
	}
}

if ($i == $num_files){
	header('', true, 500);
	echo 'Server cannot log anymore errors.';
	exit;
}

$result = file_put_contents($file, $body . "\n\n", FILE_APPEND);
if ($result === false){
	header('', true, 500);
	echo 'Server could not write the error to disc.';
	exit;
}
