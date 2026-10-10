package spec

import cliparsing.Parser.{ValidModelFlags, ValidRouterApiFlags}
import domain.Entity
import org.yaml.snakeyaml.Yaml

import java.io.{FileInputStream, InputStream}
import java.util
import scala.util.Using

/** Parser and Spec generator for yaml files. Class to do IO work with existing schema files.
  * @param args
  *   SchemaArgs to extract Entity type, name either Model/Router and list of arguments
  */

class SchemaParser(private val args: SchemaArgs):

  private val yaml = Yaml()

  private def parseModel(data: util.Map[String, Object]): List[FieldSpec] =
    data.get("modelDef")
      .asInstanceOf[util.List[util.Map[String, Object]]]
      .toArray
      .toList
      .map {
        raw =>
          val field = raw.asInstanceOf[util.Map[String, Object]]
          FieldSpec(
            name = field.get("name").toString,
            valueType = field.get("valueType").toString
          )
      }

  private def parseEndpoints(data: util.Map[String, Object]): Option[List[EndpointSpec]] =
    Option(data.get("endpoints"))
      .map(_.asInstanceOf[util.List[util.Map[String, Object]]])
      .map(_.toArray.toList)
      .map(_.map {
        raw =>
          val endpoint = raw.asInstanceOf[util.Map[String, Object]]
          EndpointSpec(
            path = endpoint.get("path").toString,
            method = endpoint.get("method").toString,
            request = Option(endpoint.get("request")).map(_.toString),
            response = endpoint.get("response").toString
          )
      })

  /** we choose which entity we're processing
    */
  private def createSpec(data: util.Map[String, Object]): SchemaSpec =
    val name = data.get("name").toString
    this.args.entity match
      case Entity.Model =>
        val modelDef = parseModel(data)
        val endpoints = Option.when(args.flags.containsAny(ValidModelFlags.toList*)) {
          parseEndpoints(data)
        }.flatten
        ModelSpec(name, modelDef, endpoints)
      case Entity.Router =>
        val endpointsFromYaml = parseEndpoints(data).getOrElse(Nil)
        val hasApiFlag        = args.flags.containsAny(ValidRouterApiFlags.toList*)
        val endpoints =
          if hasApiFlag then
            List(
              EndpointSpec(name.toLowerCase, "get", None, name),
              EndpointSpec(name.toLowerCase, "post", Some(name), name),
              EndpointSpec(name.toLowerCase, "put", Some(name), name),
              EndpointSpec(name.toLowerCase, "delete", None, name)
            )
          else endpointsFromYaml
        val hasModelGenFlag = args.flags.containsAny("--model")
        val modelSpec = Option.when(hasModelGenFlag) {
          ModelSpec(name, parseModel(data), None)
        }
        RouterSpec(name, endpoints, modelSpec)

  def loadSpec(): SchemaSpec =
    Using.resource(
      new FileInputStream(s"src/main/resources/schemas/${this.args.filename}.yaml")
    )(loadFile)

  private def loadFile(input: InputStream): SchemaSpec =
    val data = yaml.load[util.Map[String, Object]](input)
    createSpec(data)
