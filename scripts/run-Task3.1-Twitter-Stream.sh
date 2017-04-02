#!/usr/bin/env bash

echo "*** Cleaning previous twitter data from HDFS ***"
echo "hdfs dfs -rm -r /data/processed /data/raw/twitter"

hdfs dfs -rm -r /data/processed /data/raw/twitter

echo "*** Running Spark job to stream live tweets, analyze and store ***"
echo "
spark-submit \
  --class org.mcapavan.spark.TweetStream \
  --master local[4] \
  /root/work/spark-submit-job/twitter-streaming-1.0-SNAPSHOT.jar
"

# Run application locally
spark-submit \
  --class org.mcapavan.spark.TweetStream \
  --master local[4] \
  /root/work/spark-submit-job/twitter-streaming-1.0-SNAPSHOT.jar