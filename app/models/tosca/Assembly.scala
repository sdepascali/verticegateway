package models.tosca

import scalaz._
import Scalaz._
import scalaz.effect.IO
import scalaz.EitherT._
import scalaz.Validation
import scalaz.Validation.FlatMap._
import scalaz.NonEmptyList._
import scalaz.syntax.SemigroupOps
import scala.collection.mutable.ListBuffer

import cache._
import db._
import models.Constants._
import models.json.tosca._
import models.json.tosca.carton._
import models.base.RequestInput
import io.megam.auth.funnel.FunnelErrors._

import com.datastax.driver.core.{ ResultSet, Row }
import com.websudos.phantom.dsl._
import scala.concurrent.{ Future => ScalaFuture }
import com.websudos.phantom.connectors.{ ContactPoint, KeySpaceDef }
import scala.concurrent.Await
import scala.concurrent.duration._

import utils.DateHelper
import io.megam.util.Time
import org.joda.time.{DateTime, DateTimeZone}
import org.joda.time.format.{DateTimeFormat,ISODateTimeFormat}

import io.megam.common.uid.UID
import net.liftweb.json._
import net.liftweb.json.scalaz.JsonScalaz._
import java.nio.charset.Charset
import controllers.stack.ImplicitJsonFormats


/**
 * @author rajthilak
 *
 */
case class Operation(operation_type: String, description: String, properties: models.tosca.KeyValueList, status: String)

case class AssemblyResult(id: String,
                          org_id: String,
                          account_id: String,
                          name: String,
                          components: models.tosca.ComponentLinks,
                          tosca_type: String,
                          policies: models.tosca.PoliciesList,
                          inputs: models.tosca.KeyValueList,
                          outputs: models.tosca.KeyValueList,
                          status: String,
                          state: String,
                          json_claz: String,
                          created_at: DateTime)

sealed class AssemblySacks extends CassandraTable[AssemblySacks, AssemblyResult]  with ImplicitJsonFormats {

  object org_id extends StringColumn(this) with PartitionKey[String]
  object id extends StringColumn(this) with PrimaryKey[String]
  object created_at extends DateTimeColumn(this) with PrimaryKey[DateTime]
    object account_id extends StringColumn(this)
  object name extends StringColumn(this)
  object components extends ListColumn[AssemblySacks, AssemblyResult, String](this)
  object tosca_type extends StringColumn(this)

  object policies extends JsonListColumn[AssemblySacks, AssemblyResult, Policy](this) {
    override def fromJson(obj: String): Policy = {
      JsonParser.parse(obj).extract[Policy]
    }
    override def toJson(obj: Policy): String = {
      compactRender(Extraction.decompose(obj))
    }
  }

  object inputs extends JsonListColumn[AssemblySacks, AssemblyResult, KeyValueField](this) {
    override def fromJson(obj: String): KeyValueField = {
      JsonParser.parse(obj).extract[KeyValueField]
    }

    override def toJson(obj: KeyValueField): String = {
      compactRender(Extraction.decompose(obj))
    }
  }

  object outputs extends JsonListColumn[AssemblySacks, AssemblyResult, KeyValueField](this) {
    override def fromJson(obj: String): KeyValueField = {
      JsonParser.parse(obj).extract[KeyValueField]
    }

    override def toJson(obj: KeyValueField): String = {
      compactRender(Extraction.decompose(obj))
    }
  }

  object status extends StringColumn(this)
  object state extends StringColumn(this)
  object json_claz extends StringColumn(this)

  def fromRow(row: Row): AssemblyResult = {
    AssemblyResult(
      id(row),
      org_id(row),
      account_id(row),
      name(row),
      components(row),
      tosca_type(row),
      policies(row),
      inputs(row),
      outputs(row),
      status(row),
      state(row),
      json_claz(row),
      created_at(row))
  }
}

abstract class ConcreteAssembly extends AssemblySacks with RootConnector {

  override lazy val tableName = "assembly"
  override implicit def space: KeySpace = scyllaConnection.space
  override implicit def session: Session = scyllaConnection.session

  def insertNewRecord(ams: AssemblyResult): ValidationNel[Throwable, ResultSet] = {
    val res = insert.value(_.id, ams.id)
      .value(_.org_id, ams.org_id)
      .value(_.account_id, ams.account_id)
      .value(_.name, ams.name)
      .value(_.components, ams.components)
      .value(_.tosca_type, ams.tosca_type)
      .value(_.policies, ams.policies)
      .value(_.inputs, ams.inputs)
      .value(_.outputs, ams.outputs)
      .value(_.status, ams.status)
      .value(_.state, ams.state)
      .value(_.json_claz, ams.json_claz)
      .value(_.created_at, ams.created_at)
      .future()
    Await.result(res, 5.seconds).successNel
  }

  def listRecords(email: String, org: String): ValidationNel[Throwable, Seq[AssemblyResult]] = {
    val res = select.allowFiltering().where(_.org_id eqs org).fetch()
    Await.result(res, 5.seconds).successNel
  }

  //Grand dump of all.
  def listallRecords(): ValidationNel[Throwable, Seq[AssemblyResult]] = {
     val res = select.fetch()
    Await.result(res, 5.seconds).successNel
   }

  def dateRangeBy(startdate: String, enddate: String): ValidationNel[Throwable, Seq[AssemblyResult]] = {
      val starttime = ISODateTimeFormat.dateTime().withZone(DateTimeZone.UTC).parseDateTime(startdate);
      val endtime = ISODateTimeFormat.dateTime().withZone(DateTimeZone.UTC).parseDateTime(enddate);

     val res = select.allowFiltering().where(_.created_at gte starttime).and(_.created_at lte endtime).fetch()
    Await.result(res, 5.seconds).successNel
  }

  def getRecord(id: String): ValidationNel[Throwable, Option[AssemblyResult]] = {
    val res = select.allowFiltering().where(_.id eqs id).and(_.created_at lte DateHelper.now()).one()
    Await.result(res, 5.seconds).successNel
  }

  def updateRecord(org_id: String, rip: AssemblyResult): ValidationNel[Throwable, ResultSet] = {
    val res = update.where(_.created_at eqs rip.created_at).and(_.id eqs rip.id).and(_.org_id eqs org_id)
      .modify(_.name setTo rip.name)
      .and(_.components setTo rip.components)
      .and(_.tosca_type setTo rip.tosca_type)
      .and(_.policies setTo rip.policies)
      .and(_.inputs setTo rip.inputs)
      .and(_.outputs setTo rip.outputs)
      .and(_.status setTo rip.status)
      .and(_.state setTo rip.state)
      .future()
    Await.result(res, 5.seconds).successNel
  }

}

case class Policy(name: String, ptype: String, members: models.tosca.MembersList)

case class Assembly(name: String,
                    components: models.tosca.ComponentsList,
                    tosca_type: String,
                    policies: models.tosca.PoliciesList,
                    inputs: models.tosca.KeyValueList,
                    outputs: models.tosca.KeyValueList,
                    status: String,
                    state: String)

// The difference between Assembly and AssemblyUpdateInput is the `id` field
case class AssemblyUpdateInput(id: String,
                               name: String,
                               components: models.tosca.ComponentLinks,
                               tosca_type: String,
                               policies: models.tosca.PoliciesList,
                               inputs: models.tosca.KeyValueList,
                               outputs: models.tosca.KeyValueList,
                               status: String,
                               state: String) {
}

case class WrapAssemblyResult(thatGS: Option[AssemblyResult]) extends ImplicitJsonFormats  {

  val asm = thatGS.get
  val cattype = asm.tosca_type.split('.')(1)
  val domain = asm.inputs.find(_.key.equalsIgnoreCase(DOMAIN))
  val alma = asm.name + "." + domain.get.value //None is ignored here. dangerous.

}

object Assembly extends ConcreteAssembly {

  def findById(assemblyID: Option[List[String]]): ValidationNel[Throwable, AssemblyResults] = {
    (assemblyID map {
      _.map { asm_id =>
        (getRecord(asm_id) leftMap { t: NonEmptyList[Throwable] =>
          new ServiceUnavailableError(asm_id, (t.list.map(m => m.getMessage)).mkString("\n"))
        }).toValidationNel.flatMap { xso: Option[AssemblyResult] =>
          xso match {
            case Some(xs) => {
              play.api.Logger.warn(("%s%s%-20s%s").format(Console.GREEN, Console.BOLD, "Assembly."+asm_id + " successfully", Console.RESET))
              Validation.success[Throwable, AssemblyResults](List(xs.some)).toValidationNel //screwy kishore, every element in a list ?
            }
            case None => {
              Validation.failure[Throwable, AssemblyResults](new ResourceItemNotFound(asm_id, "")).toValidationNel
            }
          }
        }
      } // -> VNel -> fold by using an accumulator or successNel of empty. +++ => VNel1 + VNel2
    } map {
      _.foldRight((AssemblyResults.empty).successNel[Throwable])(_ +++ _)
    }).head //return the folded element in the head.
  }

  def listAll(): ValidationNel[Throwable, Seq[AssemblyResult]] = {
     (listallRecords() leftMap { t: NonEmptyList[Throwable] =>
      new ResourceItemNotFound("", "Assembly = nothing found.")
    }).toValidationNel.flatMap { nm: Seq[AssemblyResult] =>
      if (!nm.isEmpty)
        Validation.success[Throwable, Seq[AssemblyResult]](nm).toValidationNel
      else
        Validation.failure[Throwable, Seq[AssemblyResult]](new ResourceItemNotFound("", "Assembly = nothing found.")).toValidationNel
     }
  }


  def findByDateRange(startdate: String, enddate: String): ValidationNel[Throwable, Seq[AssemblyResult]] = {
    dateRangeBy(startdate, enddate) match {
      case Success(value) => Validation.success[Throwable, Seq[AssemblyResult]](value).toValidationNel
      case Failure(err) => Validation.success[Throwable, Seq[AssemblyResult]](List()).toValidationNel
    }
  }

  private def updateAssemblySack(org_id: String, input: String): ValidationNel[Throwable, Option[AssemblyResult]] = {
    val ripNel: ValidationNel[Throwable, AssemblyUpdateInput] = (Validation.fromTryCatchThrowable[AssemblyUpdateInput, Throwable] {
      parse(input).extract[AssemblyUpdateInput]
    } leftMap { t: Throwable => new MalformedBodyError(input, t.getMessage) }).toValidationNel //capture failure

    for {
      rip <- ripNel
      asm_collection <- (Assembly.findById(List(rip.id).some) leftMap { t: NonEmptyList[Throwable] => t })
    } yield {
      val asm = asm_collection.head
      val json = AssemblyResult(rip.id, org_id, asm.get.account_id, asm.get.name, asm.get.components, asm.get.tosca_type, rip.policies, rip.inputs,
         rip.outputs,
         NilOrNot(rip.status, asm.get.status),
         NilOrNot(rip.state, asm.get.state),
         asm.get.json_claz,
         asm.get.created_at)
      json.some
    }
  }

  private def NilOrNot(rip: String, aor: String): String = {
    rip == null || rip == "" match {
      case true => return aor
      case false => return rip
    }
  }

  def update(org_id: String, input: String): ValidationNel[Throwable, Option[AssemblyResult]] = {

    for {
      gs <- (updateAssemblySack(org_id, input) leftMap { err: NonEmptyList[Throwable] => err })
      set <- (updateRecord(org_id, gs.get) leftMap { t: NonEmptyList[Throwable] => t })
    } yield {
      play.api.Logger.warn(("%s%s%-20s%s").format(Console.GREEN, Console.BOLD, "Assembly.updated successfully", Console.RESET))
      gs
    }
  }
}

object AssemblysList extends ConcreteAssembly {

  implicit def AssemblysListsSemigroup: Semigroup[AssemblysLists] = Semigroup.instance((f1, f2) => f1.append(f2))

  def apply(assemblyList: List[Assembly]): AssemblysList = { assemblyList }

  def createLinks(authBag: Option[io.megam.auth.stack.AuthBag], input: AssemblysList): ValidationNel[Throwable, AssemblysLists] = {
    val res = (input map {
      asminp => (create(authBag, asminp))
    }).foldRight((AssemblysLists.empty).successNel[Throwable])(_ +++ _)
      res.getOrElse(new ResourceItemNotFound(authBag.get.email, "assembly = ah. ouh. for some reason.").failureNel[AssemblysLists])
      res
  }


  def create(authBag: Option[io.megam.auth.stack.AuthBag], input: Assembly): ValidationNel[Throwable, AssemblysLists] = {
    for {
      ogsi <- mkAssemblySack(authBag, input) leftMap { err: NonEmptyList[Throwable] => err }
      set <- (insertNewRecord(ogsi.get) leftMap { t: NonEmptyList[Throwable] => t })
    } yield {
      play.api.Logger.warn(("%s%s%-20s%s").format(Console.GREEN, Console.BOLD, "Assembly.created successfully", Console.RESET))
      nels(ogsi)
    }
  }

  private def mkAssemblySack(authBag: Option[io.megam.auth.stack.AuthBag], rip: Assembly): ValidationNel[Throwable, Option[AssemblyResult]] = {
    var outlist = rip.outputs
    for {
      uir <- (UID("asm").get leftMap { ut: NonEmptyList[Throwable] => ut })
      com <- (ComponentsList.createLinks(authBag, rip.components, (uir.get._1 + uir.get._2)) leftMap { t: NonEmptyList[Throwable] => t })
    } yield {
      var components_links = new ListBuffer[String]()
      if (com.size > 1) {
        for (component <- com) {
          component match {
            case Some(value) => components_links += value.id
            case None => components_links
          }
        }
      }
      val json = AssemblyResult(uir.get._1 + uir.get._2, authBag.get.org_id, authBag.get.email, rip.name, components_links.toList, rip.tosca_type, rip.policies, rip.inputs, outlist, rip.status, rip.state, "Megam::Assembly", DateHelper.now())
      json.some
    }
  }

}
