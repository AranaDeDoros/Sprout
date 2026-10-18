package spec

import cliparsing.Parser.{ValidModelFlags, ValidRouterApiFlags}
import domain.Entity
import org.yaml.snakeyaml.Yaml
import spec.YamlOps.*

import java.io.{FileInputStream, InputStream}
import java.util
import scala.util.Using

/** Parser and Spec generator for yaml files. Class to do IO work with existing schema files.
  * @param args
  *   SchemaArgs to extract Entity type, name either Model/Router and list of arguments
  */

class SchemaParser(private val args: SchemaArgs):

  private val yaml = Yaml()

  private def parseField(raw: util.Map[String, Object]): Either[SchemaError, FieldSpec] =
    for
      n <- raw.str("name").flatMap(FieldName.from)
      t <- raw.str("valueType").flatMap(TypeName.from)
    yield FieldSpec(n, t)

  private def parseEndpoint(raw: util.Map[String, Object]): Either[SchemaError, EndpointSpec] =
    for
      path <- raw.str("path").flatMap(EndpointPath.from)
      m    <- raw.str("method").flatMap(HttpMethod.parse)
      req  <- raw.optStr("request").flatMap {
                case None    => Right(None)
                case Some(s) => TypeName.from(s).map(Some(_))
              }
      res  <- raw.optStr("response").flatMap {
                case None    => Right(None)
                case Some(s) => TypeName.from(s).map(Some(_))
              }
    yield EndpointSpec(path, m, req, res)

  private def parseModel(data: util.Map[String, Object]): Either[SchemaError, List[FieldSpec]] =
    data.objList("modelDef").flatMap { rows =>
      rows.foldLeft[Either[SchemaError, List[FieldSpec]]](Right(Nil)) { (acc, row) =>
        for
          fields <- acc
          field  <- parseField(row)
        yield fields :+ field
      }
    }

  private def parseEndpoints(data: util.Map[String, Object]): Either[SchemaError, Option[List[EndpointSpec]]] =
    data.optObjList("endpoints").flatMap {
      case Nil  => Right(None)
      case rows =>
        rows.foldLeft[Either[SchemaError, List[EndpointSpec]]](Right(Nil)) { (acc, row) =>
          for
            eps <- acc
            ep  <- parseEndpoint(row)
          yield eps :+ ep
        }.map(Some(_))
    }

  /** We choose which entity we're processing */
  private def createSpec(data: util.Map[String, Object]): Either[SchemaError, SchemaSpec] =
    val name = data.get("name").toString
    this.args.entity match
      case Entity.Model =>
        for
          modelDef  <- parseModel(data)
          endpoints <- Option
                         .when(args.flags.containsAny(ValidModelFlags.toList*))(parseEndpoints(data))
                         .getOrElse(Right(None))
        yield ModelSpec(name, modelDef, endpoints)

      case Entity.Router =>
        val hasApiFlag = args.flags.containsAny(ValidRouterApiFlags.toList*)
        for
          endpointsFromYaml <- parseEndpoints(data)
          endpoints =
            if hasApiFlag then
              // Construct the four standard CRUD endpoints using the validated ADT constructors.
              // These are known-good values so we call .getOrElse(???), but use Right-biased
              // validation to keep it safe.
              val nameLower = name.toLowerCase
              List(
                EndpointSpec(EndpointPath.from(nameLower).getOrElse(???), HttpMethod.Get,    None,               TypeName.from(name).toOption),
                EndpointSpec(EndpointPath.from(nameLower).getOrElse(???), HttpMethod.Post,   TypeName.from(name).toOption, TypeName.from(name).toOption),
                EndpointSpec(EndpointPath.from(nameLower).getOrElse(???), HttpMethod.Put,    TypeName.from(name).toOption, TypeName.from(name).toOption),
                EndpointSpec(EndpointPath.from(nameLower).getOrElse(???), HttpMethod.Delete, None,               TypeName.from(name).toOption)
              )
            else endpointsFromYaml.getOrElse(Nil)
          hasModelGenFlag = args.flags.containsAny("--model")
          modelSpec <-
            if hasModelGenFlag then parseModel(data).map(fields => Some(ModelSpec(name, fields, None)))
            else Right(None)
        yield RouterSpec(name, endpoints, modelSpec)

  def loadSpec(): Either[SchemaError, SchemaSpec] =
    Using.resource(
      new FileInputStream(s"src/main/resources/schemas/${this.args.filename}.yaml")
    )(loadFile)

  private def loadFile(input: java.io.InputStream): Either[SchemaError, SchemaSpec] =
    val data = yaml.load[util.Map[String, Object]](input)
    createSpec(data)
