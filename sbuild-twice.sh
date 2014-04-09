#!/bin/sh

VERSION=0.7.5.9000

./sbuild-wrapper "$@" && cp -r ./sbuild-dist/target/sbuild-${VERSION} sbuild-${VERSION} && ./sbuild-${VERSION}/bin/sbuild "$@"

