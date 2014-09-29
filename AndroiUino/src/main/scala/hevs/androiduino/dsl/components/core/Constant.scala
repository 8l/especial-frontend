package hevs.androiduino.dsl.components.core

import hevs.androiduino.dsl.components.fundamentals._

import scala.reflect.runtime.universe._

case class Constant[T <: CType : TypeTag](value: T) extends Component with hw_implemented {

  override val description = "Constant generator"

  val valName = s"cstComp${id}" // unique name

  val out = new OutputPort[T](this) {

    override val description = "The constant value"

    override def getValue: String = valName
  }

  def getOutputs = Some(Seq(out))

  def getInputs = None

  override def toString = super.toString + s", constant `${value}`."

  /**
   * Constant declaration in the C code.
   * @return the constant declaration as boolean
   */
  override def getGlobalConstants = {
    // const bool_t cstComp1 = true;
    Some(s"const ${value.getType} $valName = ${value.asBool};")
  }

  override def getBeginOfMainAfterInit = {
    var result = "// Propagating constants\n"

    for (wire ← out.wires)
      result += wire.to.updateValue(s"$valName") + ";\n"

    Some(result)
  }
}