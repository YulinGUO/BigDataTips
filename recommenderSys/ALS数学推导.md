## ALS数学推导

### 显性反馈代价函数
![代价函数](https://blog-10039692.file.myqcloud.com/1500350617965_1900_1500350618009.png)  
其中，$r_{ij}$表示用户$u_i$对物品$v_j$的打分，$u_i$为矩阵U的第i行(1* k) ， $v_j^T$为矩阵V的第j列 (k*1)，$\lambda$为正则项系数。

###隐式反馈代价函数
很多情况下，用户并没有明确反馈对物品的偏好，需要通过用户的相关行为来推测其对物品的偏好，例如，在视频推荐问题中，可能由于用户就懒得对其所看的视频进行反馈，通常是收集一些用户的行为数据，得到其对视频的偏好，例如观看时长等。通过这种方式得到的偏好值称之为隐式反馈值，即矩阵$R$为隐式反馈矩阵，引入变量$P_{ij}$表示用户$u_i$对物品$v_j$的置信度，如果隐式反馈值大于0，置信度为1，否则置信度为0。  
![](https://blog-10039692.file.myqcloud.com/1500350921671_50_1500350921689.png)


但是隐式反馈值为0并不能说明用户就完全不喜欢，用户对一个物品没有得到一个正的偏好可能源于多方面的原因，例如，用户可能不知道该物品的存在，另外，用户购买一个物品也并不一定是用户喜欢它，所以需要一个信任等级来显示用户偏爱某个物品，一般情况下，越大，越能暗示用户喜欢某个物品，因此，引入变量$c_{ij}$，来衡量的信任度，其中$\alpha$为置信系数。
![](https://blog-10039692.file.myqcloud.com/1500350982953_9485_1500350982985.png)

那么，代价函数则变成如下形式：
![](https://blog-10039692.file.myqcloud.com/1500350996008_5172_1500350996037.png)

###算法实现
无论是显示反馈代价函数还是隐式反馈代价函数，它们都不是凸的，变量互相耦合在一起，常规的梯度下降法可不好使了。但是如果先固定U求解V，再固定V求解U ，如此迭代下去，问题就可以得到解决了。
![](https://blog-10039692.file.myqcloud.com/1500351068209_3929_1500351068233.png)  
那么固定一个变量求解另一个变量如何实现呢?虽然可以用梯度下降，但是需要迭代，计算起来相对较慢.试想想，固定求解，或者固定求解 ，其实是一个最小二乘问题，由于一般隐含特征个数取值不会特别大，可以将最小二乘转化为正规方程一次性求解，而不用像梯度下降一样需要迭代。如此交替地解最小二乘问题，所以得名交替最小二乘法ALS，下面是基于显示反馈和隐式反馈的最小二乘正规方程。

#### 数学推导
损失函数如下：

$$L=\sum_{(u,i)\in K}  (r_{ui}-x_u^T*y_i)^2 + \lambda(||x_u||^2 +||y_i||^2)
$$
最小二乘法求解最优取之，需要对其中一个元素进行求导。这里先固定$y_i$,对$x_u$进行求导。
这里涉及一个向量求导：  
![](http://img.blog.csdn.net/20170606174618532?watermark/2/text/aHR0cDovL2Jsb2cuY3Nkbi5uZXQvYnVwdGZhbnJx/font/5a6L5L2T/fontsize/400/fill/I0JBQkFCMA==/dissolve/70/gravity/SouthEast)  
![](https://www.zhihu.com/equation?tex=%5Cfrac%7B%5Cpartial+y%5ET+x%7D%7B%5Cpartial+x%7D%3Dy)
![](https://www.zhihu.com/equation?tex=%5Cfrac%7B%5Cpartial%28x%5ETA+x%29%7D%7B%5Cpartial+x%7D%3D%28A%2BA%5ET%29x+)  

对于$\sum_{(u,i)\in K}  (r_{ui}-x_u^T*y_i)^2$这一部分对$x_u$进行求导：  
$\sum_{(u,i)\in K}  (r_{ui}-x_u^T*y_i)^2$  
$\to$$\sum_{(u,i)\in K}  (r_{ui}^2+(x_u^T*y_i)^2-2r_{ui}(x_u^T*y_i))$ 

对$x_u$进行求导：
$r_{ui}^2$ $\to$ 0
$(x_u^T*y_i)^2$ $\to$ $2 (x_u^T*y_i) \frac{\delta(x_u^T*y_i))}{\delta(x_u)} \to 2 (x_u^T*y_i) (y_i^T)$
这里面，$\frac{\delta(x_u^T*y_i))}{\delta(x_u)}$$\to\frac{\delta(y_i^T*x_u)^T)}{\delta(x_u)} \to y_i^T$
$-2r_{ui}(x_u^T*y_i)) \to -2r_{ui}y_i^T$ 

对于$\lambda(||x_u||^2 +||y_i||^2)$进行求导
$\lambda(||x_u||^2 +||y_i||^2) \to \lambda (x_u^Tx_u)^2 \to 2\lambda (x_u^Tx_u)\frac{\delta(x_u^Tx_u)}{\delta(x_u)} \to $$2\lambda (x_u^Tx_u) x_u^T \to 2\lambda x_u^T$

另求导后结果为0，这样后：
$\sum_{(u,i)\in K}(2 x_u^T*y_i y_i^T-2r_{ui}y_i^T) + 2\lambda x_u^T = 0$
化简如下，其中Ku表示用户u接触的物品集： 
$$\sum_{i\in K_u} (x_u^T*y_i*y_i^T-r_{ui}*y_i^T) + \lambda x_u^T=0$$
两边转置
$$\sum_{i\in K_u} (y_i*y_i^T*x_u-r_{ui}*y_i) + \lambda x_u=0
$$
$\to$
$$\sum_{i\in K_u} (y_i*y_i^T+\lambda) x_u = \sum_{i\in K_u}r_{ui}*y_i
$$
其中：
$$\sum_{i\in K_u} y_i*y_i^T=Y^TY,\sum_{i\in K_u}r_{ui}*y_i=Y^T*r_u
$$
于是：
$$x_u=(Y^{\top}Y + \lambda I)^{-1}Y^\top r_u
$$
###spark 分布式实现
参考链接<https://cloud.tencent.com/developer/article/1005504>
###错误debug
如果出现Stackoverflow错误，需要设置checkpoint
spark.sparkContext.setCheckpointDir("hdfs://Path")

Checkpointing helps with recovery (when nodes fail) and StackOverflow exceptions caused by long lineage. It also helps with eliminating temporary shuffle files on disk, which can be important when there are many ALS iterations.
###优化
spark2.2中最新的recommend for user做了很大的提升，在之前的版本中，为了求TopN,是使用userfactor crossjoin itemfactor,计算效率很差。最新的版本中是先分块求得TopN,然后利用TopByKeyAggregator计算全局TopN。计算效率大大提升。
##参考
<http://blog.csdn.net/buptfanrq/article/details/72885760>
<https://en.wikipedia.org/wiki/Matrix_calculus#Scalar-by-vector_identities>
<https://www.zhihu.com/question/31845977>
<https://www.zhihu.com/question/39523290>
<https://cloud.tencent.com/developer/article/1005504>



