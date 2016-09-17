#!/bin/sh

MY_CLASS_NAME="Implementor"
MY_PACKAGE_NAME="implementor"
TESTER_CLASS="Tester"
TESTER_JAR="ImplementorTest"
TESTER_PACKAGE_NAME="implementor"

PACKAGE_PREFIX="ru.ifmo.ctddev.kichigin"
BUILDDIR="./out"

PACKAGEPATH=src/ru/ifmo/ctddev/kichigin/$MY_PACKAGE_NAME/
FULLPATH=$PACKAGEPATH/$MY_CLASS_NAME.java
PACKAGE=$PACKAGE_PREFIX.$MY_PACKAGE_NAME
TESTER_PACKAGE="info.kgeorgiy.java.advanced."$TESTER_PACKAGE_NAME
OURCLASS=$PACKAGE.$MY_CLASS_NAME
OURJAR=$MY_CLASS_NAME.jar
ARTIFACTS=../../java-advanced-2016/artifacts/$TESTER_JAR.jar:../../java-advanced-2016/lib/*


case $1 in
    manifest )
        mkdir -p META-INF
        echo -e "Manifest-Version: 1.0\nClass-Path: $TESTER_PACKAGE.$TESTER_CLASS\nMain-Class: $OURCLASS\n" > META-INF/MANIFEST.MF
        ;;
    compile )
	mkdir -p out
        javac -cp $ARTIFACTS -d $BUILDDIR $FULLPATH $2
        ;;
    doc )
        javadoc -author -link https://docs.oracle.com/javase/8/docs/api/ -private -sourcepath "src" -classpath $ARTIFACTS -d javadoc $PACKAGE ../../java-advanced-2016/java/info/kgeorgiy/java/advanced/$TESTER_PACKAGE_NAME/*.java $2
        ;;
    jar )
        jar -cvfm $BUILDDIR/$OURJAR META-INF/MANIFEST.MF  -C $BUILDDIR ./ru
        ;;
    run )
        java -cp "$ARTIFACTS:$BUILDDIR" $TESTER_PACKAGE.$TESTER_CLASS $2 $OURCLASS "$3"
        ;;
    run-jar )
        java -cp "$ARTIFACTS:$BUILDDIR" $OURCLASS "$2"	#-jar $BUILDDIR/$OURJAR "$2" "$3" "$4" "$5"
        ;;
    run-main )
        java -cp "$ARTIFACTS:$BUILDDIR" $OURCLASS "$2"
        ;;
    clean )
	rm -rf $BUILDDIR
        rm -rf ./javadoc
        rm -rf ./META-INF
        ;;
    *)
        echo "Usage: build.sh manifest | clean | compile | doc | jar | run-jar | run | run-main"
        ;;
esac

