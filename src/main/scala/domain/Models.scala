package domain

import generators.{GenerationResult, Message}
import spec.*
import writers.{wire, SourceFileWriter}
import scala.util.*

/** The root of all commands, or just the name of the binary, if you may.
  */
private val RootCommand = "sprout"

sealed trait Generatable:
  def generate(opts: List[CLIOption], args: List[Flag]): GenerationResult

/** Enum of both current Models available: Model and Router
  */
enum Entity extends Generatable:
  case Model, Router

  override def toString: String = productPrefix.toLowerCase

  /** Generation entrypoint for code generation.
    * @param args
    *   list of flags
    * @return
    *   A GenerationResult wrapping a Success/Failure message
    */
  def generate(opts: List[CLIOption], args: List[Flag]): GenerationResult =
    makeArgs(this, opts, args).flatMap(mapResult)

  /** Attempts to create a SchemArgs
    * @param e
    *   Entity to base the args on
    * @param args
    *   List of flags.
    * @return
    *   A SchemaArgs if successful otherwise, an error Message
    */
  private def makeArgs(e: Entity, opts: List[CLIOption], args: List[Flag]): Either[Message, SchemaArgs] =
    opts match
      case domain.CLIOption(m) :: rest =>
        if m.isBlank then Left(Message("Not enough arguments"))
        else Right(SchemaArgs(e, m, args))
      case _ =>
        Left(Message("Invalid or missing Option argument"))

  /** default mapper function
    */
  final private val mapResult: SchemaArgs => GenerationResult = specArgs =>
    if specArgs.entity == Entity.Router && specArgs.flags.containsAny("-m", "--model") then
      val modelFound = SourceFileWriter.modelExists(specArgs.filename)
      if specArgs.flags.containsAny("-m") && !specArgs.flags.containsAny("--model") && !modelFound then
        GenerationResult.failure(s"Model '${specArgs.filename}' not found in sprouted generated model files")
      else
        processSpec(specArgs)
    else
      processSpec(specArgs)

  private def processSpec(specArgs: SchemaArgs): GenerationResult =
    val spc = new SchemaParser(specArgs).loadSpec()
    val sfw = new SourceFileWriter(spc)
    sfw.generateAndWrite() match
      case Right(()) =>
        GenerationResult.success(s"Successfully generated and wrote files for : ${specArgs.filename}")
      case Left(error) =>
        GenerationResult.failure(s"Failed to process ${specArgs.filename}: ${error.getMessage}")

/** Subcommand trait for root operations: New (create) or Forage (bootstrap)
  */
sealed trait Subcommand:
  def generate(opts: List[CLIOption], args: List[Flag]): GenerationResult

// new model or router
case class New(ent: Entity) extends Subcommand:
  export ent.generate
  override def toString: String = s"new:$ent"

case object Forage extends Subcommand:
  override def generate(opts: List[CLIOption], args: List[Flag] = Nil): GenerationResult =
    wire() match {
      case Right(()) => GenerationResult.success("Sprout code wired successfully.")
      case Left(err) => GenerationResult.failure(s"An error occurred: ${err.getMessage}")
    }

  override def toString: String = productPrefix.toLowerCase

/** Base trait for Argument hierarchy.
  */
sealed trait Argument:
  def input: Seq[String]
  override def toString: String = input.mkString(" ")

case class CLIOption(input: String*) extends Argument
case class Flag(input: String*)      extends Argument

/** Base trait for commands; a command is composed of a subcommand a list of Arguments which in turn is composed of
  * either a CLIOption or a Flag
  */
sealed trait Command

case class Valid(sub: Subcommand, option: List[CLIOption], args: List[Flag]) extends Command:
  override def toString: String =
    if args.isEmpty then s"$RootCommand $sub"
    else s"$RootCommand $sub ${args.mkString(" ")}"

case class Invalid(reason: String) extends Command:
  override def toString: String = s"Error: $reason\n\n$Help"

case object Help extends Command:
  val helpMessage: String =
    """Usage: sprout <subcommand> [options/flags]
      |  new:model <name> [--with-router | -wr]
      |  new:router <name> [[--api | -a], [--model | -m]]
      |  forage [-y]
      |""".stripMargin

  override def toString: String = helpMessage
