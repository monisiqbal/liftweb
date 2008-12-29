package net.liftweb.util

/*
 * Copyright 2006-2008 WorldWide Conferencing, LLC
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at http://www.apache.org/licenses/LICENSE-2.0
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */


import _root_.java.lang.reflect.{Method, Modifier, InvocationTargetException}
import _root_.java.lang.reflect.Modifier._
import _root_.scala.reflect.Manifest

object ClassHelpers extends ClassHelpers with ControlHelpers

/**
 * ClassHelpers provide several functions to instantiate a Class object given the class name and one or more package names
 */
trait ClassHelpers { self: ControlHelpers =>

  private val nameModifiers = List[String => String](camelCase _, n => n)

  /**
   * utility function returning varargs as a List
   * @return the list of arguments passed as varargs
   */
  def ^ [T](i: T*): List[T] = i.toList


  /**
   * General method to in find a class according to its name, a list of possible packages,
   * a list of functions modifying the given name create a target name to look for
   * (e.g: 'name' is hello_world and the target name may be 'HelloWorld').
   *
   * @parameter name name of the class to find
   * @parameter where list of package names which may contain the class
   * @parameter modifiers list of functions that modify the 'name' of the class (e.g., leave it alone, make it camel case, etc.)
   * @parameter targetType optional expected type which the retrieved class should conform to
   *
   * @return a Box, either containing the found class or an Empty can.
   */
  def findClass[C <: AnyRef](name: String, where: List[String], modifiers: List[Function1[String, String]], targetType: Class[C]): Box[Class[C]] =
    (for (
      place <- where.projection;
      mod <- modifiers.projection;
      val fullName = place + "." + mod(name);
      val ignore = List(classOf[ClassNotFoundException], classOf[ClassCastException]);
      klass <- tryo(ignore)(Class.forName(fullName).asSubclass(targetType).asInstanceOf[Class[C]])
    ) yield klass).firstOption

  /**
   * General method to in find a class according to its type, its name, a list of possible
   * packages and a list of functions modifying the given name create a target name to look for
   * (e.g: 'name' is hello_world and the target name may be 'HelloWorld').
   *
   * @parameter C type of the class to find
   * @parameter name name of the class to find
   * @parameter where list of package names which may contain the class
   * @parameter modifiers list of functions that modify the 'name' of the class (e.g., leave it alone, make it camel case, etc.)
   *
   * @return a Box, either containing the found class or an Empty can.
   */
  def findType[C <: AnyRef](name: String, where: List[String], modifiers: List[String => String])(implicit m: Manifest[C]): Box[Class[C]] =
    findClass(name, where, modifiers, m.erasure.asInstanceOf[Class[C]])

  /**
   * General method to in find a class according to its name, a list of possible packages and a
   * list of functions modifying the given name create a target name to look for (e.g: 'name' is
   * hello_world and the target name may be 'HelloWorld').
   *
   * @parameter name name of the class to find
   * @parameter where list of package names which may contain the class
   * @parameter modifiers list of functions that modify the 'name' of the class (e.g., leave it alone, make it camel case, etc.)
   *
   * @return a Box, either containing the found class or an Empty can.
   */
  def findClass(name: String, where: List[String], modifiers: List[String => String]): Box[Class[AnyRef]] =
    findType[AnyRef](name, where, modifiers)

  /**
   * Find a class given its name and a list of packages, turning underscored names to
   * CamelCase if necessary.
   *
   * @parameter name name of the class to find
   * @parameter where list of package names which may contain the class
   * @parameter targetType optional expected type which the retrieved class should conform to
   *
   * @return a Box, either containing the found class or an Empty can.
   */
  def findClass[C <: AnyRef](name: String, where: List[String], targetType: Class[C]): Box[Class[C]] =
    findClass(name, where, nameModifiers, targetType)

  /**
   * Find a class given its type, its name and a list of packages, turning underscored names to
   * CamelCase if necessary.
   *
   * @parameter C type of the class to find
   * @parameter name name of the class to find
   * @parameter where list of package names which may contain the class
   *
   * @return a Box, either containing the found class or an Empty can.
   */
  def findType[C <: AnyRef](name: String, where: List[String])(implicit m: Manifest[C]): Box[Class[C]] =
    findType[C](name, where, nameModifiers)

  /**
   * Find a class given its name and a list of packages, turning underscored names to CamelCase if
   * necessary.
   *
   * @parameter name name of the class to find
   * @parameter where list of package names which may contain the class
   *
   * @return a Box, either containing the found class or an Empty can.
   */
  def findClass(name: String, where: List[String]): Box[Class[AnyRef]] =
    findClass(name, where, nameModifiers)

  /**
   * Find a class given its type, a list of possible names and corresponding packages, turning
   * underscored names to CamelCase if necessary
   *
   * @parameter C type of the class to find
   * @parameter where list of pairs (name, package names) which may contain the class
   *
   * @return a Box, either containing the found class or an Empty can.
   */
  def findType[C <: AnyRef](where: List[(String, List[String])])(implicit m: Manifest[C]): Box[Class[C]] =
    (for (
      (name, packages) <- where.projection;
      klass <- findType[C](name, packages)
    ) yield klass).firstOption

  /**
   * Find a class given a list of possible names and corresponding packages, turning underscored
   * names to CamelCase if necessary
   *
   * @parameter where list of pairs (name, package names) which may contain the class
   *
   * @return a Box, either containing the found class or an Empty can.
   */
  def findClass(where: List[(String, List[String])]): Box[Class[AnyRef]] =
    findType[AnyRef](where)

  /**
   * Turns a string of format "foo_bar" into camel case "FooBar"
   *
   * Functional code courtesy of Jamie Webb (j@jmawebb.cjb.net) 2006/11/28
   * @param name the String to CamelCase
   *
   * @return the CamelCased string
   */
  def camelCase(name : String): String = {
    def loop(x : List[Char]): List[Char] = (x: @unchecked) match {
      case '_' :: '_' :: rest => loop('_' :: rest)
      case '_' :: c :: rest => Character.toUpperCase(c) :: loop(rest)
      case '_' :: Nil => Nil
      case c :: rest => c :: loop(rest)
      case Nil => Nil
    }
    if (name == null)
      ""
    else
      List.toString(loop('_' :: name.toList))
  }

  /**
   * Turn a string of format "foo_bar" into camel case with the first letter in lower case: "fooBar"
   * This function is especially used to camelCase method names.
   *
   * @param name the String to CamelCase
   *
   * @return the CamelCased string
   */
  def camelCaseMethod(name: String): String = {
    val tmp: String = camelCase(name)
    if (tmp.length == 0)
      ""
    else
      tmp.substring(0,1).toLowerCase + tmp.substring(1)
  }

  /**
   * Turn a string of format "FooBar" into camel case "foo_bar"
   *
   * @return the underscored string
   */
  def unCamelCase(name : String) = {
    def loop(x : List[Char]) : List[Char] = x match {
      case c :: rest if (Character.isUpperCase(c)) => '_' :: Character.toLowerCase(c) :: loop(rest)
      case c :: rest => c :: loop(rest)
      case Nil => Nil
    }
    if (name.isEmpty)
      ""
    else
      List.toString(Character.toLowerCase(name.charAt(0)) :: loop(name.substring(1).toList))
  }

  /**
   * @return true if the method is public and has no parameters
   */
  def callableMethod_?(meth: Method) = {
    meth != null && meth.getParameterTypes.length == 0 && isPublic(meth.getModifiers)
  }

  /**
   * Is the clz an instance of (assignable from) any of the classes in the list
   *
   * @param clz the class to test
   * @param toMatch the list of classes to match against
   *
   * @return true if clz is assignable from any of the matching classes
   *
   * @deprecated use List.exists instead
   */
  @deprecated
  def containsClass[C](clz: Class[C], toMatch: List[Class[_]]): Boolean =
    if (toMatch eq null) false
    else toMatch.exists(_.isAssignableFrom(clz))

  /**
   * Check that the method 'name' is callable for class 'clz'
   *
   * @param clz the class supposed to own the method
   * @param name name of the method to test
   *
   * @return true if the method exists on the class and is callable
   */
  def classHasControllerMethod(clz: Class[_], name: String): Boolean = {
    tryo {
      clz match {
        case null => false
        case _ => callableMethod_?(clz.getDeclaredMethod(name))
      }
    } openOr false
  }

  /**
   * Invoke a controller method (parameterless, public) on a class
   *
   * @param clz the class owning the method
   * @param name name of the method to invoke
   *
   * @return the result of the method invocation or throws the root exception causing an error
   */
  def invokeControllerMethod(clz: Class[_], meth: String) = {
    try {
      clz.getMethod(meth).invoke(clz.newInstance)
    } catch {
      case c : InvocationTargetException => {
        def findRoot(e : Throwable) { if (e.getCause == null || e.getCause == e) throw e else findRoot(e.getCause) }
        findRoot(c)
      }
    }
  }

  /**
   * Invoke the given method for the given class, with no params.
   * The class is not instanciated if the method is static, otherwise the passed instance is used
   *
   * @param clz class whose method should be invoked
   * @param inst instance of the class who method should be invoked, if the method is not static
   * @param meth method to invoke
   *
   * @return a Box containing the value returned by the method
   */
  def invokeMethod[C](clz: Class[C], inst: AnyRef, meth: String): Box[Any] = invokeMethod(clz, inst, meth, Nil.toArray)

  /**
   * Invoke the given method for the given class, with some parameters.
   * Tries the method name, then the method as a CamelCased name and the method as a camelCased name
   * The class is not instanciated if the method is static, otherwise the passed instance is used
   *
   * @param clz class whose method should be invoked
   * @param inst instance of the class who method should be invoked, if the method is not static
   * @param meth method to invoke
   * @param params parameters to pass to the method
   *
   * @return a Box containing the value returned by the method
   */
  def invokeMethod[C](clz: Class[C], inst: AnyRef, meth: String, params: Array[AnyRef]): Box[Any] = {
    _invokeMethod(clz, inst, meth, params, Empty) or
    _invokeMethod(clz, inst, camelCase(meth), params, Empty) or
    _invokeMethod(clz, inst, camelCaseMethod(meth), params, Empty)
  }

  /**
   * Invoke the given method for the given class, with some parameters and their types
   * Tries the method name, then the method as a CamelCased name and the method as a camelCased name
   * The class is not instanciated if the method is static, otherwise the passed instance is used
   *
   * @param clz class whose method should be invoked
   * @param inst instance of the class who method should be invoked, if the method is not static
   * @param meth method to invoke
   * @param params parameters to pass to the method
   * @param ptypes list of types of the parameters
   *
   * @return a Box containing the value returned by the method
   */
  def invokeMethod[C](clz: Class[C], inst: AnyRef, meth: String, params: Array[AnyRef], ptypes: Array[Class[_]]): Box[Any] = {
    _invokeMethod(clz, inst, meth, params, Full(ptypes)) or
    _invokeMethod(clz, inst, camelCase(meth), params, Full(ptypes)) or
    _invokeMethod(clz, inst, camelCaseMethod(meth), params, Full(ptypes))
  }


  /**
   * Invoke the given method for the given class, with the given params.
   * The class is not instanciated if the method is static, otherwise the passed instance is used
   *
   * @param clz class whose method should be invoked
   * @param inst instance of the class who method should be invoked, if the method is not static
   * @param meth method to invoke
   * @param params parameters to pass to the method
   * @param ptypes list of types of the parameters
   *
   * @return a Box containing the value returned by the method
   */
  private def _invokeMethod[C](clz: Class[C], inst: AnyRef, meth: String, params: Array[AnyRef], ptypes: Box[Array[Class[_]]]): Box[Any] = {
     // try to find a method matching the given parameters
    def possibleMethods: List[Method] = {
      /*
       * try to find a method with the same name and the same number of arguments. Doesn't check the types.
       * The reason is that it's hard to know for the programmer what is the class name of a given object/class, because scala
       * add some extra $ for ex.
       */
      def alternateMethods: List[Method] = clz.getDeclaredMethods.toList.filter( m => m.getName.equals(meth) &&
                                            isPublic(m.getModifiers) &&
                                            m.getParameterTypes.length == params.length)

      try {
        val classes: Array[Class[_]] = ptypes openOr params.map(_.getClass)
        List(clz.getMethod(meth, classes : _*))
      } catch {
        case e: NullPointerException => Nil
        case e: NoSuchMethodException => alternateMethods
      }
    }
    def findFirst[T, U](l: List[T], f: T => U, predicate: U => Boolean): Box[U] = {
      l match {
        case Nil => Empty
        case x :: xs => {
         val result = f(x)
         if (predicate(result)) Full(result) else findFirst(xs, f, predicate)
        }
      }
    }
    possibleMethods.elements.filter(m => inst != null || isStatic(m.getModifiers)).
                                   map((m: Method) => tryo{m.invoke(inst, params : _*)}).
                                   find((x: Box[Any]) => x match {
                                            case result@Full(_) => true
                                            case Failure(_, Full(c: IllegalAccessException), _) => false
                                            case Failure(_, Full(c: IllegalArgumentException), _) => false
                                            case Failure(_, Full(c), _) => if (c.getCause != null) throw c.getCause else throw c
					    case _ => false
                                   }) match {
                                            case Some(result@Full(_)) => result
                                            case _ => Failure("invokeMethod " + meth, Empty, Empty)
                                   }
  }

  /**
   * Create a new instance of a class
   *
   * @return a Full can with the instance or a Failure if the instance can't be created
   */
  def instantiate[C](clz: Class[C]): Box[C] = tryo { clz.newInstance }

  /**
   * Create a function (the 'invoker') which will trigger any public, parameterless method
   * That function will throw the cause exception if the method can't be invoked
   *
   * @param clz class whose method should be invoked
   * @param on instance whose method must be invoked
   *
   * @return Empty if instance is null or Full(invoker)
   */
  def createInvoker[C <: AnyRef](name: String, on: C): Box[() => Box[Any]] = {
    def controllerMethods(instance: C) = instance.getClass.getDeclaredMethods.filter { m =>
      m.getName == name && isPublic(m.getModifiers) && m.getParameterTypes.isEmpty
    }
    on match {
      case null => Empty
      case instance => {
        controllerMethods(instance).toList match {
            case Nil => Empty
            case x :: xs => Full(() => {
              try {
                Full(x.invoke(instance))
              } catch {
                case e : InvocationTargetException => throw e.getCause
              }
            }
          )
        }
      }
    }
  }
}
