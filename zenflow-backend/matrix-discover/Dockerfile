FROM registry.cn-hangzhou.aliyuncs.com/dockerhub_mirror/java:21-anolis

MAINTAINER deepdimen@gmail.com

ENV TZ=Asia/Shanghai
ENV LANG C.UTF-8
ENV JAVA_OPTS="-Xms512m -Xmx1024m -Djava.security.egd=file:/dev/./urandom"
ENV MATRIX_STARTUP_DELAY_SECONDS=60

RUN ln -sf /usr/share/zoneinfo/$TZ /etc/localtime && echo $TZ > /etc/timezone

RUN mkdir -p /matrix-discover

WORKDIR /matrix-discover

EXPOSE 9010

ADD ./target/matrix-discover.jar ./

CMD ["sh", "-c", "sleep \"$MATRIX_STARTUP_DELAY_SECONDS\"; exec java $JAVA_OPTS -jar matrix-discover.jar"]
