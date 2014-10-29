package hevs.especial.generator

import java.io.File

import hevs.especial.dsl.components.ComponentManager
import hevs.especial.dsl.components.ComponentManager.Wire
import hevs.especial.dsl.components.fundamentals.{Component, InputPort, OutputPort, Port}
import hevs.especial.utils._

import scala.language.existentials
import scalax.collection.Graph
import scalax.collection.edge.LDiEdge
import scalax.collection.io.dot._

/**
 * Depending on the Settings, export the DOT and the PDF file.
 * The DOT diagram and the PDF are exported to the "output/<input>/dot" folder.
 */
class DotPipe extends Pipeline[String, Unit] {

  /**
   * Create the DOT and the PDF file that correspond to a DSL program.
   * Files written directly in the output folder (pipeline output not used).
   *
   * @param ctx the context of the program with the logger
   * @param input the name of the program
   * @return nothing (not used)
   */
  def run(ctx: Context)(input: String): Unit = {
    // First block of the pipeline. Force to clean the output folder.
    val folder: RichFile = new File("output/")
    folder.createEmptyFolder()

    if (!Settings.PIPELINE_RUN_DOT) {
      ctx.log.info(s"$currentName is disabled.")
      return
    }

    // Generate the DOT file
    val res = DotGenerator.generateDotFile(input)
    if (!res)
      ctx.log.error("Unable to generate the DOT file !")
    else
      ctx.log.info("DOT file generated.")

    // Generate the PDF file if necessary
    if (Settings.PIPELINE_EXPORT_PDF) {
      // Check the if dot is installed and print the installed version
      val valid = OSUtils.runWithCodeResult("dot -V")
      if (valid._1 == 0)
        ctx.log.info(s"Running '${valid._2}'.")
      else {
        ctx.log.error(s"Unable to run DOT. Must be installed and in the PATH !\n> ${valid._2}")
        return
      }

      val res = DotGenerator.convertDotToPdf(input)
      if (res._1 == 0)
        ctx.log.info("PDF file generated.")
      else
        ctx.log.error(s"Unable to generate the PDF file !\n> ${res._2}")
    }
  }
}

/**
 * Helper object to generate the DOT file, format it correctly and finally convert it to a PDF file.
 */
object DotGenerator {

  /** DOT output path */
  private final val OUTPUT_PATH = "output/%s/dot/"

  /** General dot diagrams settings */
  private final val dotSettings =
    """
      |	// Diagram settings
      |	graph [rankdir=LR labelloc=b, fontname=Arial, fontsize=14];
      |	node [ fontname=serif, fontsize=11, shape=Mrecord];
      |	edge [ fontname=Courier, color=dimgrey fontsize=12 ];
      |
      |	// Exported nodes from the components graph
    """.stripMargin

  private final val dotHeader =
    """// This file was auto-generated by DotGenerator version %s
      |// Visualisation of the '%s' program.""".stripMargin

  /**
   * Generate the dot file and save it.
   * @param progName the name of the program
   * @return true if file generated correctly
   */
  def generateDotFile(progName: String): Boolean = {
    val dot = generateDot(progName)

    // Create the folder if it not exist
    val folder: RichFile = new File(String.format(OUTPUT_PATH, progName))
    if (!folder.createEmptyFolder())
      return false // Error: unable to create the folder

    // Generate the DOT file to the created folder
    val path = String.format(OUTPUT_PATH, progName) + progName + ".dot"
    val f: RichFile = new File(path)
    f.write(dot) // Write succeed or not
  }

  /**
   * Generate the dot file and return it as a String.
   * @param graphName the title to display on the graph
   * @return the dot file as a String
   */
  def generateDot(graphName: String): String = {
    val dot = new DotGenerator(graphName).generateDot()
    // Add static settings by hand after the first line
    val dotLines = dot.split("\\r?\\n\\t", 2)
    dotLines(0) += dotSettings
    // Add the file header
    val header = String.format(dotHeader, Version.getVersion, graphName)
    header + "\n" + dotLines.mkString
  }

  def convertDotToPdf(progName: String): (Int, String) = {
    // Convert the dot file to PDF with the same file name
    val path = String.format(OUTPUT_PATH, progName)
    val dotFile = path + progName + ".dot"
    val pdfFile = path + progName + ".pdf"
    OSUtils.runWithCodeResult(s"dot $dotFile -Tpdf -o $pdfFile")
  }
}

/**
 * Display the graph of the `ComponentManager` on a DOT diagram.
 * Components are the nodes, connected together with ports. The label on the edge describe the type
 * of the connection - from an OutputPort to an InputPort of two components. Unconnected ports are display as "NC".
 * Unconnected components (nodes) are in orange.
 * @param graphName the title to display on the graph
 */
class DotGenerator(val graphName: String) {

  // Basic diagram settings and title
  private final val name = "\"\\n\\nVisualisation of the '" + graphName + "' program.\""
  private final val root = DotRootGraph(directed = true, id = Some("G"), kvList = Seq(DotAttr("label", name)))

  /**
   * Generate the DOT diagram of the `ComponentManager` graph.
   * @return the dot file as a String
   */
  private def generateDot(): String = {
    // Generate the dot diagram and return it as String
    val g = ComponentManager.cpGraph
    g.toDot(root, edgeTransformer,
      hEdgeTransformer = Option(edgeTransformer),
      iNodeTransformer = Option(nodeTrans),
      cNodeTransformer = Option(nodeTrans))
  }

  /**
   * Transform all connected nodes.
   * @param innerNode graph nodes
   * @return the same transformation for all connected nodes of the graph
   */
  private def nodeTrans(innerNode: Graph[Component, LDiEdge]#NodeT):
  Option[(DotGraph, DotNodeStmt)] = {
    val n = innerNode.value.asInstanceOf[Component]

    // The label is something like: {{<in1>in1|<in2>in2}|Cmp[01]|{<out1>out1|<out2>out2}}
    val in = makeLabelList(n.getInputs.getOrElse(Nil))
    val out = makeLabelList(n.getOutputs.getOrElse(Nil))
    val label = s"{{$in}|${nodeName(n)}|{$out}}" // Double '{' are necessary with rankdir=LR !

    // Change the color for unconnected nodes
    val color = if (!n.isConnected) Seq(DotAttr("color", "orange")) else Nil
    Some(root, DotNodeStmt(nodeId(n), Seq(DotAttr("label", label)) ++ color))
  }

  /**
   * Format the name of a Component to display it in a node.
   * @param c Component to display in a node
   * @return the node title value
   */
  private def nodeName(c: Component): String = {
    // Display the component id and description on two lines
    val title = s"Cmp[${c.getId}]"
    val connected = if (c.isConnected) "" else "\\n(NC)"
    s"$title\\n${c.getDescription}$connected"
  }

  /**
   * Return the component ID as String.
   * @param c the Component
   * @return the ID as String
   */
  private def nodeId(c: Component): String = c.getId.toString

  /**
   * Format a list of input or output of a component. Check if it is connected or not and display it.
   * @param l list of input or output of the component
   * @return list formatted for dot record structure
   */
  private def makeLabelList(l: Seq[Port[_]]) = {
    // Return the ID of the port with a label
    l.map(
      x => {
        val id = x.getId
        val nc = if (x.isNotConnected) " (NC)" else ""
        x match {
          case _: InputPort[_] => s"<$id>In[$id]$nc"
          case _: OutputPort[_] => s"<$id>Out[$id]$nc"
        }
      }
    ).mkString("|")
  }

  /**
   * Transform all edges of the graph. Display the wire from an InputPort to an OutputPort of two components.
   * @param innerEdge graph edges
   * @return the same transformation for all edges of the graph
   */
  private def edgeTransformer(innerEdge: Graph[Component, LDiEdge]#EdgeT): Option[(DotGraph, DotEdgeStmt)] = {
    val edge = innerEdge.edge
    val label = edge.label.asInstanceOf[Wire]

    val nodeFrom = edge.from.value.asInstanceOf[Component].getId
    val nodeTo = edge.to.value.asInstanceOf[Component].getId
    val attrs = Seq(DotAttr("label", labelName(label)))
    Some(root, DotEdgeStmt(nodeFrom + ":" + label.from.getId, nodeTo + ":" + label.to.getId, attrs))
  }

  /**
   * Display the type of the connection on thw wire.
   * @param w the wire to display as a edge label
   * @return the type of the connection as a label value
   */
  private def labelName(w: Wire): String = {
    // Something like "hevs.especial.dsl.components.fundamentals.uint1"
    val t = w.from.getType

    // Return the child class (ex: uint1) as String
    t.baseClasses.head.asClass.name.toString
  }
}