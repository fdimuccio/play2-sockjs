package play.sockjs.core


import play.sockjs.api.Frame

import scala.collection.mutable

/**
 * Mutable queue based buffer used by session actors.
 * Contiguos MessageFrame are aggregated as one.
 */
private[sockjs] class FrameBuffer {

  private[this] val queue = mutable.Queue[Frame]()
  private[this] var last: Frame = _
  private[this] var length: Int = 0

  def isEmpty: Boolean = size == 0

  def nonEmpty: Boolean = size != 0

  /**
    * Size in bytes
    */
  def size: Int = length

  /**
    * Enqueue the given frame.
    *
    * If the last enqueued frame is a MessageFrame, they will be squashed together.
    */
  def enqueue(frame: Frame) {
    (last, frame) match {
      case (null, f) => last = f
      case (f1: Frame.MessageFrame, f2: Frame.MessageFrame) => last = f1 ++ f2
      case (f1, f2) => queue.enqueue(f1); last = f2
    }
    length += size(frame)
  }

  def dequeue(): Frame = {
    val frame =
      if (queue.nonEmpty) queue.dequeue()
      else if (!isEmpty) {val cur = last; last = null; cur}
      else throw new NoSuchElementException("queue empty")
    length -= size(frame)
    frame
  }

  private def size(frame: Frame) = frame match {
    case Frame.MessageFrame(data) => data.size * java.lang.Character.BYTES
    case _ => 1
  }
}
