#Spark Streaming Tips
Spark Streaming是Spark实时流计算大数据框架，这里介绍一下Streaming的常识以及一些问题。

##1.DStream
##2.Checkpoint 
##3.Zero data loss and exactly once 
##4.Tips
###4.1Spark Kafka Direct 从checkpoint恢复后，速度很慢，并且WEB Ui上显示input events:0

正常的batches显示一般如下： 
![normal](https://raw.githubusercontent.com/YulinGUO/BigDataTips/master/spark/imgs/streamingNormal.png)  
每隔batchDuration，就有一个新的batch执行，input size不为0.  
当使用checkpoint从failure中恢复时候，发现前几个job总是一直卡在那边不动，另外，input size数目为0.这是怎么回事？  

![abnormal](https://raw.githubusercontent.com/YulinGUO/BigDataTips/master/spark/imgs/StreamingRecoved.png)  
实际上，spark从checkpoint中根据时间戳，计算需要恢复计算的events.默认情况下，spark会在一个batch中把所有events给计算完毕（导致数据量大，时间久，也容易出错），然后其余的batch就执行速度很快(input size=0)，一直到正常的batch位置。    

* 如何重现？如何得知真实的events数目？  
DS.foreachRDD(println(_.count))  
* 如何控制spark，不让他在一次batch中执行完毕所有的events?  
sparkConf.set("spark.streaming.kafka.maxRatePerPartition","2000") 

maxRatePerPartition ＝》 指的是每秒从Kafka每个partition接收的events数目  

```
N* numpartitionOfKafka * BatchDuration = NumEventsRecovered  
```
N就是需要多少个batch才能执行完毕回复。当然，正常状态下，NumEventsRecovered＝你认为的比较正常的读取数目(可以通过web ui去查看 Input size)
