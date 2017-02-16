# SparkEnv 源码解析

## 1.定义
SparkEnv,包含了driver,executor端运行时的环境object,包括:serializer, RpcEnv, block manager, map output tracker等。

## 2.类中的内容

```
  private def create(
      conf: SparkConf,
      executorId: String,
      hostname: String,
      port: Int,
      isDriver: Boolean,
      isLocal: Boolean,
      numUsableCores: Int,
      listenerBus: LiveListenerBus = null,
      mockOutputCommitCoordinator: Option[OutputCommitCoordinator] = None)
```

上面为构造SparkEnv的help类。  
这个函数初始化了SparkEnv大部分有用的变量，包含：  
RpcEnv,  
serializerManager,closureSerializer,broadcastManager,mapOutputTracker,shuffleManager,useLegacyMemoryManager,blockManagerMaster,blockManager,metricsSystem,outputCommitCoordinator