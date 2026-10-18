package spec

import java.util
import scala.jdk.CollectionConverters.*

/** Safe typed accessors on top of SnakeYAML's untyped `Map[String, Object]` output.
  */
object YamlOps:

  extension (m: util.Map[String, Object])

    /** Read a required String field. */
    def str(key: String): Either[SchemaError, String] =
      Option(m.get(key)) match
        case Some(s: String) => Right(s)
        case Some(_)         => Left(SchemaError.WrongType(key, "string"))
        case None            => Left(SchemaError.Missing(key))

    /** Read an optional String field. */
    def optStr(key: String): Either[SchemaError, Option[String]] =
      Option(m.get(key)) match
        case None            => Right(None)
        case Some(s: String) => Right(Some(s))
        case Some(_)         => Left(SchemaError.WrongType(key, "string"))

    /** Read a list of nested objects (maps). */
    def objList(key: String): Either[SchemaError, List[util.Map[String, Object]]] =
      Option(m.get(key)) match
        case None => Left(SchemaError.Missing(key))
        case Some(l: util.List[?]) =>
          l.asScala.toList.zipWithIndex
            .foldLeft[Either[SchemaError, List[util.Map[String, Object]]]](Right(Nil)) {
              case (Left(err), _) => Left(err)
              case (Right(acc), (e: util.Map[?, ?], _)) =>
                Right(acc :+ e.asInstanceOf[util.Map[String, Object]])
              case (_, (_, i)) =>
                Left(SchemaError.WrongType(s"$key[$i]", "object"))
            }
        case Some(_) => Left(SchemaError.WrongType(key, "list"))

    /** Like `objList` but returns `Right(Nil)` when the key is absent. */
    def optObjList(key: String): Either[SchemaError, List[util.Map[String, Object]]] =
      Option(m.get(key)) match
        case None => Right(Nil)
        case _    => m.objList(key)
