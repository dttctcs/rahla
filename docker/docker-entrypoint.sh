#!/bin/bash
export JAVA_DEBUG_PORT=0.0.0.0:5005
export EXTRA_JAVA_OPTS="-XX:+UseG1GC -Duser.timezone=UTC -Dkaraf.log.console=ALL "$EXTRA_JAVA_OPTS
exec "$@"