package me.zeling.newsfeed.rest
import me.zeling.newsfeed.domain.Post.PostComment
import spray.httpx.SprayJsonSupport
import spray.json.DefaultJsonProtocol
import spray.json.DefaultJsonProtocol._
import me.zeling.newsfeed.domain._

/**
  * Created by zeling on 2016/12/6.
  */
object Entity {
  case class UserEntity(name: String, password: String)
  case class AuthResp(flag: Boolean, user: User.UserProfile)
}

object EntityJsonSupport extends DefaultJsonProtocol with SprayJsonSupport {
  import Entity._
  implicit val userTransform = jsonFormat2(UserEntity)
  implicit val userProfileTransform = jsonFormat3(User.UserProfile)
  implicit val AuthRespTransform = jsonFormat2(AuthResp)
  implicit val contentTransform = jsonFormat4(Post.PostContent)
  implicit val commentTransform = jsonFormat2(Post.PostComment)
  implicit val postTransform = jsonFormat3(Post.PostView)
  implicit val timelineT = jsonFormat1(User.TimeLine)
  implicit val followingListT = jsonFormat1(User.FollowingList)
  implicit val topicListT = jsonFormat1(User.TopicList)
  implicit val postListT = jsonFormat1(User.PostList)
  implicit val tpostListT = jsonFormat1(Topic.PostList)
}
