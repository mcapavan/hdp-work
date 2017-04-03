#!/usr/bin/env bash

echo "******* Setting up the environment for Task 3 *******"

echo "check maven version"
mvn -version

echo "Download Hive-JSON-Serde from git"
git clone https://github.com/rcongiu/Hive-JSON-Serde.git

cd Hive-JSON-Serde
mvn clean package
hdfs dfs -mkdir /lib
hdfs dfs -put json-serde/target/json-serde-1.3.8-SNAPSHOT-jar-with-dependencies.jar /lib
hdfs dfs -chmod -R 755 /lib

echo "*** upload the sample_twitter_data.txt file to HDFS ***"
hdfs dfs -mkdir -p /data/raw/sample_twitter_data
hdfs dfs -put ../../data/sample_twitter_data.txt /data/raw/sample_twitter_data