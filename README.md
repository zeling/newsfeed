NewsFeed
=====================================

Prototype application for a news sharing application, sth like a Wechat.
Event-sourcing application with the power of akka-cluster and akka-sharding.
Providing rest apis via spray.

Instructions on running the application
--------------------------------------
Nothing to see here:) try sbt;

Thoughts on Akka
-----------------
The pattern is awesome, message passing and actors! But why the hell everything
is untyped? _Any_ EVERYWHERE! Erlang is untyped and is the king in the realm of
distributed application, but that is not the excuse for making everything untyped!
Then why the hell should I use scala? I am a stupid guy so I need types to help.

p.s. I totally understand why the untyped actors decision choice is made.
Is dynamic typing really the only choice we got on hand for message passing actor?

