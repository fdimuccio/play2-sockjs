package play.sockjs.core

import scala.collection.mutable

/**
 * Mutable queue based buffer used by session actors. When multiple MessageFrame are
 * enqueued the are aggregated as one
 */
private[sockjs] class FrameBuffer {

  private[this] val queue = mutable.Queue[Frame]()

  def isEmpty: Boolean = queue.isEmpty

  def enqueue(frame: Frame) {
    if (queue.isEmpty) queue.enqueue(frame)
    else (queue.dequeue(), frame) match {
      case (f1: Frame.MessageFrame, f2: Frame.MessageFrame) => queue.enqueue(f1 ++ f2)
      case (f1, f2) => queue.enqueue(f1, f2)
    }
  }

  def dequeue(): Frame = queue.dequeue()

}
