#!/bin/sh

SBUILDDIR=/home/lefou/work/tototec/sbuild/de.tototec.sbuild
TMPDIR=$(mktemp --directory)

cp -r -- ${SBUILDDIR}/target/classes/* ${TMPDIR}

scala -cp ${TMPDIR}:/home/lefou/.m2/repository-tototec/de/tototec/de.tototec.cmdoption/0.1.0/de.tototec.cmdoption-0.1.0.jar de.tototec.sbuild.runner.SBuild --compile-cp "${TMPDIR}" "$@"

rm -rf ${TMPDIR}
