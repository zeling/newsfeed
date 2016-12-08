package me.zeling.newsfeed.domain

import java.util.Calendar

import akka.actor.{ActorLogging, ReceiveTimeout}
import akka.cluster.sharding.{ClusterSharding, ShardRegion}
import akka.persistence.PersistentActor

/**
  * Created by zeling on 2016/12/3.
  */
object Post {

  import scala.collection.immutable.Vector

  Calendar.getInstance.getTime

  case class PostContent(name: String, content: String, topic: String, time: Long)
  case class PostComment(name: String, content: String)

  sealed trait Command {
    def id: String
  }

  case class CreatePost(id: String, content: PostContent) extends Command
  case class AppendComment(id: String, comment: PostComment) extends Command
  case class GetPostView(id: String) extends Command

  sealed trait Event
  case class PostCreated(postId: String, postContent: PostContent) extends Event
  case class CommentAppended(comment: PostComment) extends Event

  val shardName = "Post"

  val extractPostId: ShardRegion.ExtractEntityId = {
    case cmd: Command => (cmd.id, cmd)
  }

  val shardId: ShardRegion.ExtractShardId = {
    case cmd: Command => (math.abs(cmd.id.hashCode) % 100).toString
  }


  case class PostView(id: String, content: PostContent, comment: Vector[PostComment]) {

    def updated(event: Event): PostView = event match {
      case PostCreated(postId, postContent) => copy(id = postId, content = postContent)
      case CommentAppended(c) => copy(comment = comment :+ c)
    }

  }

  object State {
    def initial = PostView("", PostContent("", "", "", 0), Vector.empty)
  }


//  implicit object descByTimestamp extends Ordering[PostView] {
//    override def compare(x: PostView, y: PostView): Int = {
//      y.content.timestamp compare x.content.timestamp
//    }
//  }
//
  implicit val descByTimestamp: Ordering[PostView] = Ordering[Long].reverse.on(_.content.time)


}


class Post extends PersistentActor
  with ActorLogging
  with HasUserRegion
  with HasTopicRegion
  with ShardPassivate {
  import Post._

  val shard = ClusterSharding(context.system)

  var postView = State.initial

  override def receiveRecover: Receive = {
    case evt: Event => postView = postView.updated(evt)
  }

  import Topic._
  import User._
  override def receiveCommand: Receive = {
    case CreatePost(postId, content) => persist(PostCreated(postId, content)) {
      evt => postView = postView.updated(evt)
        log.info(s"user {} created a new post {} with topic {} at {}",
          evt.postContent.name,
          evt.postContent.content,
          evt.postContent.topic,
          evt.postContent.time)
        if (evt.postContent.topic != "") topicRegion ! AppendPost(evt.postContent.topic, evt.postId)
        userRegion ! PublishPost(evt.postContent.name, evt.postId)
    }
    case AppendComment(_, comment) => persist(CommentAppended(comment)) {
      evt => postView = postView.updated(evt)
        log.info(s"user {} made an comment {}",
          evt.comment.name,
          evt.comment.content)
    }
    case GetPostView(_) => sender ! postView
  }

  override def persistenceId: String = "post" + self.path.parent.name + "-" + self.path.name

}
