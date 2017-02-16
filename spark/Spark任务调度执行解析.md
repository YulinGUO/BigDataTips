# Spark 任务调度执行解析

RDD  

```
  def count(): Long = sc.runJob(this, Utils.getIteratorSize _).sum
```

SparkContext

```
 dagScheduler.runJob(rdd, cleanedFunc, partitions, callSite, resultHandler, localProperties.get)
```

DAGScheduler

```
val waiter = submitJob(rdd, func, partitions, callSite, resultHandler, properties)

->

 eventProcessLoop.post(JobSubmitted(
      jobId, rdd, func2, partitions.toArray, callSite, waiter,
      SerializationUtils.clone(properties)))
```

eventProcessLoop是dagScheduler的内部类，当收到JobSubmitted的消息时候，  

```
case JobSubmitted(jobId, rdd, func, partitions, callSite, listener, properties) =>
      dagScheduler.handleJobSubmitted(jobId, rdd, func, partitions, callSite, listener, properties)
      
 ->
 dagScheduler.handleJobSubmitted会经过分析划分Job,Stage
 submitStage(finalStage)
 
 ->
 submitMissingTasks(stage, jobId.get)
 
 ->
       taskScheduler.submitTasks(new TaskSet(
        tasks.toArray, stage.id, stage.latestInfo.attemptId, jobId, properties))
```
taskScheduler的实现类是taskSchedulerImpl, 他的submitTasks如下： 
 
```
 backend.reviveOffers()
```
这边的backend的实现类是CoarseGrainedSchedulerBackend,向NettyRpcEnv注册并自己保存了driverEndPointRef,

```
  override def reviveOffers() {
    driverEndpoint.send(ReviveOffers)
  }
```
上个函数发送了ReviveOffers函数，driverEndPoint会执行makeOffers,  

```
launchTasks(scheduler.resourceOffers(workOffers))

->
  executorData.executorEndpoint.send(LaunchTask(new SerializableBuffer(serializedTask)))
```
这样，通过本身保存的executorEndpointRef，发送LaunchTask命令。

在CoarseGrainedExecutorBackend中，会收到LaunchTask消息  

```
val taskDesc = ser.deserialize[TaskDescription](data.value)
        logInfo("Got assigned task " + taskDesc.taskId)
        executor.launchTask(this, taskId = taskDesc.taskId, attemptNumber = taskDesc.attemptNumber,
          taskDesc.name, taskDesc.serializedTask)
```
收到消息后，反序列化task，并让executor 执行task  

```
   val tr = new TaskRunner(context, taskId = taskId, attemptNumber = attemptNumber, taskName,
      serializedTask)
    runningTasks.put(taskId, tr)
    threadPool.execute(tr)
```
至此，task已经运行。
