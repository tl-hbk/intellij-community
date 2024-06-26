// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.vcs.log.graph

import com.intellij.vcs.log.graph.api.EdgeFilter
import com.intellij.vcs.log.graph.api.LinearGraph
import com.intellij.vcs.log.graph.api.elements.GraphEdge
import com.intellij.vcs.log.graph.api.elements.GraphElement
import com.intellij.vcs.log.graph.api.elements.GraphNode
import com.intellij.vcs.log.graph.api.printer.GraphPrintElement
import com.intellij.vcs.log.graph.api.printer.PrintElementGenerator
import com.intellij.vcs.log.graph.parser.CommitParser
import com.intellij.vcs.log.graph.parser.EdgeNodeCharConverter.toChar

fun LinearGraph.asString(sorted: Boolean = false): String {
  val s = StringBuilder()
  for (nodeIndex in 0 until nodesCount()) {
    if (nodeIndex > 0) s.append("\n")
    val node = getGraphNode(nodeIndex)
    s.append(node.asString()).append(CommitParser.SEPARATOR)

    var adjEdges = getAdjacentEdges(nodeIndex, EdgeFilter.ALL)
    if (sorted) {
      adjEdges = adjEdges.sortedWith(GraphStrUtils.GRAPH_ELEMENT_COMPARATOR)
    }
    adjEdges.joinTo(s, separator = " ") { it.asString() }
  }
  return s.toString()
}

fun GraphNode.asString(): String = "${nodeIndex}_${toChar(type)}"

fun Int?.asString() = this?.toString() ?: "n"

fun GraphEdge.asString(): String = "${upNodeIndex.asString()}:${downNodeIndex.asString()}:${targetId.asString()}_${toChar(type)}"

fun GraphElement.asString(): String = when (this) {
  is GraphNode -> asString()
  is GraphEdge -> asString()
  else -> throw IllegalArgumentException("Unknown type of PrintElement: $this")
}

fun GraphPrintElement.asString(): String {
  val element = graphElement.asString()

  val row = rowIndex
  val color = colorId
  val pos = positionInCurrentRow
  val sel = if (isSelected) "Select" else "Unselect"
  return when (this) {
    is NodePrintElement -> {
      "Node|-$row:${pos}|-$color:${sel}($element)"
    }
    is EdgePrintElement -> {
      val t = type
      val ls = lineStyle
      val posO = positionInOtherRow
      val arrow = if (hasArrow()) "_ARROW" else ""
      "Edge:$t${arrow}:${ls}|-$row:$pos:${posO}|-$color:$sel($element)"
    }

    else -> {
      throw IllegalStateException("Uncown type of PrintElement: $this")
    }
  }
}

fun PrintElementGenerator.asString(size: Int): String {
  val s = StringBuilder()

  for (row in 0 until size) {
    if (row > 0) s.append("\n")
    val elements = getPrintElements(row).sortedBy {
      val pos = it.positionInCurrentRow
      when (it) {
        is NodePrintElement -> 1024 * pos
        is EdgePrintElement -> 1024 * pos + (it.type.ordinal + 1) * 64 + it.positionInOtherRow
        else -> 0
      }
    }
    elements.joinTo(s, separator = "\n  ") { it.asString() }
  }

  return s.toString()
}