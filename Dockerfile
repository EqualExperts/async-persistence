FROM hseeberger/scala-sbt:8u141-jdk_2.12.3_0.13.16

WORKDIR /async-persistence

COPY ./.git /async-persistence/.git/

COPY ./build.sbt /async-persistence

COPY ./bintray.sbt /async-persistence

COPY ./LICENSE /async-persistence

COPY ./project/*.sbt /async-persistence/project/

COPY ./project/*.scala /async-persistence/project/

COPY ./src /async-persistence/src/

RUN sbt test

CMD ["sbt", "publishLocal"]
