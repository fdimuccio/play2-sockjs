import sbt._

object ShellPrompt {
  import scala.sys.process._

  val devnull = ProcessLogger(_ => (), _ => ())

  def currBranch = (
    ("git status -sb" lineStream_! devnull headOption)
      getOrElse "-" stripPrefix "## ")

  def buildShellPrompt(version: String) = (state: State) => {
    val currProject = Project.extract(state).currentProject.id
    "%s:%s:%s> ".format(currProject, currBranch, version)
  }
}