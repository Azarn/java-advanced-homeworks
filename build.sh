#!/bin/sh

NAME="WebCrawler"

TESTER="$NAME"Test
BUILDDIR="./out"
LOWERNAME=crawler #$(echo $NAME|awk '{print tolower($$0)}')
FULLPATH=src/ru/ifmo/ctddev/kichigin/$LOWERNAME/$NAME.java
PACKAGE=ru.ifmo.ctddev.kichigin.$LOWERNAME
OURCLASS=$PACKAGE.$NAME
OURJAR=$NAME.jar
ARTIFACTS=../../java-advanced-2016/artifacts/$TESTER.jar:../../java-advanced-2016/lib/*


case $1 in
    manifest )
        mkdir -p META-INF
        echo "Manifest-Version: 1.0\nClass-Path: $TESTER\nMain-Class: $OURCLASS" > META-INF/MANIFEST.MF
        ;;
    compile )
        javac -cp $ARTIFACTS -d $BUILDDIR $FULLPATH
        ;;
    doc )
        javadoc -author -link https://docs.oracle.com/javase/8/docs/api/ -private -sourcepath "src" -classpath $ARTIFACTS -d javadoc $PACKAGE $2
        ;;
    jar )
        jar -cvfm $OURJAR META-INF/MANIFEST.MF  -C $BUILDDIR ./
        ;;
    run )
        java -cp "$ARTIFACTS:out" info.kgeorgiy.java.advanced.$LOWERNAME.Tester $2 $OURCLASS "$3"
        ;;
    run-jar )
        java -jar $OURJAR "$2" "$3" "$4" "$5"
esac

