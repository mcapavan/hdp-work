#!/usr/bin/env bash

echo "***** Starting Twitter Streaming program *****"

cd ../
mvn clean package

echo "*** setup input and output directories in HDFS ***"

hdfs dfs -rm -r /data/processed /data/raw/twitter src/data
hdfs dfs -mkdir -p /data/raw/twitter
hdfs dfs -mkdir -p src/data
hdfs dfs -put data/AFINN-111.txt src/data

echo "*** Running Spark job to stream live tweets, analyze and store ***"
echo "
spark-submit \
  --class org.mcapavan.spark.TweetStream \
  --master local[4] \
  target/twitter-streaming-1.0-SNAPSHOT.jar
"

# Run application locally
spark-submit \
  --class org.mcapavan.spark.TweetStream \
  --master local[4] \
  target/twitter-streaming-1.0-SNAPSHOT.jar

echo "***** Twitter Streaming program is completed *****"