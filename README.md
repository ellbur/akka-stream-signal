
Maven Dependency
================

    resolvers += "jitpack" at "https://jitpack.io"
    
    libraryDependencies += "com.github.ellbur" %% "akka-stream-signal" % "1.0.0"

Example
=======

    val numbers = Source(0 to 10) flatMapConcat(n => Source(List(n)).delay(0.5.seconds))
    
    val latestNumber = Signal.fromSource(numbers)
    
    latestNumber.toSource.runForeach(println)

Purpose
=======

Creates a broadcast hub similar to [scalaz Signal](https://gist.github.com/pchiusano/8087426).

