<h1 id="id1">Spark RDD, DataFrame, DataSet 的使用选择 </h1>
Spark版本更新迭代很快，其核心分布式数据集也一路从RDD,新增到DataFrame到最新的DataSet。那这三者使用场景该如何选择？各自有什么优点？

<h2 id="id2">RDD</h2> 
<h3>Resilient Distributed DataSet 定义</h3> 
 > 
RDD is a collection of elements partitioned across the nodes of the cluster that can be operated on in parallel.

另外，RDD可以从比如HDFS的文件系统中生成，也有lineage 血缘系统，可以出错后重新计算。提供了transform,action 两大抽象操作。是Spark的核心类。

<h3>什么时候使用RDD</h3>
* 你的数据是unstructed
* 希望操作transform,action等low-level api
* 希望使用函数式而不是DSL
* 不使用schema，也不愿意使用DataSet对structured semi-structured数据的效率提升。


<h2 id="id4">DataSet</h2> 

>
A Dataset is a distributed collection of data. Dataset is a new interface added in Spark 1.6 that provides the benefits of RDDs (strong typing, ability to use powerful lambda functions) with the benefits of Spark SQL’s optimized execution engine.

另外，DataSet能够从JVM Object中生成.

>
A DataFrame is a Dataset organized into named columns. It is conceptually equivalent to a table in a relational database.

理论上,DatFrame可以看成 DataSet[Row]，不过Row实际在JVM中untyped JVM Object,而DataSet是strong typed JVM Object 的集合。

<h3>DataSet的优点</h3>
* High-level Api
* 效率更高，包含空间使用，执行效率。
  Spark SQL engine使用Catalyst执行Query,会有优化效果。
  DataSet使用Encoders map JVM Objects into Tungsten's 的内存管理，效率更高。
  
  
<h2 id="id4">三者互相转化</h2> 
<h3>DataSet to RDD</h3>
 ds.rdd  
 df.rdd

<h3>RDD to DataFrame</h3>

```
import org.apache.spark.sql.types._

// Create an RDD
val peopleRDD = spark.sparkContext.textFile("examples/src/main/resources/people.txt")

// The schema is encoded in a string
val schemaString = "name age"

// Generate the schema based on the string of schema
val fields = schemaString.split(" ")
  .map(fieldName => StructField(fieldName, StringType, nullable = true))
val schema = StructType(fields)

// Convert records of the RDD (people) to Rows
val rowRDD = peopleRDD
  .map(_.split(","))
  .map(attributes => Row(attributes(0), attributes(1).trim))

// Apply the schema to the RDD
val peopleDF = spark.createDataFrame(rowRDD, schema)
```

<h3>RDD,DataFrame to DataSet</h3>

RDD,DataFrame to DataSet,必须定义Case class:  
T代表Case class  
RDD -> RDD[T] -> RDD[T].toDS  
DataFrame.as[T]

<h2 id="id5">总结</h2> 
RDD拥有low-level api以及控制。
DataSet拥有high-level api, DSL,Structure以及更快的速度，更好的效率。

<h2 id="id6">参考</h2> 
Spark document
