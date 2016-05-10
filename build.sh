#!/bin/sh

NAME="Implementor"
PACKAGE_NAME="implementor"
TESTER="ImplementorTest"

BUILDDIR="./out"
FULLPATH=src/ru/ifmo/ctddev/kichigin/$PACKAGE_NAME/$NAME.java
PACKAGE=ru.ifmo.ctddev.kichigin.$PACKAGE_NAME
OURCLASS=$PACKAGE.$NAME
OURJAR=$NAME.jar
ARTIFACTS=../../java-advanced-2016/artifacts/$TESTER.jar:../../java-advanced-2016/lib/*


case $1 in
    manifest )
        mkdir -p META-INF
        echo "Manifest-Version: 1.0\nClass-Path: $TESTER\nMain-Class: $OURCLASS" > META-INF/MANIFEST.MF
        ;;
    compile )
	mkdir -p out
        javac -cp $ARTIFACTS -d $BUILDDIR $FULLPATH $2
        ;;
    doc )
        javadoc -author -link https://docs.oracle.com/javase/8/docs/api/ -private -sourcepath "src" -classpath $ARTIFACTS -d javadoc $PACKAGE $2
        ;;
    jar )
        jar -cvfm $OURJAR META-INF/MANIFEST.MF  -C $BUILDDIR ./
        ;;
    run )
        java -cp "$ARTIFACTS:$BUILDDIR" info.kgeorgiy.java.advanced.$PACKAGE_NAME.$2 $3 $OURCLASS "$4"
        ;;
    run-jar )
        java -jar $OURJAR "$2" "$3" "$4" "$5"
        ;;
    *)
        echo "Usage: build.sh manifest | compile | doc | jar | run-jar | run"
        ;;
esac

