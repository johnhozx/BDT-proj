/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

// scalastyle:off println
package org.bara

import net.liftweb.json.DefaultFormats
import org.apache.spark.SparkConf
import org.apache.spark.streaming.{Minutes, Seconds, StreamingContext}
import org.apache.spark.streaming.kafka.KafkaUtils
import net.liftweb.json._
//import org.apache.hadoop.conf.Configuration
//import org.apache.spark.sql.SparkSession
//import org.apache.spark.streaming.{Seconds, StreamingContext}
//import org.apache.spark.streaming.kafka.KafkaUtils
//import net.liftweb.json._
//
//import java.util.Date
import org.apache.hadoop.hbase.HBaseConfiguration
import org.apache.hadoop.hbase.client.Put
import org.apache.hadoop.hbase.io.ImmutableBytesWritable
import org.apache.hadoop.hbase.mapred.TableOutputFormat
import org.apache.hadoop.hbase.util.Bytes
import org.apache.hadoop.mapred.JobConf
import org.apache.kafka.connect.data.Timestamp
//import org.apache.spark.sql.functions.from_unixtime

import java.text.SimpleDateFormat
import scala.io.Source

case class TweetData(data: TweetRead)
case class TweetRead(Id: String, Text: String, CreatedAt: Long)
case class TweetWrite(Id: String, Text: String, date: String, score: Double)


object KafkaHBaseWordCount {

  def loadFile(pathToFile: String): Set[String] = {
    val filename = pathToFile
    val bufferedSource = Source.fromFile(filename)
    val listOfLines = bufferedSource.getLines.toSet
    bufferedSource.close()
    listOfLines
  }

  def computeScore(words: Array[String], posWords: Set[String], negWords: Set[String]): Double = {
    words
      .map(w => if (posWords.contains(w)) 1.0 else if (negWords.contains(w)) -1.0 else 0)
      .reduce(_ + _)
  }

  def main(args: Array[String]): Unit = {



    // Create the context with a 1 second batch size
    val sparkConf = new SparkConf().setAppName("KafkaHBaseWordCount")
    val ssc = new StreamingContext(sparkConf, Seconds(2))

    //TODO: get from args
    val zkQuorum = "172.17.0.1:2182"
    val group =  "kafka-spark-streaming-example"
    val topics = "twitter_status_connect"
    val numThreads = "1"
    ssc.checkpoint("/home")

    //Kafka
    val topicMap = topics.split(",").map((_, numThreads.toInt)).toMap
    val lines = KafkaUtils.createStream(ssc, zkQuorum, group, topicMap).map(_._2)

    //Complex algorithm material

    val positiveWords = loadFile("/home/words/pos-words.dat")
    val negativeWords = loadFile("/home/words/neg-words.dat")

    val tweets = lines.map(t => {
      implicit val formats: DefaultFormats.type = net.liftweb.json.DefaultFormats
      val tweetJson = parse(t)
      val readTweet = tweetJson.extract[TweetRead]
      val df:SimpleDateFormat = new SimpleDateFormat("yyyy-MM-dd")
      val tweet = TweetWrite(
        readTweet.Id,
        readTweet.Text,
        df.format(Timestamp.toLogical(Timestamp.SCHEMA, readTweet.CreatedAt)),
        computeScore(readTweet.Text.split(" "), positiveWords, negativeWords)
      )
      tweet
    })


    //RDD -> HBase
    tweets.foreachRDD(rdd => {
      val hbaseConf = HBaseConfiguration.create()
      hbaseConf.set("hbase.zookeeper.quorum","hbase-docker")  //To set the zookeeper cluster address, you can also import hbase-site.xml into the classpath, but it is recommended to set it in the program

      hbaseConf.set("hbase.zookeeper.property.clientPort", "2182")       //Set the connection port of zookeeper, 2181 by default
      hbaseConf.set(TableOutputFormat.OUTPUT_TABLE, "tweets")

      val jobConf = new JobConf(hbaseConf)
      jobConf.setOutputFormat(classOf[TableOutputFormat])

      val putObj = rdd.map(tw => {
        val put = new Put(Bytes.toBytes(tw.Id))
        put.addColumn(Bytes.toBytes("tweet-data"),Bytes.toBytes("id"),Bytes.toBytes(tw.Id))
        put.addColumn(Bytes.toBytes("tweet-data"),Bytes.toBytes("text"),Bytes.toBytes(tw.Text))
        put.addColumn(Bytes.toBytes("tweet-data"),Bytes.toBytes("date"),Bytes.toBytes(String.valueOf(tw.date)))
        put.addColumn(Bytes.toBytes("tweet-data"),Bytes.toBytes("score"),Bytes.toBytes(String.valueOf(tw.score)))

        (new ImmutableBytesWritable, put)
      })

      putObj.saveAsHadoopDataset(jobConf)
    })

    ssc.start()
    ssc.awaitTermination()
  }
}
