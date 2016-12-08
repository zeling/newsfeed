package me.zeling.newsfeed.rest

import akka.actor.{Actor, ActorLogging, ActorRefFactory, ActorSystem, Props}
import akka.cluster.sharding.ClusterSharding
import akka.util.Timeout
import me.zeling.newsfeed.domain._
import spray.http.HttpHeaders.RawHeader
import spray.http._
import java.util.UUID

import scala.util.{Failure, Success}

/**
  * Created by zeling on 2016/12/6.
  */
class RestActor extends Actor with RestService with ActorLogging {

  implicit val system = context.system
  override implicit def actorRefFactory: ActorRefFactory = context

  override def receive = runRoute(newsFeedRoute)

  override def shard: ClusterSharding = ClusterSharding(system)

  import Entity._
  import EntityJsonSupport._
  import akka.pattern.ask
  import scala.concurrent.duration._
  import context.dispatcher
  implicit val timeout = Timeout(10.seconds)
  val newsFeedRoute = cors {
    path("user") {
      post {
        entity(as[UserEntity]) { user =>
          complete {
            log.debug(s"")
            userRegion ! User.CreateUser(user.name, user.password)
            AuthResp(true, User.UserProfile(user.name, "http://0.0.0.0:8080/user/" + user.name + "/portrait/" + 0, "http://0.0.0.0:8080/user/" + user.name + "/header/" + 0))
          }
        }
      }
    } ~ path("user" / Segment) { username =>
      get {
        onComplete(userRegion ? User.GetProfile(username)) {
          case Success(x@User.UserProfile(_,_,_)) => complete(x)
          case _ => complete(StatusCodes.NotFound)
        }
      }
    } ~ path("login"/ Segment/ Segment) { (username, password) =>
      get {
        onComplete(userRegion ? User.LoginRequest(username, password)) {
          case Success(up: User.LoginResponse) =>
            complete(AuthResp(up.flag, up.userProfile))
          case _ =>
            complete(AuthResp(false, User.UserProfile(username, "user/" + username + "/portrait/" + 0, "user/" + username + "/header/" + 0)))
        }
      }
    } ~ path("user" / Segment / "portrait" ~ (Slash ~ Segment).?) { (username, _) =>
      get{
        onComplete(userRegion ? User.GetPortrait(username)) {
          case Success(User.PortraitResponse(data)) =>
            if (data(0) == 0xff && data(1) == 0xd8) {
              complete(HttpEntity(ContentTypes.NoContentType.withMediaType(MediaTypes.`image/jpeg`), data))
            } else {
              complete(HttpEntity(ContentTypes.NoContentType.withMediaType(MediaTypes.`image/png`), data))
            }
          case _ =>
            complete(StatusCodes.NotFound)
        }
      } ~
        post {
          entity(as[MultipartFormData]) { formData =>
            complete {
              formData.get("picture") match {
                case Some(bdpt) =>
                  userRegion ! User.ModifyPortrait(username, bdpt.entity.data.toByteArray)
                  StatusCodes.OK
                case _ =>
                  log.error(s"something went wrong")
                  StatusCodes.InternalServerError
              }
            }
          }
        }
    } ~ path("user" / Segment / "header" ~ (Slash ~ Segment).?) { (username, _) =>
      get{
        onComplete(userRegion ? User.GetHeader(username)) {
          case Success(User.HeaderResponse(data)) =>
            if (data(0) == 0xff && data(1) == 0xd8) {
              complete(HttpEntity(ContentTypes.NoContentType.withMediaType(MediaTypes.`image/jpeg`), data))
            } else {
              complete(HttpEntity(ContentTypes.NoContentType.withMediaType(MediaTypes.`image/png`), data))
            }
          case _ =>
            complete(StatusCodes.NotFound)
        }
      } ~
        post {
          entity(as[MultipartFormData]) { formData =>
            complete {
              formData.get("picture") match {
                case Some(bdpt) =>
                  userRegion ! User.ModifyHeader(username, bdpt.entity.data.toByteArray)
                  StatusCodes.OK
                case _ =>
                  log.error(s"something went wrong")
                  StatusCodes.InternalServerError
              }
            }
          }
        }
    } ~ path("news") {
      post {
        entity(as[Post.PostContent]) { post =>
          complete {
            postRegion ! Post.CreatePost(UUID.randomUUID.toString, post)
            StatusCodes.OK
          }
        }
      }
    } ~ path("user" / Segment / "people") { username =>
      post {
        entity(as[String]) { other =>
          complete {
            userRegion ! User.FollowUser(username, other)
            StatusCodes.OK
          }
        }
      } ~
        get {
          onComplete(userRegion ? User.GetFollowingUsers(username)) {
            case Success(x: User.FollowingList) => complete(x)
            case _ => complete(StatusCodes.NotFound)
          }
        }
    } ~ path("user" / Segment / "topic") { username =>
      post {
        entity(as[String]) { topic =>
          complete {
            userRegion ! User.FollowTopic(username, topic)
            StatusCodes.OK
          }
        }
      } ~
        get {
          onComplete(userRegion ? User.GetFollowingTopics(username)) {
            case Success(x: User.TopicList) => complete(x)
            case _ => complete(StatusCodes.NotFound)
          }
        }
    } ~ path("user" / Segment / "timeline") { username =>
      get {
        onComplete(ask(userRegion, User.GetTimeline(username)).mapTo[User.TimeLine]) {
          case Success(x) =>
            complete(x)
          case ex =>
            log.error(s"$ex")
            complete(StatusCodes.NotFound)
        }
      }
    } ~ path("news" / JavaUUID / "comment") { postId =>
      post {
        entity(as[Post.PostComment]) { comment =>
          complete {
            postRegion ! Post.AppendComment(postId.toString, comment)
            StatusCodes.OK
          }
        }
      }
    } ~ path("user" / Segment / "posts") { username =>
      get {
        onComplete(ask(userRegion, User.ListPosts(username)).mapTo[User.PostList]) {
          case Success(x) => complete(x)
          case _ => complete(StatusCodes.NotFound)
        }
      }

    } ~ path("topic" / Segment ) { topicname =>
      get {
        onComplete(ask(topicRegion, Topic.ListPosts(topicname)).mapTo[Topic.PostList]) {
          case Success(x) => complete(x)
          case _ => complete(StatusCodes.NotFound)
        }
      }

    }
  }


}
