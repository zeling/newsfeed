package me.zeling.newsfeed.domain

import akka.actor.ActorLogging
import akka.cluster.sharding.{ClusterSharding, ShardRegion}
import akka.persistence.PersistentActor
import akka.util.Timeout

import scala.concurrent.Future
import scala.util.{Failure, Success}
import scala.concurrent.duration._

/**
  * Created by zeling on 2016/12/4.
  */
object Topic {
  import scala.collection.immutable.Vector
  sealed trait Command {
    def topicName: String
  }
  case class AppendPost(topicName: String, postId: String) extends Command
  case class ListPosts(topicName: String) extends Command

  sealed trait Event
  case class PostAppended(postId: String) extends Event

  sealed trait Response
  case class PostList(posts: Vector[Post.PostView]) extends Response

  case class State(posts: Vector[String]) {
    def updated(event: Event) = event match {
      case PostAppended(postId) => copy(posts = posts :+ postId)
    }
  }

  object State {
    def initial = State(Vector.empty)
  }

  val extractTopicName: ShardRegion.ExtractEntityId = {
    case cmd: Command => (cmd.topicName, cmd)
  }

  val shardId: ShardRegion.ExtractShardId = {
    case cmd: Command => (math.abs(cmd.topicName.hashCode) % 100) toString
  }

  val shardName = "Topic"

}

class Topic extends PersistentActor with ActorLogging with HasPostRegion with ShardPassivate {
  import Topic._

  val shard = ClusterSharding(context.system)

  var topicState = State.initial

  override def receiveRecover: Receive = {
    case event: Event => topicState = topicState.updated(event)
  }

  override def receiveCommand: Receive = {
    case AppendPost(_, postId) => persist(PostAppended(postId)) { evt =>
      topicState = topicState.updated(evt)
    }
    case ListPosts(topicName) =>
      import context.dispatcher
      import akka.pattern.ask
      import akka.pattern.pipe
      implicit val timeout: Timeout = 15.seconds
      val eventualPostViews = Future.sequence(topicState.posts.map(Post.GetPostView).map(ask(postRegion, _).mapTo[Post.PostView])).map(_.sorted)
      eventualPostViews.map(PostList) pipeTo sender
  }

  override def persistenceId: String = "topic@" + self.path.parent.name + "-" + self.path.name
}
