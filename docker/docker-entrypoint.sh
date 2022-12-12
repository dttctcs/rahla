#!/bin/bash
export JAVA_DEBUG_PORT=0.0.0.0:5005
export EXTRA_JAVA_OPTS="-XX:+UseG1GC -Duser.timezone=UTC -Dkaraf.log.console=ALL -Doracle.jdbc.J2EE13Compliant=true "$EXTRA_JAVA_OPTS
if [ "$LOG_FORMAT" == "json" ]; then
  cp /rahla/etc/org.ops4j.pax.logging.json.cfg.disabled /rahla/etc/org.ops4j.pax.logging.cfg
fi

exec "$@"
