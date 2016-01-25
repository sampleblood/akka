/**
 * Copyright (C) 2009-2016 Typesafe Inc. <http://www.typesafe.com>
 */
package akka.persistence

import java.lang.{ Iterable ⇒ JIterable }
import akka.actor.UntypedActor
import akka.japi.Procedure
import akka.actor.AbstractActor
import akka.japi.Util

abstract class RecoveryCompleted
/**
 * Sent to a [[PersistentActor]] when the journal replay has been finished.
 */
@SerialVersionUID(1L)
case object RecoveryCompleted extends RecoveryCompleted {
  /**
   * Java API: get the singleton instance
   */
  def getInstance = this
}

/**
 * Reply message to a successful [[Eventsourced#deleteMessages]] request.
 */
final case class DeleteMessagesSuccess(toSequenceNr: Long)

/**
 * Reply message to a failed [[Eventsourced#deleteMessages]] request.
 */
final case class DeleteMessagesFailure(cause: Throwable, toSequenceNr: Long)

/**
 * Recovery mode configuration object to be returned in [[PersistentActor#recovery]].
 *
 * By default recovers from latest snapshot replays through to the last available event (last sequenceId).
 *
 * Recovery will start from a snapshot if the persistent actor has previously saved one or more snapshots
 * and at least one of these snapshots matches the specified `fromSnapshot` criteria.
 * Otherwise, recovery will start from scratch by replaying all stored events.
 *
 * If recovery starts from a snapshot, the persistent actor is offered that snapshot with a [[SnapshotOffer]]
 * message, followed by replayed messages, if any, that are younger than the snapshot, up to the
 * specified upper sequence number bound (`toSequenceNr`).
 *
 * @param fromSnapshot criteria for selecting a saved snapshot from which recovery should start. Default
 *                     is latest (= youngest) snapshot.
 * @param toSequenceNr upper sequence number bound (inclusive) for recovery. Default is no upper bound.
 * @param replayMax maximum number of messages to replay. Default is no limit.
 */
@SerialVersionUID(1L)
final case class Recovery(
  fromSnapshot: SnapshotSelectionCriteria = SnapshotSelectionCriteria.Latest,
  toSequenceNr: Long = Long.MaxValue,
  replayMax: Long = Long.MaxValue)

object Recovery {

  /**
   * Java API
   * @see [[Recovery]]
   */
  def create() = Recovery()

  /**
   * Java API
   * @see [[Recovery]]
   */
  def create(toSequenceNr: Long) =
    Recovery(toSequenceNr = toSequenceNr)

  /**
   * Java API
   * @see [[Recovery]]
   */
  def create(fromSnapshot: SnapshotSelectionCriteria) =
    Recovery(fromSnapshot = fromSnapshot)

  /**
   * Java API
   * @see [[Recovery]]
   */
  def create(fromSnapshot: SnapshotSelectionCriteria, toSequenceNr: Long) =
    Recovery(fromSnapshot, toSequenceNr)

  /**
   * Java API
   * @see [[Recovery]]
   */
  def create(fromSnapshot: SnapshotSelectionCriteria, toSequenceNr: Long, replayMax: Long) =
    Recovery(fromSnapshot, toSequenceNr, replayMax)

  /**
   * Convenience method for skipping recovery in [[PersistentActor]].
   * @see [[Recovery]]
   */
  val none: Recovery = Recovery(toSequenceNr = 0L)
}

/**
 * An persistent Actor - can be used to implement command or event sourcing.
 */
trait PersistentActor extends Eventsourced with PersistenceIdentity {
  def receive = receiveCommand
}

/**
 * Java API: an persistent actor - can be used to implement command or event sourcing.
 */
abstract class UntypedPersistentActor extends UntypedActor with Eventsourced with PersistenceIdentity {

  final def onReceive(message: Any) = onReceiveCommand(message)

  final def receiveRecover: Receive = {
    case msg ⇒ onReceiveRecover(msg)
  }

  final def receiveCommand: Receive = {
    case msg ⇒ onReceiveCommand(msg)
  }

  /**
   * Java API: asynchronously persists `event`. On successful persistence, `handler` is called with the
   * persisted event. It is guaranteed that no new commands will be received by a persistent actor
   * between a call to `persist` and the execution of its `handler`. This also holds for
   * multiple `persist` calls per received command. Internally, this is achieved by stashing new
   * commands and unstashing them when the `event` has been persisted and handled. The stash used
   * for that is an internal stash which doesn't interfere with the inherited user stash.
   *
   * An event `handler` may close over persistent actor state and modify it. The `getSender()` of a persisted
   * event is the sender of the corresponding command. This means that one can reply to a command
   * sender within an event `handler`.
   *
   * Within an event handler, applications usually update persistent actor state using persisted event
   * data, notify listeners and reply to command senders.
   *
   * If persistence of an event fails, [[#onPersistFailure]] will be invoked and the actor will
   * unconditionally be stopped. The reason that it cannot resume when persist fails is that it
   * is unknown if the even was actually persisted or not, and therefore it is in an inconsistent
   * state. Restarting on persistent failures will most likely fail anyway, since the journal
   * is probably unavailable. It is better to stop the actor and after a back-off timeout start
   * it again.
   *
   * @param event event to be persisted.
   * @param handler handler for each persisted `event`
   */
  def persist[A](event: A, handler: Procedure[A]): Unit =
    persist(event)(event ⇒ handler(event))

  /**
   * Java API: asynchronously persists `events` in specified order. This is equivalent to calling
   * `persist[A](event: A, handler: Procedure[A])` multiple times with the same `handler`,
   * except that `events` are persisted atomically with this method.
   *
   * @param events events to be persisted.
   * @param handler handler for each persisted `events`
   */
  def persistAll[A](events: JIterable[A], handler: Procedure[A]): Unit =
    persistAll(Util.immutableSeq(events))(event ⇒ handler(event))

  @deprecated("use persistAll instead", "2.4")
  def persist[A](events: JIterable[A], handler: Procedure[A]): Unit =
    persistAll(events, handler)

  /**
   * JAVA API: asynchronously persists `event`. On successful persistence, `handler` is called with the
   * persisted event.
   *
   * Unlike `persist` the persistent actor will continue to receive incoming commands between the
   * call to `persist` and executing it's `handler`. This asynchronous, non-stashing, version of
   * of persist should be used when you favor throughput over the "command-2 only processed after
   * command-1 effects' have been applied" guarantee, which is provided by the plain [[persist]] method.
   *
   * An event `handler` may close over persistent actor state and modify it. The `sender` of a persisted
   * event is the sender of the corresponding command. This means that one can reply to a command
   * sender within an event `handler`.
   *
   * If persistence of an event fails, [[#onPersistFailure]] will be invoked and the actor will
   * unconditionally be stopped. The reason that it cannot resume when persist fails is that it
   * is unknown if the even was actually persisted or not, and therefore it is in an inconsistent
   * state. Restarting on persistent failures will most likely fail anyway, since the journal
   * is probably unavailable. It is better to stop the actor and after a back-off timeout start
   * it again.
   *
   * @param event event to be persisted
   * @param handler handler for each persisted `event`
   */
  def persistAsync[A](event: A)(handler: Procedure[A]): Unit =
    super[Eventsourced].persistAsync(event)(event ⇒ handler(event))

  /**
   * JAVA API: asynchronously persists `events` in specified order. This is equivalent to calling
   * `persistAsync[A](event: A)(handler: A => Unit)` multiple times with the same `handler`,
   * except that `events` are persisted atomically with this method.
   *
   * @param events events to be persisted
   * @param handler handler for each persisted `events`
   */
  def persistAllAsync[A](events: JIterable[A], handler: Procedure[A]): Unit =
    super[Eventsourced].persistAllAsync(Util.immutableSeq(events))(event ⇒ handler(event))

  /**
   * Defer the handler execution until all pending handlers have been executed.
   * Allows to define logic within the actor, which will respect the invocation-order-guarantee
   * in respect to `persistAsync` calls. That is, if `persistAsync` was invoked before defer,
   * the corresponding handlers will be invoked in the same order as they were registered in.
   *
   * This call will NOT result in `event` being persisted, please use `persist` or `persistAsync`,
   * if the given event should possible to replay.
   *
   * If there are no pending persist handler calls, the handler will be called immediately.
   *
   * If persistence of an earlier event fails, the persistent actor will stop, and the `handler`
   * will not be run.
   *
   * @param event event to be handled in the future, when preceding persist operations have been processes
   * @param handler handler for the given `event`
   */
  def deferAsync[A](event: A)(handler: Procedure[A]): Unit =
    super[Eventsourced].deferAsync(event)(event ⇒ handler(event))

  /**
   * Java API: recovery handler that receives persisted events during recovery. If a state snapshot
   * has been captured and saved, this handler will receive a [[SnapshotOffer]] message
   * followed by events that are younger than the offered snapshot.
   *
   * This handler must not have side-effects other than changing persistent actor state i.e. it
   * should not perform actions that may fail, such as interacting with external services,
   * for example.
   *
   * If there is a problem with recovering the state of the actor from the journal, the error
   * will be logged and the actor will be stopped.
   *
   * @see [[Recovery]]
   */
  @throws(classOf[Exception])
  def onReceiveRecover(msg: Any): Unit

  /**
   * Java API: command handler. Typically validates commands against current state (and/or by
   * communication with other actors). On successful validation, one or more events are
   * derived from a command and these events are then persisted by calling `persist`.
   */
  @throws(classOf[Exception])
  def onReceiveCommand(msg: Any): Unit
}

/**
 * Java API: an persistent actor - can be used to implement command or event sourcing.
 */
abstract class AbstractPersistentActor extends AbstractActor with PersistentActor with Eventsourced {

  /**
   * Java API: asynchronously persists `event`. On successful persistence, `handler` is called with the
   * persisted event. It is guaranteed that no new commands will be received by a persistent actor
   * between a call to `persist` and the execution of its `handler`. This also holds for
   * multiple `persist` calls per received command. Internally, this is achieved by stashing new
   * commands and unstashing them when the `event` has been persisted and handled. The stash used
   * for that is an internal stash which doesn't interfere with the inherited user stash.
   *
   * An event `handler` may close over persistent actor state and modify it. The `getSender()` of a persisted
   * event is the sender of the corresponding command. This means that one can reply to a command
   * sender within an event `handler`.
   *
   * Within an event handler, applications usually update persistent actor state using persisted event
   * data, notify listeners and reply to command senders.
   *
   * If persistence of an event fails, [[#onPersistFailure]] will be invoked and the actor will
   * unconditionally be stopped. The reason that it cannot resume when persist fails is that it
   * is unknown if the even was actually persisted or not, and therefore it is in an inconsistent
   * state. Restarting on persistent failures will most likely fail anyway, since the journal
   * is probably unavailable. It is better to stop the actor and after a back-off timeout start
   * it again.
   *
   * @param event event to be persisted.
   * @param handler handler for each persisted `event`
   */
  def persist[A](event: A, handler: Procedure[A]): Unit =
    persist(event)(event ⇒ handler(event))

  /**
   * Java API: asynchronously persists `events` in specified order. This is equivalent to calling
   * `persist[A](event: A, handler: Procedure[A])` multiple times with the same `handler`,
   * except that `events` are persisted atomically with this method.
   *
   * @param events events to be persisted.
   * @param handler handler for each persisted `events`
   */
  def persistAll[A](events: JIterable[A], handler: Procedure[A]): Unit =
    persistAll(Util.immutableSeq(events))(event ⇒ handler(event))

  @deprecated("use persistAll instead", "2.4")
  def persist[A](events: JIterable[A], handler: Procedure[A]): Unit =
    persistAll(events, handler)

  /**
   * Java API: asynchronously persists `event`. On successful persistence, `handler` is called with the
   * persisted event.
   *
   * Unlike `persist` the persistent actor will continue to receive incoming commands between the
   * call to `persistAsync` and executing it's `handler`. This asynchronous, non-stashing, version of
   * of persist should be used when you favor throughput over the strict ordering guarantees that `persist` guarantees.
   *
   * If persistence of an event fails, [[#onPersistFailure]] will be invoked and the actor will
   * unconditionally be stopped. The reason that it cannot resume when persist fails is that it
   * is unknown if the even was actually persisted or not, and therefore it is in an inconsistent
   * state. Restarting on persistent failures will most likely fail anyway, since the journal
   * is probably unavailable. It is better to stop the actor and after a back-off timeout start
   * it again.
   *
   * @param event event to be persisted
   * @param handler handler for each persisted `event`
   */
  def persistAsync[A](event: A, handler: Procedure[A]): Unit =
    persistAsync(event)(event ⇒ handler(event))

  /**
   * Java API: asynchronously persists `events` in specified order. This is equivalent to calling
   * `persistAsync[A](event: A)(handler: A => Unit)` multiple times with the same `handler`,
   * except that `events` are persisted atomically with this method.
   *
   * @param events events to be persisted
   * @param handler handler for each persisted `events`
   */
  def persistAllAsync[A](events: JIterable[A], handler: Procedure[A]): Unit =
    persistAllAsync(Util.immutableSeq(events))(event ⇒ handler(event))

  @deprecated("use persistAllAsync instead", "2.4")
  def persistAsync[A](events: JIterable[A], handler: Procedure[A]): Unit =
    persistAllAsync(events, handler)

  /**
   * Defer the handler execution until all pending handlers have been executed.
   * Allows to define logic within the actor, which will respect the invocation-order-guarantee
   * in respect to `persistAsync` calls. That is, if `persistAsync` was invoked before defer,
   * the corresponding handlers will be invoked in the same order as they were registered in.
   *
   * This call will NOT result in `event` being persisted, please use `persist` or `persistAsync`,
   * if the given event should possible to replay.
   *
   * If there are no pending persist handler calls, the handler will be called immediately.
   *
   * If persistence of an earlier event fails, the persistent actor will stop, and the `handler`
   * will not be run.
   *
   * @param event event to be handled in the future, when preceding persist operations have been processes
   * @param handler handler for the given `event`
   */
  def deferAsync[A](event: A)(handler: Procedure[A]): Unit =
    super.deferAsync(event)(event ⇒ handler(event))

  override def receive = super[PersistentActor].receive

}
