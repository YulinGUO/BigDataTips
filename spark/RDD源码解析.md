# RDD源码解析

## 1.RDD是什么
A Resilient Distributed Dataset (RDD), the basic abstraction in Spark. Represents an immutable, partitioned collection of elements that can be operated on in parallel. This class contains the basic operations available on all RDDs, such as `map`, `filter`, and `persist`. In addition,
 * [[org.apache.spark.rdd.PairRDDFunctions]] contains operations available only on RDDs of key-value pairs, such as `groupByKey` and `join`;
 
  Internally, each RDD is characterized by five main properties:
 
 - A list of partitions
 - A function for computing each split
 - A list of dependencies on other RDDs
 - Optionally, a Partitioner for key-value RDDs (e.g. to say that the RDD is hash-partitioned)
 - Optionally, a list of preferred locations to compute each split on (e.g. block locations for an HDFS file)
 
 
## 2.RDD源码


### 2.1类定义

```

abstract class RDD[T: ClassTag](
    @transient private var _sc: SparkContext,
    @transient private var deps: Seq[Dependency[_]]
  ) extends Serializable with Logging
 
 ```  
 RDD从定义上来看，是某个特定类型T的容器，并且定义了含参构造器 :sc,deps
 
 
### 2.2persist,cache

persist可以指定storageLevel, cache实际上调用persist,默认storageLevel: Memory-only
 

### 2.3 repartition, coalesce
coalesce：返回一个指定数目partitions的RDD，含有(numPartitions: Int, shuffle: Boolean = false,partitionCoalescer: Option[PartitionCoalescer] = Option.empty)。默认shuffle为false,当从100个partitions降低为10个，实际上每个partitions会拥有10个paritions。使用的shuffle算法为HashPartitioner。  
repartition,调用coalesce(shuffle=true)

### 2.4 GroupBy ,groupByKey
groupBy[K] (f: T => K, p: Partitioner),通过f,得到k。将RDD map 为PairRDD,调用groupByKey.

### 2.5 SortBy, sortByKey
sortBy[K] (f: (T) => K,ascending: Boolean = true,numPartitions: Int = this.partitions.length),可以通过f得到K，然后调用PairRDD functions sortByKey

### 2.6 Map,MapPartitions, mapPartitionsWithIndex

MapPartitions is a specialised map that is called only once for each partition.  
mapPartitionsWithIndex相对于mapPartitions来说，保留了计算前的index

### 2.7 checkpoint, localCheckpoint
RDD checkpoint 到分布式文件系统中，lineage会被切断。  
localCheckpoint 是将RDD保存到executor中，所以性能有提升，但是放弃了fault-tolerance.  

### 2.8 action,transformation区别
从源代码中可以看出，action都有sc.runJob，也就是直接运行。

 
##3.scala语法

### 3.1 with scope

在RDD的action，transformation的函数定义中，经常会看到 withscope函数:

```
 private[spark] def withScope[U](body: => U): U = RDDOperationScope.withScope[U](sc)(body)
 ```
 这个函数起到了AOP的作用.

