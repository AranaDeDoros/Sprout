package generators

import domain.*

/** BoilerplateGenerator Class to act as a bridge between the CLI layer and the generate methods belonging to each Model
  */

opaque type Result  = String
opaque type Message = String

object Result:
  def apply(s: String): Result = s

object Message:
  def apply(s: String): Message = s

/** Generic type alias to represent the result of code generation as it is now, both just take a String
  */
type GenerationResult = Either[Message, Result]

object GenerationResult:
  def success(message: String): GenerationResult =
    Right(Result(message))

  def failure(message: String): GenerationResult =
    Left(Message(message))

extension (result: GenerationResult)
  /** prints the result and terminates with a non-zero exit code on failure. */
  def report(): Unit =
    result match
      case Right(msg) => println(msg)
      case Left(err) =>
        System.err.println(s"Error: $err")
        sys.exit(1)

class BoilerPlateGenerator(private val command: Command):

  def run(): GenerationResult =
    this.command match
      case Valid(subcommand, opts, args) => subcommand.generate(opts, args)
      case Invalid(reason)               => GenerationResult.failure(reason)
      case Help                          => Right(Result(Help.helpMessage))
