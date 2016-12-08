package me.zeling.newsfeed.domain

import java.io.File
import java.nio.file.{Files, Path, Paths}

import akka.actor.{ActorLogging, ActorRef}
import akka.cluster.sharding.{ClusterSharding, ShardRegion}
import akka.persistence.PersistentActor
import akka.util.Timeout

import scala.concurrent.Future

/**
  * Created by zeling on 2016/12/4.
  */
object User {
  import scala.collection.immutable.Vector
  sealed trait Command {
    def name: String
  }
  sealed trait Data {
    def data: Array[Byte]
  }
  case class CreateUser(name: String, password: String) extends Command
  case class LoginRequest(name: String, password: String) extends Command
  case class FollowUser(name: String, userToFollow: String) extends Command
  case class FollowTopic(name: String, topic: String) extends Command
  case class GetTimeline(name: String) extends Command
  case class PublishPost(name: String, postId: String) extends Command
  case class ListPosts(name: String) extends Command
  case class ModifyPortrait(name: String, data: Array[Byte]) extends Command with Data
  case class GetPortrait(name: String) extends Command
  case class ModifyHeader(name: String, data: Array[Byte]) extends Command with Data
  case class GetHeader(name: String) extends Command
  case class GetProfile(name: String) extends Command
  case class GetFollowingTopics(name: String) extends Command
  case class GetFollowingUsers(name: String) extends Command

  sealed trait Event
  case class UserCreated(username: String, password: String) extends Event
  case class UserFollowed(userToFollow: String) extends Event
  case class TopicFollowed(topicToFollow: String) extends Event
  case class PostPublished(postId: String) extends Event
  case class PortraitModified(data: Array[Byte]) extends Event
  case class HeaderModified(data: Array[Byte]) extends Event

  sealed trait Response
  case class PostList(posts: Vector[Post.PostView]) extends Response
  case class LoginResponse(flag: Boolean, userProfile: UserProfile) extends Response
  case class UserProfile(name: String, portraitUrl: String, headUrl: String) extends Response
  case class TopicList(topics: Vector[String]) extends Response
  case class FollowingList(users: Vector[String]) extends Response
  case class TimeLine(posts: Vector[Post.PostView]) extends Response
  case class HeaderResponse(data: Array[Byte]) extends Data
  case class PortraitResponse(data: Array[Byte]) extends Data

  case class State(username: String, password: String,
                   portrait: Array[Byte], portraitVer: Int,
                   header: Array[Byte], headerVer: Int,
                   followingUsers: Vector[String],
                   followingTopics: Vector[String],
                   publishedPosts: Vector[String]) {
    def updated(event: Event): State = event match {
      case UserCreated(username, password) =>
        copy(username = username, password = password)
      case UserFollowed(userToFollow) =>
        copy(followingUsers = followingUsers :+ userToFollow)
      case TopicFollowed(topicToFollow) =>
        copy(followingTopics = followingTopics :+ topicToFollow)
      case PostPublished(postId) =>
        copy(publishedPosts = publishedPosts :+ postId)
      case PortraitModified(data) =>
        copy(portrait = data, portraitVer = portraitVer + 1)
      case HeaderModified(data) =>
        copy(header = data, headerVer = headerVer + 1)
    }
  }

  object State {
    def initial = {
      State("", "", Files.readAllBytes(Paths.get("portrait.png")), 0, Files.readAllBytes(Paths.get("header.jpeg")), 0, Vector.empty, Vector.empty, Vector.empty)
    }
  }

  val shardName = "User"

  val extractUsername: ShardRegion.ExtractEntityId = {
    case cmd: Command => (cmd.name, cmd)
  }

  val shardId: ShardRegion.ExtractShardId = {
    case cmd: Command => (math.abs(cmd.name.hashCode) % 100) toString
  }

}

class User extends PersistentActor with ActorLogging with HasPostRegion with HasTopicRegion with HasUserRegion with ShardPassivate {
  import User._

  var userState = State.initial
  val shard = ClusterSharding(context.system)

  override def receiveRecover: Receive = {
    case event: Event => {
      userState = userState.updated(event)
      log.info(s"Received a recover action")
    }
//    case _ => throw new IllegalStateException("The Persisted Events are corrupted")
  }

  override def receiveCommand: Receive = {
    case cmd: Command => cmd match {
      case CreateUser(username, password) =>
        persist(UserCreated(username, password)) { evt =>
          userState = userState.updated(evt)
          log.info(s"A new user registered as {} with password {}", evt.username, evt.password)
        }
      case FollowUser(_, userToFollow) =>
        persist(UserFollowed(userToFollow)) { evt =>
          userState = userState.updated(evt)
          log.info(s"User {} has just followed user {}", userState.username, evt.userToFollow)
        }
      case FollowTopic(username, topicToFollow) =>
        persist(TopicFollowed(topicToFollow)) { evt =>
          userState = userState.updated(evt)
          log.info(s"User {} has just followed topic {}", userState.username, evt.topicToFollow)
        }

      case PublishPost(_, postId) => persist(PostPublished(postId)) { evt =>
        userState = userState.updated(evt)
        log.info(s"User {} has just published post {}", userState.username, evt.postId)
      }

      case ListPosts(username) =>
        import akka.pattern.pipe
        import context.dispatcher
        val f = retrievePostList(userState.publishedPosts).map(_.sorted).map(PostList) pipeTo sender
        f onFailure {
          case ex: Throwable => log.error(s"Failed to retrieve the published posts of User {}: {}", username, ex.getMessage)
        }

      case GetTimeline(username) =>
        import akka.pattern.ask
        import akka.pattern.pipe
        import concurrent.duration._
        implicit val timeout: Timeout = 15.seconds
        import context.dispatcher
        val eventualPostList: Future[Vector[Post.PostView]] = for {
          selfPublished <- retrievePostList(userState.publishedPosts)
          following <- Future.sequence(userState.followingUsers.map(ListPosts).map(ask(userRegion, _).mapTo[User.PostList].map(_.posts))).map(_.flatten)
          topicPosts <- Future.sequence(userState.followingTopics.map(Topic.ListPosts).map(ask(topicRegion, _).mapTo[Topic.PostList].map(_.posts))).map(_.flatten)
        } yield (selfPublished ++ following ++ topicPosts).sorted.distinct
        eventualPostList.map(TimeLine) pipeTo sender


      case GetProfile(_) =>
        sender ! UserProfile(userState.username, portraitUrl, headerUrl)


      case GetFollowingTopics(_) =>
        sender ! TopicList(userState.followingTopics)

      case GetFollowingUsers(_) =>
        sender ! FollowingList(userState.followingUsers)

      case ModifyHeader(_, data) => persist(HeaderModified(data)) { evt =>
        userState = userState.updated(evt)
        log.info(s"User {} has modified his header")
      }

      case ModifyPortrait(_, data) => persist(PortraitModified(data)) { evt =>
        userState = userState.updated(evt)
        log.info(s"User {} has modified his portrait")
      }

      case LoginRequest(_, password) =>
        sender ! LoginResponse(password == userState.password, UserProfile(userState.username, portraitUrl, headerUrl))

      case GetPortrait(_) =>
        sender ! PortraitResponse(userState.portrait)

      case GetHeader(_) =>
        sender ! HeaderResponse(userState.header)
    }
  }

  def retrievePostList(postIds: Vector[String], whomToAsk: ActorRef = postRegion, transform: String => Any = Post.GetPostView): Future[Vector[Post.PostView]] = {
    import akka.pattern.ask
    import concurrent.duration._
    import context.dispatcher
    implicit val timeout: Timeout = 15.seconds
    Future.sequence(postIds.map(transform).map(ask(whomToAsk, _).mapTo[Post.PostView]))//.map(_.sorted)
  }

  def portraitUrl = "http://0.0.0.0:8080/user/" + userState.username + "/portrait/" + userState.portraitVer
  def headerUrl = "http://0.0.0.0:8080/user/" + userState.username + "/header/" + userState.headerVer

  override def persistenceId: String = "user@" + self.path.parent.name + "-" + self.path.name
}
