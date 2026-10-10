package writers

import org.scalafmt.interfaces.Scalafmt
import spec.*

import java.io.File
import java.nio.file.{Files, Path, Paths, StandardOpenOption}
import scala.jdk.StreamConverters.*
import scala.util.{Try, Using, boundary}
import scala.util.boundary.break

final val SPROUT_MODELS_PATH = Paths.get("src/main/scala/sprout/models")
final val SPROUT_ROUTERS_PATH = Paths.get("src/main/scala/sprout/routes")
final val SPROUT_PACKAGE_PATH = Paths.get("src/main/scala/sprout")
final val SPROUT_SCHEMA_PATH = Paths.get("src/main/resources/schemas")

/** Configuration for directories and package management */
case class GeneratorConfig(
    basePackage: String = "sprout",
    schemasDir: Path = SPROUT_SCHEMA_PATH,
    outputRootDir: Path = Paths.get("src/main/scala")
):
  /** Maps a sub-package (e.g. "models") to a path like "src/main/scala/com/example/models" */
  def packageToPath(subPackage: String): Path =
    val fullPackage =
      if basePackage.isEmpty then subPackage
      else if subPackage.isEmpty then basePackage
      else s"$basePackage.$subPackage"
    val relativePath = fullPackage.replace('.', File.separatorChar)
    outputRootDir.resolve(relativePath)

object GeneratorConfig:
  /** attempts to auto-detect the base package name from existing .scala files under src/main/scala */
  def autoDetect(outputRootDir: Path = Paths.get("src/main/scala")): GeneratorConfig =
    val detectedPackage = detectBasePackage(outputRootDir).getOrElse("sprout")
    GeneratorConfig(basePackage = detectedPackage, outputRootDir = outputRootDir)

  private def detectBasePackage(sourceDir: Path): Option[String] =
    if !Files.exists(sourceDir) then None
    else
      val packages = Using.resource(Files.walk(sourceDir)) { stream =>
        stream
          .toScala(LazyList)
          .filter(p => Files.isRegularFile(p) && p.toString.endsWith(".scala"))
          .flatMap { path =>
            val content = Files.readString(path)
            """package\s+([a-zA-Z0-9_.]+)""".r.findFirstMatchIn(content).map(_.group(1))
          }
          .toList
      }

      if packages.isEmpty then None
      else
        // check if there is an existing package ending in .models, .routes
        val existingLayerPkg = packages.collectFirst {
          case pkg if pkg.matches(".+\\.(models|routes)$") =>
            pkg.replaceAll("\\.(models|routes)$", "")
        }

        existingLayerPkg.orElse(findCommonPackagePrefix(packages))

  private def findCommonPackagePrefix(packages: List[String]): Option[String] =
    val splitPackages = packages.map(_.split('.').toList)
    splitPackages match
      case Nil => None
      case head :: tail =>
        val commonSegments = head.zipWithIndex.takeWhile { case (segment, idx) =>
          tail.forall(l => l.lift(idx).contains(segment))
        }.map(_._1)

        val prefix = commonSegments.mkString(".")
        val stripped = prefix.replaceAll("(?<=^|\\.)(models|routes|domain)$", "").stripSuffix(".")
        if stripped.nonEmpty then Some(stripped) else None

/** Source Code Generator */
class SourceFileWriter(
    private val spec: SchemaSpec,
    private val config: GeneratorConfig = GeneratorConfig.autoDetect(),
    private[writers] val formatter: String => String = SourceFileWriter.formatScalaCode
):
  def this(spec: SchemaSpec, formatter: String => String) =
    this(spec, GeneratorConfig(), formatter)

  private val specName = spec match
    case ModelSpec(name, _, _)  => name
    case RouterSpec(name, _, _) => name

  /** Public entrypoint to generate source files and write them to disk. */
  def generateAndWrite(): Either[Throwable, Unit] =
    for
      files <- Try(generate()).toEither
      _     <- writeToDisk(files)
    yield ()

  private def writeToDisk(files: Vector[(String, String)]): Either[Throwable, Unit] =
    boundary:
      for (subPackage, content) <- files do
        writeOne(subPackage, content) match
          case Left(err) => break(Left(err))
          case Right(_)  => ()
      Right(())

  private def writeOne(subPackage: String, content: String): Either[Throwable, Unit] =
    Try {
      val targetDir = config.packageToPath(subPackage)
      if !Files.exists(targetDir) then Files.createDirectories(targetDir)

      val fileName = if subPackage == "models" then s"$specName.scala" else s"${specName}Router.scala"
      Files.writeString(
        targetDir.resolve(fileName),
        content,
        StandardOpenOption.CREATE,
        StandardOpenOption.TRUNCATE_EXISTING
      )
    }.toEither.map(_ => ())

  private[writers] def generate(): Vector[(String, String)] =
    spec match
      case ModelSpec(name, modelDef, endpoints) =>
        val fields = modelDef.map(field => field.name -> field.valueType)
        val model = "models" -> generateModel(name, fields)
        val router = "routes" -> generateRouter(name, endpoints.getOrElse(Nil))
        Vector(model) ++ endpoints.map(_ => router)

      case RouterSpec(name, endpoints, model) =>
        val router = "routes" -> generateRouter(name, endpoints)
        val generatedModel = model.map { modelSpec =>
          val fields = modelSpec.modelDef.map(field => field.name -> field.valueType)
          "models" -> generateModel(modelSpec.name, fields)
        }
        Vector(router) ++ generatedModel

  private def generateModel(
      entityName: String,
      fields: List[(String, String)]
  ): String =
    val params = fields.map { (name, tpe) => s"$name: $tpe" }.mkString(", ")

    val code = s"""
      |package ${config.basePackage}.models
      |
      |import com.github.plokhotnyuk.jsoniter_scala.core.JsonValueCodec
      |import com.github.plokhotnyuk.jsoniter_scala.macros.JsonCodecMaker
      |
      |case class $entityName($params)
      |
      |object $entityName:
      |  given JsonValueCodec[$entityName] = JsonCodecMaker.make
      |""".stripMargin

    formatter(code)

  private def generateRouter(entityName: String, endpoints: List[EndpointSpec]): String =
    val body =
      if endpoints.nonEmpty then
        val endpointsCode = endpoints.map { ep =>
          val methodName = ep.method.toLowerCase
          val valName    = s"$methodName${ep.response}"
          val reqPart = ep.request match
            case Some(req) if req.nonEmpty => s"\n    .in(jsonBody[$req])"
            case _                         => ""
          val resPart =
            if ep.response.nonEmpty then s"\n    .out(jsonBody[${ep.response}])"
            else ""

          s"""  val $valName = endpoint
             |    .$methodName
             |    .in("${ep.path}")$reqPart$resPart""".stripMargin
        }.mkString("\n\n")

        s"""|
            |import sttp.tapir.*
            |import sttp.tapir.json.jsoniter.*
            |import ${config.basePackage}.models.*
            |
            |object ${entityName}Router:
            |$endpointsCode
            |""".stripMargin
      else
        s"""|
            |object ${entityName}Router
            |""".stripMargin

    val code = s"""package ${config.basePackage}.routes
      |$body""".stripMargin

    formatter(code)


/** Netty Server / Main Class Wiring Generator */
def wire(config: GeneratorConfig = GeneratorConfig.autoDetect()): Either[Throwable, Unit] =

  def findFiles(dir: Path): List[Path] =
    if !Files.exists(dir) then List.empty
    else
      Using.resource(Files.walk(dir)) { stream =>
        stream
          .toScala(LazyList)
          .filter(p => Files.isRegularFile(p) && p.toString.endsWith(".scala"))
          .toList
      }

  // check that some sprout files already exist
  if findFiles(SPROUT_MODELS_PATH).isEmpty ||
     findFiles(SPROUT_ROUTERS_PATH).isEmpty then
      return Left(Exception("No sprout files found. /src/main/scala/sprout package exists?"))

  val code =
    s"""package ${config.basePackage}
       |
       |import sttp.tapir.*
       |import sttp.tapir.server.netty.NettyFutureServer
       |import scala.concurrent.ExecutionContext.Implicits.global
       |import scala.concurrent.{Await, Future}
       |import scala.concurrent.duration.*
       |
       |import ${config.basePackage}.models.*
       |import ${config.basePackage}.routes.*
       |
       |object Main:
       |
       |  val allEndpoints = List[Endpoint[Unit, Unit, Unit, Unit, Any]].empty
       |
       |  def main(args: Array[Array[String]] | Array[String]): Unit =
       |    val program = for
       |      binding <- NettyFutureServer()
       |        .host("0.0.0.0")
       |        .port(8080)
       |        .addEndpoints(allEndpoints)
       |        .start()
       |      _ = println(s"Server started at http://$${binding.hostName}:$${binding.port}/")
       |    yield ()
       |
       |    Await.result(program, Duration.Inf)
       |""".stripMargin

  val formattedCode = SourceFileWriter.formatScalaCode(code)
  Try {
    val targetDir = SPROUT_PACKAGE_PATH
    if !Files.exists(targetDir) then Files.createDirectories(targetDir)
    val fileName = "Server.scala"
    Files.writeString(
      targetDir.resolve(fileName),
      formattedCode,
      StandardOpenOption.CREATE,
      StandardOpenOption.TRUNCATE_EXISTING
    )
  }.toEither.map(_ => ())

object SourceFileWriter:
  private val scalafmt = Scalafmt.create(getClass.getClassLoader)
  private val configFile = Paths.get(".scalafmt.conf")

  def formatScalaCode(code: String): String =
    if Files.exists(configFile) then
      scalafmt.format(configFile, Paths.get("Generated.scala"), code)
    else
      code

  def modelExists(modelName: String, config: GeneratorConfig = GeneratorConfig.autoDetect()): Boolean =
    val modelFileName = s"$modelName.scala"
    val candidatePaths = List(
      config.packageToPath("models").resolve(modelFileName),
      Paths.get("models").resolve(modelFileName)
    )
    if candidatePaths.exists(Files.exists(_)) then true
    else
      val searchDirs = List(config.packageToPath("models"), Paths.get("models"), config.outputRootDir)
        .filter(Files.exists(_))
      searchDirs.exists { dir =>
        Using.resource(Files.walk(dir)) { stream =>
          stream.toScala(LazyList).exists { p =>
            Files.isRegularFile(p) && (
              p.getFileName.toString == modelFileName ||
              (p.toString.endsWith(".scala") && Files.readString(p).contains(s"case class $modelName"))
            )
          }
        }
      }