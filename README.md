# CS523-BDT
Project for CS523 - Big Data Technology @ MIU
September 15-20, 2021

----
## Cluster setup

### Quick and dirty setup

1. Navigate to project directory, start up the cluster and run the host names script
```sh
    docker-compose up
```
(on another terminal)
```sh
    ./updateHosts.sh
```

2. Navigate to spark-master and create necessary directories
```sh
#   Create /home on HDFS
sudo docker exec -it spark-master bash
#   (inside)
hadoop fs -mkdir /home
hadoop fs -chmod 777 /home
mkdir /home/words

# exit from spark-master
exit
```

3. Navigate to your local build directory, move jar and data files to master

```sh
    sudo docker cp KafkaWC-1.0-SNAPSHOT-jar-with-dependencies.jar spark-master:/home/kwc.jar
    sudo docker cp ../resources/pos-words.dat spark-master:/home/words/pos-words.dat
    sudo docker cp ../resources/neg-words.dat spark-master:/home/words/neg-words.dat

```

4. Navigate to hbase and create table
```sh
    sudo docker exec -it hbase bash
    #   (inside)
    hbase shell
    #   (inside inside)
    create 'tweets', 'tweet-data'

    #back to build directory
    exit
```

5. Follow [kafka setup steps](kafka-twitter/kafka-setup.md)

6. Start the system
```sh
    sudo docker exec -it spark-master /usr/local/spark-2.2.1/bin/spark-submit --class org.bara.KafkaHBaseWordCount /home/kwc.jar
```

#### don't forget to stop the kafka start script while testing as to not hit your API limits in one run :-)


### References
    This project wouldn't be possible without these open source images
* [wurstmeister/kafka](https://github.com/wurstmeister/kafka-docker)
* [singularities/spark](https://hub.docker.com/r/singularities/spark/)
* [dajobe/hbase](https://github.com/dajobe/hbase-docker).