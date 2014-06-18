package play.sockjs.core.iteratee

import scala.concurrent._
import scala.concurrent.stm._
import scala.collection.immutable.Queue
import scala.util.{Success, Failure}

import play.api.libs.iteratee._
import play.api.libs.iteratee.Concurrent.Channel

/**
 * Extra Enumerator
 */
object EnumeratorX {

  /**
   * like [[play.api.libs.iteratee.Concurrent.unicast]] except that buffers messages
   * until the enumerator isn't applied
   */
  def concurrent[E](
    onComplete: => Unit = (),
    onError: (String, Input[E]) => Unit = (_: String, _: Input[E]) => ())(implicit ec: ExecutionContext) = {

    sealed trait Command
    case class Push(input: Input[E]) extends Command
    case class End(throwable: Option[Throwable]) extends Command

    val iteratee = Ref[Option[Future[Option[Input[E] => Iteratee[E, _]]]]](None)
    val buffer = Ref(Queue.empty[Command])
    val result = Promise[Iteratee[E, _]]()
    val ConT = Ref[Option[(Input[E] => Iteratee[E, _]) => Iteratee[E, _]]](None)

    def write(chunk: Input[E]) {
      val eventuallyNext = Promise[Option[Input[E] => Iteratee[E, _]]]()
      iteratee.single.swap(Some(eventuallyNext.future)).get.onComplete {
        case Success(None) =>
          eventuallyNext.success(None)

        case Success(Some(k)) =>
          eventuallyNext.completeWith({
            val next = k(chunk)
            next.pureFold {
              case Step.Done(a, in) =>
                onComplete
                result.success(next)
                None

              case Step.Error(msg, e) =>
                onError(msg, e)
                result.success(next)
                None

              case Step.Cont(k) =>
                Some(k)
            }
          })

        case Failure(e) =>
          result.failure(e)
          eventuallyNext.success(None)
      }
    }

    def reedem(throwable: Option[Throwable]) {
      throwable match {
        case Some(e) =>
          iteratee.single.swap(Some(Future.successful(None))).get.onComplete {
            case Success(maybeK) => maybeK.foreach(_ => result.failure(e))
            case Failure(e) => result.failure(e)
          }

        case None =>
          iteratee.single.swap(Some(Future.successful(None))).get.onComplete { maybeK =>
            maybeK.get.foreach(k => result.success(ConT.single().get(k)))
          }
      }
    }

    val enumerator = new Enumerator[E] {
      def apply[A](it: Iteratee[E, A]): Future[Iteratee[E, A]] = {
        atomic { implicit txn =>
          if (iteratee().isDefined) throw new IllegalArgumentException("This enumerator can not be applied twice!")
          iteratee() = Some(it.pureFold { case Step.Cont(k) => Some(k); case other => result.success(other.it); None })
          ConT() = Some((k: Input[E] => Iteratee[E, A]) => Cont[E, A](k)).asInstanceOf[Option[(Input[E] => Iteratee[E, _]) => Iteratee[E, _]]]
          def flush(buffer: Queue[Command]) {
            if (!buffer.isEmpty) buffer.dequeue match {
              case (Push(el), queue) => write(el); flush(queue)
              case (End(maybeE), _) => reedem(maybeE)
            }
          }
          flush(buffer.swap(Queue.empty))
        }
        result.asInstanceOf[Promise[Iteratee[E, A]]].future
      }
    }

    val channel = new Channel[E] {
      def push(chunk: Input[E]) {
        atomic { implicit txn =>
          if (iteratee().isDefined) write(chunk)
          else buffer() = buffer().enqueue(Push(chunk))
        }
      }

      def end(e: Throwable) {
        atomic { implicit txn =>
          if (iteratee().isDefined) reedem(Some(e))
          else buffer() = buffer().enqueue(End(Some(e)))
        }
      }

      def end() {
        atomic { implicit txn =>
          if (iteratee().isDefined) reedem(None)
          else buffer() = buffer().enqueue(End(None))
        }
      }
    }

    (enumerator, channel)
  }
}
