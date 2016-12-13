<h1 id="id1">How to implement connection pool in spark </h1>

<h2 id="id2">问题所在</h2>  
在[Spark Streaming Guid中](http://spark.apache.org/docs/latest/streaming-programming-guide.html)，提到:  

	dstream.foreachRDD { rdd =>
	    rdd.foreachPartition { partitionOfRecords =>
    	// ConnectionPool is a static, lazily initialized pool of connections
    	val connection = ConnectionPool.getConnection()
    	partitionOfRecords.foreach(record => connection.send(record))
    	ConnectionPool.returnConnection(connection)  // return to the pool for future reuse
    	}}   
    	
    	
 可是如何具体实现呢？ 
  
 <h2 id="id2">scala + Mongodb实现连接池</h2> 
 一个通常意义上的连接池，能够请求获取资源，也能释放资源。不过MongoDB java driver已经帮我们实现了这一套逻辑。
 
 > 
 Note: The Mongo object instance actually represents a pool of connections to the database; you will only need one object of class Mongo even with multiple threads.  See the concurrency doc page for more information.

> The Mongo class is designed to be thread safe and shared among threads. Typically you create only 1 instance for a given DB cluster and use it across your app. If for some reason you decide to create many mongo intances, note that:

> all resource usage limits (max connections, etc) apply per mongo instance
to dispose of an instance, make sure you call mongo.close() to clean up resources

也就是说，我们的pool，只要能获得Mongo就可以了。也就是说每次请求，在executor端，能get已经创建好了MongoClient就可以了。

 ```
object MongoPool {

  var  instances = Map[String, MongoClient]()

  //node1:port1,node2:port2 -> node
  def nodes2ServerList(nodes : String):java.util.List[ServerAddress] = {
    val serverList = new java.util.ArrayList[ServerAddress]()
    nodes.split(",")
      .map(portNode => portNode.split(":"))
      .flatMap{ar =>{
      if (ar.length==2){
        Some(ar(0),ar(1).toInt)
      }else{
        None
      }
    }}
      .foreach{case (node,port) => serverList.add(new ServerAddress(node, port))}

    serverList
  }

  def apply(nodes : String) : MongoClient = {
    instances.getOrElse(nodes,{
      val servers = nodes2ServerList(nodes)
      val client =  new MongoClient(servers)
      instances += nodes -> client
      println("new client added")
      client
    })
  }
}
```

这样，一个static 的MongoPool的Object已经创建，scala Ojbect类，在每个JVM中会初始化一次。

```
rdd.foreachPartition(partitionOfRecords => {

   val nodes = "node:port,node2:port2"
   lazy val  client = MongoPool(nodes)
   lazy val  coll2 = client.getDatabase("dm").getCollection("profiletags")

   partitionOfRecords.grouped(500).foreach()
})
```    

注意，此处client用lazy修饰，等到executor使用client的时候，才会执行。否则的话，会出现client not serializable.

<h3 id="id222">优点分析</h3>

1.不重复创建，销毁跟数据库的连接，效率高。 
 Spark 每个executor  申请一个JVM进程，task是多线程模型，运行在executor当中。task==partition数目。Object只在每个JVM初始化一次。  
2.代码design pattern


<h3 id="id222">参考资料</h3>

[Spark Streaming Guid](http://spark.apache.org/docs/latest/streaming-programming-guide.html)

 
 