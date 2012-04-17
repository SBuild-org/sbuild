#!/bin/sh

SBUILDDIR="/home/lefou/work/tototec/sbuild/de.tototec.sbuild"
TMPDIR="$(mktemp --directory)"

SBUILDANT_DIR="/home/lefou/work/tototec/sbuild/de.tototec.sbuild.ant"
SBUILDANT_TMPDIR="$(mktemp --directory)"

SCALACP="/home/lefou/.m2/repository-tototec/org/scala-lang/scala-library/2.9.1/scala-library-2.9.1.jar"
SCALACOMPILERCP="/home/lefou/.m2/repository-tototec/org/scala-lang/scala-compiler/2.9.1/scala-compiler-2.9.1.jar"
CMDOPTIONCP="/home/lefou/.m2/repository-tototec/de/tototec/de.tototec.cmdoption/0.1.0/de.tototec.cmdoption-0.1.0.jar"

RUNCP="${TMPDIR}:${SCALACP}:${CMDOPTIONCP}"
PROJECTCP="${SBUILDANT_TMPDIR}"
COMPILECP="${SCALACOMPILERCP}"
SBUILDCP="${TMPDIR}"

#DEBUG="-Xdebug -Xrunjdwp:transport=dt_socket,server=y,suspend=y,address=5005"

cp -r -- ${SBUILDDIR}/target/classes/* ${TMPDIR}
cp -r -- ${SBUILDANT_DIR}/target/classes/* ${SBUILDANT_TMPDIR}

#find ${SBUILDANT_TMPDIR}

java ${DEBUG} -cp ${RUNCP} de.tototec.sbuild.runner.SBuildRunner --sbuild-cp "${SBUILDCP}"  --compile-cp "${COMPILECP}" --project-cp "${PROJECTCP}" "$@"

rm -rf ${TMPDIR}
rm -rf ${SBUILDANT_TMPDIR}

