dscript.play
============

dscript.play is the server side for the dscript.js JavaScript API for
DDS. 


Installation and Dependencies
=============================

dscript.play has to main dependencies, one toward OpenSplice Mobile
and the other toward Play!

  * OpenSplice Mobile (http://www.prismtech.com/opensplice/software-downloads)	     
  * Play! Framework  (http://www.playframework.com/download)

OpenSplice Mobile is used to avoid dependencies from platforms. In
essence the dscript.play is a pure Java solution.

The Play! framework is used for mostly two reasons, (1)to integratie
with an increasingly popular Web Framework, and (2) to leverage some
of Play!'s facitilies such as URL routing and WebSockets support.


Compiling dscript.play 
=====================

Compiling dscript.play is pretty straight forward. Assuming you have
installed the dependencies you simply have to issue the following
command:

$ play compile


Starting dscript.play 
=====================

To run dscript.play without any specific configuration you can simply
do the following

$ play run

Otherwise if you want to customize the http address and port and
perhaps pass other JVM options you can do the following:

$ play

> start -Dhttp.port=9999 -Dhttp.address=192.168.0.38 -Dddsi.networkInterfaces=en1

For additional configuration informations please refer to

http://www.playframework.com/documentation/2.1.x/ProductionConfiguration


Standalone Deployment
=====================

To deploy dscript.play as a standalone application you should run the
following commands:

$ play dist

This will generate the archive dscript-1.0-SNAPSHOT.zip under the
'dist' directory. This archive contains all the required jars along
with a start script. You can simply copy the archive on your targed,
expand it and then run the 'start' script with relevant options.


Issues and Suggestions
======================
For any issues, questions of suggestions feel free to contact me at io@nuvo.io