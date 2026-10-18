package spec

/** Typed errors produced during YAML parsing and spec validation.
  */
enum SchemaError:
  case Missing(path: String)
  case WrongType(path: String, expected: String)
  case UnknownMethod(s: String)
  case InvalidType(s: String)
  case InvalidField(s: String)
  case InvalidPath(s: String)

  def message: String = this match
    case Missing(path)          => s"Missing required field: '$path'"
    case WrongType(path, exp)   => s"Field '$path' must be a $exp"
    case UnknownMethod(s)       => s"Unknown HTTP method: '$s'"
    case InvalidType(s)         => s"Invalid type name: '$s'"
    case InvalidField(s)        => s"Invalid field name: '$s'"
    case InvalidPath(s)         => s"Invalid endpoint path: '$s'"
