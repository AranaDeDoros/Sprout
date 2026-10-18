package writers

/** Represents the two output layers produced by the generator.
  *
  * Replaces the `subPackage: String` parameter and the `subPackage == "models"` string comparison
  * in [[SourceFileWriter]].  Adding a new layer here will surface as a compiler exhaustiveness error
  * everywhere a `Layer` is pattern-matched, so nothing can be silently forgotten.
  */
enum Layer:
  case Models, Routes

  /** Sub-package name relative to the configured base package. */
  def dir: String = this match
    case Models => "models"
    case Routes => "routes"

  /** The output filename for the given spec name. */
  def fileName(specName: String): String = this match
    case Models => s"$specName.scala"
    case Routes => s"${specName}Router.scala"

/** A single generated source file, combining the target layer with the formatted content. */
case class GeneratedFile(layer: Layer, content: String)
