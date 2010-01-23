BEGIN {line="";}
{
    if (length(line)>=160) { 
        print line;
        line=""; 
    }
    if (length($0)>0 && (substr($0, length($0), 1) == ";")) {
	line = line $0;
    } else {
        print line $0;
        line = "";
    }
}
END {print line;}
