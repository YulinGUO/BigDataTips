<h1 id="id1">Spark 内存相关问题</h1>

<h2 id="id2">Spark Memory Management Overview</h2> 
Spark中，内存的使用大致可以分为两类：execution and storage.Execution指的是用于shuffles,joins,sort以及aggregation的内存。而storage指的是用于cache以及分发内部数据到整个cluster的内存。

<h3>1.5版本之前</h3> 

![1.5before](https://raw.githubusercontent.com/jacksu/utils4s/master/spark-knowledge/images/Spark-Heap-Usage.png)

####executor memory
Spark 通过设置executor memory来设置worker的JVM的heap大小,默认JVM堆为512MB.

####storage.safetyFraction

spark为了避免OOM错误，只使用heap大小的90%。通过spark.storage.safetyFraction来设置。

#### storage memory

spark通过内存来存储需要处理的数据，使用安全空间的60%，通过 spark.storage.memoryFraction来控制。如果我们想知道spark缓存数据可以使用多少空间？假设执行任务需要executors数为N，那么可使用空间为N*90%*60%*512MB，但实际缓存数据的空间还要减去unroll memory。

####shuffle memory

shuffle memory的内存为“Heap Size” * spark.shuffle.safetyFraction * spark.shuffle.memoryFraction。默认spark.shuffle.safetyFraction 是 0.8 ，spark.shuffle.memoryFraction是0.2 ，因此shuffle memory为 0.8*0.2*512MB = 0.16*512MB，shuffle memory为shuffle用作数据的排序等。

####unroll memory

unroll memory的内存为spark.storage.unrollFraction * spark.storage.memoryFraction * spark.storage.safetyFraction，即0.2 * 0.6 * 0.9 * 512MB = 0.108 * 512MB。unroll memory用作数据序列化和反序列化。

<h3>1.5版本之后</h3> 
提出了一个新的内存管理模型： Unified Memory Management。意思是Execution, Storage共享一个统一的区域。当没有Execution内存使用时候，storage可以获取所有的内存，反之亦可。Execution可以驱逐storage，但是不能超过特定阀值。


###JVM executor memory  

JVM executor memory 分两部分

1.  Reserved Memory  
预留给系统使用，默认是300M  
2.  executor memory － Reserved Memory  => M   
   这部分的也是分为两部分，使用spark.memory.fraction切分。  
   2.1  User Space（default:40% of M）
   用于user data structures, internal metadata in Spark, and safeguarding against OOM errors in the case of sparse and unusually large records。  
   2.2 Execution And Storage空间 （default:60% of M）  
   这部分空间也被分为两种，用于execution and storage.  
   spark.memory.storageFraction切分（50%）.表示execution不能驱逐storage的大小。
   
这样子划分，一方面灵活使用所有的空间大小，另一方面也保证了cache不能被驱逐。


<h2 >Spark Memory 开发碰到的问题</h2> 
1.  PermGen space OOM  
   这个问题出现场景：多次反复使用spark sql查询Hive,例如在streaming中使用sql查询hive。  
   原因：在Spark中使用hql方法执行hive语句时，由于其在查询过程中调用的是Hive的获取元数据信息、SQL解析，并且使用Cglib等进行序列化反序列化，中间可能产生较多的class文件，导致JVM中的持久代使用较多。  
   解决：

	spark.driver.extraJavaOptions -XX:PermSize=128M -XX:MaxPermSize=256M
   

