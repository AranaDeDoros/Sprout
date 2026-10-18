package spec

import domain.*

/** Classes hierarchy for Spec to represent the files to be generated into code
  */

case class SchemaArgs(
  entity: Entity,
  filename: String,
  flags: List[Flag]
)

sealed trait SchemaSpec(name: String)

/** A single field in a model definition.
  * @param name      validated camelCase identifier (e.g. "userId")
  * @param valueType validated Scala type name (e.g. "Long", "List[String]")
  */
case class FieldSpec(name: FieldName, valueType: TypeName)

case class ModelSpec(
  name: String,
  modelDef: List[FieldSpec],
  endpoints: Option[List[EndpointSpec]]
) extends SchemaSpec(name)

/** A single REST endpoint.
  * @param path     validated non-empty URL path segment
  * @param method   typed HTTP verb
  * @param request  optional request body type
  * @param response optional response body type (None = no body)
  */
case class EndpointSpec(
  path: EndpointPath,
  method: HttpMethod,
  request: Option[TypeName],
  response: Option[TypeName]
)

case class RouterSpec(
  name: String,
  endpoints: List[EndpointSpec],
  model: Option[ModelSpec]
) extends SchemaSpec(name)

// extension to check for specific flags, will later be either deprecated or removed in favour of making flags typed
extension (flags: List[Flag])
  def containsAny(aliases: String*): Boolean =
    val aliasSet = aliases.toSet
    flags.exists(
      f => f.input.exists(aliasSet.contains)
    )
