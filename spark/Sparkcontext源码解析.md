# SparkContext源码解析

## 1.定义
SparkContext是应用启动时创建的Spark上下文对象，是进行Spark应用开发的主要接口，是Spark上层应用与底层实现的中转站。  

```
class SparkContext(config: SparkConf) extends Logging with ExecutorAllocationClient
```
使用sparkConf初始化 sparkContext，而sparkConf类中包含了 一个 new ConcurrentHashMap [String, String] (),用来保存k,v的配置。 


### 1.1 类中定义内容

sparkContext中包含很多有用的类的实例，其中对于调度执行程序最有用的是：TaskScheduler，DAGScheduler，这两个是核心所在。

```
   // Create and start the scheduler
    val (sched, ts) = SparkContext.createTaskScheduler(this, master, deployMode)
    _schedulerBackend = sched
    _taskScheduler = ts
    _dagScheduler = new DAGScheduler(this)
    _heartbeatReceiver.ask[Boolean](TaskSchedulerIsSet)
```

Spark把整个DAG抽象层从实际的task执行中剥离了出来DAGScheduler, 负责解析spark命令,生成stage, 形成DAG, 最终划分成tasks, 提交给TaskScheduler。这样设计的好处, 就是Spark可以通过提供不同的TaskScheduler简单的支持各种资源调度和执行平台。

## 2. 函数

### 2.1 parallize

```
 def parallelize[T: ClassTag](
      seq: Seq[T],
      numSlices: Int = defaultParallelism): RDD[T]
```
这个函数接受seq接口的容器，生成一个new ParallelCollectionRDD[T]， 这个RDD划分partitions的算法是slice:  

```
  override def getPartitions: Array[Partition] = {
    val slices = ParallelCollectionRDD.slice(data, numSlices).toArray
    slices.indices.map(i => new ParallelCollectionPartition(id, i, slices(i))).toArray
  }
```

### 2.2 runJob

```
  def runJob[T, U: ClassTag](
      rdd: RDD[T],
      func: (TaskContext, Iterator[T]) => U,
      partitions: Seq[Int],
      resultHandler: (Int, U) => Unit)
```
spark actions的主要进入点，在rdd上执行func,并将结果返回给handler。
当然，实际的执行是调用dagScheduler.runJob