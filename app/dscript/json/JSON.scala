package dscript.json

import com.google.gson._
object JSON {
  private val gson = new Gson()
  def toJson[T](v: T) = gson.toJson(v)
  def fromJson[T <: Manifest[T]](json: String) = gson.fromJson(json, manifest.getClass)
  def fromJson[T](json: String, cls: Class[T])  = cls.cast(gson.fromJson(json, cls))
}
