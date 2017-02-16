#Spark RpcEnv解析

#1.RpcEnv  
RpcEnvironment是RpcEndpoints的环境，通常来说管理RpcEndpoints的整个生命周期:  

* 注册（使用name或者uri）
* 路由发送给RpcEndpoints的消息
* 停止RpcEndpoints  

现在spark中唯一实现了RpcEnv的是基于Netty的，可以通过RpcEnv.create的工厂方法新建。  
RpcEndpoints定义了如何处理得到的消息，通过name,uri向RpcEnv注册以便从RpcEndpointRefs接受消息。  

三者关系如下图：  


![icon](https://jaceklaskowski.gitbooks.io/mastering-apache-spark/content/diagrams/rpcenv-endpoints.png)

另外，可以通过name,uri来查找RpcEndpointsRef。


#2.Netty  

Netty的官网定义:  
```
Netty is an asynchronous event-driven network application framework 
for rapid development of maintainable high performance protocol servers & clients.
```

Netty & Akka区别？  

#3.工作流程

SparkContext初始化了sparkEnv,在sparkEnv中，

```
 val rpcEnv = RpcEnv.create(systemName, hostname, port, conf, securityManager,
      clientMode = !isDriver)
```

而RpcEnv的create函数如下： 
 
```
  def create(
      name: String,
      host: String,
      port: Int,
      conf: SparkConf,
      securityManager: SecurityManager,
      clientMode: Boolean = false): RpcEnv = {
    val config = RpcEnvConfig(conf, name, host, port, securityManager, clientMode)
    new NettyRpcEnvFactory().create(config)
  }
```

从这里可以看出，spark2.0后Rpc实现方式仅仅保留了Netty。这段函数利用nettyRpc的工厂函数，新建了一个RpcEnv。  
NettyRpc工厂的create函数如下：  

```
val nettyEnv =
      new NettyRpcEnv(sparkConf, javaSerializerInstance, config.host, config.securityManager)
```
这里实例化了一个NettyRpcEnv。  

如何使用driverEndPoint:  

接下来我们看看RpcEndPoint，RpcEndPointRef是如何使用的。  

在TaskSchedulerImpl类中: 
 
```
 override def submitTasks(taskSet: TaskSet) {
 ...
  backend.reviveOffers()
 }
```
backend是SchedulerBackend的一个实例，这个函数最后调用的是:
CoarseGrainedSchedulerBackend中的函数：

```
  override def reviveOffers() {
    driverEndpoint.send(ReviveOffers)
  }
```
driverEndpoint类型是RpcEndpointRef，RpcEnv会把从ref发送的消息转发到endpoint中以便调用正确的函数。  
我们看一下CoarseGrainedSchedulerBackend如何新建注册driverEndpointRef:  

```
protected def createDriverEndpointRef(
      properties: ArrayBuffer[(String, String)]): RpcEndpointRef = {
    rpcEnv.setupEndpoint(ENDPOINT_NAME, createDriverEndpoint(properties))
  }

  protected def createDriverEndpoint(properties: Seq[(String, String)]): DriverEndpoint = {
    new DriverEndpoint(rpcEnv, properties)
  }
```
而在NettyRpcEnv,则定义了driverEndpoint，以及消息接受处理机制，executor的信息。这样在接受到消息的时候，可以直接调用对应的函数。 

NettyRpcEnv的setupEndpoint如下：  

```
 override def setupEndpoint(name: String, endpoint: RpcEndpoint): RpcEndpointRef = {
    dispatcher.registerRpcEndpoint(name, endpoint)
  }
``` 
dispatcher负责dispatch消息，路由消息到对应的endpoint，可以说是rpcEnv主要做事的。有一个val threadpool: ThreadPoolExecutor ，一直循环，处理消息。

那driver端是如何让executor端接受到命令呢？  
executor端有CoarseGrainedExecutorBackend,在开始的时候，会主动调用driverEndPointRef来注册自己：  

```
 driver = Some(ref)
      ref.ask[Boolean](RegisterExecutor(executorId, self, hostname, cores, extractLogUrls))
```
这样，CoarseGrainedSchedulerBackend端会接受到消息并将executorRef保存下来：  

```
      val data = new ExecutorData(executorRef, executorRef.address, hostname,
            cores, cores, logUrls)
```


