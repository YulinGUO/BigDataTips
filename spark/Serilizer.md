#Java Serializer, Kyro Serializer

在spark中，需要对数据(Object)进行远程传输(Shuffle)，由于现有的网络架构只能传输字节，不能直接传输Object,所以会用到SerDe。序列化就是把Java对象中的value/states翻译为字节，以便通过网络传输或者保存。另外，反序列化就是通过读取字节码，并把它翻译回java对象。  
默认情况下，spark使用java serializer.同时也提供了Kyro。

##1Java Serializer   

序列化：  

```
FileOutputStream fileOut = new FileOutputStream("employee.ser");  
ObjectOutputStream outStream = new ObjectOutputStream(fileOut);  
outStream.writeObject(emp);
 
```  

ObjectOutputStream把primitive data以及Java Object的图写到一个OutputStream中。
默认的序列化的机制是：将object的类，类签名，以及其他所有非transient,非static的属性写入。如果有其他类的引用，也会写入。 writeObject方法负责将object的state写入，而对应的readObject负责恢复。State是通过把单独的属性写入ObjectOutputStream而保存的。  

序列化的数据是写入到block中，block data是由header以及data组成。而header包含marker以及字节树木。＝》实际上很占用空间，这也是为啥spark使用kyro的原因之一。  

反序列化:

```
FileInputStream fileIn =new FileInputStream("employee.ser");
ObjectInputStream in = new ObjectInputStream(fileIn);
emp = (Employee) in.readObject();
 
```    

ObjectInputStream负责反序列化。ObjectInputStream确保所有的object的类型是匹配上JVM中的类。

默认的反序列化机制是恢复每个属性的值以及类型，而transient,static的被忽略。Object的图使用引用分享机制而被正确的恢复。  
读取一个object是跟使用constructor构造一个新的object类似。内存分配给这个object,初始化为null。无参的构造器被调用（non-serializable classed）,然后可序列化的类的属性被从stream中恢复，可序列化的类被恢复为java.lang.object。


##2 Kyro Serializer

Java缺点： Java serialization很灵活，但是通常很慢，占用空间也很大。

Kryo serialization:  Kryo 比java要快很多，但是并不支持所有的 Serializable types.
