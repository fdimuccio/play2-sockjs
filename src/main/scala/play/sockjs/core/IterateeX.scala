package play.sockjs.core

import scala.concurrent.{ExecutionContext, Future, Promise}

import play.api.libs.iteratee.{Step, Enumerator, Iteratee}

/**
 * Extra iteratee
 */
object IterateeX {

  /**
   * A nice `echo` iteratee took from: https://jazzy.id.au/default/2013/06/12/call_response_websockets_in_play_framework.html
   *
   * Create a joined iteratee enumerator pair.
   *
   * When the enumerator is applied to an iteratee, the iteratee subsequently consumes whatever the iteratee in the pair
   * is applied to.  Consequently the enumerator is "one shot", applying it to subsequent iteratees will throw an
   * exception.
   */
  def joined[A](implicit ec: ExecutionContext): (Iteratee[A, Unit], Enumerator[A]) = {
    val promisedIteratee = Promise[Iteratee[A, Unit]]()
    val enumerator = new Enumerator[A] {
      def apply[B](i: Iteratee[A, B]) = {
        val doneIteratee = Promise[Iteratee[A, B]]()

        // Equivalent to map, but allows us to handle failures
        def wrap(delegate: Iteratee[A, B]): Iteratee[A, B] = new Iteratee[A, B] {
          def fold[C](folder: (Step[A, B]) => Future[C])(implicit ec: ExecutionContext) = {
            val toReturn = delegate.fold {
              case done @ Step.Done(a, in) => {
                doneIteratee.success(done.it)
                folder(done)
              }
              case Step.Cont(k) => {
                folder(Step.Cont(k.andThen(wrap)))
              }
              case err => folder(err)
            }
            toReturn.onFailure {
              case e => doneIteratee.failure(e)
            }
            toReturn
          }
        }

        if (promisedIteratee.trySuccess(wrap(i).map(_ => ()))) {
          doneIteratee.future
        } else {
          throw new IllegalStateException("Joined enumerator may only be applied once")
        }
      }
    }
    (Iteratee.flatten(promisedIteratee.future), enumerator)
  }
}
