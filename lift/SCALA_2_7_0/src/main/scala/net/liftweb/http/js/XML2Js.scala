package net.liftweb.http.js

/*                                                *\
(c) 2007 WorldWide Conferencing, LLC
Distributed under an Apache License
http://www.apache.org/licenses/LICENSE-2.0
\*                                                 */

import scala.xml._
import net.liftweb.util._
import Helpers._

import JE._
import JsCmds._

trait JxYieldFunc {
  this: JxBase =>
  def yieldFunction: JsExp
}

trait JxBase {
  self: Node =>
  
  // def ++(other: Node): Seq[Node] = this :: other :: Nil
  
  def appendToParent(parentName: String): JsCmd
  
  def label = throw new UnsupportedOperationException("Xml2Js does not have a label")
  
  def addAttrs(varName: String, attrs: List[MetaData]): JsCmd = attrs.map {
    m =>
    m.value.map{
      case exp: JsExp => 
      JsRaw( varName+"."+m.key+" = "+exp.toJsCmd).cmd
      
      case cmd: JsCmd => val varName = "v"+randomString(20)
      JsCrVar(varName, AnonFunc(cmd)) &
      JsRaw(varName+"."+m.key+" = "+varName+"()")
      
      case JxAttr(cmd) => 
      JsRaw(varName+"."+m.key+" = "+ cmd.toJsCmd).cmd
      
      case JxFuncAttr(cmd) => 
      JsRaw(varName+"."+m.key+" = "+ AnonFunc(cmd).toJsCmd).cmd
      
      case x => 
      JsRaw(varName+".setAttribute("+m.key.encJs+","+x.text.encJs+");").cmd
    }.foldLeft(Noop)(_ & _)
  }.foldLeft(Noop)(_ & _)
  
  def addToDocFrag(parent: String, elems: List[Node]): JsCmd = elems.map{
    case Jx(kids) => addToDocFrag(parent, kids.toList)
    case jb: JxBase => jb.appendToParent(parent)      
    case Group(nodes) => addToDocFrag(parent, nodes.toList)
    case Text(txt) => JsRaw(parent+".appendChild(document.createTextNode("+txt.encJs+"));").cmd
    case a: Atom[_] => JsRaw(parent+".appendChild(document.createTextNode("+a.text.encJs+"));").cmd
    case e: scala.xml.Elem =>
    val varName = "v"+randomString(10)
    JsCrVar(varName, JsRaw("document.createElement("+e.label.encJs+")")) &
    addAttrs(varName, e.attributes.toList) &
    JsRaw(parent+".appendChild("+varName+")") &    
    addToDocFrag(varName, e.child.toList)
    case ns: Seq[Node] =>
    if (ns.length == 0) Noop
    else if (ns.length == 1) {
      Log.error("In addToDocFrag, got a "+ns+" of type "+ns.getClass.getName)
      Noop
    } else addToDocFrag(parent, ns.toList)

  }.foldLeft(Noop)(_ & _)
}


abstract class JxNodeBase extends Node with JxBase {
  
}

/*
case class JxExp(in: JsExp) extends Node with JxBase {
  def child = Nil
  
  def appendToParent(parentName: String): JsCmd = {
    val ran = "v"+randomString(10)
    JsCrVar(ran, in) ++
    JsRaw("if ("+ran+".nodeType) {"+parentName+".appendChild("+ran+".cloneNode(true));} else {"+
    parentName+".appendChild(document.createTextNode("+ran+"));}")
  }
}*/


case class JxAttr(in: JsCmd) extends Node with JxBase {
  def child = Nil
  
  def appendToParent(parentName: String): JsCmd = {
    Noop
  }
}

case class JxFuncAttr(in: JsCmd) extends Node with JxBase {
  def child = Nil
  
  def appendToParent(parentName: String): JsCmd = {
    Noop
  }
}

case class JxMap(in: JsExp, what: JxYieldFunc) extends Node with JxBase {
  def child = Nil
  
  def appendToParent(parentName: String): JsCmd = {
    val ran = "v"+randomString(10)
    val fr = "f"+randomString(10)
    val cr = "c"+randomString(10)
    JsCrVar(ran, in) &
    JsCrVar(fr, what.yieldFunction) &
    JsRaw("for ("+cr+" = 0; "+cr+" < "+ran+".length; "+cr+"++) {"+
    parentName+".appendChild("+fr+"("+ran+"["+cr+"]));"+
    "}")
  }
}

case class JxCmd(in: JsCmd) extends Node with JxBase {
  def child = Nil
  
  def appendToParent(parentName: String) = in
}

case class JxMatch(exp: JsExp, cases: JxCase*) extends Node with JxBase {
  def child = Nil
  
  def appendToParent(parentName: String): JsCmd = {
    val vn = "v" + randomString(10)
    JsCrVar(vn, exp) &
    JsRaw("if (false) {\n} "+
    cases.map{c =>
      " else if ("+vn+" == "+c.toMatch.toJsCmd+") {"+
      addToDocFrag(parentName, c.toDo.toList).toJsCmd+
      "\n}"
    }.mkString("")+
    " else {throw new Expception('Unmatched: '+"+vn+");}")
  }
}

case class JxCase(toMatch: JsExp, toDo: NodeSeq)

case class JxIf(toTest: JsExp, ifTrue: NodeSeq) extends Node with JxBase {
  def child = Nil
  def appendToParent(parentName: String): JsCmd = {
    JsRaw("if ("+toTest.toJsCmd+") {\n"+
    addToDocFrag(parentName, ifTrue.toList).toJsCmd+
    "}\n")
  }
}

case class JxIfElse(toTest: JsExp, ifTrue: NodeSeq, ifFalse: NodeSeq) extends Node with JxBase {
  def child = Nil
  def appendToParent(parentName: String): JsCmd = {
    JsRaw("if ("+toTest.toJsCmd+") {\n"+
    addToDocFrag(parentName, ifTrue.toList).toJsCmd+
    "} else {\n" +
    addToDocFrag(parentName, ifFalse.toList).toJsCmd+
    "}\n")
  }
}



case class Jx(child: NodeSeq) extends Node with JxBase with JxYieldFunc {
  
  def appendToParent(parentName: String): JsCmd = 
  addToDocFrag(parentName,child.toList)
  
  def yieldFunction: JsExp = toJs
  
  def toJs: JsExp = AnonFunc("it", 
  JsCrVar("df", JsRaw("document.createDocumentFragment()")) &
  addToDocFrag("df", child.toList) &
  JsRaw("return df"))
  
  
}
/*
case class Jx(child: NodeSeq) extends Node {
def label = throw new UnsupportedOperationException("Xml2Js does not have a label")

def toJs = 
"""function(in) {
var df = document.createDocumentFragment();
"""+
addToDocFrag("df", child.toList)+
"""
return df;
}"""

private def addAttrs(varName: String, attrs: List[MetaData]): List[String] = attrs.flatMap {
case up: UnprefixedAttribute => List(varName+".setAttribute("+up.key.encJs+","+up.value.text.encJs+");")
case pe: PrefixedAttribute if pe.pre == "l" =>  List(varName+".setAttribute("+pe.key.encJs+","+pe.value.text+");")
case _ => Nil
}

private def addToDocFrag(parent: String, elems: List[Node]): List[String] = elems.flatMap{
case Group(nodes) => addToDocFrag(parent, nodes.toList)
case Text(txt) => List(parent+".appendChild(document.createTextNode("+txt.encJs+"));")
case Elem(prefix, label, attrs :_ , _, kids :_) =>
val varName = "v"+randomString(10)
"var "+varName+" = document.createElement("+label.encJs+");" :: parent+".appendChild("+varName+");" ::
addAttrs(varName, attrs.toList) :::
addToDocFrag(varName, kids.toList)
case Xml2Js(kids) => addToDocFrag(parent, kids.toList)
case _ => Nil
}
}*/