package writers

import munit.FunSuite
import _root_.spec.{EndpointSpec, FieldSpec, ModelSpec, RouterSpec}

class SourceFileWriterSpec extends FunSuite:
  private val unformatted = identity[String]
  private val endpoint = EndpointSpec("users", "get", None, "User")
  private val model = ModelSpec(
    "User",
    List(FieldSpec("id", "Long"), FieldSpec("name", "String")),
    None
  )

  test("a model spec generates its model from modelDef"):
    val files = new SourceFileWriter(model, unformatted).generate()

    assertEquals(files.map(_._1), Vector("models"))
    assert(files.head._2.contains("case class User(id: Long, name: String)"))

  test("a model spec with endpoints also generates one router"):
    val spec = model.copy(endpoints = Some(List(endpoint)))
    val files = new SourceFileWriter(spec, unformatted).generate()

    assertEquals(files.map(_._1), Vector("models", "routes"))

  test("a router spec generates its optional model from modelDef"):
    val spec = RouterSpec("User", List(endpoint), Some(model))
    val files = new SourceFileWriter(spec, unformatted).generate()

    assertEquals(files.map(_._1), Vector("routes", "models"))
    assert(files(1)._2.contains("case class User(id: Long, name: String)"))

  test("a router spec without model only generates router"):
    val spec = RouterSpec("User", List(endpoint), None)
    val files = new SourceFileWriter(spec, unformatted).generate()

    assertEquals(files.map(_._1), Vector("routes"))
    assert(files.head._2.contains("object UserRouter"))
    assert(files.head._2.contains("getUser"))

  test("a router spec without endpoints generates router without endpoints"):
    val spec = RouterSpec("User", Nil, None)
    val files = new SourceFileWriter(spec, unformatted).generate()

    assertEquals(files.map(_._1), Vector("routes"))
    assert(files.head._2.contains("object UserRouter"))
    assert(!files.head._2.contains("getUser"))

  test("default formatter works"):
    val files = new SourceFileWriter(model).generate()
    assert(files.nonEmpty)
