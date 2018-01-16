##升级Spark2.2.1 with Hadoop 2.3

###1.Spark2.2X
Spark2.2.X官方文档中已经明确指定不支持Hadoop2.7以下，Java1.8，Scala2.11以下版本。同时引入了很多新的特性。那么如何不升级Hadoop集群的情况下，升级到Spark2.2.X呢？  

解决方案参考一下链接：  
<https://www.iteblog.com/archives/2305.html>

###2.build

下载了<https://github.com/397090770/spark-2.2-for-hadoop-2.2>代码后：

>./dev/make-distribution.sh --tgz -Phadoop-2.2 -Dhadoop.version=2.3.0 -Pyarn  -DskipTests -Phive -Phive-thriftserver

其中，需要指定你的Hadoop版本，支持 Yarn，支持Hive

###3.conf
Build完毕之后，进入bin后，其实就可以执行spark-submit了，所以这个也不存在升级。   
所以之前spark版本的配置文件，也可以一起copy到conf文件下，包括hdfs,hive等配置。  

 
需要注意的是，如何配置能够使多个Spark版本并存呢？  
 
 
1.更改每个版本的spark-submit => spark-submit-2.2.1  
2.ln -s YOUR_SPARK_SUMIT-2.2.2 /usr/bin
3.修改spark-submit-2.2.1
这里修改的是让spark-submit找到对应版本的spark-home


	if [ -z "${SPARK_HOME}" ]; then  
    	export SPARK_HOME="/usr/local/software/spark-2.2-for-hadoop-2.2"  
	fi


###4.参考
[源代码下载](https://github.com/397090770/spark-2.2-for-hadoop-2.2)