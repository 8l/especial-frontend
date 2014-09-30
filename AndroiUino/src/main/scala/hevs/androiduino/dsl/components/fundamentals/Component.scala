package hevs.androiduino.dsl.components.fundamentals

// TODO: un composant est sotcké dans un graph, doit avoir `override def equals` et `override def hashCode`
abstract class Component {

  val id = IdGenerator.newUniqueId // Must be unique

  def getId = id

  // Component description (optional)
  val description: String = ""

  // Create a variable name for the component
  def compVar(varName: String) = s"cmp${id}_$varName"

  ComponentManager.registerComponent(this)

  // List of outputs of the block, if any.
  // The default implementation of a Seq is a List.
  def getOutputs: Option[Seq[OutputPort[_]]]

  // List of inputs of the block, if any.
  // The default implementation of a Seq is a List.
  def getInputs: Option[Seq[InputPort[_]]]

  def getFullDescriptor = {
    toString +
      "\n\tInputs:  " + getInputs.mkString(", ") +
      "\n\tOutputs: " + getOutputs.mkString(", ")
  }

  // For the graph
  override def equals(other: Any) = other match {
    // A component ID must be unique
    case that: Component => that.id == this.id
    case _ => false
  }

  // For the graph
  override def hashCode = id.##

  override def toString = s"Cmp[$id] $description"
}

trait hw_implemented {
  // Code inserted to init the component (executed
  // once in C++)
  def getInitCode: Option[String] = None

  def getBeginOfMainAfterInit: Option[String] = None

  // Code inserted in the main loop of the C++
  def getLoopableCode: Option[String] = None

  // Code inserted in the global section of the C++
  def getGlobalConstants: Option[String] = None

  // Code inserted in for function definitions (pre-main)
  def getFunctionsDefinitions: Option[String] = None

}

//abstract class Button(description: String) extends Component(description)
//class SW_Button(pin: Int) extends Button("A software button")
//class HW_Button(pin: Int) extends Button("An hardware button")

