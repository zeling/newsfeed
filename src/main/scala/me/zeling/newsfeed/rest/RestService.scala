package me.zeling.newsfeed.rest

import akka.actor.ActorContext
import akka.util.Timeout
import me.zeling.newsfeed.domain.User.LoginResponse
import me.zeling.newsfeed.domain.{HasPostRegion, HasTopicRegion, HasUserRegion}
import spray.routing.HttpService
import me.zeling.newsfeed.domain._
import spray.http.{StatusCode, StatusCodes}

import scala.concurrent.ExecutionContext

/**
  * Created by zeling on 2016/12/6.
  */
trait RestService extends HttpService with HasPostRegion with HasUserRegion with HasTopicRegion with CORSSupport { }
