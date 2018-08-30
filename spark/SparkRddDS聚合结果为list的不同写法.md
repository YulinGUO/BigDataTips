## Spark 聚合结果为list
很多推荐算法，比如item-cf,ALS等，都有物品相似度作为结果，保存在后台模型当中，供后台使用。结构一般保存为:key->当前item a, values->list,其中list的每个元素为item b, value b。一般还要按照value进行排序。这样后，直接把结果保存到数据库当中。
##模拟数据


```
    case class Item(ida: Int, idb: Int, distance: Double)
    val data = spark.createDataFrame(Seq(
      (0, 1, 0.6d),
      (0, 2, 0.2d),
      (0, 3, 0.9d),
      (1, 0, 0.6d),
      (2, 0, 0.2d),
      (2, 3, 0.5d),
      (3, 0, 0.9d),
      (3, 2, 0.5d)
    )).toDF("ida", "idb", "distance")
      .as[Item]
```

这样我们就得到了原始数据，里面每一行格式为: ida, idb,value。
## RDD聚合的写法


```    
    data.rdd
      .map(one => (one.ida, ListBuffer((one.idb, one.distance))))
      .reduceByKey { case (lia, lib) =>
        lia.appendAll(lib)
        lia
      }
      .map { case (id, lia) =>
        (id, lia.sortBy(_._2)(Ordering[Double]).toList)
      }
```
这种写法，首先将每一行的idb,distance，放到一个listbuffer当中，然后按照ida进行聚合，最后排序listbuffer.写起来简单，不过效率比较低，1是每一条记录都要新建一个listbuffer，2是shuffle的时候，listbuffer的体积也比较大。

```
    data.rdd
      .map(one => (one.ida, (one.idb, one.distance)))
      .aggregateByKey(new ListBuffer[(Int, Double)])(
        (li: ListBuffer[(Int, Double)], v: (Int, Double)) => {
          li.append(v)
          li
        },
        (c1: ListBuffer[(Int, Double)], c2: ListBuffer[(Int, Double)]) => {
          c1.appendAll(c2)
          c1
        }
      )
      .map { case (id, lia) =>
        (id, lia.sortBy(_._2)(Ordering[Double]).toList)
      }
```
改进的写法：利用了aggregateByKey,这样相同的ida的记录，在每个partition里面就会创建一次。  
流程是：在每个partition当中，aggregator碰到一条记录，如果这个ida已经存在listbuffer，那就把新的记录中的idb,distance加到这个listbuffer当中,这个操作叫做mergeValue,如果不存在，就新建一个listbuffer,这个操作叫做createCombiner
不同的partition之间的数据合并，也就是不同的listbuffer合并,这个操作叫做mergeCombiner。

##DataSet的写法
我们来看看dataset的写法，作为一个使用spark1.1很久的人，第一种写法当然是模仿rdd了，毕竟dataset也具有函数式的写法。

```
    data.map { case Item(ida, idb, distance) =>
      (ida, ListBuffer((idb, distance)))
    }
      .groupByKey(_._1)
      .reduceGroups { (a, b) =>
        (a._1, {
          a._2.appendAll(b._2)
          a._2
        })
      }
      .map { case (ida, (idaa, li)) => (ida, li.sortBy(_._2)(Ordering[Double])) }
```
问题跟rdd中每条记录都要新建一个lisbuffer是一样的。
实际上，dataset(dataframe)中具有很多内置的function,这些function能充分利用spark1.5以后的一些优化，比如内存管理，encoders等，效率还是很高的。


``` 
    case class ItemB(idb: Int, distance: Double)
    val w = Window.partitionBy(col("ida")).orderBy(col("distance"))
    import spark.implicits._

    data.withColumn("nn", struct(col("idb"), col("distance")).as[ItemB])
      .withColumn("res", collect_list("nn").over(w))
      .groupBy(col("ida"))
      .agg(max(col("res")).as("res"))
```

我们需要把多个列归并到一个列当中，struct可以帮忙实现这个。而聚合的功能，则交给collectlist完成。但是如何排序呢？  
这时候就要用到window操作了，这个window的大致意思是，首先按照ida进行partition,并且按照distance排序，然后从头到尾一条一条的遍历记录。跟collect_list一起使用后，结果如下：  

![res](https://raw.githubusercontent.com/YulinGUO/BigDataTips/master/spark/imgs/windowRes.png)
可以看到，里面是按照ida进行partition的，然后res的记录数是随着遍历的ida慢慢变多了。
这样以后，我们只要取每条记录的最后一条记录就好，groupby 以及max可以帮忙实现这个。

如果，你想限制最后结果中，list的个数呢？当然，也是要按照得分从低到高取。 

```
    val filterRange = 2
    val rankedData =data.withColumn("rank", dense_rank.over(w))
        .filter(col("rank") leq filterRange)

    rankedData.withColumn("nn", struct(col("idb"), col("distance")).as[ItemB])
      .withColumn("res", collect_list("nn").over(w))
      .groupBy(col("ida"))
      .agg(max(col("res")).as("res"))
```
首先使用dense_rank排序得到序号，然后按照序号过滤。






