package hevs.especial.dsl.components.core.logic

import hevs.especial.dsl.components._

import scala.collection.mutable
import scala.collection.mutable.ListBuffer

/**
 * Generic logic gate. Deal with Boolean (`bool`) values.
 * Compute different logic operations, on a generic number of input to one output.
 * @param nbrIn The number of generic input of the component
 * @param operator Boolean operator to compute
 */
abstract class AbstractLogic(nbrIn: Int, operator: String) extends GenericCmp[bool, bool](nbrIn,
  1) with HwImplemented with Out1 {

  /* I/O management */

  protected def setInputValue(index: Int, s: String) = s"${inValName(index)} = $s"

  protected def getOutputValue(index: Int) = {
    // Print the boolean operation with all inputs
    // Example: in1_comp2 & in2_comp2 & ...
    val inputs = for (i <- 0 until nbrIn) yield inValName(i)
    inputs.mkString(s" $operator ")
  }

  // Single output connected here
  override val out: OutputPort[bool] = out(0)

  /* Code generation */

  private val tpe = bool().getType

  // FIXME: Output connection must not be checked here.
  override def getGlobalCode = out(0).isConnected match {
    // Input variables declarations for the gate
    // Example: bool_t in1_comp3, in2_comp3, in3_comp3;
    case true =>
      val inputs = for (i <- 0 until nbrIn) yield inValName(i)
      Some(s"$tpe ${inputs.mkString(", ")}; // $this")
    case _ => None
  }

  // FIXME: Output connection must not be checked here.
  override def getLoopableCode = out(0).isConnected match {
    case true =>
      val result: ListBuffer[String] = ListBuffer()

      // Compute the result from the global variables
      result += s"$tpe ${outValName()} = ${out(0).getValue}; // ${out(0)}"

      // Set the output value to connected components
      for (inPort ← ComponentManager.findConnections(out(0)))
        result += inPort.setInputValue(s"${outValName()}") + "; // " + inPort

      Some(result.mkString("\n"))
    case _ => None
  }
}
