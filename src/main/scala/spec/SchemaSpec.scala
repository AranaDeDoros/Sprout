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

case class FieldSpec(name: String, valueType: String)

case class ModelSpec(
  name: String,
  modelDef: List[FieldSpec],
  endpoints: Option[List[EndpointSpec]]
) extends SchemaSpec(name)

case class EndpointSpec(
  path: String,
  method: String,
  request: Option[String],
  response: String
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
