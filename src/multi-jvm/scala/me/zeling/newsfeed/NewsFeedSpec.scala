package me.zeling.newsfeed

import java.io.File
import java.util.{Calendar, UUID}

import scala.concurrent.duration._
import org.apache.commons.io.FileUtils
import com.typesafe.config.ConfigFactory
import akka.actor.ActorIdentity
import akka.actor.Identify
import akka.actor.Props
import akka.cluster.Cluster
import akka.cluster.sharding.{ClusterSharding, ClusterShardingSettings}
import akka.persistence.Persistence
import akka.persistence.journal.leveldb.SharedLeveldbJournal
import akka.persistence.journal.leveldb.SharedLeveldbStore
import akka.remote.testconductor.RoleName
import akka.remote.testkit.MultiNodeConfig
import akka.remote.testkit.MultiNodeSpec
import akka.testkit.ImplicitSender
import me.zeling.newsfeed.domain.User.GetTimeline
import me.zeling.newsfeed.domain._

import scala.collection.immutable.Vector

object NewsFeedSpec extends MultiNodeConfig {
  val controller = role("controller")
  val node1 = role("node1")
  val node2 = role("node2")

  commonConfig(ConfigFactory.parseString("""
    akka.cluster.metrics.enabled=off
    akka.actor.provider = "akka.cluster.ClusterActorRefProvider"
    akka.persistence.journal.plugin = "akka.persistence.journal.leveldb-shared"
    akka.persistence.journal.leveldb-shared.store {
      native = off
      dir = "target/test-shared-journal"
    }
    akka.persistence.snapshot-store.plugin = "akka.persistence.snapshot-store.local"
    akka.persistence.snapshot-store.local.dir = "target/test-snapshots"
    """))

}

class NewsFeedSpecMultiJvmNode1 extends NewsFeedSpec
class NewsFeedSpecMultiJvmNode2 extends NewsFeedSpec
class NewsFeedSpecMultiJvmNode3 extends NewsFeedSpec

class NewsFeedSpec extends MultiNodeSpec(NewsFeedSpec)
  with STMultiNodeSpec with ImplicitSender with HasPostRegion with HasTopicRegion with HasUserRegion {

  import NewsFeedSpec._

  implicit val shard = ClusterSharding(system)

  def initialParticipants = roles.size

  val storageLocations = List(
    "akka.persistence.journal.leveldb.dir",
    "akka.persistence.journal.leveldb-shared.store.dir",
    "akka.persistence.snapshot-store.local.dir").map(s => new File(system.settings.config.getString(s)))

  override protected def atStartup() {
    runOn(controller) {
      storageLocations.foreach(dir => FileUtils.deleteDirectory(dir))
    }
  }

  override protected def afterTermination() {
    runOn(controller) {
      storageLocations.foreach(dir => FileUtils.deleteDirectory(dir))
    }
  }

  def join(from: RoleName, to: RoleName): Unit = {
    runOn(from) {
      Cluster(system) join node(to).address
      startSharding()
    }
    enterBarrier(from.name + "-joined")
  }

  def startSharding(): Unit = {
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
  }

  "Sharded blog app" must {

    "setup shared journal" in {
      // start the Persistence extension
      Persistence(system)
      runOn(controller) {
        system.actorOf(Props[SharedLeveldbStore], "store")
      }
      enterBarrier("peristence-started")

      runOn(node1, node2) {
        system.actorSelection(node(controller) / "user" / "store") ! Identify(None)
        val sharedStore = expectMsgType[ActorIdentity].ref.get
        SharedLeveldbJournal.setStore(sharedStore, system)
      }

      enterBarrier("after-1")
    }

    "join cluster" in within(15.seconds) {
      join(node1, node1)
      join(node2, node1)
      enterBarrier("after-2")
    }

    "create and retrieve blog post" in within(15.seconds) {
      val postId = "45902e35-4acb-4dc2-9109-73a9126b604f"

      runOn(node1) {
        postRegion ! Post.CreatePost(postId,
          Post.PostContent("zeling", "Sharding test", "#test", 1481035143977L))
        postRegion ! Post.AppendComment(postId, Post.PostComment("shijian", "cao tmd shabi fenbushi pj"))
      }

      runOn(node2) {
        awaitAssert {
          within(1.second) {
            postRegion ! Post.GetPostView(postId)
            expectMsg(Post.PostView(postId, Post.PostContent("zeling", "Sharding test", "#test", 1481035143977L), Vector(Post.PostComment("shijian", "cao tmd shabi fenbushi pj"))))
          }
        }
        awaitAssert {
          within(1.second) {
            userRegion ! User.ListPosts("zeling")
            expectMsg(User.PostList(Vector(Post.PostView(postId, Post.PostContent("zeling", "Sharding test", "#test", 1481035143977L), Vector(Post.PostComment("shijian", "cao tmd shabi fenbushi pj"))))))
          }
        }
        awaitAssert {
          within(1.second) {
            topicRegion ! Topic.ListPosts("#test")
            expectMsg(Topic.PostList(Vector(Post.PostView(postId, Post.PostContent("zeling", "Sharding test", "#test", 1481035143977L), Vector(Post.PostComment("shijian", "cao tmd shabi fenbushi pj"))))))
          }
        }
        awaitAssert {
          within(1.second) {
            userRegion ! GetTimeline("zeling")
            expectMsg(User.TimeLine(Vector(Post.PostView(postId, Post.PostContent("zeling", "Sharding test", "#test", 1481035143977L), Vector(Post.PostComment("shijian", "cao tmd shabi fenbushi pj"))))))
          }
        }
      }
      enterBarrier("after-3")
    }

    "generate timeline" in within(5.seconds) {

      runOn(node1) {
//        userRegion ! GetTimeline("zeling")
//        expectMsg(User.TimeLine(Vector(Post.PostView(postId, Post.PostContent("zeling", "Sharding test", "#test", 1481035143977L), Vector(Post.PostComment("shijian", "cao tmd shabi fenbushi pj"))))))
//        postRegion ! Post.AddPost(postId,
//          Post.PostContent("Patrik", "Hash functions", "A hash function should be deterministic..."))
//        postRegion ! Post.Publish(postId)
      }
//
//      runOn(node2) {
//        val listingRegion = ClusterSharding(system).shardRegion(AuthorListing.shardName)
//        awaitAssert {
////          within(1.second) {
////            listingRegion ! AuthorListing.GetPosts("Patrik")
////            val posts = expectMsgType[AuthorListing.Posts].list
////            posts.isEmpty shouldBe false
////            posts.last.title should be("Hash functions")
//          }
//        }
//      }
//      enterBarrier("after-4")
    }

  }
}
