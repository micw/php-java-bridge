#!/bin/sh
cat $* |
sed "s|@BACKEND_VERSION@| `cat ../VERSION`|" >php/java/bridge/global.properties
