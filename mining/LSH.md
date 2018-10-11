##Locality-Sensitive Hashing, LSH
局部敏感哈希(Locality Sensitive Hashing，LSH)算法,是近似最近邻搜索算法中最流行的一种，它有坚实的理论依据并且在高维数据空间中表现优异。它的主要作用就是从海量的数据中挖掘出相似的数据，可以具体应用到文本相似度检测、网页搜索等领域。
### 1.基本思想
局部敏感哈希的基本思想类似于一种空间域转换思想，LSH算法基于一个假设，如果两个文本在原有的数据空间是相似的，那么分别经过哈希函数转换以后的它们也具有很高的相似度；相反，如果它们本身是不相似的，那么经过转换后它们应仍不具有相似性。

　　哈希函数，大家一定都很熟悉，那么什么样的哈希函数可以具有上述的功能呢，可以保持数据转化前后的相似性？当然，答案就是局部敏感哈希。普通的哈希算法，即使原始数据微小的改动，哈希后的两个结果也相差巨大。
　　
### 2.文本相似度计算
基本步骤包含三个：
1.Shingling:Converts a document into a set representation (Boolean vector)
2.Min-Hashing: Convert large sets to short signatures, while preserving similarity
3.Locality-Sensitive Hashing: Focus on pairs of signatures likely to be from similar documents:
 Candidate pairs!
####2.1 Shingling
假设现在有4个网页，我们将它们分别进行Shingling（将待查询的字符串集进行映射，映射到一个集合里，如字符串“abcdeeee", 映射到集合”(a,b,c,d,e)", 注意集合中元素是无重复的，这一步骤就叫做Shingling, 意即构建文档中的短字符串集合，即shingle集合。），得到如下的特征矩阵：
![](https://static.oschina.net/uploads/img/201708/14005312_ZUHR.jpg)
其中“1”代表对应位置的Shingles在文档中出现过，“0”则代表没有出现过。
在衡量文档的相似度中，我们有很多的方法去完成，比如利用欧式距离、编辑距离、余弦距离、Jaccard距离等来进行相似度的度量。在这里我们运用Jaccard相似度。接下来我们就要去找一种哈希函数，使得在hash后尽量还能保持这些文档之间的Jaccard相似度,如果原来documents的Jaccard相似度sim(C1, C2)高，那么它们的hash值相同h(C1)=h(C2)的概率高，如果原来documents的Jaccard相似度低，那么它们的hash值不相同h(C1)≠h(C2)的概率高。
#### 2.2 Min-hashing
Min-hashing定义为：特征矩阵按行进行一个随机的排列后，第一个列值为1的行的行号。  
![](https://static.oschina.net/uploads/img/201708/14005312_d0VP.jpg)
首先生成一堆随机置换，把Signature Matrix的每一行进行置换，然后hash function就定义为把一个列C hash成置换后的列C上第一个值为1的行的行号（ 这里的hash仅仅是一个排列+选择，如何使用真正的hash函数模拟这种排列+选择见下面它的具体实现）:
![](https://static.oschina.net/uploads/img/201708/14005313_oycW.jpg)
图中展示了三个置换（彩色的那三条）。比如现在看蓝色的那个置换，置换后的Signature Matrix（不用真的置换，使用索引就OK）为：
![](https://static.oschina.net/uploads/img/201708/14005313_wH5H.jpg)
然后看第一列的第一个是1的行是第2行，第二列的第一个是1的行号是2， 同理再看三四列，分别是2，1，因此这四列（document）在这个置换下，被哈希成了2，1，2，1，对应图右图蓝色部分，也就相当于每个document现在是1维的。再通过另外两个置换然后再hash，又得到右边的另外两行，于是最终结果是每个document从7维降到了3维。也就是说最后 通过minhash，我们将input matrix压缩成了n个维度（n为minhash函数个数）。注意，每置换（排列）对应生成签名矩阵的一行。 

#### 2.3 LSH
上面通过minhash降低了文档相似性比较复杂度（features减少了），但是即使文档数目很少，需要比较的数目可能还是相当大。所以要生成候选对，只有形成候选对的两列才进行相似度计算，相似度计算复杂度又降低了
基本思想：只有两列有比例fraction t的元素是一样的才当成一个candidate pair。列c和d要成为候选集，其signatures相同比例至少为t，也就是signatures矩阵中行相同的个数即M(i,c)=M(i,d)的数目占的比例至少为t。threshold t是成为候选集的最小阈值。我们想要达到的目的是：保证相似的列（signatures）尽可能地hash到同一个bucket中，而不是不相似的两列。
![](https://static.oschina.net/uploads/img/201708/14005319_Tfdg.jpg)
由于列c和d要成为候选集，其signatures相似度至少为t。对signature矩阵，我们通过创建很大数目的hash函数（普通的hash函数，不是minhash函数）。对每个选择的hash函数，我们把列hash到buckets中，并将同一个bucket中的所有pairs作为候选pair。也就是说只要有一个hash函数将两列hash到同一个bucket中，它们就成为一个候选对，之后进行相似度计算。可以看出这样做同一个bucket中列两两之间才进行相似度计算，而不是所有列两两之间进行相似度计算，减少了计算量。

哈希函数数目和桶数目的协调：为了使每个bucket中的signatures数目相对较少，从而生成较少的候选pairs，我们需要调整hash函数和每个hash函数的buckets数目。但是也不能使用太多的buckets，否则真正相似的pairs都不会被任意一个hash函数wind up到同一个bucket中。

1 首先将Signature Matrix分成一些bands，每个bands包含r行rows。b*r就是signatures的总长度（也就是在minhash阶段用于创建signatures的min hash函数的数目）。
![](https://static.oschina.net/uploads/img/201708/14005320_yqna.jpg)
2 然后把每个band哈希到一些bucket中（不同的band使用不同的hash函数，也就是对每个band我们都要创建一个hash函数）。
这个方法的直觉含义就是，只要两个signatures在某个片断band上相似（hash到同一个bucket中），它们在整体上就有一定的概率相似，就要加入候选pairs进行比较！同一个bucket中的两列是局部相似的（因为只要某个band相似就会至少hash一次到同一个bucket中，这应该就是局部敏感哈希名称的来源），所以同一个bucket中的两两对都是候选对。如果两个signatures大部分都是相同的，那么存在bands 100%相同就有很大的机会。而两列如果不相似，即很少有相同的片段，那他们被hash到同一个bucket中的概率就相当小，只要bucket的数量要足够多，两个不一样的bands就会被哈希到不同的bucket中。
![](https://static.oschina.net/uploads/img/201708/14005320_ZMae.jpg)
LSH准确率分析:
我们想要的LSH理想情况

设s是2个sets(docs、colums、signatures)真实相似度，t是相似度阈值，共享同一个bucket的概率为1时（也就是两列总是hash到同一bucket中，其应该推断出相似度是100%），那么两列的相似性越大，并且大于这个阈值t，说明两列是相似的（与应该推断出相似度是100%相符）。

理想阶跃函数：当两个sets相似度很高时（>t），总是分到同一个bucket中，相反相似度低时，总是不会分到同一个bucket中（分到同一bucket中的概率为0）。
![](https://static.oschina.net/uploads/img/201708/14005322_Ibdm.jpg)
实际情况：分析signature matrix的单行（两列）
阈值为t时的false pos和false neg。感觉图中false pos填充的区域不直观，反直觉，但是要记得false pos是相似性t小但是分到同一buckets中的概率。
![](https://static.oschina.net/uploads/img/201708/14005322_zaqJ.jpg)
由于两个minhash值相等的概率=underlying set的Jaccard相似度，所以单行对应的是图中对角线（红线）。

False pos和neg的概率大小控制

这些false pos和false neg的概率是可以通过选取不同的band数量b以及每个band中的row的数量r来控制的。b和r都越大，也就是signatures的长度（signature矩阵的行数）越长，S曲线就越接近于阶跃函数，false positives and negatives相应就会减小，但是signatures越长，它们占用的空间的计算量就越大，minhash阶段就需要更多工作。通俗点就，就是在minhash阶段多弄几个minhash函数，就可以使LSH阶段的错误更小一些

S曲线分析：阈值t的确定

直接在signature matrix中比较相似度不容易确定threshold t，而添加s,r后，可以根据理论很好的设置一个t=(1/b)^1/r， 但是我们不管b和r的值是多少，怎么确定。
s是2个sets(docs、colums、signatures)真实相似度。则可得出以下概率计算公式：
![](https://static.oschina.net/uploads/img/201708/14005322_tFgM.jpg)![](https://static.oschina.net/uploads/img/201708/14005322_tFgM.jpg)![](https://static.oschina.net/uploads/img/201708/14005322_V7yw.jpg)![](https://static.oschina.net/uploads/img/201708/14005323_IaLe.jpg)
当b和r变大时，函数1-（1-s^r)^b （阈值s下相似的列hash到至少同一个桶中的概率）的增长类似一个阶跃函数step function。当阈值在大概(1/b)^1/r这个位置时跳跃。

这个阈值实际上是函数f(S) = (1-S^r)^b 的不动点（近似值），也就是输入一个相似度S，得到一个相同的概率p（hash到同一个bucket中的概率）。当相似性相对不动点变大时，其hash到同一个bucket中的概率也变大，反之相似性相对不动点变小时，其hash到同一个bucket中的概率也变小了。Threshold t will be approximately (1/b)^1/r. But there are many suitable values of b and r for a given threshold. 所以应该选择t = (1/b)^1/r 作为阈值，这样大于这个阈值说明两列相似，否则不相似。
![](https://static.oschina.net/uploads/img/201708/14005323_Ihdc.jpg)
LSH分析实例
![](https://static.oschina.net/uploads/img/201708/14005323_EZCG.jpg)
从上表中看出，0.4-0.6之间的跳跃最大，幅度超过0.6，thresh取值在这个范围内最优。实际计算(1/b)^1/r也正好在这个范围内。

###3.Bucketed Random Projection for Euclidean Distance
这是Spark实现的另一个算法。一般来讲，Jacard Distance对比feature,重点在于某个features有还是没有，而Euclidean关注的重点是特征上面的取值(自然包含有还是没有)。
Euclidean distance定义如下：
$d(\mathbf{x}, \mathbf{y}) = \sqrt{\sum_i (x_i - y_i)^2}
$

Its LSH family projects feature vectors x onto a random unit vector v and portions the projected results into hash buckets: 
$h(\mathbf{x}) = \Big\lfloor \frac{\mathbf{x} \cdot \mathbf{v}}{r} \Big\rfloor
$
where r is a user-defined bucket length. The bucket length can be used to control the average size of hash buckets (and thus the number of buckets). A larger bucket length (i.e., fewer buckets) increases the probability of features being hashed to the same bucket (increasing the numbers of true and false positives).
我们项目中，比如分析用户相似，这时候关注的不是用户是否有某个特征，也关心用户在某个特征上面的取值。这时候BRP是个更好的选择。
###参考
https://my.oschina.net/u/3579120/blog/1508140
http://web.stanford.edu/class/cs246/slides/03-lsh.pdf
https://www.youtube.com/watch?v=bQAYY8INBxg



