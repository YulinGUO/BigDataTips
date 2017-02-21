#Spark Shuffle解析

## Shuffle write

在shuffle时候，会新建ShuffleMapTask，他最重要的函数就是runTask,

```
      val manager = SparkEnv.get.shuffleManager
      writer = manager.getWriter[Any, Any](dep.shuffleHandle, partitionId, context)
      writer.write(rdd.iterator(partition, context).asInstanceOf[Iterator[_ <: Product2[Any, Any]]])
```

从SparkEnv获得了shuffleManager,然后manager中得到writer,这里的writer,正常说来是SortShuffleWriter。

下面是write函数，

```
override def write(records: Iterator[Product2[K, V]]): Unit = {
    sorter = if (dep.mapSideCombine) {
      require(dep.aggregator.isDefined, "Map-side combine without Aggregator specified!")
      new ExternalSorter[K, V, C](
        context, dep.aggregator, Some(dep.partitioner), dep.keyOrdering, dep.serializer)
    } else {
      // In this case we pass neither an aggregator nor an ordering to the sorter, because we don't
      // care whether the keys get sorted in each partition; that will be done on the reduce side
      // if the operation being run is sortByKey.
      new ExternalSorter[K, V, V](
        context, aggregator = None, Some(dep.partitioner), ordering = None, dep.serializer)
    }
    sorter.insertAll(records)

    // Don't bother including the time to open the merged output file in the shuffle write time,
    // because it just opens a single file, so is typically too fast to measure accurately
    // (see SPARK-3570).
    val output = shuffleBlockResolver.getDataFile(dep.shuffleId, mapId)
    val tmp = Utils.tempFileWith(output)
    val blockId = ShuffleBlockId(dep.shuffleId, mapId, IndexShuffleBlockResolver.NOOP_REDUCE_ID)
    val partitionLengths = sorter.writePartitionedFile(blockId, tmp)
    shuffleBlockResolver.writeIndexFileAndCommit(dep.shuffleId, mapId, partitionLengths, tmp)
    mapStatus = MapStatus(blockManager.shuffleServerId, partitionLengths)
  }
```

从sorter这边可以看出，如果指定mapSideCombine，则新建ExternalSorter的时候会指定aggregator，然后执行sorter.insertAll,insertAll中将数据从JVM内存中保存到map或者buffer中，如果超过一定范围，则spill到磁盘中 :
  @volatile private var map = new PartitionedAppendOnlyMap[K, C]
  
当然，如果这边指定mapSideCombine，则会mergeValue,保存到map中。没有指定，保存到buffer中。


![Mou icon](http://upload-images.jianshu.io/upload_images/3736220-9cf015a0eecc50c1.png?imageMogr2/auto-orient/strip%7CimageView2/2/w/1240)

之后就是将上一步的map,spill到磁盘中的数据，一起写入到磁盘中，以便提供数据给reducer。  
sorter执行writePartitionedFile，将ExternalSorter中的数据写入磁盘中。

在写入磁盘的过程中，会使用merge-sort算法，对内存以及磁盘中的数据进行merge,然后aggregate,后写入磁盘，过程如下：  

ExternalSorter的merge函数会尝试将内存，磁盘数据生成的两个iterator进行合并   

```
      // Merge spilled and in-memory data
      merge(spills, destructiveIterator(
        collection.partitionedDestructiveSortedIterator(comparator)))
```

merge函数会调用：  

```
mergeWithAggregation
```
mergeWithAggregation会首先使用merge-sort算法，生成一个iterator,然后遍历iterator,保存到文件中。  
merge-sort的算法实现 ：
不管基于文件，内存的数据，都是基于partition，并且有序的。  
首先将两个iterator，按照partition分组。  

```
  val heap = new mutable.PriorityQueue[Iter]()(new Ordering[Iter] {
      // Use the reverse of comparator.compare because PriorityQueue dequeues the max
      override def compare(x: Iter, y: Iter): Int = -comparator.compare(x.head._1, y.head._1)
    })
    heap.enqueue(bufferedIters: _*)
```
利用优先队列，对比每个partition分组头一条记录的大小。重写Iterator的next方法，优先队列dequeue,得到第一个。如果第一个partition中next，则继续。否则，dequeue,使用下一个partition数据。很巧妙的视线方法。   


![ico](http://upload-images.jianshu.io/upload_images/3736220-1c53d7d2f8d9ecbd.png?imageMogr2/auto-orient/strip%7CimageView2/2/w/1240)



## shuffle read

我们就直接看这个ShuffledRDD中的compute方法：

```
  override def compute(split: Partition, context: TaskContext): Iterator[(K, C)] = {
    val dep = dependencies.head.asInstanceOf[ShuffleDependency[K, V, C]]
    SparkEnv.get.shuffleManager.getReader(dep.shuffleHandle, split.index, split.index + 1, context)
      .read()
      .asInstanceOf[Iterator[(K, C)]]
  }
```

这里的read是BlockStoreShuffleReader中的read方法:

```
  val blockFetcherItr = new ShuffleBlockFetcherIterator(
      context,
      blockManager.shuffleClient,
      blockManager,
      mapOutputTracker.getMapSizesByExecutorId(handle.shuffleId, startPartition, endPartition),
      // Note: we use getSizeAsMb when no suffix is provided for backwards compatibility
      SparkEnv.get.conf.getSizeAsMb("spark.reducer.maxSizeInFlight", "48m") * 1024 * 1024,
      SparkEnv.get.conf.getInt("spark.reducer.maxReqsInFlight", Int.MaxValue))
```

首先新建了一个ShuffleBlockFetcherIterator,其中的SparkEnv.get.mapOutputTracker,通过netty rpc得到这stage中map 输出的位置,得到Seq[(BlockManagerId, Seq[(BlockId, Long)])],也就是当前这个reduce task的数据源，来自于哪些节点的哪些block，及相应的block的数据大小。  

通过NettyBlockTransferService来读取其他节点上的block,以及本地的block.  
最后的聚合:  

```
  if (dep.mapSideCombine) {
        // We are reading values that are already combined
        val combinedKeyValuesIterator = interruptibleIter.asInstanceOf[Iterator[(K, C)]]
        dep.aggregator.get.combineCombinersByKey(combinedKeyValuesIterator, context)
      }
```
combineCombinersByKey会执行最后的combine操作，也就是把iterator of keys and values 聚合进入map（可以spill到磁盘）。
之后会生成一个ExternalIterator，An iterator that sort-merges (K, C) pairs from the in-memory map and the spilled maps 。  


这个ExternalIterator包含了mergeHeap = new mutable.PriorityQueue[StreamBuffer]，ExternalIterator重新打乱了K,V,而是按照hashcode，放到mergeHeap中。而ExternalIterator中的next方法就是最后的结果，Select a key with the minimum hash, then combine all values with the same key from all input streams.  结果是(minKey, minCombiner)
