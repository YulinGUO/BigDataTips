import org.apache.spark.mllib.recommendation.{Rating, MatrixFactorizationModel}

import scala.collection.mutable

import org.apache.spark.mllib.linalg.DenseMatrix
import org.apache.spark.mllib.rdd.MLPairRDDFunctions.fromPairRDD
import org.apache.spark.rdd.RDD
import org.apache.spark.storage.StorageLevel

/**
  * Support more tuning feature for LAS model: org.apache.spark.mllib.recommendation.MatrixFactorizationModel
  */
object ExtMatrixFactorizationModelHelper {

  /**
    * Recommends topK products for all users.
    *
    * @param mdl: trained model of LAS.
    * @param num: how many products to return for every user.
    * @param blockSize: the size of operation block when performing blockify.
    * @param lockCacheLevel: the cache level of operation block.
    * @return [(Int, Array[Rating])] objects, where every tuple contains a userID and an array of
    * rating objects which contains the same userId, recommended productID and a "score" in the
    * rating field. Semantics of score is same as recommendProducts API
    */
  def recommendProductsForUsers(mdl: MatrixFactorizationModel, num: Int, blockSize: Int = 4096, lockCacheLevel: StorageLevel = StorageLevel.NONE): RDD[(Int, Array[Rating])] = {
    recommendForAll(mdl.rank, mdl.userFeatures, mdl.productFeatures, num, blockSize, lockCacheLevel).map {
      case (user, top) =>
        val ratings = top.map { case (product, rating) => Rating(user, product, rating) }
        (user, ratings)
    }
  }

  /**
    * Recommends topK users for all products.
    *
    * @param mdl: trained model of LAS.
    * @param num: how many products to return for every user.
    * @param blockSize: the size of operation block when performing blockify.
    * @param lockCacheLevel: the cache level of operation block.
    * @return [(Int, Array[Rating])] objects, where every tuple contains a productID and an array
    * of rating objects which contains the recommended userId, same productID and a "score" in the
    * rating field. Semantics of score is same as recommendUsers API
    */
  def recommendUsersForProducts(mdl: MatrixFactorizationModel, num: Int, blockSize: Int = 4096, lockCacheLevel: StorageLevel = StorageLevel.NONE): RDD[(Int, Array[Rating])] = {
    recommendForAll(mdl.rank, mdl.productFeatures, mdl.userFeatures, num, blockSize, lockCacheLevel).map {
      case (product, top) =>
        val ratings = top.map { case (user, rating) => Rating(user, product, rating) }
        (product, ratings)
    }
  }

  private def recommendForAll(
                               rank: Int,
                               srcFeatures: RDD[(Int, Array[Double])],
                               dstFeatures: RDD[(Int, Array[Double])],
                               num: Int,
                               blockSize: Int = 4096,
                               blockCacheLevel: StorageLevel = StorageLevel.NONE): RDD[(Int, Array[(Int, Double)])] = {

    val srcBlocks = blockify(rank, srcFeatures, blockSize).setName("srcBlocks")
    val dstBlocks = blockify(rank, dstFeatures, blockSize).setName("dstBlocks")
    if (StorageLevel.NONE != blockCacheLevel) { //force cache
      srcBlocks.persist(blockCacheLevel)
      dstBlocks.persist(blockCacheLevel)
      srcBlocks.count()
      dstBlocks.count()
    }

    def foreachActive(m: DenseMatrix, f: (Int, Int, Double) => Unit): Unit = {
      if (!m.isTransposed) {
        // outer loop over columns
        var j = 0
        while (j < m.numCols) {
          var i = 0
          val indStart = j * m.numRows
          while (i < m.numRows) {
            f(i, j, m.values(indStart + i))
            i += 1
          }
          j += 1
        }
      } else {
        // outer loop over rows
        var i = 0
        while (i < m.numRows) {
          var j = 0
          val indStart = i * m.numCols
          while (j < m.numCols) {
            f(i, j, m.values(indStart + j))
            j += 1
          }
          i += 1
        }
      }
    }

    val ratings = srcBlocks.cartesian(dstBlocks).flatMap {
      case ((srcIds, srcFactors), (dstIds, dstFactors)) =>
        val m = srcIds.length
        val n = dstIds.length
        val ratings = srcFactors.transpose.multiply(dstFactors)
        val output = new Array[(Int, (Int, Double))](m * n)
        var k = 0
        foreachActive(ratings, { (i, j, r) =>
          output(k) = (srcIds(i), (dstIds(j), r))
          k += 1
        })
        output.toSeq
    }
    ratings.topByKey(num)(Ordering.by(_._2))
  }
  private def blockify(
                        rank: Int,
                        features: RDD[(Int, Array[Double])],
                        blockSize: Int = 4096): RDD[(Array[Int], DenseMatrix)] = {
    val blockStorage = rank * blockSize
    features.mapPartitions { iter =>
      iter.grouped(blockSize).map { grouped =>
        val ids = mutable.ArrayBuilder.make[Int]
        ids.sizeHint(blockSize)
        val factors = mutable.ArrayBuilder.make[Double]
        factors.sizeHint(blockStorage)
        var i = 0
        grouped.foreach {
          case (id, factor) =>
            ids += id
            factors ++= factor
            i += 1
        }
        (ids.result(), new DenseMatrix(rank, i, factors.result()))
      }
    }
  }

}