# RDD源码解析之二

## 1.Word count

```
sc.textFile("/tmp/yulin/sougou") // read from hdfs
.map(one=>one.split(" ")) //split by " "
.flatMap(_.toList) //flatMap Array
.map(word=>(word,1)) //map word to Tuple(word, number)
.reduceByKey(_ + _) // reduce by key
```
上面的代码的作用是从HDFS中读取一些文件，文件的每一行包含多个word,由空格分割后，统计每个word出现的次数。  

* sc.textFile("/tmp/yulin/sougou") 

从源代码可以得知，此处sparkContext.textFile("") 首先生成HadoopRDD，然后将HadoopRDD中每个元素map为string,map中实际上新建了一个MapPartitionsRDD

* map(one=>one.split(" ")),flatMap(_.toList), map(word=>(word,1))  

这三个函数，实际上每一步都新建了一个MapPartitionsRDD，再调用mapPartitionsRDD中的函数。

* reduceByKey( _ + _ )

由于上一步中，得到了RDD[(String,Int)]的形式，通过隐式函数implicit def rddToPairRDDFunctions[K, V] (rdd: RDD[(K, V)])直接使用PairRDDFuncions中的reduceByKey函数，聚合得到了每个单词出现的次数。


## 2. HadoopRDD
HadoopRDD主要包含了读取Hadoop相关(HDFS,HBase,S3)的功能。  
笼统说来，HaddopRDD中读取功能的实现，都是调用封装Hadoop java api实现的,比如InputFormat接口。

### 2.1 读取Hdfs,spark partitions
函数def getPartitions: Array[Partition] 重写了RDD的方法。返回了一个包含Partitions的Array。
那这个Array的数目是由什么决定的呢？  

```
 val inputFormat = getInputFormat(jobConf)
 val inputSplits = inputFormat.getSplits(jobConf, minPartitions)
 val array = new Array[Partition](inputSplits.size)
```
通过这三行代码可以得知，第一行首先初始化了InputFormat的一个实例，而后调用getSplits方法，获得inputSplits,后利用inputSplits的数目初始化了Array[Partition]。  
那getSplits方法逻辑如何？通过Hadoop网上资料得知：  
最常用的是FileInputFormat,FileInputFormat默认为文件在HDFS上的每一个Block生成一个对应的FileSplit,但是可以设置“mapred.min.split.size”参数，使得Split的大小大于一个Block，这时候FileInputFormat会将连续的若干个Block分在一个Split中、也可能会将一个Block分别划在不同的Split中（但是前提是一个Split必须在一个文件中）。也就是说一个split不会包含零点几或者几点几个Block，一定是包含大于等于1个整数个Block. 一个split不会包含两个File的Block,不会跨越File边界。split和Block的关系是一对多的关系
## 3. PairRDDFunctions
PairRDDFunctions为RDD[(K,V)]提供了额外的函数支持。在处理数据中，我们经常遇到针对Key, 对其所有Values做操作的情况，因而PairRDDFunctions提供的函数也很实用。  

### 3.1 combineByKeyWithClassTag
```
def combineByKeyWithClassTag[C](
      createCombiner: V => C,
      mergeValue: (C, V) => C,
      mergeCombiners: (C, C) => C,
      partitioner: Partitioner,
      mapSideCombine: Boolean = true,
      serializer: Serializer = null)(implicit ct: ClassTag[C]): RDD[(K, C)]
```
spark2.0之后出现的新的Api,之前为combineByKey。区别在于提供了 ct, combined type的ClassTag。

这个函数是一个比较通用的函数,使用aggregation functions 去combine每一个key的所有values。把RDD[(K,V)]转化为RDD[(K,C)], V,C的类型可以不同。  

* createCombiner:将V转化为C，例如 创建一个element的list
* mergeValue: 将V merge入C
* mergeCombiners: combine两个C
* mapSideCombine :默认map端combine
* partitioner: partitioner


```
    if (self.partitioner == Some(partitioner)) {
      self.mapPartitions(iter => {
        val context = TaskContext.get()
        new InterruptibleIterator(context, aggregator.combineValuesByKey(iter, context))
      }, preservesPartitioning = true)
    } else {
      new ShuffledRDD[K, V, C](self, partitioner)
        .setSerializer(serializer)
        .setAggregator(aggregator)
        .setMapSideCombine(mapSideCombine)
    }
```

这段代码的逻辑如下：

1. 如果传入的partitioner跟PairRDD一样,则直接调用aggregator.combineValuesByKey,也就是mergeValue函数。  
2. 如果不同，则新建一个shuffledRDD  
  2.1 mapSideCombine==false
      reducer端会调用combineValuesByKey,得到结果
  2.2 mapSideCombine==true
      mapper端会先进行map-side-combine,也就是aggregator.combineValuesByKey，然后进行shuffle, 后reducer端会再做一次combine,调用aggregator.combineCombinersByKey。


reducer端是如何实现combineCombinersByKey?  
MapReduce shuffle 阶段就是边 fetch 边使用 combine() 进行处理，只是 combine() 处理的是部分数据。MapReduce 为了让进入 reduce() 的 records 有序，必须等到全部数据都 shuffle-sort 后再开始 reduce()。因为 Spark 不要求 shuffle 后的数据全局有序，因此没必要等到全部数据 shuffle 完成后再处理。那么如何实现边 shuffle 边处理，而且流入的 records 是无序的？答案是使用可以 aggregate 的数据结构，比如 HashMap。每 shuffle 得到（从缓冲的 FileSegment 中 deserialize 出来）一个 <Key, Value> record，直接将其放进 HashMap 里面。如果该 HashMap 已经存在相应的 Key，那么直接进行 aggregate 也就是 func(hashMap.get(Key), Value)。这个 func 功能上相当于 reduce()，但实际处理数据的方式与 MapReduce reduce() 有差别。  

Todo:  
如何验证？？？

### 3.2 combineByKey
简化版，旧版的通用函数，相比于 combineByKeyWithClassTag，仅仅不传入ct而已。

### 3.3 aggregateByKey

```
def aggregateByKey[U: ClassTag](zeroValue: U, partitioner: Partitioner)(seqOp: (U, V) => U,combOp: (U, U) => U): RDD[(K, U)] 
```
   
AggregateByKey是使用两个聚合函数seqOp, combOp，根据zeroValue来aggregate RDD[(K,U)]的数据。返回值U可以跟原始类型不同。  
看源码可以得知，  

```
combineByKeyWithClassTag[U]((v: V) => cleanedSeqOp(createZero(), v),
      cleanedSeqOp, combOp, partitioner)
```
aggregateByKey最后调用的也是combineByKeyWithClassTag.  另外，seqOp用于partition内merge,comOp用于partition之间merge（也就是map-side combine）.

### 3.4 foldByKey

```
def foldByKey(
      zeroValue: V,
      partitioner: Partitioner)(func: (V, V) => V): RDD[(K, V)]
```

foldByKey是使用一个关联性的函数以及一个zero value来为每个key来聚合函数。这边要注意的是zero value不应该改变result,也就是说对于加法来说，给予0，乘法给1.   
从源代码来看，foldByKey也调用了combineByKeyWithClassTag，  

```
 combineByKeyWithClassTag[V]((v: V) => cleanedFunc(createZero(), v),
      cleanedFunc, cleanedFunc, partitioner)
```
仅仅是mergeValue,mergeCombiners都是使用同一个函数。

### 3.5 reduceByKey

```
def reduceByKey(partitioner: Partitioner, func: (V, V) => V): RDD[(K, V)]
```
   
 reduceByKey是使用一个关联性reduce函数来为每个key来聚合数据。这个函数也会在每个mapper的本地来聚合数据，类似于MapReduce的reducer.

```
combineByKeyWithClassTag[V]((v: V) => v, func, func, partitioner)
```
从源代码来看，也调用了combineByKeyWithClassTag，仅仅是mergeValue,mergeCombiners都是使用同一个函数。

### 3.6 groupByKey

```
def groupByKey(partitioner: Partitioner): RDD[(K, Iterable[V])]
```
   
groupByKey作用是按照Key来分组，并将key所对应的所有values放到一个sequence里面。  

```
 combineByKeyWithClassTag[CompactBuffer[V]](
      createCombiner, mergeValue, mergeCombiners, partitioner, mapSideCombine = false)
```
最后调用的也是combineByKeyWithClassTag,不过mapSideCombine被置为false.  

需要注意的是: 
 
* 如果你的目标是进行aggregation,比如求和之类的，避免使用groupByKey。  
* 现在groupByKey聚合后的结果全部放入内存中，容易导致 OOM

为什么不开启mapSideCombine?  
mapSideCombine 工作原理：mapper端先mergeValues,然后reducer端再combineCombiners,具体来说，使用 aggregate 的数据结构，比如 HashMap。每 shuffle 得到（从缓冲的 FileSegment 中 deserialize 出来）一个 <Key, Value> record，直接将其放进 HashMap 里面。如果该 HashMap 已经存在相应的 Key，那么直接进行 aggregate 也就是 func(hashMap.get(Key), Value)。  

mapSideCombine从数据总量上来讲，并没有减少shuffled之后的数目(给reducer,所有的object还是被保存的),并且还要使用另一个hashMap来combineCombiners。


## 4.注意的问题

###4.1 foldByKey, aggregateByKey zeroValue的问题
为什么说zeroValue一定要选择对结果无影响的初始值呢？请看下面两组代码.  

```
scala> val people = List(("Mobin", 2), ("Mobin", 1), ("Lucy", 2), ("Amy", 1), ("Lucy", 3))
people: List[(String, Int)] = List((Mobin,2), (Mobin,1), (Lucy,2), (Amy,1), (Lucy,3))

scala>  val rdd = sc.parallelize(people)
rdd: org.apache.spark.rdd.RDD[(String, Int)] = ParallelCollectionRDD[0] at parallelize at <console>:26

//结果1  
scala>     val foldByKeyRDD = rdd.foldByKey(2)(_+_).collect.foreach(println)
(Mobin,7)
(Amy,3)
(Lucy,9)

scala> import org.apache.spark.HashPartitioner
import org.apache.spark.HashPartitioner

scala> val rdd = sc.parallelize(people).partitionBy(new HashPartitioner(2))
rdd: org.apache.spark.rdd.RDD[(String, Int)] = ShuffledRDD[4] at partitionBy at <console>:27

//结果2
scala>   rdd.foldByKey(2)(_+_).collect.foreach(println)
(Amy,3)
(Mobin,5)
(Lucy,7)

```
为啥两次结果不同？第二次就多了个 hashPartitioner啊。  
实际上，从源码角度来讲，zeroValue是会作用于每个partition，sc.parallelize(people)不能保证同样的key对应的值落到同样的partition,所以每个partition中的每个key都会加zeroValue。

## 5. 参考  
旧版本spark <http://www.cnblogs.com/fxjwind/p/3489111.html>  
spark 2.10源码
