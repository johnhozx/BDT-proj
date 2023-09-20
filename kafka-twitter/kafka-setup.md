### To get a kafka twitter stream up

1. move the contents of the directory kafka-twitter to kafka host
```sh
    sudo docker cp foldername kafka:/home
```
2. make sure credentials in twitter.properties are properly set up
3. get a shell on kafka host
```sh
    sudo docker exec -it kafka bash
```
4. go on kafka host, make sure run.sh has execute permissions and all the files have read permissions
```sh
    cd home
    chmod -R 755 *
    chmod 777 run.sh
```
5. do not modify directory names

6. create a topic with the same name as topics in twitter.properties
    on kafka host:
```sh
    kafka-topics.sh --zookeeper 172.17.0.1:2181 \
                    --create --topic twitter_status_connect \
                    --partitions 3 \
                    --replication-factor 1
```
7. if you'd like to watch this topic in console for testing (do this on another terminal/tab)
    on kafka host:
```sh
    kafka-console-consumer.sh --bootstrap-server localhost:9092 \
                                --topic twitter_status_connect \
                                --from-beginning
```
8. start run.sh
```sh
    cd home
    ./run.sh
```

### To change filter words
1. open twitter.properties
2. change filter.keywords to whatever, this can be one word or a list of words