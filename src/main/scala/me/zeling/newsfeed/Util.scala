package me.zeling.newsfeed

/**
  * Created by zeling on 2016/12/6.
  */
object Util {
  import scala.collection.immutable.Vector
  def merge[A](v1: Vector[A], v2: Vector[A])(implicit ord: Ordering[A]): Vector[A] = {
    var i = 0
    var j = 0
    val b = Vector.newBuilder[A]
    b.sizeHint(v1.size + v2.size)
    while (i < v1.size && j < v2.size) {
      if (ord.lteq(v1(i), v2(j))) {
        b += v1(i)
        i = i + 1
      } else {
        b += v2(j)
        j = j + 1
      }
    }
    while (i < v1.size) {
      b += v1(i)
      i = i + 1
    }
    while (j < v2.size) {
      b += v2(j)
      j = j + 1
    }
    b.result
  }
}
