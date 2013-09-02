#!/bin/sh

GROUP=com/threerings
ARTIFACT=getdown

if [ -z "$1" ]; then
    echo "Usage: $0 M.N"
    echo "Where M.N is the version of the just performed release."
    exit 255
fi

COREDIR=$HOME/.m2/repository/$GROUP/$ARTIFACT/$1
if [ ! -d $COREDIR ]; then
    echo "Can't find: $COREDIR"
    echo "Is $1 the correct version?"
    exit 255
fi

echo "Unpacking $ARTIFACT-$1-javadoc.jar..."
cd apidocs
jar xf $COREDIR/$ARTIFACT-$1-javadoc.jar
rm -rf META-INF

echo "Adding and committing updated docs..."
git add .
git commit -m "Updated docs for $1 release." .
cd ..
git push

echo "Tagging docs..."
git tag -a v$1 -m "Tagged docs for $1 release."
git push origin v$1

echo "Thank you, please drive through."
