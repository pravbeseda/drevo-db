cd src
del *.class
copy D:\workspace\Android\MakeDrevoDB\src\*.java .
cd ..
javac -cp .;./lib/javaxdelta-2.0.1.jar ./src/Utils.java ./src/MakeDB.java 
java -Dfile.encoding=utf8 -cp .;./src/;./lib/javaxdelta-2.0.1.jar;./lib/trove-2.1.0.jar;./lib/sqlite-jdbc-3.7.2.jar; MakeDB
