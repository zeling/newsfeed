package me.zeling.newsfeed.domain

import akka.cluster.sharding.ClusterSharding

/**
  * Created by zeling on 2016/12/6.
  */
trait HasShard {
  def shard: ClusterSharding
}
