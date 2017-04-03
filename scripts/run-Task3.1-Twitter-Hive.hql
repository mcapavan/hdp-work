
-- ---------------------------------------------------------------------------
!echo "*** Create and set database ***";
-- ---------------------------------------------------------------------------

CREATE DATABASE IF NOT EXISTS twitter;
USE twitter;

-- ---------------------------------------------------------------------------
!echo "*** Add json-serde jar ***";
-- ---------------------------------------------------------------------------

add jar hdfs:///lib/json-serde-1.3.8-SNAPSHOT-jar-with-dependencies.jar;

-- ---------------------------------------------------------------------------
!echo "*** set hive support on reserved keywords ***";
-- ---------------------------------------------------------------------------

SET hive.support.sql11.reserved.keywords=false;

-- ---------------------------------------------------------------------------
!echo "*** Create external table ***";
-- ---------------------------------------------------------------------------

DROP TABLE IF EXISTS twitter.tweets_hdp;
CREATE EXTERNAL TABLE twitter.tweets_hdp (
    user STRUCT<
     userlocation:STRING,
     id:INT,
     name:STRING,
     screenname:STRING,
     geoenabled:BOOLEAN>,
    tweetmessage STRING,
    createddate STRING,
    geolocation STRING
 )
ROW FORMAT SERDE 'org.openx.data.jsonserde.JsonSerDe'
LOCATION '/data/raw/sample_twitter_data';

-- ---------------------------------------------------------------------------
!echo "*** Run query to answer, what are all the tweets by the twitter user Aimee_Cottle? ***";
-- ---------------------------------------------------------------------------

SELECT tweetmessage from twitter.tweets_hdp where user.screenname='Aimee_Cottle';
