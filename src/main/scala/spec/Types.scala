package spec

/** Typed HTTP method replacing raw strings in EndpointSpec.
  */
enum HttpMethod:
  case Get, Post, Put, Delete

object HttpMethod:
  def parse(s: String): Either[SchemaError, HttpMethod] =
    s.toLowerCase match
      case "get"    => Right(Get)
      case "post"   => Right(Post)
      case "put"    => Right(Put)
      case "delete" => Right(Delete)
      case other    => Left(SchemaError.UnknownMethod(other))

  extension (m: HttpMethod) def tapirName: String = m.toString.toLowerCase

/** Validated Scala type identifier, e.g. "User", "Long", "List[String]".
  * Cannot be constructed without going through `TypeName.from`.
  */
opaque type TypeName = String
object TypeName:
  private val Valid = """[A-Z][A-Za-z0-9_]*(\[.+\])?""".r
  def from(s: String): Either[SchemaError, TypeName] =
    Either.cond(Valid.matches(s), s, SchemaError.InvalidType(s))
  extension (t: TypeName) def value: String = t

/** Validated camelCase field name, e.g. "id", "userName".
  * Cannot be constructed without going through `FieldName.from`.
  */
opaque type FieldName = String
object FieldName:
  private val Valid = """[a-z][A-Za-z0-9_]*""".r
  def from(s: String): Either[SchemaError, FieldName] =
    Either.cond(Valid.matches(s), s, SchemaError.InvalidField(s))
  extension (f: FieldName) def value: String = f

/** Validated URL path segment, e.g. "users", "orders/items".
  * Cannot be constructed without going through `EndpointPath.from`.
  */
opaque type EndpointPath = String
object EndpointPath:
  def from(s: String): Either[SchemaError, EndpointPath] =
    Either.cond(s.nonEmpty && !s.contains(' '), s, SchemaError.InvalidPath(s))
  extension (p: EndpointPath) def value: String = p
