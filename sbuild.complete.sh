# bash completion for GNU make

have sbuild &&
_sbuild()
{
    # variables
    local file buildfile addBuildFile addBuildFiles
    local makef_inc cur prev i split=false

    COMPREPLY=()
    _get_comp_words_by_ref cur prev

    _split_longopt && split=true

    case $prev in
        -f|-F|--buildfile|--additional-buildfile)
            _filedir
            return 0
            ;;
    esac

    $split && return 0

    if [[ "$cur" == -* ]]; then
        COMPREPLY=( $( compgen -W '-D -f -F -h -l -L -q -v\
          --additional-buildfile,--buildfile\
          --check --check-recursive --clean --create-stub\
          --define --dependency-tree\
          --execution-plan\
          --fsc\
          --help\
          --just-clean --just-clean-recursive\
          --list-modules --list-targets --list-targets-recursive\
          --no-color --no-fsc --no-progress\
          --quiet\
          --verbose --version' -- "$cur" ) )
    else
        # evaluate the given buildfiles, default is "SBuild.scala"
        # -f/-F/--buildfile/--additional-buildfile
        for (( i=0; i < ${#COMP_WORDS[@]}; i++ )); do
            if [[ ${COMP_WORDS[i]} == -@(f|-buildfile) ]]; then
                # eval for tilde expansion
                eval buildfile=${COMP_WORDS[i+1]}
                buildfile="-f ${buildfile}"
                break
            fi
            if [[ ${COMP_WORDS[i]} == -@(F|-additional-buildfile) ]]; then
                # eval for tilde expansion
                eval addBuildFile=${COMP_WORDS[i+1]}
                addBuildFiles="${addBuildFiles} -F ${addBuildFile}"
                break
            fi
        done

        COMPREPLY=( $( compgen -W "$( sbuild -q ${buildfile} ${addBuildFiles} -l 2>/dev/null | \
            sed 's/\t.*$//' )" -- "$cur" ) )

    fi
} &&
complete -F _sbuild sbuild
