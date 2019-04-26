javac -cp bin;lib/log4j-core-2.11.2.jar;lib/log4j-api-2.11.2.jar; -d bin src/*.java

cd bin

start rmiregistry

rmic SuperNode

cd ..

