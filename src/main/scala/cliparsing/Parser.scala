package cliparsing

import domain.*
import scala.annotation.tailrec
import generators.GenerationResult

object Parser:

  final val ValidModelFlags       = Set("--with-router", "-wr")
  final val ValidRouterApiFlags   = Set("--api", "-a")
  final val ValidRouterModelFlags = Set("--model", "-m")
  final val ValidRouterFlags      = ValidRouterApiFlags ++ ValidRouterModelFlags
  final val ValidForageFlags      = Set.empty[String] // Set("-o", "--overwrite")

  def parseInput(args: List[String]): Either[Invalid, Command] =
    if args.isEmpty then Right(Help)
    else
      args.head match
        case "new:model" =>
          val (opts, flags) = parseArgs(args.tail)
          validate("new:model", opts, flags, ValidModelFlags) {
            Valid(New(Entity.Model), opts, flags)
          }

        case "new:router" =>
          val (opts, flags) = parseArgs(args.tail)
          val hasApi        = flags.flatMap(_.input).exists(ValidRouterApiFlags.contains)
          val hasModel      = flags.flatMap(_.input).exists(ValidRouterModelFlags.contains)
          if hasApi && !hasModel then
            Left(Invalid("Flag(s) '--api/-a' requires '--model' or '-m'."))
          else
            validate("new:router", opts, flags, ValidRouterFlags) {
              Valid(New(Entity.Router), opts, flags)
            }

        case "forage" =>
          val (opts, flags) = parseArgs(args.tail)
          if opts.nonEmpty then
            Left(Invalid("'forage' does not accept positional arguments."))
          else
            validateFlags("forage", flags, ValidForageFlags).map {
              _ =>
                Valid(Forage, Nil, flags)
            }

        case "--help" | "-h" | "" => Right(Help)
        case unknown              => Left(Invalid(s"Unknown command '$unknown'"))

  private def validate(
    cmdName: String,
    opts: List[CLIOption],
    flags: List[Flag],
    allowedFlags: Set[String]
  )(onSuccess: => Command): Either[Invalid, Command] =
    if opts.isEmpty then
      Left(Invalid(s"'$cmdName' requires a <name> argument."))
    else
      validateFlags(cmdName, flags, allowedFlags).map(
        _ => onSuccess
      )

  private def validateFlags(
    cmdName: String,
    flags: List[Flag],
    allowedFlags: Set[String]
  ): Either[Invalid, Unit] =
    val invalidFlags = flags.flatMap(_.input).filterNot(allowedFlags.contains)
    if invalidFlags.nonEmpty then
      Left(Invalid(s"Unrecognized flag(s) for '$cmdName': ${invalidFlags.mkString(", ")}"))
    else Right(())

  private def parseArgs(tokens: List[String]): (List[CLIOption], List[Flag]) =
    @tailrec
    def loop(
      remaining: List[String],
      opts: List[CLIOption],
      flags: List[Flag]
    ): (List[CLIOption], List[Flag]) =
      remaining match
        case Nil => (opts.reverse, flags.reverse)

        case token :: tail if token.startsWith("-") =>
          loop(tail, opts, Flag(token) :: flags)

        case token :: tail =>
          loop(tail, CLIOption(token) :: opts, flags)

    loop(tokens, Nil, Nil)
