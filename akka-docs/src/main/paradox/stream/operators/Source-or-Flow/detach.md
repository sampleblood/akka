# detach

Detach upstream demand from downstream demand without detaching the stream rates.

@ref[Simple operators](../index.md#simple-operators)

@@@div { .group-scala }

## Signature

@@signature [Flow.scala]($akka$/akka-stream/src/main/scala/akka/stream/scaladsl/Flow.scala) { #detach }

@@@

## Description

Detach upstream demand from downstream demand without detaching the stream rates.


@@@div { .callout }

**emits** when the upstream stage has emitted and there is demand

**backpressures** when downstream backpressures

**completes** when upstream completes

@@@

