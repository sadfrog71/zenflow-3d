FROM registry.cn-hangzhou.aliyuncs.com/dockerhub_mirror/java:21-anolis

MAINTAINER wangiegie@gmail.com

ENV TZ=Asia/Shanghai
ENV LANG C.UTF-8
ENV JAVA_OPTS="-Xms128m -Xmx256m -Djava.security.egd=file:/dev/./urandom"

RUN ln -sf /usr/share/zoneinfo/$TZ /etc/localtime && echo $TZ > /etc/timezone

RUN mkdir -p /matrix-mp-platform

WORKDIR /matrix-mp-platform

EXPOSE 6000

ADD ./target/matrix-mp-platform.jar ./

CMD sleep 180;java $JAVA_OPTS -jar matrix-mp-platform.jar
