package play.sockjs.core


import play.sockjs.api.Frame

import scala.collection.mutable

/**
 * Mutable queue based buffer used by session actors.
 * Contiguos MessageFrame are aggregated as one.
 */
private[sockjs] class FrameBuffer {

  private[this] val queue = mutable.Queue[Frame]()
  private[this] var tail: Frame = _
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
    * Merge two contiguous MessageFrame in one single Frame
    */
  def enqueue(frame: Frame) {
    (tail, frame) match {
      case (null, f) => tail = f
      case (f1: Frame.MessageFrame, f2: Frame.MessageFrame) => tail = f1 ++ f2
      case (f1, f2) => queue.enqueue(f1); tail = f2
    }
    length += weight(frame)
  }

  def dequeue(): Frame = {
    val frame =
      if (queue.nonEmpty) queue.dequeue()
      else if (!isEmpty) {val cur = tail; tail = null; cur}
      else throw new NoSuchElementException("queue empty")
    length -= weight(frame)
    frame
  }

  private def weight(frame: Frame) = frame match {
    case Frame.MessageFrame(data) => data.size * java.lang.Character.BYTES
    case _ => 1
  }
}
