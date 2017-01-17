#Java Serializer, Kyro Serializer, Encoders

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

##3 Encoders
Encoders是spark Sql 2.0以后引入的一个serde框架的基础概念。  

```
trait Encoder[T] extends Serializable {
  def schema: StructType
  def clsTag: ClassTag[T]
}
```

 Encoder[T]是用于(encode and decode)任何JVM object，primitive type和spark sql's internalRow转换。而InternalRow是 使用catalyst expressions ,code generation表示的internal binary row format.    
Encoders保存了纪录的schema,这也是他们serde速度相比java,kryo快的原因。

```
// The domain object for your records in a large dataset
case class Person(id: Long, name: String)

import org.apache.spark.sql.Encoders

scala> val personEncoder = Encoders.product[Person]
personEncoder: org.apache.spark.sql.Encoder[Person] = class[id[0]: bigint, name[0]: string]

scala> personEncoder.schema
res0: org.apache.spark.sql.types.StructType = StructType(StructField(id,LongType,false), StructField(name,StringType,true))

scala> personEncoder.clsTag
res1: scala.reflect.ClassTag[Person] = Person

import org.apache.spark.sql.catalyst.encoders.ExpressionEncoder

scala> val personExprEncoder = personEncoder.asInstanceOf[ExpressionEncoder[Person]]
personExprEncoder: org.apache.spark.sql.catalyst.encoders.ExpressionEncoder[Person] = class[id[0]: bigint, name[0]: string]

// ExpressionEncoders may or may not be flat
scala> personExprEncoder.flat
res2: Boolean = false

// The Serializer part of the encoder
scala> personExprEncoder.serializer
res3: Seq[org.apache.spark.sql.catalyst.expressions.Expression] = List(assertnotnull(input[0, Person, true], top level non-flat input object).id AS id#0L, staticinvoke(class org.apache.spark.unsafe.types.UTF8String, StringType, fromString, assertnotnull(input[0, Person, true], top level non-flat input object).name, true) AS name#1)

// The Deserializer part of the encoder
scala> personExprEncoder.deserializer
res4: org.apache.spark.sql.catalyst.expressions.Expression = newInstance(class Person)

scala> personExprEncoder.namedExpressions
res5: Seq[org.apache.spark.sql.catalyst.expressions.NamedExpression] = List(assertnotnull(input[0, Person, true], top level non-flat input object).id AS id#2L, staticinvoke(class org.apache.spark.unsafe.types.UTF8String, StringType, fromString, assertnotnull(input[0, Person, true], top level non-flat input object).name, true) AS name#3)

// A record in a Dataset[Person]
// A mere instance of Person case class
// There could be a thousand of Person in a large dataset
val jacek = Person(0, "Jacek")

// Serialize a record to the internal representation, i.e. InternalRow
scala> val row = personExprEncoder.toRow(jacek)
row: org.apache.spark.sql.catalyst.InternalRow = [0,0,1800000005,6b6563614a]

// Spark uses InternalRows internally for IO
// Let's deserialize it to a JVM object, i.e. a Scala object
import org.apache.spark.sql.catalyst.dsl.expressions._

// in spark-shell there are competing implicits
// That's why DslSymbol is used explicitly in the following line
scala> val attrs = Seq(DslSymbol('id).long, DslSymbol('name).string)
attrs: Seq[org.apache.spark.sql.catalyst.expressions.AttributeReference] = List(id#8L, name#9)

scala> val jacekReborn = personExprEncoder.resolveAndBind(attrs).fromRow(row)
jacekReborn: Person = Person(0,Jacek)

// Are the jacek instances same?
scala> jacek == jacekReborn
res6: Boolean = true
```


