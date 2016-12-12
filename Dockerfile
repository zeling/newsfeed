FROM java:8u111
MAINTAINER zeling <zeling.feng@hotmail.com>

RUN apt-get -yqq update
RUN apt-get -yqq install haproxy tmux

COPY target/scala-2.11/news-feed-assembly-1.0.jar /opt/newsfeed/target/scala-2.11/news-feed-assembly-1.0.jar
COPY demo/ /opt/newsfeed/demo/
COPY portrait.png /opt/newsfeed/portrait.png
COPY header.jpeg /opt/newsfeed/header.jpeg

WORKDIR /opt/newsfeed/
COPY target/shared-journal /opt/newsfeed/target/shared-journal

VOLUME "target/shared-journal/"

EXPOSE 8080

CMD ["bash", "demo/nohup.sh"]
