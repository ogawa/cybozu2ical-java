cybozu2ical-java
================

cybozu2ical-java reads schedule events from Cybozu Office 8 and generates an iCalendar-format calendar file.

How to use
----------

Download and build.

    $ git clone git://github.com/ogawa/cybozu2ical-java.git
    $ cd cybozu2ical-java
    $ ant

Edit "cybozu2ical.properties" so as to fit to your environment.

    $ cp cybozu2ical.properties.sample cybozu2ical.properties
    $ vi cybozu2ical.properties

Create an input file which specifies login name, start date, and end date.

    $ cat > input.csv
    your-login-name,2011/06/01 00:00:00,2011/08/01 00:00:00
    ^D

Run cybozu2ical.sh script.

    $ ./cybozu2ical.sh -i input.csv

Acknowledgment
--------------

* This software is based on [Joyzo's OfficeAPI](https://www.facebook.com/joyzojp?sk=app_208195102528120).
