package dscript.util

/**
 * A simple cache implementation that allows to maintain association between a key and a value
 * @tparam K the key type
 * @tparam V the value type
 */
class Cache[K, V] {
  private var cache = Map[K, V]()

  def lookup(k: K): Option[V] = cache.get(k)
  def add(k: K, v: V): Unit = {
    cache = cache + (k -> v)
  }
  def evict(k: K): Option[V] = {
    val v = cache.get(k)
    cache = cache - k
    v
  }
  def size = cache.size
}
