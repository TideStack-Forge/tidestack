print_colorful_log(){
  local log_file_path=$1
  local max_lines=$2
  #2
  local date_time_pattern='\(.* .*\)'
  #3
  local thread_name_pattern='\[\(.*\)\]'
  #4
  local class_name_pattern='\(.*\)'
  #5
  local msg_pattern='\(.*\)'

  local INFO_PATTERN="\(\[INFO \]\) $date_time_pattern $thread_name_pattern $class_name_pattern - $msg_pattern"
  local ERROR_PATTERN="\(\[ERROR\]\) $date_time_pattern $thread_name_pattern $class_name_pattern - $msg_pattern"
  local WARN_PATTERN="\(\[WARN \]\) $date_time_pattern $thread_name_pattern $class_name_pattern - $msg_pattern"
  local DEBUG_PATTERN="\(\[DEBUG\]\) $date_time_pattern $thread_name_pattern $class_name_pattern - $msg_pattern"
  local TRACE_PATTERN="\(\[TRACE\]\) $date_time_pattern $thread_name_pattern $class_name_pattern - $msg_pattern"

  #2
  local DATE_TIME='\x1b[38;5;81m\2'
  #3
  local THREAD_NAME='\x1b[48;5;250m\x1b[39m\3\x1b[49m'
  #4
  local CLASS_NAME='\x1b[38;5;33m\4'

  local INFO_LABEL='\x1b[46m\1\x1b[49m'
  local ERROR_LABEL='\x1b[41m\x1b[5m\1\x1b[0m\x1b[49m'
  local WARN_LABEL='\x1b[48;5;214m\1\x1b[49m'
  local DEBUG_LABEL='\x1b[48;5;5m\1\x1b[49m'
  local TRACE_LABEL='\x1b[48;5;7m\1\x1b[49m'

  local  INFO_BODY="$DATE_TIME $THREAD_NAME $CLASS_NAME \x1b[39m\5"
  local ERROR_BODY="$DATE_TIME $THREAD_NAME $CLASS_NAME \x1b[33m\5"
  local  WARN_BODY="$DATE_TIME $THREAD_NAME $CLASS_NAME \x1b[39m\5"
  local DEBUG_BODY="$DATE_TIME $THREAD_NAME $CLASS_NAME \x1b[39m\5"
  local TRACE_BODY="$DATE_TIME $THREAD_NAME $CLASS_NAME \x1b[39m\5"

  local END="\x1b[39m"

  tail -n ${max_lines:=100} -f $log_file_path | sed \
    -e "s/$INFO_PATTERN/\x1b[39m$INFO_LABEL $INFO_BODY$END/" \
    -e "s/$ERROR_PATTERN/\x1b[39m$ERROR_LABEL $ERROR_BODY/" \
    -e "s/$WARN_PATTERN/\x1b[39m$WARN_LABEL $WARN_BODY$END/" \
    -e "s/$DEBUG_PATTERN/\x1b[39m$DEBUG_LABEL $DEBUG_BODY$END/" \
    -e "s/$TRACE_PATTERN/\x1b[39m$TRACE_LABEL $TRACE_BODY$END/" \
    -e "s/\(  .   ____.*\)/\x1b[32m\1/" \
    -e "s/\( :: Spring Boot ::\s*\)\(.*\)/\x1b[36m\1\x1b[38;5;81m\2\x1b[39m/"

}
