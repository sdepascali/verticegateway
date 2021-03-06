package models.admin.reports

import scalaz._
import Scalaz._
import scalaz.Validation.FlatMap._
import models.admin.{ ReportInput, ReportResult }

trait Reporter { def report: ValidationNel[Throwable, Option[ReportResult]] }

object Builder {
  def apply(ri: ReportInput): Builder = new Builder(ri)
}

class Builder(ri: ReportInput) {

  //When a new report type is needed add a constant type here.
  val SALES         = "sales"
  val LAUNCHES      = "launches"

  val USERDOT       = "userdot"
  val LAUNCHDOT     = "launchdot"

  //When a new report is needed add a class that will be a reporter.
  private val GLOBAL_REPORTS = Map(SALES         -> "models.admin.reports.Sales",
                                   USERDOT       -> "models.admin.reports.UsersDot",
                                   LAUNCHDOT     -> "models.admin.reports.LaunchesDot",
                                   LAUNCHES      -> "models.admin.reports.Launches")

  private lazy val cls =  GLOBAL_REPORTS.get(ri.type_of).getOrElse("models.admin.reports.NoOp")

  private lazy val reporter: Reporter = {
    Class.forName(cls).getConstructor(Class.forName("models.admin.ReportInput")).newInstance(ri).asInstanceOf[Reporter]
  }

  def build: ValidationNel[Throwable, Option[ReportResult]] = reporter.report

}
