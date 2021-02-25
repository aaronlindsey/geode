#!/bin/sh

./geode-assembly/build/install/apache-geode/bin/gfsh \
  -e "start locator --name=locator --security-properties-file=security.properties" \
  -e "start server --name=server --security-properties-file=security.properties"
