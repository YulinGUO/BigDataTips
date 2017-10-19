# Spark Ml model 线上使用

## 前言
Spark Ml训练出来的模型，一般使用到生产中，有下面几种选项: 

1. 离线使用  
也就是利用离线的spark app，将需要预测的数据，整合成RDD，Dataset等候放入模型执行，得到的结果保存在数据库之类中。好处是大批量运行，坏处是很多不需要预测的数据也是被执行了。
2. 将新数据定期执行  
类似利用streaming加载保存的model,然后定期执行需要被预测的数据。
3. Api模式  
将模型toPMML,然后在java程序中加载。
将模型利用spark local模型加载，然后在java程序中运行。
封装Api模式，利用dubbo,可以作为一个类似服务的进程存在。下面将简单介绍如何实现。


##实现

###模型保存  
Spark的模型可以persist到hdfs等分布式文件系统中，以便后来程序可以复用。   
 
    
	  model.save(sparkcontext, modelPath) 

###spark local 模式加载并保存入缓存  

   
     val conf = new SparkConf().setMaster("local").setAppName("CacheProvider").set("spark.driver.host", "localhost").set("spark.driver.allowMultipleContexts", "true")
     
    val sc = new SparkContext(conf)
    val GBTModelCache = CacheBuilder.newBuilder().
    maximumSize(2).
    build(
      new CacheLoader[String, GradientBoostedTreesModel]{
        def load(path: String): GradientBoostedTreesModel = {
          GradientBoostedTreesModel.load(sc, path)
        }
      }
    )
    
    
### 定期更新缓存  
	

    Executors.newScheduledThreadPool(1).scheduleAtFixedRate(new Runnable {
      override def run() = {
        logger.info("refresh model!")
        GBTModelCache.refresh(key)
      }
    }, 24, 24, TimeUnit.HOURS)  
    
    
###获取缓存  


    val key = modelPath  
    val model = GBTModelCache.get(key)  
    
这样子就获得了model,这个model可以被定期更新.