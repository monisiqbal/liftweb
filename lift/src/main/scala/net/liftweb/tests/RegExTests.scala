package net.liftweb.tests

/*                                                *\
  (c) 2007 WorldWide Conferencing, LLC
  Distributed under an Apache License
  http://www.apache.org/licenses/LICENSE-2.0
\*                                                 */
  
import scala.testing.SUnit
import SUnit._

import net.liftweb.util.RE
import net.liftweb.util.RE._

class RegExTests extends TestCase("RegExTests") {
  
  override def runTest() {
    testRegEx
    testRegEx2
    testCapture
  }
  
  def testRegEx() {
    assert(RE("moo") =~ "I like to say moo", "basic test")
  }
  
  def testRegEx2() {
    assert("I like to say moo" =~: RE("moo") , "basic test")
  }
  
  def testCapture {
    assert(("1 2 3 a b c 5" =~: RE("([0-9])")).capture.toList.length == 4, "Do we capture 4 numbers?")
  }
  
}