package domain

import munit.FunSuite
import cliparsing.Parser
import spec.*

class RouterCommandSpec extends FunSuite:

  test("Parser accepts valid router flags combinations"):
    val validCombos = List(
      List("new:router", "User"),
      List("new:router", "User", "--model"),
      List("new:router", "User", "-m"),
      List("new:router", "User", "--api", "-m"),
      List("new:router", "User", "-a", "--model")
    )

    for args <- validCombos do
      val res = Parser.parseInput(args)
      assert(res.isRight, s"Expected success for args: $args but got: ${res.left.getOrElse(null)}")

  test("Parser enforces --api / -a requires --model or -m"):
    val resApi = Parser.parseInput(List("new:router", "User", "--api"))
    assert(resApi.isLeft)

    val resA = Parser.parseInput(List("new:router", "User", "-a"))
    assert(resA.isLeft)

  test("Parser rejects removed -model flag and unknown flags"):
    val resModel = Parser.parseInput(List("new:router", "User", "-model"))
    assert(resModel.isLeft)

    val resUnknown = Parser.parseInput(List("new:router", "User", "--unknown"))
    assert(resUnknown.isLeft)

  test("SchemaParser parses endpoints from yaml when run with no flags"):
    val args = SchemaArgs(Entity.Router, "User", Nil)
    val spec = SchemaParser(args).loadSpec()

    assert(spec.isRight, s"Expected Right but got: $spec")
    val routerSpec = spec.getOrElse(sys.error("unreachable")).asInstanceOf[RouterSpec]

    // From User.yaml: path: users, method: post, request: User, response: User
    assertEquals(routerSpec.endpoints.length, 1)
    assertEquals(routerSpec.endpoints.head.path.value, "users")
    assertEquals(routerSpec.endpoints.head.method, HttpMethod.Post)
    assertEquals(routerSpec.endpoints.head.request.map(_.value), Some("User"))
    assertEquals(routerSpec.endpoints.head.response.map(_.value), Some("User"))
    assertEquals(routerSpec.model, None)

  test("SchemaParser generates classic http verb endpoints when --api or -a is passed"):
    val argsApi = SchemaArgs(Entity.Router, "User", List(Flag("--api"), Flag("-m")))
    val specApi = SchemaParser(argsApi).loadSpec()

    assert(specApi.isRight, s"Expected Right but got: $specApi")
    val routerSpec = specApi.getOrElse(sys.error("unreachable")).asInstanceOf[RouterSpec]

    val methods = routerSpec.endpoints.map(_.method)
    assertEquals(methods, List(HttpMethod.Get, HttpMethod.Post, HttpMethod.Put, HttpMethod.Delete))
    assertEquals(routerSpec.model, None)

  test("SchemaParser generates model only when --model is passed"):
    val argsModel = SchemaArgs(Entity.Router, "User", List(Flag("--model")))
    val specModel = SchemaParser(argsModel).loadSpec()
    assert(specModel.isRight)
    assert(specModel.getOrElse(sys.error("unreachable")).asInstanceOf[RouterSpec].model.isDefined)

    val argsShortM = SchemaArgs(Entity.Router, "User", List(Flag("-m")))
    val specShortM = SchemaParser(argsShortM).loadSpec()
    assert(specShortM.isRight)
    assertEquals(specShortM.getOrElse(sys.error("unreachable")).asInstanceOf[RouterSpec].model, None)

  test("new:router -m fails when existing model is not found"):
    val res = Entity.Router.generate(List(CLIOption("NonExistent")), List(Flag("-m")))
    assert(res.isLeft)
