# bash completion for de.tototec.sbuild

# have sbuild &&
_sbuild()
{
    # variables
    local file buildfile addBuildFile addBuildFiles
    local makef_inc cur prev i split=false
    local sbuild_out

    COMPREPLY=()
    _get_comp_words_by_ref -n : cur prev

    _split_longopt && split=true

    case $prev in
        -f|-F|--buildfile|--additional-buildfile)
            _filedir
            return 0
            ;;
        -j|--jobs|--repeat)
            COMPREPLY=(0 1 2 3 4 5 6 7 8 9)
            return 0
            ;;
    esac

    $split && return 0

    if [[ "$cur" == -* ]]; then
        COMPREPLY=( $( compgen -W '-D -f -F -h -j -k -l -L -q -v\
          --additional-buildfile,--buildfile\
          --check --check-recursive --clean --create-stub\
          --define --dependency-tree\
          --execution-plan\
          --fsc\
          --help\
          --jobs --just-clean --just-clean-recursive\
          --keep-going\
          --list-modules --list-targets --list-targets-recursive\
          --no-color --no-fsc --no-global --no-progress\
          --quiet\
          --repeat\
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

        sbuild_out="$( sbuild -q ${buildfile} ${addBuildFiles} -L 2>/dev/null | sed -e 's/\t.*$//' )"

        if [[ "$cur" =~ .*::.* ]]; then

            # already specified a modules, so complete all targets of modules 
            COMPREPLY=( $( compgen -W "${sbuild_out}" -- "$cur" ) )

        else

            # no module selected, remove all target of submodules
            COMPREPLY=( $( compgen -W "$( echo "${sbuild_out}" | sed -e 's/^\(.*\)::.*$/\1::/' | sort -u )" -- "$cur" ) )

            if [[ ${#COMPREPLY[@]} -eq 1 && "${COMPREPLY[0]}" =~ .*:: ]]; then
                
		# we can already complete and provide details
                COMPREPLY=( $( compgen -W "${sbuild_out}" -- "$cur" ) )

            fi

        fi

    fi

    __ltrim_colon_completions "${cur}"

} &&
complete -F _sbuild sbuild
