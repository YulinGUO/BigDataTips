## Word2Vec详解

###1.Huffman编码  
哈夫曼树又称最优二叉树，是一种带权路径长度最短的二叉树。  
所谓树的带权路径长度，就是树中所有的叶结点的权值乘上其到根结点的 路径长度（若根结点为0层，叶结点到根结点的路径长度为叶结点的层数）。树的带权路径长度记为  

>WPL= (W1 * L1+W2 * L2+W3 * L3+...+Wn * Ln)，  

N个权值Wi(i=1,2,...n)构成一棵有N个叶结点的二叉树，相应的叶结点的路径长度为Li(i=1,2,...n)。可以证明哈夫曼树的WPL是最小的。

哈夫曼编码步骤：

1.对给定的n个权值{W1,W2,W3,...,Wi,...,Wn}构成n棵二叉树的初始集合F= {T1,T2,T3,...,Ti,...,Tn}，其中每棵二叉树Ti中只有一个权值为Wi的根结点，它的左右子树均为空。（为方便在计算机上实现算 法，一般还要求以Ti的权值Wi的升序排列。）  
2.在F中选取两棵根结点权值最小的树作为新构造的二叉树的左右子树，新二叉树的根结点的权值为其左右子树的根结点的权值之和。  
3.从F中删除这两棵树，并把这棵新的二叉树同样以升序排列加入到集合F中。  
4.重复二和三两步，直到集合F中只有一棵二叉树为止

###2.词向量  
在NLP任务中，我们将自然语言交给机器学习算法去处理，但是机器无法直接理解人类的语言，因此首要任务就是将语言数学化。  
一种最简单的词向量是 one-hot representation,就是用一个很长的向量表示词，向量长度L等于词典D的大小，向量的分量只有一个为1，其余为0.  
另一种是Distributed Representation,基本想法是：通过训练将某种语言中的每个词映射成一个固定长度的短向量，所有这些向量构成一个词向量空间，而每一个向量可以视为空间中的一个点。word2vec就是采用这种词向量。  

###3.基于Hierarchical Softmax的模型  
CBOW: Continuous Bag-of-words Model,在已知词Wt的上下文Wt-2,wt-1,wt+1,wt+2的前提下预测当前词Wt.  
Skip-gram:在已知当前词Wt的前提下，预测t的上下文Wt-2,wt-1,wt+1,wt+2  

![模型](https://deeplearning4j.org/img/word2vec_diagrams.png)  

###3.1目标优化函数 
基于神经网络的语言模型的目标函数通常取为如下对数似然函数：
CBOW
$$L =\sum_{w\in c}log p(Context(w)|w)$$
Skip-gram:
$$L =\sum_{w\in c}log p(w|Context(w))$$

###3.2 CBOW
CBOW的网络结构如下：
![CBOW](https://images2015.cnblogs.com/blog/524764/201612/524764-20161205143941304-931970603.png)
1.输入层：包含Context(w)中2c个词的词向量v(Context(w)1)..v(Context(w)2c) $\in R^m$ ,这里m表示词向量的长度
2.投影层：将2c个词向量做求和累加，得到$X_w$
3.输出层：输出层对应一颗二叉树，它是出现过的词当作叶子节点，以各个词在预料中出现的次数当作权制构造出来的Huffman树。
####3.2.1梯度计算
Hierarchical softmax是用于提升性能的一项重要技术。
记号如下：
![](https://raw.githubusercontent.com/YulinGUO/BigDataTips/master/ml/imgs/2.png)
下面举一个例子，考虑词w = "足球"的情形：
![](https://raw.githubusercontent.com/YulinGUO/BigDataTips/master/ml/imgs/word2vecHuff.png)
以上图中足球为例，从跟节点出发到达“足球”这个叶子节点，中间共经历了4次分支，而每一次分支都可以视为一次二分类，这里约定分到左边的就是负类，右边的就是正类。  

![](https://raw.githubusercontent.com/YulinGUO/BigDataTips/master/ml/imgs/3.png)
对于词典中的任意词w，huffman树中必存在一条从根节点到词w对应节点的路径$p^w$,路径上存在$l^w-1$个分支，将每个分支看作一次二分类，每一次分类就产生一个概率，将这些概率乘起来，就是所需的p(w|Context(w)).  
![](https://raw.githubusercontent.com/YulinGUO/BigDataTips/master/ml/imgs/4.png)
将上面公式代入对数似然函数，变得到了CBOW的目标优化函数，采用随机梯度上升算法，将这个函数最大化。 

![](https://raw.githubusercontent.com/YulinGUO/BigDataTips/master/ml/imgs/5.png)

###参考  
<http://www.cnblogs.com/Jezze/archive/2011/12/23/2299884.html>
<http://www.cnblogs.com/peghoty/p/3857839.html>

