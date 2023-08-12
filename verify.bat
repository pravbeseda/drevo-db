javac -cp .;./lib/javaxdelta-2.0.1.jar ./src/Utils.java ./src/MyDiff.java ./src/VerifyDiff.java 
java -Dfile.encoding=utf8 -cp .;./src/;./lib/javaxdelta-2.0.1.jar;./lib/trove-2.1.0.jar;./lib/sqlite-jdbc-3.7.2.jar; VerifyDiff
