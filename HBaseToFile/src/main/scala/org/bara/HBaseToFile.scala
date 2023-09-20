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

import org.apache.spark.SparkConf
import org.apache.hadoop.hbase.HBaseConfiguration
import org.apache.hadoop.hbase.client.Result
import org.apache.hadoop.hbase.io.ImmutableBytesWritable
import org.apache.hadoop.hbase.mapreduce.TableInputFormat
import org.apache.hadoop.hbase.util.Bytes
import org.apache.spark.sql._


case class Tweet(id: String, text: String, date: String, score: Double)

object HBaseToFile {

  def parseRow(result:Result) : Tweet = {
    val rowkey = Bytes.toString(result.getRow())
    val cf = Bytes.toBytes("tweet-data")

    val d0 = rowkey
    val d1 = Bytes.toString(result.getValue(cf, Bytes.toBytes("id")))
    val d2 = Bytes.toString(result.getValue(cf, Bytes.toBytes("text")))
    val d3 = Bytes.toString(result.getValue(cf, Bytes.toBytes("date")))
    val d4 = Bytes.toString(result.getValue(cf, Bytes.toBytes("score"))).toDouble
    Tweet(d1, d2, d3, d4)
  }

  def main(args: Array[String]): Unit = {
    // Create the context with a 1 second batch size
    val sparkConf = new SparkConf().setAppName("HBaseToFile")

    val spark = SparkSession.builder().config(sparkConf).getOrCreate()
    val hbaseConf = HBaseConfiguration.create()
    hbaseConf.set("hbase.zookeeper.quorum","172.17.0.1")  //To set the zookeeper cluster address, you can also import hbase-site.xml into the classpath, but it is recommended to set it in the program
    hbaseConf.set("hbase.zookeeper.property.clientPort", "2182")       //Set the connection port of zookeeper, 2181 by default
    hbaseConf.set(TableInputFormat.INPUT_TABLE, "tweets")

    val hbaseRDD = spark.sparkContext.newAPIHadoopRDD(
      hbaseConf,
      classOf[TableInputFormat],
      classOf[ImmutableBytesWritable],
      classOf[Result]
    )

    import spark.implicits._
    val resultRDD = hbaseRDD.map(tuple => tuple._2)
    val twRDD = resultRDD.map(parseRow)
    val twDF = twRDD.toDF

//    twDF.createOrReplaceTempView("Tweet")
//    spark.sql("SELECT * FROM Tweet Limit 5").show()

    val csvWithHeaderOptions: Map[String, String] = Map(("delimiter", ","), ("header", "true"))
    twDF.select("*")
      .limit(1000)
      .repartition(1)
      .write
      .mode("append")
      .format("csv")
      .options(csvWithHeaderOptions)
      .save("/home/tweets")
  }
}
// scalastyle:on println