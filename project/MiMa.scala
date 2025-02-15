/*
 * Copyright (C) 2009-2020 Lightbend Inc. <https://www.lightbend.com>
 */

package akka

import sbt._
import sbt.Keys._
import com.typesafe.tools.mima.core.ProblemFilter
import com.typesafe.tools.mima.plugin.MimaPlugin
import com.typesafe.tools.mima.plugin.MimaPlugin.autoImport._

import scala.util.Try

object MiMa extends AutoPlugin {

  override def requires = MimaPlugin
  override def trigger = allRequirements

  //Exclude these non-existent versions when checking compatibility with previous versions
  private val ignoredModules = Map(
    "akka-http-caching" -> Set("10.0.0", "10.0.1", "10.0.2", "10.0.3", "10.0.4", "10.0.5", "10.0.6", "10.0.7", "10.0.8", "10.0.9", "10.0.10")
  )

  // A fork is a branch of the project where new releases are created that are not ancestors of the current release line
  val forks = Seq("10.0.", "10.1.")
  val currentFork = "10.2."

  // manually maintained list of previous versions to make sure all incompatibilities are found
  // even if so far no files have been been created in this project's mima-filters directory
  val pre213Versions = Set(
    "10.0.15",
    "10.1.0",
    "10.1.1",
    "10.1.2",
    "10.1.3",
    "10.1.4",
    "10.1.5",
    "10.1.6",
    "10.1.7",
  )

  val `10.1-post-2.13-versions` = Set(
    "10.1.8",
    "10.1.9",
    "10.1.10",
    "10.1.11",
    "10.1.12",
    "10.1.13",
    "10.1.14"
  )
  val `10.2-versions` = Set(
    "10.2.0",
    "10.2.1",
    "10.2.2",
    "10.2.3",
    "10.2.4",
    "10.2.5",
  )

  val post213Versions = `10.1-post-2.13-versions` ++ `10.2-versions`

  lazy val latestVersion = post213Versions.max(versionOrdering)
  lazy val latest101Version = `10.1-post-2.13-versions`.max(versionOrdering)

  override val projectSettings = Seq(
    mimaPreviousArtifacts := {
      val versions =
        if (scalaBinaryVersion.value == "2.13") post213Versions
        else pre213Versions ++ post213Versions

      versions.collect { case version if !ignoredModules.get(name.value).exists(_.contains(version)) =>
        organization.value %% name.value % version
      }
    },
    mimaBackwardIssueFilters := {
      val filters = mimaBackwardIssueFilters.value
      val allVersions = (mimaPreviousArtifacts.value.map(_.revision) ++ filters.keys).toSeq

      /**
       * Collect filters for all versions of a fork and add them as filters for the latest version of the fork.
       * Otherwise, new versions in the fork that are listed above will reintroduce issues that were already filtered
       * out before. We basically rebase this release line on top of the fork from the view of Mima.
       */
      def forkFilter(fork: String): Option[(String, Seq[ProblemFilter])] = {
        val forkVersions = filters.keys.filter(_.startsWith(fork)).toSeq
        val collectedFilterOption = forkVersions.map(filters).reduceOption(_ ++ _)
        collectedFilterOption.map(latestForkVersion(fork, allVersions) -> _)
      }

      forks.flatMap(forkFilter).toMap ++
      filters.filterKeys(_ startsWith currentFork)
    }
  )

  def latestForkVersion(fork: String, allVersions: Seq[String]): String =
    allVersions
      .filter(_.startsWith(fork))
      .sorted(versionOrdering)
      .last

  // copied from https://github.com/lightbend/migration-manager/blob/e54f3914741b7f528da5507e515cc84db60abdd5/core/src/main/scala/com/typesafe/tools/mima/core/ProblemReporting.scala#L14-L19
  private lazy val versionOrdering = Ordering[(Int, Int, Int)].on { version: String =>
    val ModuleVersion = """(\d+)\.?(\d+)?\.?(.*)?""".r
    val ModuleVersion(epoch, major, minor) = version
    val toNumeric = (revision: String) => Try(revision.replace("x", Short.MaxValue.toString).filter(_.isDigit).toInt).getOrElse(0)
    (toNumeric(epoch), toNumeric(major), toNumeric(minor))
  }
}
