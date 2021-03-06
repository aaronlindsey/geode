#!/bin/bash
# Licensed to the Apache Software Foundation (ASF) under one or more
# contributor license agreements.  See the NOTICE file distributed with
# this work for additional information regarding copyright ownership.
# The ASF licenses this file to You under the Apache License, Version 2.0
# (the "License"); you may not use this file except in compliance with
# the License.  You may obtain a copy of the License at
#
#      http://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing, software
# distributed under the License is distributed on an "AS IS" BASIS,
# WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
# See the License for the specific language governing permissions and
# limitations under the License.

# usage: list-management-url.sh <GEODE installation dir>

if [ "$#" -ne 1 ]; then
 echo "ERROR. Usage: list-management-url.sh <GEODE installation dir>"
 exit 1
fi

GEODE=$1

$GEODE/bin/gfsh -e "start locator --name=temp-for-api" > /dev/null
curl -H "Content-Type: application/json" http://localhost:7070/management/experimental/api-docs --fail --silent --show-error | jq -r '.paths | to_entries[] | {url:.key, method:.value|keys[]} | .method + " " + .url'

$GEODE/bin/gfsh -e "stop locator --dir=temp-for-api" > /dev/null
rm -r temp-for-api