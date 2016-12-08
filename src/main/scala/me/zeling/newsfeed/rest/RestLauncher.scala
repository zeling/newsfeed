package me.zeling.newsfeed.rest

import akka.actor.{ActorSystem, Props}
import akka.io.IO
import spray.can.Http

/**
  * Created by zeling on 2016/12/6.
  */
object RestLauncher extends App {
    val actorSystem = ActorSystem("newsfeed")
    implicit val system = actorSystem
    val restActor = actorSystem.actorOf(Props(classOf[RestActor]))
    IO(Http) ! Http.Bind(restActor, "localhost", 8080)
}
