#!/bin/sh

BASEDIR=.
CLASSNAME=office.api.main.cybozu2ical.Main
CLASSPATH=$BASEDIR/cybozu2ical.jar
for jar in `ls $BASEDIR/lib`; do
    CLASSPATH=$CLASSPATH:$BASEDIR/lib/$jar
done

java -Dfile.encoding=UTF-8 -cp $CLASSPATH $CLASSNAME $*
