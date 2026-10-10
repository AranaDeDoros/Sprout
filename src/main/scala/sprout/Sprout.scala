package sprout

import cliparsing.Parser
import generators.{BoilerPlateGenerator, GenerationResult}
import domain.Invalid
import generators.report

@main def main(args: String*): Unit =
  val input = if args.nonEmpty then args.toList else List("new:router", "User", "--api", "-m")
  Parser.parseInput(input) match
    case Left(Invalid(reason)) => GenerationResult.failure(reason).report()
    case Right(cmd)            => new BoilerPlateGenerator(cmd).run().report()
