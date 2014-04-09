#!/bin/sh

./sbuild-wrapper "$@" && ./sbuild-dist/target/sbuild-*/bin/sbuild "$@"

