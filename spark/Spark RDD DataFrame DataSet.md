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

<h3>DataSet详解</h3>  

![](https://jaceklaskowski.gitbooks.io/mastering-apache-spark/content/images/spark-sql-Dataset.png)   


从上图来看， dataset就是 Encoder以及QueryExecution的二元组。跟RDD相同，ds也是lazy,只有当action被执行的时候，query才会执行。实际上，DS是在某些数据源（包含分布式文件系统，HIVE，JDBC）上执行Query expression.而结构化的qe可以是sql query, column-based sql或者scala lamda function.  


```
scala> val dataset = (0 to 4).toDS
dataset: org.apache.spark.sql.Dataset[Int] = [value: int]

// Variant 1: filter operator accepts a Scala function
dataset.filter(n => n % 2 == 0).count

// Variant 2: filter operator accepts a Column-based SQL expression
dataset.filter('value % 2 === 0).count

// Variant 3: filter operator accepts a SQL query
dataset.filter("value % 2 = 0").count

```



<h3>DataSet的优点</h3>
* High-level Api
* 效率更高，包含空间使用，执行效率。
  Spark SQL engine使用Catalyst执行Query,会有优化效果。
  DataSet使用Encoders map JVM Objects into Tungsten's 的内存管理，效率更高。
  
  
<h2 id="id4">三者关系</h2> 
<h3>General</h3>  

```  
import spark.implicits._
case class Token(name: String, productId: Int, score: Double)
val data = Seq(Token("aaa", 100, 0.12))
// Transform data to a Dataset[Token]
val ds = data.toDS
// Transform data into a DataFrame with no explicit schema
val df = data.toDF

// Transform DataFrame into a Dataset
val ds = df.as[Token]

ds.printSchema
root
 |-- name: string (nullable = true)
 |-- productId: integer (nullable = false)
 |-- score: double (nullable = false)
ds.show
+----+---------+-----+
|name|productId|score|
+----+---------+-----+
| aaa|      100| 0.12|

// In DataFrames we work with Row instances
scala> df.map(_.getClass.getName).show(false)
+--------------------------------------------------------------+
|value                                                         |
+--------------------------------------------------------------+
|org.apache.spark.sql.catalyst.expressions.GenericRowWithSchema|

// In Datasets we work with case class instances
scala> ds.map(_.getClass.getName).show(false)
+---------------------------+
|value                      |
+---------------------------+
|$line40.$read$$iw$$iw$Token|

``` 

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
