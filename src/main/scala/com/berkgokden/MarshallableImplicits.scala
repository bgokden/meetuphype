package com.berkgokden

/**
  * This is Implicit class definition to support easy json serialization and deserialization
  *
  * Just import this class:
  * import MarshallableImplicits._
  *
  * case class ExampleCaseClass()
  *
  * Convert string to case class:
  * val jsonString = "{...}"
  * val exampleCaseClass = jsonString.fromJson[ExampleCaseClass]
  *
  * Convert case class to Json string:
  * val exampleCaseClass = ExampleCaseClass(...)
  * val jsonString = exampleCaseClass.toJson()
  *
  * This is a common code for Jackson usage in Scala
  */
object MarshallableImplicits {

  implicit class Unmarshallable(unMarshallMe: String) {
    def fromJson[T]()(implicit m: Manifest[T]): T =  JsonUtil.fromJson[T](unMarshallMe)
  }

  implicit class Marshallable[T](marshallMe: T) {
    def toJson: String = JsonUtil.toJson(marshallMe)
  }

}
