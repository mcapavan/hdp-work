package org.mcapavan.spark;

import org.apache.spark.SparkConf;
import org.apache.spark.api.java.JavaPairRDD;
import org.apache.spark.api.java.function.FlatMapFunction;
import org.apache.spark.api.java.function.Function;
import org.apache.spark.api.java.function.Function2;
import org.apache.spark.api.java.function.PairFunction;
import org.apache.spark.api.java.function.VoidFunction;
import org.apache.spark.streaming.Duration;
import org.apache.spark.streaming.api.java.JavaDStream;
import org.apache.spark.streaming.api.java.JavaPairDStream;
import org.apache.spark.streaming.api.java.JavaReceiverInputDStream;
import org.apache.spark.streaming.api.java.JavaStreamingContext;
import org.apache.spark.streaming.twitter.TwitterUtils;
import scala.Tuple2;
import twitter4j.Status;

import java.util.Arrays;
import java.util.List;

import org.apache.log4j.Logger;
import org.apache.log4j.Level;

/**
 * Displays the most positive hash tags by joining the streaming Twitter data with a static RDD of
 * the AFINN word list (http://neuro.imm.dtu.dk/wiki/AFINN)
 * Ref: https://github.com/eBay/Spark/blob/master/examples/src/
 * main/java/org/apache/spark/examples/streaming/JavaTwitterHashTagJoinSentiments.java
 * http://blog-wassim.azurewebsites.net/en/spark-for-beginners-tutorials-apache-spark-streaming-twitter-java-example/
 */

public class TweetStream {
    public static void main(String[] args) {


        Logger.getLogger("org").setLevel(Level.OFF);
        Logger.getLogger("akka").setLevel(Level.OFF);

        // Set the system properties so that Twitter4j library used by Twitter stream
        // can use them to generate OAuth credentials

        final String consumerKey = "BlOWut5cFs7F2xJL4wFgJIR00";
        final String consumerSecret = "iJSEb1hH7ISjJZkjIVEjO4SUEfPwaQV4vHiVg0WodsZO63buSP";
        final String accessToken = "2365498483-KQEjssBQrBclC9dF8oZkLNnOWqosfLIFWRjwCor";
        final String accessTokenSecret = "39Ncxsgn6d9dov7ZN1kYKx1eMQe2zi3autgZyF7rR4q9h";

        SparkConf conf = new SparkConf().setMaster("local[4]").setAppName("SparkTwitterStreaming");
        JavaStreamingContext jssc = new JavaStreamingContext(conf, new Duration(20000));

        System.setProperty("twitter4j.oauth.consumerKey", consumerKey);
        System.setProperty("twitter4j.oauth.consumerSecret", consumerSecret);
        System.setProperty("twitter4j.oauth.accessToken", accessToken);
        System.setProperty("twitter4j.oauth.accessTokenSecret", accessTokenSecret);

        String[] filters = new String[] {"dataworks","bigdata", "IoT", "brexit", "beauty and the beast", "logan"};


        JavaReceiverInputDStream<Status> stream = TwitterUtils.createStream(jssc, filters);

        stream.repartition(1).dstream()
                .saveAsTextFiles("/data/raw/twitter/tweet","txt");

        // Without filter: Output text of all tweets
        JavaDStream<String> statuses = stream.map(
                new Function<Status, String>() {
                    public String call(Status status) { return status.getText(); }
                }
        );

        statuses.print();
        statuses.repartition(1).dstream()
                .saveAsTextFiles("/data/processed/twitter/tweet","txt");

        JavaDStream<String> words = stream.flatMap(new FlatMapFunction<Status, String>() {
            @Override
            public Iterable<String> call(Status s) {
                return Arrays.asList(s.getText().split(" "));
            }
        });

        JavaDStream<String> hashTags = words.filter(new Function<String, Boolean>() {
            @Override
            public Boolean call(String word) {
                return word.startsWith("#");
            }
        });

        // Read in the word-sentiment list and create a static RDD from it
        String wordSentimentFilePath = "src/data/AFINN-111.txt";
        final JavaPairRDD<String, Double> wordSentiments = jssc.sparkContext()
                .textFile(wordSentimentFilePath)
                .mapToPair(new PairFunction<String, String, Double>(){
                    @Override
                    public Tuple2<String, Double> call(String line) {
                        String[] columns = line.split("\t");
                        return new Tuple2<>(columns[0], Double.parseDouble(columns[1]));
                    }
                });


        JavaPairDStream<String, Integer> hashTagCount = hashTags.mapToPair(
                new PairFunction<String, String, Integer>() {
                    @Override
                    public Tuple2<String, Integer> call(String s) {
                        // leave out the # character
                        return new Tuple2<>(s.substring(1), 1);
                    }
                });

        JavaPairDStream<String, Integer> hashTagTotals = hashTagCount.reduceByKeyAndWindow(
                new Function2<Integer, Integer, Integer>() {
                    @Override
                    public Integer call(Integer a, Integer b) {
                        return a + b;
                    }
                }, new Duration(60000));

        // Determine the hash tags with the highest happiest10 values by joining the streaming RDD
        // with the static RDD inside the transform() method and then multiplying
        // the frequency of the hash tag by its sentiment value
        JavaPairDStream<String, Tuple2<Double, Integer>> joinedTuples =
                hashTagTotals.transformToPair(new Function<JavaPairRDD<String, Integer>,
                        JavaPairRDD<String, Tuple2<Double, Integer>>>() {
                    @Override
                    public JavaPairRDD<String, Tuple2<Double, Integer>> call(
                            JavaPairRDD<String, Integer> topicCount) {
                        return wordSentiments.join(topicCount);
                    }
                });

        JavaPairDStream<String, Double> topicHappiness = joinedTuples.mapToPair(
                new PairFunction<Tuple2<String, Tuple2<Double, Integer>>, String, Double>() {
                    @Override
                    public Tuple2<String, Double> call(Tuple2<String,
                            Tuple2<Double, Integer>> topicAndTuplePair) {
                        Tuple2<Double, Integer> happinessAndCount = topicAndTuplePair._2();
                        return new Tuple2<>(topicAndTuplePair._1(),
                                happinessAndCount._1() * happinessAndCount._2());
                    }
                });

        JavaPairDStream<Double, String> happinessTopicPairs = topicHappiness.mapToPair(
                new PairFunction<Tuple2<String, Double>, Double, String>() {
                    @Override
                    public Tuple2<Double, String> call(Tuple2<String, Double> topicHappiness) {
                        return new Tuple2<>(topicHappiness._2(),
                                topicHappiness._1());
                    }
                });

        JavaPairDStream<Double, String> happiest10 = happinessTopicPairs.transformToPair(
                new Function<JavaPairRDD<Double, String>, JavaPairRDD<Double, String>>() {
                    @Override
                    public JavaPairRDD<Double, String> call(
                            JavaPairRDD<Double, String> happinessAndTopics) {
                        return happinessAndTopics.sortByKey(false);
                    }
                }
        );

        happiest10.repartition(1).dstream()
                .saveAsTextFiles("/data/processed/tweet-sentiment/happiest10","txt");


        // Print hash tags with the most positive sentiment values
        happiest10.foreachRDD(new VoidFunction<JavaPairRDD<Double, String>>() {
            @Override
            public void call(JavaPairRDD<Double, String> happinessTopicPairs) {
                List<Tuple2<Double, String>> topList = happinessTopicPairs.take(10);
                System.out.println(
                        String.format("\nHappiest topics in last 60 seconds (%s total):",
                                happinessTopicPairs.count()));
                for (Tuple2<Double, String> pair : topList) {
                    System.out.println(
                            String.format("%s (%s happiness)", pair._2(), pair._1()));
                }
            }
        });

        jssc.start();
        jssc.awaitTermination();

    }
}
