#!/bin/sh

SBUILDDIR="/home/lefou/work/tototec/sbuild/de.tototec.sbuild"
TMPDIR="$(mktemp --directory)"

SCALACP="/home/lefou/.m2/repository-tototec/org/scala-lang/scala-library/2.9.1/scala-library-2.9.1.jar:/home/lefou/.m2/repository-tototec/org/scala-lang/scala-compiler/2.9.1/scala-compiler-2.9.1.jar"
CMDOPTIONCP="/home/lefou/.m2/repository-tototec/de/tototec/de.tototec.cmdoption/0.1.0/de.tototec.cmdoption-0.1.0.jar"

COMPILECP="${TMPDIR}:${SCALACP}:${CMDOPTIONCP}"
RUNCP="${TMPDIR}:${SCALACP}:${CMDOPTIONCP}"

#DEBUG="-Xdebug -Xrunjdwp:transport=dt_socket,server=y,suspend=y,address=5005"

cp -r -- ${SBUILDDIR}/target/classes/* ${TMPDIR}

java ${DEBUG} -cp ${RUNCP} de.tototec.sbuild.runner.SBuild --compile-cp "${COMPILECP}" --use-classloader-hack true "$@"

rm -rf ${TMPDIR}
