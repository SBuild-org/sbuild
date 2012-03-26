#!/bin/sh

SBUILDDIR="/home/lefou/work/tototec/sbuild/de.tototec.sbuild"
TMPDIR="$(mktemp --directory)"
COMPILECP="${TMPDIR}:/home/lefou/.m2/repository-tototec/org/apache/ant/ant/1.7.0/ant-1.7.0.jar"
RUNCP="${COMPILECP}:/home/lefou/.m2/repository-tototec/de/tototec/de.tototec.cmdoption/0.1.0/de.tototec.cmdoption-0.1.0.jar"

cp -r -- ${SBUILDDIR}/target/classes/* ${TMPDIR}

scala -cp ${RUNCP} de.tototec.sbuild.runner.SBuild --compile-cp "${COMPILECP}" "$@"

rm -rf ${TMPDIR}

