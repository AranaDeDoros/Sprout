package writers

import munit.FunSuite
import _root_.spec.{EndpointSpec, FieldSpec, ModelSpec, RouterSpec, HttpMethod, TypeName, FieldName, EndpointPath}

class SourceFileWriterSpec extends FunSuite:
  private val unformatted = identity[String]

  // Helper constructors using the validated ADT types
  private def typeName(s: String)  = TypeName.from(s).getOrElse(sys.error(s"Bad TypeName: $s"))
  private def fieldName(s: String) = FieldName.from(s).getOrElse(sys.error(s"Bad FieldName: $s"))
  private def epPath(s: String)    = EndpointPath.from(s).getOrElse(sys.error(s"Bad EndpointPath: $s"))

  private val endpoint = EndpointSpec(epPath("users"), HttpMethod.Get, None, Some(typeName("User")))
  private val model = ModelSpec(
    "User",
    List(FieldSpec(fieldName("id"), typeName("Long")), FieldSpec(fieldName("name"), typeName("String"))),
    None
  )

  test("a model spec generates its model from modelDef"):
    val files = new SourceFileWriter(model, unformatted).generate()

    assertEquals(files.map(_.layer), Vector(Layer.Models))
    assert(files.head.content.contains("case class User(id: Long, name: String)"))

  test("a model spec with endpoints also generates one router"):
    val spec = model.copy(endpoints = Some(List(endpoint)))
    val files = new SourceFileWriter(spec, unformatted).generate()

    assertEquals(files.map(_.layer), Vector(Layer.Models, Layer.Routes))

  test("a router spec generates its optional model from modelDef"):
    val spec = RouterSpec("User", List(endpoint), Some(model))
    val files = new SourceFileWriter(spec, unformatted).generate()

    assertEquals(files.map(_.layer), Vector(Layer.Routes, Layer.Models))
    assert(files(1).content.contains("case class User(id: Long, name: String)"))

  test("a router spec without model only generates router"):
    val spec = RouterSpec("User", List(endpoint), None)
    val files = new SourceFileWriter(spec, unformatted).generate()

    assertEquals(files.map(_.layer), Vector(Layer.Routes))
    assert(files.head.content.contains("object UserRouter"))
    assert(files.head.content.contains("getUser"))

  test("a router spec without endpoints generates router without endpoints"):
    val spec = RouterSpec("User", Nil, None)
    val files = new SourceFileWriter(spec, unformatted).generate()

    assertEquals(files.map(_.layer), Vector(Layer.Routes))
    assert(files.head.content.contains("object UserRouter"))
    assert(!files.head.content.contains("getUser"))

  test("default formatter works"):
    val files = new SourceFileWriter(model).generate()
    assert(files.nonEmpty)
