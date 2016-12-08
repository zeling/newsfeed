package me.zeling.newsfeed.domain

import akka.actor.{ActorContext, PoisonPill, ReceiveTimeout}
import akka.persistence.PersistentActor

/**
  * Created by zeling on 2016/12/6.
  */
trait ShardPassivate extends PersistentActor {
  import scala.concurrent.duration._
  import akka.cluster.sharding.ShardRegion.Passivate
  context.setReceiveTimeout(5.minutes)

  override def unhandled(message: Any): Unit = message match {
    case ReceiveTimeout => context.parent ! Passivate(stopMessage = PoisonPill)
    case _ => super.unhandled(message)
  }
}
