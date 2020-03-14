#!/bin/bash

[ $# -lt 3 ] && { echo "Usage: $0 <BindingIdInCamelCase> <Author> <GitHub Username>"; exit 1; }

openHABCoreVersion=2.5.0
openHABVersion=2.5.3-SNAPSHOT

camelcaseId=$1
id=`echo $camelcaseId | tr '[:upper:]' '[:lower:]'`

author=$2
githubUser=$3

mvn -s archetype-settings.xml archetype:generate -N \
  -DarchetypeGroupId=org.openhab.core.tools.archetypes \
  -DarchetypeArtifactId=org.openhab.core.tools.archetypes.binding \
  -DarchetypeVersion=$openHABCoreVersion \
  -DgroupId=org.openhab.binding \
  -DartifactId=org.openhab.binding.$id \
  -Dpackage=org.openhab.binding.$id \
  -Dversion=$openHABVersion \
  -DbindingId=$id \
  -DbindingIdCamelCase=$camelcaseId \
  -DvendorName=openHAB \
  -Dnamespace=org.openhab \
  -Dauthor="$author" \
  -DgithubUser="$githubUser"

directory="org.openhab.binding.$id/"

cp ../src/etc/NOTICE "$directory"

# temporary fix
# replace ${project.version} by ${ohc.version} in src/main/feature/feature.xml
sed -i -e "s|\-core\/\${project.version}|\-core\/\${ohc.version}|g" "$directory/src/main/feature/feature.xml"
