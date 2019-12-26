FROM clojure:tools-deps

RUN apt-get update && apt-get -y install rlwrap

# This is hack to keep image live.. Try connect to REPL via IP:PORT
ENTRYPOINT ["tail", "-f", "/dev/null"]