#!/bin/sh

java -Dfile.encoding=UTF-8 -cp cybozu2ical.jar:lib/officeapi.jar:lib/ical4j-1.0.1.jar:lib/backport-util-concurrent-3.1.jar:lib/commons-cli-1.2.jar office.api.main.cybozu2ical.Cybozu2iCal $*
