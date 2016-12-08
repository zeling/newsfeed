package me.zeling.newsfeed.domain

/**
  * Created by zeling on 2016/12/6.
  */
trait HasTopicRegion extends HasShard {
  lazy val topicRegion = shard.shardRegion(Topic.shardName)
}

trait HasPostRegion extends HasShard {
  lazy val postRegion = shard.shardRegion(Post.shardName)
}

trait HasUserRegion extends HasShard {
  lazy val userRegion = shard.shardRegion(User.shardName)
}
