package hevs.especial.resolver

import hevs.especial.dsl.components.Component
import hevs.especial.generator.Resolver
import hevs.especial.utils.Context
import org.scalatest.{FunSuite, Matchers}

/**
 * Helper methods for the `Resolver` test suite using the `ScalaTest` library.
 *
 * @version 1.0
 * @author Christopher Metrailler (mei@hevs.ch)
 */
abstract class ResolverTestSpec extends FunSuite with Matchers {

  /** Pipeline context */
  var ctx = new Context(this.getClass.getSimpleName)
  var r: Resolver = null

  /**
   * Resolve the current graph and return the result of the resolver.
   * @return the result of the resolver
   */
  def testResolver(testId: Int = 0): Map[Int, Set[Component]] = {

    ctx.log.info(s"Resolver started for 'ResolverCode$testId'.")

    // Create a new logger and a new resolver for each tests
    r = new Resolver
    val res = r.run(ctx)(Unit)

    ctx.log.terminateIfErrors(r)
    ctx.log.info("--\n")
    res
  }
}