package me.zeling.newsfeed

import akka.actor.{ActorIdentity, ActorPath, ActorSystem, Identify, Props}
import akka.cluster.sharding.{ClusterSharding, ClusterShardingSettings}
import akka.io.IO
import akka.pattern.ask
import akka.persistence.journal.leveldb.{SharedLeveldbJournal, SharedLeveldbStore}
import akka.util.Timeout
import com.typesafe.config.ConfigFactory
import me.zeling.newsfeed.domain._
import me.zeling.newsfeed.rest.RestActor
import spray.can.Http

import scala.concurrent.duration._

object Main extends App {

  if (args.isEmpty)
    startup(Seq("2551", "2552", "0"))
  else
    startup(args)

  def startup(ports: Seq[String]): Unit = {
    ports foreach { port =>
      // Override the configuration of the port
      val config = ConfigFactory.parseString("akka.remote.netty.tcp.port=" + port).
        withFallback(ConfigFactory.load())

      // Create an Akka system
      implicit val system = ActorSystem("newsfeed", config)

      startupSharedJournal(system, startStore = (port == "2551"), path =
        ActorPath.fromString("akka.tcp://newsfeed@127.0.0.1:2551/user/store"))

      ClusterSharding(system).start(
        typeName = Post.shardName,
        entityProps = Props(classOf[Post]),
        settings = ClusterShardingSettings(system),
        extractEntityId = Post.extractPostId,
        extractShardId = Post.shardId)

      ClusterSharding(system).start(
        typeName = User.shardName,
        entityProps = Props(classOf[User]),
        settings = ClusterShardingSettings(system),
        extractEntityId = User.extractUsername,
        extractShardId = User.shardId)

      ClusterSharding(system).start(
        typeName = Topic.shardName,
        entityProps = Props(classOf[Topic]),
        settings = ClusterShardingSettings(system),
        extractEntityId = Topic.extractTopicName,
        extractShardId = Topic.shardId)

      if (port == "2551") {
        val restActor = system.actorOf(Props(classOf[RestActor]))
        IO(Http) ! Http.Bind(restActor, "localhost", 8080)
      }


    }

    def startupSharedJournal(system: ActorSystem, startStore: Boolean, path: ActorPath): Unit = {

      if (startStore)
        system.actorOf(Props[SharedLeveldbStore], "store")

      import system.dispatcher
      implicit val timeout = Timeout(15.seconds)
      val f = (system.actorSelection(path) ? Identify(None))
      f.onSuccess {
        case ActorIdentity(_, Some(ref)) => SharedLeveldbJournal.setStore(ref, system)
        case _ =>
          system.log.error("Shared journal not started at {}", path)
          system.terminate()
      }
      f.onFailure {
        case _ =>
          system.log.error("Lookup of shared journal at {} timed out", path)
          system.terminate()
      }
    }

  }

}


