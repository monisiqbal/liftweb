package net.liftweb.mapper

/*
 * Copyright 2006-2009 WorldWide Conferencing, LLC
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions
 * and limitations under the License.
 */

import _root_.java.sql.{ResultSet, Types}
import _root_.java.lang.reflect.Method
import _root_.net.liftweb.util.{FatLazy, Box, Full, Empty, Failure}
import _root_.java.util.Date
import _root_.java.util.regex._
import _root_.scala.xml.{NodeSeq, Text}
import _root_.net.liftweb.http.{S, FieldError}
import _root_.net.liftweb.http.js._
import S._

/**
  * Just like MappedString, except it's defaultValue is "" and the length is auto-cropped to
  * fit in the column
  */
class MappedPoliteString[T <: Mapper[T]](towner: T, theMaxLen: Int) extends MappedString[T](towner, theMaxLen) {
  override def defaultValue = ""
  override protected def setFilter = crop _ :: super.setFilter
}

class MappedString[T<:Mapper[T]](val fieldOwner: T,val maxLen: Int) extends MappedField[String, T] {
  private val data: FatLazy[String] =  FatLazy(defaultValue) // defaultValue
  private val orgData: FatLazy[String] =  FatLazy(defaultValue) // defaultValue

  def dbFieldClass = classOf[String]

  final def crop(in: String): String = in.substring(0, Math.min(in.length, maxLen))

  final def removeRegExChars(regEx: String)(in: String): String = in.replaceAll(regEx, "")

  final def toLower(in: String): String = in match {
    case null => null
    case s => s.toLowerCase
  }
  final def toUpper(in: String): String = in match {
    case null => null
    case s => s.toUpperCase
  }

  final def trim(in: String): String = in match {
    case null => null
    case s => s.trim
  }

  final def notNull(in: String): String = in match {
    case null => ""
    case s => s
  }


  protected def real_i_set_!(value : String) : String = {
    if (!data.defined_? || value != data.get) {
      data() = value
      this.dirty_?( true)
    }
    data.get
  }

  /**
  * Get the JDBC SQL Type for this field
  */
  def targetSQLType = Types.VARCHAR

  def defaultValue = ""

  override def writePermission_? = true
  override def readPermission_? = true

  protected def i_is_! = data.get
  protected def i_was_! = orgData.get

  /**
     * Called after the field is saved to the database
     */
  override protected[mapper] def doneWithSave() {
    orgData.setFrom(data)
  }

  override def _toForm: Box[NodeSeq] =
  fmapFunc({s: List[String] => this.setFromAny(s)}){name =>
    Full(<input type='text' id={fieldId} maxlength={maxLen.toString}
	 name={name}
	 value={is match {case null => "" case s => s.toString}}/>)}

  protected def i_obscure_!(in : String) : String = {
    ""
  }

  override def setFromAny(in: Any): String = {
    in match {
      case seq: Seq[_] if !seq.isEmpty => seq.map(setFromAny)(0)
      case (s: String) :: _ => this.set(s)
      case null => this.set(null)
      case s: String => this.set(s)
      case Some(s: String) => this.set(s)
      case Full(s: String) => this.set(s)
      case None | Empty | Failure(_, _, _) => this.set(null)
      case o => this.set(o.toString)
    }
  }


  def apply(ov: Box[String]): T = {
    ov.foreach(v => this.set(v))
    fieldOwner
  }

  def asJsExp: JsExp = JE.Str(is)

  def apply(ov: String): T = apply(Full(ov))

  def jdbcFriendly(field : String): String = data.get

  def real_convertToJDBCFriendly(value: String): Object = value

  private def wholeSet(in: String) {
    this.data() = in
    this.orgData() = in
  }

  def buildSetActualValue(accessor: Method, inst: AnyRef, columnName: String): (T, AnyRef) => Unit =
    (inst, v) => doField(inst, accessor, {case f: MappedString[T] => f.wholeSet(if (v eq null) null else v.toString)})

  def buildSetLongValue(accessor: Method, columnName: String): (T, Long, Boolean) => Unit =
    (inst, v, isNull) => doField(inst, accessor, {case f: MappedString[T] => f.wholeSet(if (isNull) null else v.toString)})

  def buildSetStringValue(accessor: Method, columnName: String): (T, String) => Unit =
    (inst, v) => doField(inst, accessor, {case f: MappedString[T] => f.wholeSet(if (v eq null) null else v)})

  def buildSetDateValue(accessor: Method, columnName: String): (T, Date) => Unit =
    (inst, v) => doField(inst, accessor, {case f: MappedString[T] => f.wholeSet(if (v eq null) null else v.toString)})

  def buildSetBooleanValue(accessor: Method, columnName: String): (T, Boolean, Boolean) => Unit =
    (inst, v, isNull) => doField(inst, accessor, {case f: MappedString[T] => f.wholeSet(if (isNull) null else v.toString)})

  /**
   * A validation helper.  Make sure the string is at least a particular
   * length and generate a validation issue if not
   */
  def valMinLen(len: Int, msg: => String)(value: String): List[FieldError] =
    if ((value eq null) || value.length < len) List(FieldError(this, Text(msg)))
    else Nil

  /**
   * A validation helper.  Make sure the string is no more than a particular
   * length and generate a validation issue if not
   */
  def valMaxLen(len: Int, msg: => String)(value: String): List[FieldError] =
    if ((value ne null) && value.length > len) List(FieldError(this, Text(msg)))
    else Nil

  /**
   * Make sure that the field is unique in the database
   */
  def valUnique(msg: => String)(value: String): List[FieldError] =
    fieldOwner.getSingleton.findAll(By(this,value)).
      filter(!_.comparePrimaryKeys(this.fieldOwner)).
      map(x =>FieldError(this, Text(msg)))

  /**
   * Make sure the field matches a regular expression
   */
  def valRegex(pat: Pattern, msg: => String)(value: String): List[FieldError] = pat.matcher(value).matches match {
    case true => Nil
    case false => List(FieldError(this, Text(msg)))
  }

  /**
   * Given the driver type, return the string required to create the column in the database
   */
  def fieldCreatorString(dbType: DriverType, colName: String): String = colName+" VARCHAR("+maxLen+")"

}
