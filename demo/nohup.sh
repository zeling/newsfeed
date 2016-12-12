#!/bin/bash
nohup java -Dapplication.node.port=2551 -Dapplication.leveldb.start-shared-leveldb=true -Dapplication.rest.port=8081 -jar target/scala-2.11/news-feed-assembly-1.0.jar 2>&1 &
sleep 5
nohup java -Dapplication.node.port=2552 -Dapplication.rest.port=8082 -jar target/scala-2.11/news-feed-assembly-1.0.jar 2>&1 &
sleep 5
nohup java -Dapplication.node.port=0 -Dapplication.rest.port=8083 -jar target/scala-2.11/news-feed-assembly-1.0.jar  2>&1 &
sleep 5
nohup java -Dapplication.node.port=0 -Dapplication.rest.port=8084 -jar target/scala-2.11/news-feed-assembly-1.0.jar  2>&1 &
sleep 5
nohup haproxy -p /run/haproxy.pid -f demo/haproxy.cfg -Ds 2>&1 
